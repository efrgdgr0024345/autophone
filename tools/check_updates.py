#!/usr/bin/env python3
"""Additional release-boundary checks; crypto behavior lives in SignedUpdatesTest."""
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
AI = ROOT / 'app/src/ai/java/com/blackcat/remote'


def check_source():
    trust = (AI / 'SignedUpdateMetadata.kt').read_text()
    assert 'fun configured(): UpdateTrust? = null' in trust, 'Unreviewed production trust provisioning'
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
    print('PASS: unprovisioned trust, isolated updater, unchanged camera/Auto/planner/offline, no install capability')


def check_apks(ai_apk, offline_apk):
    for file, online in ((ai_apk, True), (offline_apk, False)):
        with zipfile.ZipFile(file) as apk:
            dex = b''.join(apk.read(name) for name in apk.namelist() if name.endswith('.dex'))
            assert (b'Lcom/blackcat/remote/UpdateActivity;' in dex) == online
            assert (b'Lcom/android/apksig/ApkVerifier;' in dex) == online
            assert b'unit-test-only' not in dex and b'updates.example.test' not in dex
            assert b'com/google/mlkit' not in dex
    print('PASS: AI-only packaged updater/apksig; no test metadata authority, endpoint or ML Kit in app APKs')


if __name__ == '__main__':
    check_source()
    if len(sys.argv) == 3:
        check_apks(*sys.argv[1:])
