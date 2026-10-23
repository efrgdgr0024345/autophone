#!/usr/bin/env python3
"""Additional release-boundary checks; crypto behavior lives in SignedUpdatesTest."""
from pathlib import Path
import subprocess
import base64
import hashlib
import json
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
AI = ROOT / 'app/src/ai/java/com/blackcat/remote'


def check_source():
    trust = (AI / 'SignedUpdateMetadata.kt').read_text()
    assert 'fun configured(): UpdateTrust? = ProvisionedUpdateTrust.configured()' in trust
    config = json.loads((ROOT / 'updates/production-trust-v1.json').read_text())
    approved_fingerprint = '8cb2f3d61ba630c526a2b3dd47449f540e1a2666414da6166655eba441147a1f'
    assert config['public_key_sha256'] == approved_fingerprint, 'Unexpected metadata authority rotation'
    assert hashlib.sha256(base64.b64decode(config['public_key_spki_base64'], validate=True)).hexdigest() == approved_fingerprint
    generated = (AI / 'ProvisionedUpdateTrust.kt').read_text()
    assert config['public_key_spki_base64'] in generated and approved_fingerprint in generated
    assert config['metadata_url'] == 'https://raw.githubusercontent.com/efrgdgr0024345/autophone/blackcat-update-feed/updates/ai.json'
    assert config['metadata_url'] in generated
    assert config['metadata_hosts'] == config['apk_hosts'] == ['raw.githubusercontent.com']
    assert 'BEGIN PUBLIC KEY' not in trust and 'BEGIN PRIVATE KEY' not in trust
    assert 'unit-test-only' not in trust
    for source in (AI / name for name in ('SignedUpdateMetadata.kt', 'UpdateHttps.kt',
                                         'UpdateVerifier.kt', 'AndroidUpdateSecurity.kt', 'UpdateActivity.kt')):
        text = source.read_text()
        for forbidden in ('ApiKeyVault', 'OpenAi', 'HidManager', 'sendKeyboard', 'HostnameVerifier',
                          'TrustManager', 'SSLContext', 'ACTION_INSTALL_PACKAGE', 'ACTION_VIEW',
                          'REQUEST_INSTALL_PACKAGES', 'Runtime.getRuntime', 'ProcessBuilder', 'android.util.Log'):
            assert forbidden not in text, f'{source.name} crossed boundary: {forbidden}'
    assert 'aiImplementation \'com.android.tools.build:apksig:8.7.3\'' in (ROOT / 'app/build.gradle').read_text()
    assert 'mlkit' not in (ROOT / 'app/build.gradle').read_text().lower(), 'OCR evaluation became implementation'
    assert (ROOT / 'app/src/ai/assets/APKSIG_LICENSE').is_file()
    assert 'delay(5000)' in (AI / 'AutoAssistPanel.kt').read_text()
    activity = (AI / 'UpdateActivity.kt').read_text()
    assert 'Intent.ACTION_CREATE_DOCUMENT' in activity and 'uri?.scheme != "content"' in activity
    assert 'v.verifyFile(found, file)' in activity
    assert 'ledger.accept(release, installed, now())' in (AI / 'UpdateVerifier.kt').read_text()
    for file in ('app/src/main/AndroidManifest.xml', 'app/src/offline/java/com/blackcat/remote/AiEntry.kt',
                 'app/src/ai/java/com/blackcat/remote/PhotoCaptureController.kt',
                 'app/src/ai/java/com/blackcat/remote/AutoAssistPanel.kt',
                 'app/src/ai/java/com/blackcat/remote/AutoObservation.kt',
                 'app/src/ai/java/com/blackcat/remote/AutoOutputCollection.kt',
                 'app/src/ai/java/com/blackcat/remote/AiEntry.kt'):
        before = subprocess.check_output(['git', 'show', f'bd7f38295404721c984ede7813a363a46ae02aba:{file}'], cwd=ROOT)
        assert before == (ROOT / file).read_bytes(), f'Unexpected baseline change: {file}'
    print('PASS: approved production public trust, isolated updater, unchanged camera/Auto/planner/offline, no install capability')


def check_apks(ai_apk, offline_apk):
    for file, online in ((ai_apk, True), (offline_apk, False)):
        with zipfile.ZipFile(file) as apk:
            if online:
                assert apk.read('assets/APKSIG_LICENSE') == (ROOT / 'app/src/ai/assets/APKSIG_LICENSE').read_bytes()
            else:
                assert 'assets/APKSIG_LICENSE' not in apk.namelist()
            dex = b''.join(apk.read(name) for name in apk.namelist() if name.endswith('.dex'))
            assert (b'Lcom/blackcat/remote/UpdateActivity;' in dex) == online
            assert (b'Lcom/android/apksig/ApkVerifier;' in dex) == online
            assert b'unit-test-only' not in dex and b'updates.example.test' not in dex
            assert b'Lcom/blackcat/remote/UpdateExportFixtureProvider;' not in dex
            assert not any('published-release-v1.json' in name for name in apk.namelist())
            assert b'com/google/mlkit' not in dex
    print('PASS: AI-only packaged updater/apksig; no test metadata authority, endpoint or ML Kit in app APKs')


if __name__ == '__main__':
    check_source()
    if len(sys.argv) == 3:
        check_apks(*sys.argv[1:])
