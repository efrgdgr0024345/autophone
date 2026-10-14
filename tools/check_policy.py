#!/usr/bin/env python3
"""Preserve golden HID/manual input, with the explicitly approved UI11 setup/session wrapper."""
from pathlib import Path
import hashlib
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FROZEN = {
    'HidManager.kt': '9f2b44f1b927567ccb6dc38cfb0bb9c1e7157d52',
    'HidDescriptor.kt': '62a1f0b98f1608680e3df47c8a59f5359f8b100a',
    'HidReports.kt': '5b4369900a92948a58b5d25fcab1022ca8d7fdcf',
    'Diagnostics.kt': '50f57a2bcdbb3b37b245023441178314f47940ab',
}
HOOK = 'AiEntry.openAssistant(this@MainActivity){hid}'

BEHAVIOR_SNIPPETS = (
    'private fun sendText(v:String){scope.launch{var unsupported=0;for(c in v){val p=HidReports.char(c);if(p!=null){hid.sendKeyboard(p.second,p.first);delay(12)}else unsupported++};event("PASS text submitted chars="+v.length+" unsupported="+unsupported+" content-not-logged")}}',
)

SHARED_PERMISSIONS = {'android.permission.' + x for x in (
    'BLUETOOTH', 'BLUETOOTH_ADMIN', 'BLUETOOTH_CONNECT', 'BLUETOOTH_ADVERTISE',
    'FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_CONNECTED_DEVICE')}
ANDROID = '{http://schemas.android.com/apk/res/android}'


def compact(text: str) -> str:
    return re.sub(r'\s+', '', text)

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
    assert text.count(HOOK) == 1, 'Exactly one primary reviewed AI home entry is required'
    for snippet in BEHAVIOR_SNIPPETS:
        assert snippet in text, 'A V2 Bluetooth/lifecycle/send behavior method changed'
    for forbidden in ('override fun onStop', 'override fun onStart', 'createBond(', 'startDiscovery(',
                      'hid.init(', 'requestPermissions(', 'startForegroundService('):
        assert forbidden not in text, f'Unexpected lifecycle/Bluetooth behavior added: {forbidden}'
    normalized = compact(text)
    for required in (
        'hid = HidManager(this) { event(it) }',
        'bluetoothSetup = BluetoothSetupController(this, { hid }, { setBluetoothStatus(it) }, { event(it) })',
        'override fun onResume() { super.onResume(); if (::bluetoothSetup.isInitialized) bluetoothSetup.onResume() }',
        'override fun onPause() { cancelManualDictation(); super.onPause() }',
        'super.onRequestPermissionsResult(r, p, g); if (::bluetoothSetup.isInitialized) bluetoothSetup.onPermissionResult(r, g)',
        'if (!bluetoothSetup.readyForDiscoverability()) return; if (!hid.registered) { event("WAIT HID not registered"); return }',
        'startActivityForResult(hid.discoverableIntent(300), 20)',
        'catch (_: Throwable) { event("FAIL discoverability: unavailable") }',
        'if (bluetoothSetup.onActivityResult(r, result)) return',
        'runOnUiThread { diagnostics.add(s); if (::bluetoothSetup.isInitialized) bluetoothSetup.observeEvent(s) }',
        'scope.cancel(); if (::bluetoothSetup.isInitialized) bluetoothSetup.close(); hid.close()',
    ):
        # Native Kotlin statements may use newline separators rather than semicolons.
        assert compact(required).replace(';', '') in normalized.replace(';', ''), f'UI11 lifecycle/setup boundary changed: {required}'
    assert text.count('hid.close()') == 1, 'Only final Activity destruction may close the golden HID manager'
    for callback in ('onPause', 'onResume', 'onDestroy'):
        assert text.count('override fun ' + callback) == 1, f'Unexpected duplicate lifecycle callback: {callback}'
    on_create = text.split('override fun onCreate', 1)[1].split('override fun onNewIntent', 1)[0]
    assert 'bluetoothSetup.setup()' not in on_create, 'Opening the app must not start a Bluetooth session'
    required_ui_actions = (
        'greenButton("Make Discoverable"){pair()}',
        'diagnostics.snapshot()',
        'setOnClickListener{diagnostics.clear()}',
        'greenButton("Send Text"){sendText(input.text.toString());input.text.clear()}',
        'hid.sendMouse(0,(e.x-x).toInt(),(e.y-y).toInt())',
        'hid.sendMouse(mask,0,0); hid.sendMouse(0,0,0)',
        'setOnClickListener{hid.sendKeyboard(mod,key)}',
        'setContentView(stack)',
    )
    for snippet in required_ui_actions:
        assert snippet in text, f'Main UI no longer maps to proven HID action: {snippet}'
    for snippet in KEYMAP_SNIPPETS:
        assert snippet in text, f'Full keyboard mapping changed: {snippet}'
    assert 'BlackCatStyle' in text and 'R.drawable.black_cat_full' in text, 'Approved Black Cat home artwork/style missing'
    assert 'BlackCatStyle.applySystemBarInsets(root)' in text, 'Main display must sit inside Android system-bar insets'
    assert 'diagnosticsBody=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE' in text, 'Diagnostics must be collapsed by default'
    for required in (
        'Black Cat AI', 'Remote · Automate · Control', 'Bluetooth', 'AI Assistant',
        'Keyboard & mouse', 'Settings', 'Make Discoverable', 'Remote Controls',
        'Set up Bluetooth', 'AiEntry.openSettings', 'Handler(Looper.getMainLooper()).postDelayed',
        'AiEntry.dictateManualText', 'input.setText(transcript)', 'Phone keyboard', 'Speak text',
        'requestDisallowInterceptTouchEvent(true)', 'requestDisallowInterceptTouchEvent(false)',
        'remoteBody.addView(sendPanel', 'remoteBody.addView(pointerPanel', 'remoteBody.addView(keyboardPanel',
    ):
        assert required in text, f'Missing UI11 remote/navigation element: {required}'
    nav = text.split('fun createBottomNavigation', 1)[1].split('private fun showMainScreen', 1)[0]
    nav_tokens = ('"Home")', '"Keyboard")', '"AI")', '"Settings")')
    assert all(nav.count(token) == 1 for token in nav_tokens), 'Exactly four reviewed bottom destinations are required'
    order = [nav.index(token) for token in nav_tokens]
    assert order == sorted(order), 'Bottom navigation must be Home → Keyboard → AI → Settings'
    for forbidden in ('sendText(', 'sendKeyboard(', 'sendMouse(', 'bluetoothSetup.setup()', 'planner.propose('):
        assert forbidden not in nav, f'Navigation must not send input or start work: {forbidden}'
    assert 'AiEntry.leaveAssistantForNavigation(this@MainActivity); onBeforeNavigate?.invoke(); action()' in nav, 'Nested AI/photo/library navigation must hide the cached parent before opening another destination'
    build_ui = text.split('private fun buildUi()', 1)[1].split('fun createDiagnosticsView()', 1)[0]
    assert 'diagnosticsBody' not in build_ui and 'createDiagnosticsView(' not in build_ui, 'Diagnostics belong in Settings'


