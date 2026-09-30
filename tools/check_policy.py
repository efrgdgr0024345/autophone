#!/usr/bin/env python3
"""Preserve proven Bluetooth/HID behavior while allowing reviewed visual-only MainActivity changes."""
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
HOOK = '  // BEGIN AI-ONLY ENTRY\n  AiEntry.attach(this,assistantRow){hid}\n  // END AI-ONLY ENTRY\n'

BEHAVIOR_SNIPPETS = (
    'override fun onCreate(s:Bundle?){super.onCreate(s);buildUi();diagnostics=Diagnostics{runOnUiThread{log.text=it}};hid=HidManager(this){event(it)};permissionsOrInit()}',
    'private fun permissionsOrInit(){if(Build.VERSION.SDK_INT>=31){val n=mutableListOf<String>();if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)n+=Manifest.permission.BLUETOOTH_CONNECT;if(checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)!=PackageManager.PERMISSION_GRANTED)n+=Manifest.permission.BLUETOOTH_ADVERTISE;if(n.isNotEmpty()){requestPermissions(n.toTypedArray(),10);return}};initHid()}',
    'private fun initHid(){status.text="HID REGISTERING";event("INFO APP_START");if(!hid.init())status.text="BLUETOOTH/HID ERROR"}',
    'override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r!=10)return;if(g.isNotEmpty()&&g.all{it==PackageManager.PERMISSION_GRANTED}){event("PASS BLUETOOTH_PERMISSIONS");initHid()}else event("FAIL Bluetooth permission denied")}',
    '@Suppress("DEPRECATION") private fun pair(){if(!hid.registered){event("WAIT HID not registered");return};event("PASS HID registered before discoverability");try{startActivityForResult(hid.discoverableIntent(300),20);event("INFO DISCOVERABILITY_REQUESTED")}catch(t:Throwable){event("FAIL discoverability: "+(t.message?:"unknown"))}}',
    '@Deprecated("Compatibility") override fun onActivityResult(r:Int,result:Int,data:Intent?){super.onActivityResult(r,result,data);if(r==20){if(result>0){status.text="DISCOVERABLE — ADD ON COMPUTER";event("PASS DISCOVERABLE seconds="+result)}else event("WARN discoverability declined")}}',
    'private fun sendText(v:String){scope.launch{var unsupported=0;for(c in v){val p=HidReports.char(c);if(p!=null){hid.sendKeyboard(p.second,p.first);delay(12)}else unsupported++};event("PASS text submitted chars="+v.length+" unsupported="+unsupported+" content-not-logged")}}',
    'private fun event(s:String){diagnostics.add(s);runOnUiThread{if(s.contains("HID_REGISTERED"))status.text="READY TO PAIR";if(s.contains("HID_CONNECTED"))status.text="READY";if(s.contains("Bluetooth off"))status.text="BLUETOOTH OFF"}}',
    'override fun onDestroy(){scope.cancel();hid.close();super.onDestroy()}',
)

KEYMAP_SNIPPETS = (
    'k("ESC",41)', 'k("TAB",43)', 'k("BKSP",42)', 'k("DEL",76)', 'k("ENTER",40)',
    'k("CTRL",0,HidReports.CTRL)', 'k("SHIFT",0,HidReports.SHIFT)', 'k("ALT",0,HidReports.ALT)', 'k("GUI",0,HidReports.GUI)',
    'k("INS",73)', 'k("HOME",74)', 'k("END",77)', 'k("PGUP",75)', 'k("PGDN",78)',
    'k("←",80)', 'k("↑",82)', 'k("↓",81)', 'k("→",79)',
    'k("F1",58)', 'k("F2",59)', 'k("F3",60)', 'k("F4",61)', 'k("F5",62)', 'k("F6",63)',
    'k("F7",64)', 'k("F8",65)', 'k("F9",66)', 'k("F10",67)', 'k("F11",68)', 'k("F12",69)',
)

def blob(data: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

def verify_activity(text: str) -> None:
    assert text.count(HOOK) == 1, 'Exactly one reviewed lazy AI entry hook is required'
    for snippet in BEHAVIOR_SNIPPETS:
        assert snippet in text, 'A V2 Bluetooth/lifecycle/send behavior method changed'
    for forbidden in ('override fun onPause', 'override fun onStop', 'override fun onResume', 'override fun onStart', 'createBond(', 'startDiscovery('):
        assert forbidden not in text, f'Unexpected lifecycle/Bluetooth behavior added: {forbidden}'
    required_ui_actions = (
        'setOnClickListener{pair()}',
        'diagnostics.snapshot()',
        'setOnClickListener{diagnostics.clear()}',
        'setOnClickListener{sendText(input.text.toString());input.text.clear()}',
        'hid.sendMouse(0,(e.x-x).toInt(),(e.y-y).toInt())',
        'hid.sendMouse(mask,0,0); hid.sendMouse(0,0,0)',
        'setOnClickListener{hid.sendKeyboard(mod,key)}',
        'setContentView(root)',
    )
    for snippet in required_ui_actions:
        assert snippet in text, f'Main UI no longer maps to proven HID action: {snippet}'
    for snippet in KEYMAP_SNIPPETS:
        assert snippet in text, f'Full keyboard mapping changed: {snippet}'
    assert 'BlackCatStyle' in text and 'R.drawable.black_cat_portrait' in text, 'Approved full-app Black Cat portrait/style missing'
    assert 'BlackCatStyle.applySystemBarInsets(root)' in text, 'Main display must sit inside Android system-bar insets'
    assert 'diagnosticsBody=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE' in text, 'Diagnostics must be collapsed by default'
    assert text.index(HOOK) < text.index('val typeCard='), 'Assistant choice belongs before remote-detail controls'
    assert text.index('val diagnosticsCard=') > text.index('val keyboardCard='), 'Diagnostics must stay at the end of the user flow'

def verify() -> None:
    src = ROOT / 'app/src/main/java/com/blackcat/remote'
    for name, sha in FROZEN.items():
        assert blob((src / name).read_bytes()) == sha, f'Golden HID source changed: {name}'
    verify_activity((src / 'MainActivity.kt').read_text(encoding='utf-8'))

    android = '{http://schemas.android.com/apk/res/android}'
    def permissions(path):
        return {e.attrib[android + 'name'] for e in ET.parse(ROOT / path).getroot().findall('uses-permission')}

    expected = {'android.permission.' + x for x in ('BLUETOOTH','BLUETOOTH_ADMIN','BLUETOOTH_CONNECT','BLUETOOTH_ADVERTISE')}
    assert permissions('app/src/main/AndroidManifest.xml') == expected
    assert permissions('app/src/ai/AndroidManifest.xml') == {'android.permission.INTERNET', 'android.permission.CAMERA'}
    app = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot().find('application')
    assert app.attrib[android + 'allowBackup'] == 'false'
    assert app.attrib[android + 'usesCleartextTraffic'] == 'false'

    build = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
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
