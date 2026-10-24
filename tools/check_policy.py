#!/usr/bin/env python3
"""The entire V2 input path, not just its helper files, is the regression boundary."""
from pathlib import Path
import hashlib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FROZEN = {
    'HidManager.kt': '9f2b44f1b927567ccb6dc38cfb0bb9c1e7157d52',
    'HidDescriptor.kt': '62a1f0b98f1608680e3df47c8a59f5359f8b100a',
    'HidReports.kt': '5b4369900a92948a58b5d25fcab1022ca8d7fdcf',
    'Diagnostics.kt': '50f57a2bcdbb3b37b245023441178314f47940ab',
}
HOOK = b'  // BEGIN AI-ONLY ENTRY\n  AiEntry.attach(this,da){hid}\n  // END AI-ONLY ENTRY\n'
GOLDEN_ACTIVITY = '4b4df22e9f8d6eeb9a1fac1b55f471ed41e72546'

def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

def verify_activity(data):
    assert data.count(HOOK) == 1, 'Only the reviewed, lazy AI button hook is permitted'
    assert blob(data.replace(HOOK, b'', 1)) == GOLDEN_ACTIVITY, 'V2 Activity/lifecycle/manual input changed'

def verify():
    src = ROOT / 'app/src/main/java/com/blackcat/remote'
    for name, sha in FROZEN.items():
        assert blob((src / name).read_bytes()) == sha, f'Golden HID source changed: {name}'
    verify_activity((src / 'MainActivity.kt').read_bytes())
    android = '{http://schemas.android.com/apk/res/android}'
    def permissions(path):
        return {e.attrib[android + 'name'] for e in ET.parse(ROOT / path).getroot().findall('uses-permission')}
    expected = {'android.permission.' + x for x in ('BLUETOOTH','BLUETOOTH_ADMIN','BLUETOOTH_CONNECT','BLUETOOTH_ADVERTISE')}
    assert permissions('app/src/main/AndroidManifest.xml') == expected
    assert permissions('app/src/ai/AndroidManifest.xml') == {'android.permission.INTERNET'}
    app = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot().find('application')
    assert app.attrib[android + 'allowBackup'] == 'false'
    assert app.attrib[android + 'usesCleartextTraffic'] == 'false'
    build = (ROOT / 'app/build.gradle').read_text()
    assert "applicationId 'com.blackcat.remote'" in build and 'applicationIdSuffix' not in build
    for path in (ROOT / 'app/src/ai/java').rglob('*.kt'):
        text = path.read_text()
        for forbidden in ('android.util.Log', 'ProcessBuilder(', 'Runtime.getRuntime', 'startDiscovery(', 'createBond(', 'registerApp(', 'unregisterApp(', 'getProfileProxy('):
            assert forbidden not in text, f'AI must not manage Bluetooth or execute commands: {path.name}'
        assert 'sk-proj-' not in text, 'No embedded project credential'
    planner = (ROOT / 'app/src/ai/java/com/blackcat/remote/OpenAiPlanner.kt').read_text()
    assert 'CommandTarget' not in planner and 'HidManager' not in planner
    for name in ('GHOSTBOARD_LICENSE','LINKPAD_LICENSE'):
        assert (ROOT / name).read_bytes() == (ROOT / 'app/src/main/assets' / name).read_bytes()
    print('PASS: complete V2 input/lifecycle restored; only lazy AI entry added; phone-key policy; original app identity')

if __name__ == '__main__': verify()