def verify_setup(root: Path) -> None:
    src = root / 'app/src/main/java/com/blackcat/remote'
    setup = (src / 'BluetoothSetupController.kt').read_text(encoding='utf-8')
    for required in (
        'listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)',
        'if (closed || permissionPending || enablePending) return',
        'grants.isEmpty() || grants.any { it != PackageManager.PERMISSION_GRANTED } || missingPermissions().isNotEmpty()',
        'if (enablePending && missingPermissions().isEmpty() && bluetoothEnabled())',
        'if (!bluetoothEnabled())', 'report("FAIL Bluetooth off")',
        'Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)', 'Intent(Settings.ACTION_BLUETOOTH_SETTINGS)',
        'Toast.makeText(activity, "Failed: Bluetooth is off"',
        'if (!BluetoothSessionService.start(activity))', 'manager().init()', 'if (!initiated)',
        'BluetoothSessionService.removeListener(serviceListener)', 'BluetoothSessionService.stop(activity)',
    ):
        assert required in setup, f'Missing explicit Bluetooth setup/readiness boundary: {required}'
    assert setup.count('manager().init()') == 1, 'Only reviewed beginSetup may initialize the HID manager'
    assert setup.count('activity.requestPermissions(') == 1, 'Bluetooth grants may only be requested by explicit setup'
    for forbidden in ('sendKeyboard(', 'sendMouse(', 'manager().close(', 'manager().disconnect(', 'createBond(', 'startDiscovery(', 'connect(', 'Timer(', 'postDelayed('):
        assert forbidden not in setup, f'Setup wrapper changed pairing or added input/retry: {forbidden}'
    begin = setup.split('private fun beginSetup()', 1)[1].split('private fun requestEnable()', 1)[0]
    assert begin.index('BluetoothSessionService.start(activity)') < begin.index('manager().init()'), 'The visible session must start before HID registration'
    begin_code = re.sub(r'//[^\n]*', '', begin)
    revoked_grant = '} catch (_: SecurityException) { report("FAIL Bluetooth permission missing"); false }'
    assert compact(revoked_grant).replace(';', '') in compact(begin_code).replace(';', ''), 'Revoked grants must fail setup rather than accepting initialization'
    service = (src / 'BluetoothSessionService.kt').read_text(encoding='utf-8')
    for required in (
        'return START_NOT_STICKY', 'if (!bluetoothReady(this))',
        'ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE', 'setOngoing(true)',
        'PendingIntent.FLAG_IMMUTABLE', 'BluetoothAdapter.ACTION_STATE_CHANGED',
        'if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return',
        'state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF',
        'stopSelf()', 'override fun onBind(intent: Intent?): IBinder? = null',
        'unregisterReceiver(stateReceiver)', 'stopForeground(STOP_FOREGROUND_REMOVE)',
    ):
        assert required in service, f'Missing visible session/lifecycle boundary: {required}'
    for forbidden in ('HidManager', 'sendKeyboard(', 'sendMouse(', 'registerApp(', 'unregisterApp(',
                      'createBond(', 'startDiscovery(', 'START_STICKY', 'BOOT_COMPLETED', 'WakeLock',
                      'HttpsURLConnection', 'URL(', 'MediaRecorder', 'FileOutputStream', 'postDelayed('):
        assert forbidden not in service, f'Foreground session must only maintain process importance: {forbidden}'


