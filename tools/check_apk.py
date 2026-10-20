#!/usr/bin/env python3
import gzip, hashlib, json, re, subprocess, sys, zipfile
from pathlib import Path

apk, variant, aapt = sys.argv[1:]
assert variant in {'ai','offline'}
info = subprocess.check_output([aapt, 'dump', 'permissions', apk], text=True)
perms = set(re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", info))
# UI11's visible connected-device foreground session is shared, with no scanning or location.
expected = {'android.permission.' + x for x in (
    'BLUETOOTH','BLUETOOTH_ADMIN','BLUETOOTH_CONNECT','BLUETOOTH_ADVERTISE',
    'FOREGROUND_SERVICE','FOREGROUND_SERVICE_CONNECTED_DEVICE')}
if variant == 'ai':
    expected.update({'android.permission.INTERNET', 'android.permission.CAMERA', 'android.permission.RECORD_AUDIO'})
assert perms == expected, f'Unexpected permissions: {perms ^ expected}'
badging = subprocess.check_output([aapt, 'dump', 'badging', apk], text=True)
assert "package: name='com.blackcat.remote'" in badging
assert 'application-debuggable' not in badging, 'Do not put API keys into a debuggable build'
assert re.search(r"application-icon-\d+:.*res/", badging), 'Packaged Black Cat launcher icon missing'
manifest = subprocess.check_output([aapt, 'dump', 'xmltree', apk, 'AndroidManifest.xml'], text=True)
services = re.split(r'\n\s*E: service\b', manifest)[1:]
session = [node.split('\n      E:', 1)[0] for node in services if 'BluetoothSessionService' in node]
assert len(session) == 1, 'Exactly one visible Bluetooth session service is required'
assert re.search(r'android:exported[^\n]*\(type 0x12\)0x0', session[0]), 'Bluetooth session must not be exported'
assert re.search(r'android:foregroundServiceType[^\n]*\(type 0x11\)0x10', session[0]), 'Bluetooth session type must be connectedDevice only'
with zipfile.ZipFile(apk) as z:
    # AAPT transparently unpacks .gz assets and strips their suffix. Check the
    # exact opaque asset the Android loader opens, not just the source tree.
    asset = z.read('assets/offline_learning_v2.catalog')
    manifest_data = json.loads(Path('catalog/MANIFEST.json').read_text())
    assert hashlib.sha256(asset).hexdigest() == manifest_data['asset_gzip_sha256']
    payload = gzip.decompress(asset)
    assert hashlib.sha256(payload).hexdigest() == manifest_data['json_sha256']
    assert json.loads(payload)['counts'] == manifest_data['counts']
    for name in ('GHOSTBOARD_LICENSE','LINKPAD_LICENSE'):
        assert z.read('assets/' + name) == Path(name).read_bytes()
    endpoint = any(b'api.openai.com' in z.read(n) for n in z.namelist() if n.endswith('.dex'))
    assert endpoint == (variant == 'ai'), 'AI network client leaked into offline APK'
print('PASS packaged', variant, ': original app ID, exact flavor permissions, non-debuggable, licences')
