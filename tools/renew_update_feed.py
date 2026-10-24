#!/usr/bin/env python3
"""Renew only an already-authenticated release. No APK build or key generation."""
import argparse
import base64
import hashlib
import os
from pathlib import Path
import tempfile
import time
import urllib.request

import update_release as release

KEY_ENV = 'BLACKCAT_UPDATE_SIGNING_KEY_ENCRYPTED'
PIN = '8cb2f3d61ba630c526a2b3dd47449f540e1a2666414da6166655eba441147a1f'


def trusted_config(path):
    config = release.load_trust(path)
    release.require(config['public_key_sha256'] == PIN, 'Renewal authority is not the approved public root')
    return config


def write_key(directory):
    material = os.environ.pop(KEY_ENV, '')
    release.require(0 < len(material) <= 16384, 'Encrypted signing-key secret absent or oversized')
    data = material.encode('ascii').replace(b'\r\n', b'\n').strip() + b'\n'
    release.require(data.startswith(b'-----BEGIN ENCRYPTED PRIVATE KEY-----\n') and
                    data.endswith(b'\n-----END ENCRYPTED PRIVATE KEY-----\n'),
                    'Expected existing encrypted PKCS8 PEM, not a new or plaintext key')
    path = Path(directory) / 'metadata-key.pem'
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'wb') as stream:
        stream.write(data)
    return release.private_path(path)


def download_apk(metadata, path):
    release.validate_apk_url(metadata['apk_url'])

    class NoRedirects(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None

    opener = urllib.request.build_opener(NoRedirects())
    digest = hashlib.sha256()
    total = 0
    deadline = time.monotonic() + 120
    with opener.open(metadata['apk_url'], timeout=30) as response, Path(path).open('xb') as output:
        release.require(response.status == 200 and response.headers.get('Content-Encoding', 'identity') == 'identity',
                        'Expected exact unencoded APK response')
        while True:
            release.require(time.monotonic() < deadline, 'APK download deadline exceeded')
            chunk = response.read(16384)
            if not chunk:
                break
            total += len(chunk)
            release.require(total <= metadata['apk_size'], 'APK exceeds authenticated size')
            digest.update(chunk)
            output.write(chunk)
    release.require(total == metadata['apk_size'] and digest.hexdigest() == metadata['apk_sha256'],
                    'APK size/hash mismatch')


def renew(config, key, directory, aapt, apksigner):
    # Read one immutable feed commit through GitHub, not a potentially cached raw URL.
    ref = release.github('GET', 'git/ref/heads/blackcat-update-feed')
    commit = ref['object']['sha']
    import re
    release.require(bool(re.fullmatch('[0-9a-f]{40}', commit)), 'Invalid feed commit')
    record = release.github('GET', 'contents/updates/ai.json?ref=' + commit)
    previous = base64.b64decode(record['content'], validate=False)
    old = release.verify(previous, config, fresh=False)
    # Expired authentic metadata may be renewed; future-dated metadata may not.
    release.require(old['issued'] <= int(time.time()) + 300, 'Prior feed is future-dated')
    apk = Path(directory) / 'existing-release.apk'
    download_apk(old, apk)
    actual = release.inspect_apk(apk, aapt, apksigner)
    release.require(all(old[k] == v for k, v in actual.items()), 'Existing APK identity differs from signed feed')
    envelope = release.sign(actual, old['apk_url'], old['sequence'] + 1, config, key, previous)
    new = release.verify(envelope, config)
    release.require(all(new[k] == v for k, v in old.items() if k not in {'sequence', 'issued', 'expires'}),
                    'Renewal attempted to change release identity')
    published = release.publish(envelope, config, aapt, apksigner)
    # Re-read the live ref to detect publication/verification failures or races.
    live = release.github('GET', 'git/ref/heads/blackcat-update-feed')['object']['sha']
    release.require(live == published, 'Feed changed during final verification; inspect public feed')
    record = release.github('GET', 'contents/updates/ai.json?ref=' + live)
    release.require(base64.b64decode(record['content']) == envelope, 'Live published envelope differs')
    release.verify(envelope, config)
    return published, new


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--trust', default=str(release.ROOT / 'updates/production-trust-v1.json'))
    parser.add_argument('--aapt', required=True)
    parser.add_argument('--apksigner', required=True)
    args = parser.parse_args()
    try:
        release.password_present()
        config = trusted_config(args.trust)
        # A fresh private directory outside the checkout; cleanup on success/failure.
        with tempfile.TemporaryDirectory(prefix='blackcat-renewal-', dir=os.environ.get('RUNNER_TEMP')) as directory:
            key = write_key(directory)
            published, metadata = renew(config, key, directory, args.aapt, args.apksigner)
        print('Verified published renewal:', published, 'sequence', metadata['sequence'], 'expires', metadata['expires'])
    except Exception as error:
        # Never echo response bodies, process stderr, secret values or exception text.
        parser.exit(1, 'Renewal failed verification (' + type(error).__name__ + '); inspect the public feed/job status.\n')


if __name__ == '__main__':
    main()
