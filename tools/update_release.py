#!/usr/bin/env python3
"""Offline key provisioning and signed release metadata. Never prints secret material.

The existing APK signer is verified, never created or modified by this tool.
Uses OpenSSL and Android SDK apksigner/aapt; no third-party Python dependencies.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
import time
import urllib.request
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = b'BlackCat signed update v1\0'
PASSWORD_ENV = 'BLACKCAT_UPDATE_KEY_PASSWORD'
SIGNER = 'fc5c0b42b9da40c2507d8f2e77ed92db9e023740aa1a44d1a3b2c14a6e9f4764'
HOST = 'raw.githubusercontent.com'
FEED = f'https://{HOST}/efrgdgr0024345/autophone/blackcat-update-feed/updates/ai.json'
PERMISSIONS = {'android.permission.' + p for p in (
    'BLUETOOTH', 'BLUETOOTH_ADMIN', 'BLUETOOTH_CONNECT', 'BLUETOOTH_ADVERTISE',
    'FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_CONNECTED_DEVICE', 'INTERNET', 'CAMERA', 'RECORD_AUDIO')}
FIELDS = {'schema', 'key_id', 'package', 'flavor', 'channel', 'sequence', 'issued', 'expires',
          'version_code', 'version_name', 'apk_size', 'apk_sha256', 'signer_sha256', 'apk_url'}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def run(command, data=None):
    result = subprocess.run(command, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90)
    require(result.returncode == 0, 'Cryptographic or APK verification command failed; no metadata accepted')
    return result.stdout


def password_present():
    require(len(os.environ.get(PASSWORD_ENV, '')) >= 24,
            f'Set a secret {PASSWORD_ENV} of at least 24 characters; never pass it as a command argument')


def private_path(value, new=False):
    path = Path(value).absolute()
    require(not path.is_symlink(), 'Private-key path cannot be a symlink')
    resolved = path.resolve()
    require(ROOT != resolved and ROOT not in resolved.parents, 'Private key must remain outside the repository')
    require(path.parent.resolve() == path.parent, 'Private-key directory cannot contain symlinks')
    if new:
        path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    require(stat.S_IMODE(path.parent.stat().st_mode) == 0o700, 'Private-key directory must have mode 0700')
    if not new:
        require(path.is_file() and stat.S_IMODE(path.stat().st_mode) == 0o600, 'Private key must have mode 0600')
        require(path.read_bytes().startswith(b'-----BEGIN ENCRYPTED PRIVATE KEY-----'), 'Only encrypted PKCS8 private keys accepted')
    return path


def public_der(private_key):
    password_present()
    return run(['openssl', 'pkey', '-in', str(private_key), '-passin', 'env:' + PASSWORD_ENV,
                '-pubout', '-outform', 'DER'])


def atomic_public(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix='.release-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(data); stream.flush(); os.fsync(stream.fileno())
        os.chmod(name, 0o644)
        os.replace(name, path)
    finally:
        if os.path.exists(name): os.unlink(name)


def generate(private_key, public_output):
    password_present()
    key = private_path(private_key, new=True)
    require(Path(public_output).resolve() != key.resolve(), 'Public output cannot overwrite the private key')
    fd = os.open(key, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    os.close(fd)
    try:
        run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:3072',
             '-aes-256-cbc', '-pass', 'env:' + PASSWORD_ENV, '-out', str(key)])
        os.chmod(key, 0o600)
        der = public_der(key)
        fingerprint = hashlib.sha256(der).hexdigest()
        config = {'schema': 1, 'key_id': 'blackcat-' + fingerprint[:16], 'public_key_spki_base64': base64.b64encode(der).decode(),
                  'public_key_sha256': fingerprint, 'metadata_url': FEED,
                  'metadata_hosts': [HOST], 'apk_hosts': [HOST], 'channel': 'testing', 'flavor': 'ai'}
        atomic_public(public_output, (json.dumps(config, indent=2) + '\n').encode())
        return config
    except Exception:
        key.unlink(missing_ok=True)
        raise


def load_trust(path):
    config = strict_json(Path(path).read_bytes())
    require(set(config) == {'schema', 'key_id', 'public_key_spki_base64', 'public_key_sha256',
                           'metadata_url', 'metadata_hosts', 'apk_hosts', 'channel', 'flavor'}, 'Unexpected trust configuration fields')
    der = base64.b64decode(config['public_key_spki_base64'], validate=True)
    require(hashlib.sha256(der).hexdigest() == config['public_key_sha256'], 'Public-key fingerprint mismatch')
    require(config['schema'] == 1 and config['metadata_url'] == FEED and config['metadata_hosts'] == [HOST]
            and config['apk_hosts'] == [HOST] and config['channel'] == 'testing' and config['flavor'] == 'ai', 'Unexpected release authority policy')
    require(config['key_id'] == 'blackcat-' + config['public_key_sha256'][:16], 'Key ID does not match fingerprint')
    return config


def strict_json(data):
    require(len(data) <= 16384, 'Oversized metadata')
    def pairs(items):
        result = {}
        for k, v in items:
            require(k not in result, 'Duplicate JSON field')
            result[k] = v
        return result
    return json.loads(data.decode('ascii'), object_pairs_hook=pairs)


def verify(envelope, config, fresh=True, now=None):
    outer = strict_json(envelope)
    require(set(outer) == {'payload', 'signature'}, 'Unexpected envelope fields')
    payload = base64.b64decode(outer['payload'], validate=True)
    signature = base64.b64decode(outer['signature'], validate=True)
    require(base64.b64encode(payload).decode() == outer['payload'] and
            base64.b64encode(signature).decode() == outer['signature'], 'Noncanonical Base64')
    with tempfile.TemporaryDirectory(prefix='verify-public-update-') as directory:
        directory = Path(directory)
        der = base64.b64decode(config['public_key_spki_base64'], validate=True)
        require(hashlib.sha256(der).hexdigest() == config['public_key_sha256'], 'Public authority fingerprint mismatch')
        (directory/'public.der').write_bytes(der)
        (directory/'signature').write_bytes(signature)
        pem = run(['openssl', 'pkey', '-pubin', '-inform', 'DER', '-in', str(directory/'public.der'), '-pubout'])
        (directory/'public.pem').write_bytes(pem)
        run(['openssl', 'dgst', '-sha256', '-verify', str(directory/'public.pem'), '-signature', str(directory/'signature')], DOMAIN + payload)
    # Match the app's deliberately restricted flat ASCII JSON grammar.
    require(all(b in (9, 10, 13) or 32 <= b <= 126 for b in payload), 'Non-ASCII payload')
    require(b'\\' not in payload, 'Escaped payload fields unsupported')
    m = strict_json(payload)
    require(set(m) == FIELDS, 'Unexpected payload fields')
    for name in ('schema', 'sequence', 'issued', 'expires', 'version_code', 'apk_size'):
        require(type(m[name]) is int and 0 < m[name] < 10**18, 'Invalid integer field')
    for name in FIELDS - {'schema', 'sequence', 'issued', 'expires', 'version_code', 'apk_size'}:
        require(type(m[name]) is str, 'Invalid string field')
    require(m['schema'] == 1 and m['key_id'] == config['key_id'] and m['package'] == 'com.blackcat.remote'
            and m['flavor'] == 'ai' and m['channel'] == config['channel'] and m['signer_sha256'] == SIGNER,
            'Release authority/package/channel/signer mismatch')
    require(1 <= m['apk_size'] <= 104857600 and m['version_code'] <= 2100000000, 'Size/version out of bounds')
    require(re.fullmatch('[0-9a-f]{64}', m['apk_sha256']) and re.fullmatch('[A-Za-z0-9._-]{1,64}', m['version_name']), 'Invalid digest or version name')
    require(0 < m['expires'] - m['issued'] <= 604800, 'Invalid metadata lifetime')
    if fresh:
        now = int(time.time()) if now is None else now
        require(m['issued'] <= now + 300 and now < m['expires'], 'Metadata expired or not yet valid')
    validate_apk_url(m['apk_url'])
    return m


def validate_apk_url(url):
    require(bool(re.fullmatch(r'https://raw\.githubusercontent\.com/efrgdgr0024345/autophone/[0-9a-f]{40}/docs/[A-Za-z0-9_./~-]+\.apk', url)),
            'APK URL must identify immutable APK bytes by full repository commit')
    require(all(part not in ('.', '..', '') for part in urlsplit(url).path.split('/')[1:]), 'Invalid APK path')


def inspect_apk(apk, aapt, apksigner):
    apk = Path(apk)
    require(apk.is_file() and 0 < apk.stat().st_size <= 104857600, 'APK absent or oversized')
    signature = run([apksigner, 'verify', '--min-sdk-version', '28', '--print-certs', str(apk)]).decode()
    require(re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})', signature) == [SIGNER], 'APK original single signer mismatch')
    badging = run([aapt, 'dump', 'badging', str(apk)]).decode()
    package = re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging)
    require(package is not None and package[1] == 'com.blackcat.remote' and int(package[2]) >= 23,
            'APK must be the intended package with a new version code')
    require('application-debuggable' not in badging, 'Debuggable APK rejected')
    permissions = run([aapt, 'dump', 'permissions', str(apk)]).decode()
    require(set(re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", permissions)) == PERMISSIONS, 'APK capabilities differ from approved AI flavor')
    return {'version_code': int(package[2]), 'version_name': package[3], 'apk_size': apk.stat().st_size,
            'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}


def sign(apk_info, apk_url, sequence, config, private_key, previous=None, now=None):
    key = private_path(private_key)
    der = public_der(key)
    require(hashlib.sha256(der).hexdigest() == config['public_key_sha256'], 'Private key does not match pinned public authority')
    validate_apk_url(apk_url)
    require(type(sequence) is int and 0 < sequence < 10**18, 'Invalid sequence')
    if previous is None:
        require(sequence == 1, 'First publication must start at sequence 1; later publications require prior signed metadata')
    else:
        old = verify(previous, config, fresh=False)
        require(sequence > old['sequence'] and apk_info['version_code'] >= old['version_code'], 'Publisher rollback or sequence reuse rejected')
    now = int(time.time()) if now is None else now
    payload = {'schema': 1, 'key_id': config['key_id'], 'package': 'com.blackcat.remote',
               'flavor': 'ai', 'channel': config['channel'], 'sequence': sequence,
               'issued': now, 'expires': now + 6 * 86400, **apk_info, 'signer_sha256': SIGNER, 'apk_url': apk_url}
    data = json.dumps(payload, separators=(',', ':'), ensure_ascii=True).encode('ascii')
    signature = run(['openssl', 'dgst', '-sha256', '-sign', str(key), '-passin', 'env:' + PASSWORD_ENV], DOMAIN + data)
    envelope = json.dumps({'payload': base64.b64encode(data).decode(), 'signature': base64.b64encode(signature).decode()}, separators=(',', ':')).encode('ascii')
    verify(envelope, config, now=now)
    return envelope


def kotlin_trust(config):
    # Public data only. Fixed host/channel values prevent configuration via untrusted input.
    return '''package com.blackcat.remote

import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Owner-approved metadata authority; private key is never part of this app. */
internal object ProvisionedUpdateTrust {
    const val KEY_ID = "%s"
    const val PUBLIC_KEY_SHA256 = "%s"
    private const val SPKI = "%s"
    fun configured(): UpdateTrust {
        val der = Base64.getDecoder().decode(SPKI)
        check(SignedUpdateMetadata.sha256(der) == PUBLIC_KEY_SHA256)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        return UpdateTrust("%s", setOf("%s"), setOf("%s"), mapOf(KEY_ID to key), "testing")
    }
}
''' % (config['key_id'], config['public_key_sha256'], config['public_key_spki_base64'], config['metadata_url'], HOST, HOST)


def github(method, endpoint, data=None):
    command = ['gh', 'api', '--method', method, 'repos/efrgdgr0024345/autophone/' + endpoint]
    raw = None
    if data is not None:
        command += ['--input', '-']
        raw = json.dumps(data).encode()
    return json.loads(run(command, raw))


def publish(envelope, config, aapt, apksigner, initial=False):
    """Publish public signed data only. No signing key or passphrase reaches GitHub."""
    metadata = verify(envelope, config)
    class NoRedirects(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs): return None
    with tempfile.TemporaryDirectory(prefix='validate-published-apk-') as directory:
        apk = Path(directory)/'release.apk'
        opener = urllib.request.build_opener(NoRedirects())
        with opener.open(metadata['apk_url'], timeout=30) as response, apk.open('wb') as output:
            require(response.status == 200 and response.headers.get('Content-Encoding', 'identity') == 'identity', 'APK host did not return exact unencoded bytes')
            total = 0
            deadline = time.monotonic() + 120
            while True:
                require(time.monotonic() < deadline, 'APK verification download deadline exceeded')
                data = response.read(16384)
                if not data: break
                total += len(data)
                require(total <= metadata['apk_size'], 'Hosted APK exceeds signed size')
                output.write(data)
        actual = inspect_apk(apk, aapt, apksigner)
        require(all(metadata[k] == v for k, v in actual.items()), 'Hosted APK differs from signed release')
        import zipfile
        with zipfile.ZipFile(apk) as archive:
            require(any(config['public_key_sha256'].encode() in archive.read(n) for n in archive.namelist() if n.endswith('.dex')),
                    'Hosted APK does not contain the pinned update authority')
    parent = None
    verify(envelope, config)  # Recheck expiry after hosted-APK validation.
    if initial:
        require(metadata['sequence'] == 1, 'Initial feed sequence must be 1')
    else:
        ref = github('GET', 'git/ref/heads/blackcat-update-feed')
        parent = ref['object']['sha']
        previous = github('GET', 'contents/updates/ai.json?ref=' + parent)
        old = verify(base64.b64decode(previous['content']), config, fresh=False)
        require(metadata['sequence'] > old['sequence'] and metadata['version_code'] >= old['version_code'], 'Remote feed rollback or sequence reuse rejected')
    files = {'updates/ai.json': envelope.decode('ascii'), 'updates/public-trust.json': json.dumps(config, indent=2) + '\n',
             'README.md': '# Black Cat signed testing update feed\n\nPublic metadata and public key only. '
             'APK links identify immutable commits. Metadata expires within seven days and must be signed again before expiry.\n'}
    tree = github('POST', 'git/trees', {'tree': [{'path': p, 'mode': '100644', 'type': 'blob', 'content': value} for p, value in files.items()]})
    commit = github('POST', 'git/commits', {'message': 'Publish signed AI update sequence ' + str(metadata['sequence']),
                                          'tree': tree['sha'], 'parents': [parent] if parent else []})
    if initial:
        # Creating an existing ref fails; this never overwrites an already published feed.
        github('POST', 'git/refs', {'ref': 'refs/heads/blackcat-update-feed', 'sha': commit['sha']})
    else:
        github('PATCH', 'git/refs/heads/blackcat-update-feed', {'sha': commit['sha'], 'force': False})
    record = github('GET', 'contents/updates/ai.json?ref=' + commit['sha'])
    require(base64.b64decode(record['content']) == envelope, 'Published signed bytes differ')
    verify(envelope, config)
    return commit['sha']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    key = sub.add_parser('generate-key')
    key.add_argument('--private-key', required=True); key.add_argument('--public-output', required=True)
    provision = sub.add_parser('kotlin-trust')
    provision.add_argument('--trust', required=True); provision.add_argument('--output', required=True)
    signer = sub.add_parser('sign')
    for name in ('trust', 'private-key', 'apk', 'apk-url', 'output', 'aapt', 'apksigner'):
        signer.add_argument('--' + name, required=True)
    signer.add_argument('--sequence', required=True, type=int); signer.add_argument('--previous')
    verifier = sub.add_parser('verify')
    verifier.add_argument('--trust', required=True); verifier.add_argument('--metadata', required=True)
    publisher = sub.add_parser('publish')
    for name in ('trust', 'metadata', 'aapt', 'apksigner'):
        publisher.add_argument('--' + name, required=True)
    publisher.add_argument('--initial', action='store_true')
    args = parser.parse_args()
    try:
        if args.action == 'generate-key':
            config = generate(args.private_key, args.public_output)
            print('Generated encrypted RSA3072 metadata key; public SPKI SHA-256:', config['public_key_sha256'])
        elif args.action == 'kotlin-trust':
            require(Path(args.output).resolve() != Path(args.trust).resolve(), 'Kotlin output cannot overwrite trust manifest')
            atomic_public(args.output, kotlin_trust(load_trust(args.trust)).encode())
            print('Wrote public-only Kotlin trust configuration')
        elif args.action == 'sign':
            require(Path(args.output).resolve() not in {Path(p).resolve() for p in (args.private_key, args.trust, args.apk)}, 'Metadata output cannot overwrite release inputs')
            config = load_trust(args.trust)
            info = inspect_apk(args.apk, args.aapt, args.apksigner)
            previous = Path(args.previous).read_bytes() if args.previous else None
            data = sign(info, args.apk_url, args.sequence, config, args.private_key, previous)
            atomic_public(args.output, data + b'\n')
            print('Signed and independently verified release metadata:', args.sequence, info['version_code'], info['apk_sha256'])
        elif args.action == 'publish':
            commit = publish(Path(args.metadata).read_bytes(), load_trust(args.trust), args.aapt, args.apksigner, args.initial)
            print('Published and reverified signed feed commit:', commit)
        else:
            value = verify(Path(args.metadata).read_bytes(), load_trust(args.trust))
            print('Verified metadata: sequence', value['sequence'], 'version', value['version_code'])
    except (ValueError, OSError, subprocess.TimeoutExpired) as error:
        # Never print child-process stderr, arguments or environment values.
        parser.exit(1, 'Release operation failed: ' + (str(error) if isinstance(error, ValueError) else type(error).__name__) + '\n')


if __name__ == '__main__':
    main()
