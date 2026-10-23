import base64
import json
import os
from pathlib import Path
import stat
import tempfile
import unittest
import io
import zipfile
from unittest.mock import patch

import update_release as release


class UpdateReleaseTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory(prefix='TEST-ONLY-metadata-keys-')
        cls.root = Path(cls.directory.name)
        cls.secret = patch.dict(os.environ, {release.PASSWORD_ENV: 'test-only-password-never-production-12345'})
        cls.secret.start()
        cls.key = cls.root/'private'/'test-only-key.pem'
        cls.public = cls.root/'public.json'
        cls.config = release.generate(cls.key, cls.public)
        cls.info = {'version_code': 23, 'version_name': '20.0-signed-updates', 'apk_size': 5,
                    'apk_sha256': 'a' * 64}
        cls.url = 'https://raw.githubusercontent.com/efrgdgr0024345/autophone/' + 'a'*40 + '/docs/ui20/install/CatAI-20.apk'

    @classmethod
    def tearDownClass(cls):
        cls.secret.stop(); cls.directory.cleanup()

    def signed(self, sequence=1, previous=None, now=1800000000):
        return release.sign(self.info, self.url, sequence, self.config, self.key, previous, now)

    def test_private_key_is_encrypted_private_and_separate_from_public_config(self):
        self.assertTrue(self.key.read_bytes().startswith(b'-----BEGIN ENCRYPTED PRIVATE KEY-----'))
        self.assertEqual(0o600, stat.S_IMODE(self.key.stat().st_mode))
        self.assertEqual(0o700, stat.S_IMODE(self.key.parent.stat().st_mode))
        self.assertNotIn('PRIVATE KEY', self.public.read_text())
        self.assertEqual(self.config, release.load_trust(self.public))

    def test_no_password_no_key_generation(self):
        with patch.dict(os.environ, {release.PASSWORD_ENV: ''}):
            with self.assertRaises(ValueError): release.generate(self.root/'new'/'key.pem', self.root/'new-public.json')
        self.assertFalse((self.root/'new'/'key.pem').exists())

    def test_existing_authority_cannot_be_overwritten(self):
        before = self.key.read_bytes()
        with self.assertRaises(FileExistsError): release.generate(self.key, self.public)
        self.assertEqual(before, self.key.read_bytes())

    def test_private_key_under_checkout_is_rejected(self):
        with self.assertRaises(ValueError): release.private_path(release.ROOT/'dangerous-key.pem', new=True)

    def test_public_output_cannot_overwrite_private_key(self):
        path = self.root/'overlap'/'key.pem'
        with self.assertRaises(ValueError): release.generate(path, path)
        self.assertFalse(path.exists())

    def test_wrong_password_cannot_sign(self):
        with patch.dict(os.environ, {release.PASSWORD_ENV: 'wrong-password-at-least-24-characters'}):
            with self.assertRaises(ValueError): self.signed()

    def test_signature_uses_exact_app_domain_and_payload(self):
        envelope = self.signed()
        m = release.verify(envelope, self.config, now=1800000010)
        self.assertEqual(23, m['version_code'])
        self.assertEqual(1, m['sequence'])
        self.assertEqual(6*86400, m['expires']-m['issued'])

    def test_modified_metadata_fails_authentication(self):
        e = json.loads(self.signed())
        payload = json.loads(base64.b64decode(e['payload']))
        payload['version_code'] = 999
        e['payload'] = base64.b64encode(json.dumps(payload).encode()).decode()
        with self.assertRaises(ValueError): release.verify(json.dumps(e).encode(), self.config)

    def test_expired_feed_is_rejected_for_clients_but_usable_for_publisher_floor(self):
        data = self.signed()
        with self.assertRaises(ValueError): release.verify(data, self.config, now=1801000000)
        next_feed = self.signed(2, data, now=1801000000)
        self.assertEqual(2, release.verify(next_feed, self.config, now=1801000000)['sequence'])

    def test_sequence_reuse_and_rollback_rejected(self):
        data = self.signed()
        with self.assertRaises(ValueError): self.signed(1, data)
        with self.assertRaises(ValueError): self.signed(2)
        old = dict(self.info)
        try:
            self.info = dict(self.info, version_code=22)
            with self.assertRaises(ValueError): self.signed(2, data)
        finally: self.info = old

    def test_bad_signature_unknown_envelope_or_duplicate_fields_rejected(self):
        e = json.loads(self.signed())
        e['signature'] = base64.b64encode(b'wrong').decode()
        with self.assertRaises(ValueError): release.verify(json.dumps(e).encode(), self.config)
        with self.assertRaises(ValueError): release.strict_json(b'{"payload":"a","payload":"b"}')
        with self.assertRaises(ValueError): release.verify(b'{}', self.config)

    def test_only_immutable_public_apk_urls_allowed(self):
        release.validate_apk_url(self.url)
        for value in [self.url.replace('https:', 'http:'), self.url+'?secret=x', self.url.replace('a'*40, 'main'),
                      self.url.replace('raw.githubusercontent.com', 'evil.test'), self.url.replace('/install/', '/../')]:
            with self.assertRaises(ValueError): release.validate_apk_url(value)

    def test_tampered_public_fingerprint_is_rejected(self):
        bad = self.root/'bad-public.json'
        bad.write_text(json.dumps(dict(self.config, public_key_sha256='b'*64)))
        with self.assertRaises(ValueError): release.load_trust(bad)

    def test_generated_kotlin_contains_public_key_only_and_exact_host_policy(self):
        text = release.kotlin_trust(self.config)
        self.assertIn(self.config['public_key_sha256'], text)
        self.assertIn(release.FEED, text)
        self.assertNotIn('PRIVATE KEY', text)
        self.assertNotIn('test-only-password', text)

    def hosted_fixture(self, pinned=True):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as z:
            z.writestr('classes.dex', self.config['public_key_sha256'] if pinned else 'unrelated authority')
        data = buffer.getvalue()
        info = dict(self.info, apk_size=len(data), apk_sha256=release.hashlib.sha256(data).hexdigest())
        class Response(io.BytesIO):
            status = 200
            headers = {}
        opener = unittest.mock.Mock()
        opener.open.return_value = Response(data)
        return info, opener

    def test_publication_verifies_hosted_bytes_and_writes_public_data_only(self):
        info, opener = self.hosted_fixture()
        envelope = release.sign(info, self.url, 1, self.config, self.key, now=1800000000)
        calls = []
        def api(method, path, data=None):
            calls.append((method, path, data))
            if method == 'GET': return {'content': base64.b64encode(envelope).decode()}
            return {'sha': 'b'*40}
        with patch.object(release.time, 'time', return_value=1800000001), \
             patch.object(release.urllib.request, 'build_opener', return_value=opener), \
             patch.object(release, 'inspect_apk', return_value=info), patch.object(release, 'github', side_effect=api):
            self.assertEqual('b'*40, release.publish(envelope, self.config, 'aapt', 'apksigner', initial=True))
        public_calls = json.dumps(calls)
        self.assertNotIn('PRIVATE KEY', public_calls)
        self.assertNotIn('test-only-password', public_calls)
        self.assertTrue(any(path == 'git/refs' and data['ref'] == 'refs/heads/blackcat-update-feed' for _, path, data in calls if data))

    def test_publication_rejects_hosted_apk_mismatch_before_any_github_write(self):
        info, opener = self.hosted_fixture()
        envelope = release.sign(info, self.url, 1, self.config, self.key, now=1800000000)
        with patch.object(release.time, 'time', return_value=1800000001), \
             patch.object(release.urllib.request, 'build_opener', return_value=opener), \
             patch.object(release, 'inspect_apk', return_value=dict(info, apk_sha256='c'*64)), \
             patch.object(release, 'github') as api:
            with self.assertRaises(ValueError): release.publish(envelope, self.config, 'aapt', 'apksigner', initial=True)
            api.assert_not_called()

    def test_publication_rejects_apk_with_another_authority(self):
        info, opener = self.hosted_fixture(pinned=False)
        envelope = release.sign(info, self.url, 1, self.config, self.key, now=1800000000)
        with patch.object(release.time, 'time', return_value=1800000001), \
             patch.object(release.urllib.request, 'build_opener', return_value=opener), \
             patch.object(release, 'inspect_apk', return_value=info), patch.object(release, 'github') as api:
            with self.assertRaises(ValueError): release.publish(envelope, self.config, 'aapt', 'apksigner', initial=True)
            api.assert_not_called()


if __name__ == '__main__': unittest.main()
