#!/usr/bin/env python3
"""Preserve Black Cat's proven Bluetooth/HID behavior while allowing reviewed visual-only MainActivity changes."""
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

# These behavior-bearing methods are byte-identical to the physically successful V2 lineage.
BEHAVIOR_SNIPPETS = (
    b'override fun onCreate(s:Bundle?){super.onCreate(s);buildUi();diagnostics=Diagnostics{runOnUiThread{log.text=it}};hid=HidManager(this){event(it)};permissionsOrInit()}',
    b'private fun permissionsOrInit(){if(Build.VERSION.SDK_INT>=31){val n=mutableListOf<String>();if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)n+=Manifest.permission.BLUETOOTH_CONNECT;if(checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)!=PackageManager.PERMISSION_GRANTED)n+=Manifest.permission.BLUETOOTH_ADVERTISE;if(n.isNotEmpty()){requestPermissions(n.toTypedArray(),10);return}};initHid()}',
    b'private fun initHid(){status.text="HID REGISTERING";event("INFO APP_START");if(!hid.init())status.text="BLUETOOTH/HID ERROR"}',
    b'override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==10&&g.isNotEmpty()&&g.all{it==PackageManager.PERMISSION_GRANTED}){event("PASS BLUETOOTH_PERMISSIONS");initHid()}else event("FAIL Bluetooth permission denied")}',
    b'@Suppress("DEPRECATION") private fun pair(){if(!hid.registered){event("WAIT HID not registered");return};event("PASS HID registered before discoverability");try{startActivityForResult(hid.discoverableIntent(300),20);event("INFO DISCOVERABILITY_REQUESTED")}catch(t:Throwable){event("FAIL discoverability: "+(t.message?:"unknown"))}}',
    b'@Deprecated("Compatibility") override fun onActivityResult(r:Int,result:Int,data:Intent?){super.onActivityResult(r,result,data);if(r==20){if(result>0){status.text="DISCOVERABLE â ADD ON COMPUTER";event("PASS DISCOVERABLE seconds="+result)}else event("WARN discoverability declined")}}',
    b'private fun sendText(v:String){scope.launch{var unsupported=0;for(c in v){val p=HidReports.char(c);if(p!=null){hid.sendKeyboard(p.second,p.first);delay(12)}else unsupported++};event("PASS text submitted chars="+v.length+" unsupported="+unsupported+" content-not-logged")}}',
    b'private fun event(s:String){diagnostics.add(s);runOnUiThread{if(s.contains("HID_REGISTERED"))status.text="READY TO PAIR";if(s.contains("HID_CONNECTED"))status.text="READY";if(s.contains("Bluetooth off"))status.text="BLUETOOTH OFF"}}',
    b'override fun onDestroy(){scope.cancel();hid.close();super.onDestroy()}',
)

KEYMAP_SNIPPETS = (
    b'k("ESC",41)', b'k("TAB",43)', b'k("BKSP",42)', b'k("DEL",76)', b'k("ENTER",40)',
    b'k("CTRL",0,HidReports.CTRL)', b'k("SHIFT",0,HidReports.SHIFT)', b'k("ALT",0,HidReports.ALT)', b'k("GUI",0,HidReports.GUI)',
    b'k("INS",73)', b'k("HOME",74)', b'k("END",77)', b'k("PGUP",75)', b'k("PGDN",78)',
    b'k("â",80)', b'k("â",82)', b'k("â",81)', b'k("â",79)',
    b'k("F1",58)', b'k("F2",59)', b'k("F3",60)', b'k("F4",61)', b'k("F5",62)', b'k("F6",63)',
    b'k("F7",64)', b'k("F8",65)', b'k("F9",66)', b'k("F10",67)', b'k("F11",68)', b'k("F12",69)',
)

def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

def verify_activity(data):
    assert data.count(HOOK) == 1, 'Exactly one reviewed lazy AI entry hook is required'
    for snippet in BEHAVIOR_SNIPPETS:
        assert snippet in data, 'A V2 Bluetooth/lifecycle/send behavior method changed'
    for forbidden in (b'override fun onPause', b'override fun onStop', b'override fun onResume', b'override fun onStart', b'createBond(', b'startDiscovery('):
        assert forbidden not in data, f'Unexpected lifecycle/Bluetooth behavior added: {forbidden!r}'
    # Styling may change buildUi, but the actual control-to-HID actions remain exact.
    required_ui_actions = (
        b'setOnClickListener{pair()}',
        b'diagnostics.snapshot()',
        b'setOnClickListener{diagnostics.clear()}',
        b'setOnClickListener{sendText(input.text.toString());input.text.clear()}',
        b'hid.sendMouse(0,(e.x-x).toInt(),(e.y-y).toInt())',
        b'hid.sendMouse(mask,0,0); hid.sendMouse(0,0,0)',
        b'setOnClickListener{hid.sendKeyboard(mod,key)}',
        b'setContentView(root)',
    )
    for snippet in required_ui_actions:
        assert snippet in data, f'Main UI no longer maps to the proven HID action: {snippet!r}'
    for snippet in KEYMAP_SNIPPETS:
        assert snippet in data, f'Full keyboard mapping changed: {snippet!r}'
    assert b'BlackCatStyle' in data and b'R.drawable.black_cat_emblem' in data, 'Approved full-app Black Cat style missing'

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
        text = path.read_text(encoding='utf-8')
        for forbidden in ('android.util.Log', 'ProcessBuilder(', 'Runtime.getRuntime', 'startDiscovery(', 'createBond(', 'registerApp(', 'unregisterApp(', 'getProfileProxy('):
            assert forbidden not in text, f'AI must not manage Bluetooth or execute commands: {path.name}'
        assert 'sk-proj-' not in text, 'No embedded project credential'
    planner = (ROOT / 'app/src/ai/java/com/blackcat/remote/OpenAiPlanner.kt').read_text(encoding='utf-8')
    assert 'CommandTarget' not in planner and 'HidManager' not in planner
    for name in ('GHOSTBOARD_LICENSE','LINKPAD_LICENSE'):
        assert (ROOT / name).read_bytes() == (ROOT / 'app/src/main/assets' / name).read_bytes()
    print('PASS: V2 HID/lifecycle behavior frozen; reviewed MainActivity styling allowed; phone-key policy; original app identity')

if __name__ == '__main__':
    verify()
