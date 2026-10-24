import base64
import hashlib
import os
from pathlib import Path
import stat
import tempfile
import time
import unittest
from unittest.mock import patch

import renew_update_feed as renewal
import update_release as release


class RenewalTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='TEST-ONLY-renewal-')
        cls.root = Path(cls.temp.name)
        cls.password = patch.dict(os.environ, {release.PASSWORD_ENV: 'test-only-renewal-password-123456789'})
        cls.password.start()
        cls.key = cls.root / 'private/key.pem'
        cls.config = release.generate(cls.key, cls.root / 'public.json')
        cls.apk = b'TEST ONLY APK'
        cls.info = {'version_code': 24, 'version_name': '20.1-signed-updates',
                    'apk_size': len(cls.apk), 'apk_sha256': hashlib.sha256(cls.apk).hexdigest()}
        cls.url = 'https://raw.githubusercontent.com/efrgdgr0024345/autophone/' + 'a' * 40 + '/docs/ui20/install/CatAI-20.1.apk'

    @classmethod
    def tearDownClass(cls):
        cls.password.stop()
        cls.temp.cleanup()

    def test_real_manifest_is_pinned_and_synthetic_authority_is_rejected(self):
        self.assertEqual(renewal.PIN, renewal.trusted_config(release.ROOT / 'updates/production-trust-v1.json')['public_key_sha256'])
        with self.assertRaises(ValueError):
            renewal.trusted_config(self.root / 'public.json')

    def test_encrypted_secret_is_private_and_removed_from_child_environment(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {renewal.KEY_ENV: self.key.read_text()}):
            key = renewal.write_key(directory)
            self.assertEqual(key.read_bytes(), self.key.read_bytes())
            self.assertEqual(stat.S_IMODE(key.stat().st_mode), 0o600)
            self.assertNotIn(renewal.KEY_ENV, os.environ)

    def test_missing_plaintext_or_oversized_key_secret_rejected(self):
        for value in ['', '-----BEGIN PRIVATE KEY-----\nsecret\n-----END PRIVATE KEY-----', 'x' * 16385]:
            with self.subTest(value_length=len(value)), tempfile.TemporaryDirectory() as directory:
                with patch.dict(os.environ, {renewal.KEY_ENV: value}), self.assertRaises(ValueError):
                    renewal.write_key(directory)
                self.assertFalse(list(Path(directory).iterdir()))

    def run_fixture(self, *, tamper=False, identity=False, future=False, race=False, readback=False):
        old = release.sign(self.info, self.url, 1, self.config, self.key,
                           now=int(time.time()) + (600 if future else -7 * 86400))
        if tamper:
            old = old.replace(b'"signature":"', b'"signature":"AAAA')
        state = {'published': None, 'ref_reads': 0}

        def github(method, endpoint, data=None):
            self.assertEqual(method, 'GET')
            if endpoint == 'git/ref/heads/blackcat-update-feed':
                state['ref_reads'] += 1
                return {'object': {'sha': ('a' if state['ref_reads'] == 1 else 'c' if race else 'b') * 40}}
            if endpoint == 'contents/updates/ai.json?ref=' + 'a' * 40:
                return {'content': base64.b64encode(old).decode()}
            self.assertEqual(endpoint, 'contents/updates/ai.json?ref=' + 'b' * 40)
            return {'content': base64.b64encode(old if readback else state['published']).decode()}

        def publish(envelope, config, aapt, apksigner):
            state['published'] = envelope
            return 'b' * 40

        actual = {**self.info, 'version_code': 25} if identity else self.info
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(release, 'github', side_effect=github), patch.object(renewal, 'download_apk') as download:
                with patch.object(release, 'inspect_apk', return_value=actual), patch.object(release, 'publish', side_effect=publish) as publication:
                    try:
                        result = renewal.renew(self.config, self.key, directory, 'aapt', 'apksigner')
                    except Exception:
                        if tamper or identity or future:
                            publication.assert_not_called()
                        if tamper or future:
                            download.assert_not_called()
                        raise
        return result, old

    def test_authentic_expired_feed_renews_only_times_and_sequence(self):
        (commit, renewed), old = self.run_fixture()
        old = release.verify(old, self.config, fresh=False)
        self.assertEqual(commit, 'b' * 40)
        self.assertEqual(renewed['sequence'], 2)
        self.assertEqual(renewed['expires'] - renewed['issued'], 6 * 86400)
        for k in old.keys() - {'sequence', 'issued', 'expires'}:
            self.assertEqual(old[k], renewed[k])

    def test_tampered_feed_cannot_download_sign_or_publish(self):
        with self.assertRaises(ValueError):
            self.run_fixture(tamper=True)

    def test_changed_apk_identity_cannot_publish(self):
        with self.assertRaises(ValueError):
            self.run_fixture(identity=True)

    def test_future_feed_cannot_download_or_publish(self):
        with self.assertRaises(ValueError):
            self.run_fixture(future=True)

    def test_concurrent_ref_change_is_not_reported_as_success(self):
        with self.assertRaises(ValueError):
            self.run_fixture(race=True)

    def test_final_published_bytes_must_match(self):
        with self.assertRaises(ValueError):
            self.run_fixture(readback=True)

    def test_download_checks_https_no_redirects_bounds_and_hash(self):
        class Response:
            status = 200
            headers = {}
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def read(self, size):
                data, self.data = self.data, b''
                return data

        for data in [self.apk, self.apk + b'x', b'truncated', b'X' * len(self.apk)]:
            response = Response(); response.data = data
            def opener(handler):
                self.assertIsNone(handler.redirect_request(None, None, None, None, None, None))
                from unittest.mock import Mock
                return Mock(open=lambda url, timeout: response)
            with tempfile.TemporaryDirectory() as directory, patch.object(renewal.urllib.request, 'build_opener', side_effect=opener):
                metadata = {**self.info, 'apk_url': self.url}
                if data == self.apk:
                    renewal.download_apk(metadata, Path(directory) / 'test.apk')
                else:
                    with self.assertRaises(ValueError):
                        renewal.download_apk(metadata, Path(directory) / 'test.apk')
        with self.assertRaises(ValueError):
            renewal.download_apk({**self.info, 'apk_url': 'http://example.test/app.apk'}, '/unused')


if __name__ == '__main__':
    unittest.main()
