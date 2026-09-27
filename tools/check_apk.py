#!/usr/bin/env python3
import re, subprocess, sys, zipfile
from pathlib import Path

apk, variant, aapt = sys.argv[1:]
assert variant in {'ai','offline'}
info = subprocess.check_output([aapt, 'dump', 'permissions', apk], text=True)
perms = set(re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", info))
expected = {'android.permission.' + x for x in ('BLUETOOTH','BLUETOOTH_ADMIN','BLUETOOTH_CONNECT','BLUETOOTH_ADVERTISE')}
if variant == 'ai': expected.add('android.permission.INTERNET')
assert perms == expected, f'Unexpected permissions: {perms ^ expected}'
badging = subprocess.check_output([aapt, 'dump', 'badging', apk], text=True)
assert "package: name='com.blackcat.remote'" in badging
assert 'application-debuggable' not in badging, 'Do not put API keys into a debuggable build'
with zipfile.ZipFile(apk) as z:
    for name in ('GHOSTBOARD_LICENSE','LINKPAD_LICENSE'):
        assert z.read('assets/' + name) == Path(name).read_bytes()
    endpoint = any(b'api.openai.com' in z.read(n) for n in z.namelist() if n.endswith('.dex'))
    assert endpoint == (variant == 'ai'), 'AI network client leaked into offline APK'
print('PASS packaged', variant, ': original app ID, expected permissions, non-debuggable, licences')