def verify_manifest(root: Path) -> None:
    manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
    permissions = manifest.findall('uses-permission')
    assert {item.attrib[ANDROID + 'name'] for item in permissions} == SHARED_PERMISSIONS
    for name in ('BLUETOOTH', 'BLUETOOTH_ADMIN'):
        item = next(item for item in permissions if item.attrib[ANDROID + 'name'] == 'android.permission.' + name)
        assert item.attrib.get(ANDROID + 'maxSdkVersion') == '30', 'Legacy Bluetooth permissions must end at Android 11'
    app = manifest.find('application')
    assert app.attrib[ANDROID + 'allowBackup'] == 'false'
    assert app.attrib[ANDROID + 'usesCleartextTraffic'] == 'false'
    assert app.attrib.get(ANDROID + 'icon', '').startswith('@mipmap/'), 'Black Cat launcher icon must be declared'
    services = app.findall('service')
    assert len(services) == 1, 'Exactly one reviewed foreground session service is permitted'
    assert services[0].attrib[ANDROID + 'name'] == '.BluetoothSessionService'
    assert services[0].attrib[ANDROID + 'exported'] == 'false', 'Other apps must not start our session'
    assert services[0].attrib[ANDROID + 'foregroundServiceType'] == 'connectedDevice'
    assert services[0].attrib[ANDROID + 'stopWithTask'] == 'true', 'Closing the task must end the visible session'
    ai_manifest = ET.parse(root / 'app/src/ai/AndroidManifest.xml').getroot()
    assert {item.attrib[ANDROID + 'name'] for item in ai_manifest.findall('uses-permission')} == {
        'android.permission.INTERNET', 'android.permission.CAMERA', 'android.permission.RECORD_AUDIO'}

def verify(root: Path = ROOT) -> None:
    src = root / 'app/src/main/java/com/blackcat/remote'
    for name, sha in FROZEN.items():
        assert blob((src / name).read_bytes()) == sha, f'Golden HID source changed: {name}'
    verify_activity((src / 'MainActivity.kt').read_text(encoding='utf-8'))

    verify_setup(root)
    verify_manifest(root)

    build = (root / 'app/build.gradle').read_text(encoding='utf-8')
    assert "applicationId 'com.blackcat.remote'" in build and 'applicationIdSuffix' not in build

    for path in (root / 'app/src/ai/java').rglob('*.kt'):
        text = path.read_text(encoding='utf-8')
        for forbidden in ('android.util.Log', 'ProcessBuilder(', 'Runtime.getRuntime', 'startDiscovery(', 'createBond(', 'registerApp(', 'unregisterApp(', 'getProfileProxy('):
            assert forbidden not in text, f'AI must not manage Bluetooth or execute commands: {path.name}'
        assert 'sk-proj-' not in text, 'No embedded project credential'

    planner = (root / 'app/src/ai/java/com/blackcat/remote/OpenAiPlanner.kt').read_text(encoding='utf-8')
    assert 'CommandTarget' not in planner and 'HidManager' not in planner
    for name in ('GHOSTBOARD_LICENSE','LINKPAD_LICENSE'):
        assert (root / name).read_bytes() == (root / 'app/src/main/assets' / name).read_bytes()

    print('PASS: golden HID/manual mappings; explicit Bluetooth setup and visible non-restarting session; exact permissions and app identity')

if __name__ == '__main__':
    verify()
