import unittest
import tempfile
from pathlib import Path
from shutil import copytree, ignore_patterns
from check_policy import ROOT, HOOK, verify_activity, verify

class BaselineBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.activity = (ROOT / 'app/src/main/java/com/blackcat/remote/MainActivity.kt').read_text(encoding='utf-8')

    def test_current_reviewed_ui_is_accepted(self):
        verify_activity(self.activity)

    def test_manual_send_gate_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity.replace('private fun sendText(v:String){', 'private fun sendText(v:String){if(false)return;'))

    def test_pause_override_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity + '\noverride fun onPause() {}\n')

    def test_duplicate_hook_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity + HOOK)

    def test_pairing_rewrite_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity.replace('hid.discoverableIntent(300)', 'hid.discoverableIntent(120)'))

    def test_callback_gate_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity.replace('hid = HidManager(this) { event(it) }', 'hid = HidManager(this) { if(false) event(it) }'))

    def test_touchpad_hid_mapping_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity.replace('hid.sendMouse(0,(e.x-x).toInt(),(e.y-y).toInt())', 'hid.sendMouse(0,0,0)'))

    def test_keyboard_mapping_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity.replace('k("ENTER",40)', 'k("ENTER",41)'))

    def test_touchpad_scroll_interception_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity.replace('requestDisallowInterceptTouchEvent(true)', 'requestDisallowInterceptTouchEvent(false)'))

    def test_discoverability_without_readiness_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity.replace('if (!bluetoothSetup.readyForDiscoverability()) return', 'if (false) return'))

    def test_bottom_navigation_reordering_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity.replace('nav("⌨", "Keyboard")', 'nav("⌨", "Target")'))

    def test_nested_navigation_must_hide_cached_assistant(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity.replace('AiEntry.leaveAssistantForNavigation(this@MainActivity); ', ''))

    def test_diagnostics_events_must_be_serialized_on_ui_thread(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity.replace('runOnUiThread {\n   diagnostics.add(s)', 'diagnostics.add(s)\n  runOnUiThread {'))


class SetupSessionBoundaryTests(unittest.TestCase):
    def mutate(self, path, before, after):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'repo'
            copytree(ROOT, root, ignore=ignore_patterns('.git', '.gradle', 'build', '__pycache__', 'docs'))
            file = root / path
            original = file.read_text(encoding='utf-8')
            self.assertIn(before, original, 'Mutation fixture must change actual source')
            file.write_text(original.replace(before, after, 1), encoding='utf-8')
            with self.assertRaises(AssertionError):
                verify(root)

    def test_current_tree_passes(self):
        verify(ROOT)

    def test_legacy_permissions_cannot_cover_android12(self):
        self.mutate('app/src/main/AndroidManifest.xml', 'android:maxSdkVersion="30"', 'android:maxSdkVersion="31"')

    def test_offline_network_permission_rejected(self):
        self.mutate('app/src/main/AndroidManifest.xml', '<uses-feature', '<uses-permission android:name="android.permission.INTERNET"/><uses-feature')

    def test_exported_foreground_session_rejected(self):
        self.mutate('app/src/main/AndroidManifest.xml', 'android:name=".BluetoothSessionService" android:exported="false"', 'android:name=".BluetoothSessionService" android:exported="true"')

    def test_session_cannot_restart_after_process_death(self):
        self.mutate('app/src/main/java/com/blackcat/remote/BluetoothSessionService.kt', 'return START_NOT_STICKY', 'return START_STICKY')

    def test_session_cannot_own_hid(self):
        self.mutate('app/src/main/java/com/blackcat/remote/BluetoothSessionService.kt', 'package com.blackcat.remote', 'package com.blackcat.remote\n// HidManager')

    def test_bluetooth_enable_must_remain_system_prompt(self):
        self.mutate('app/src/main/java/com/blackcat/remote/BluetoothSetupController.kt', 'Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)', 'Intent("silent.enable")')

    def test_foreground_resume_cannot_start_unrequested_setup(self):
        self.mutate('app/src/main/java/com/blackcat/remote/BluetoothSetupController.kt', 'if (enablePending && missingPermissions().isEmpty() && bluetoothEnabled())', 'if (missingPermissions().isEmpty() && bluetoothEnabled())')

    def test_foreground_notification_cannot_be_hidden(self):
        self.mutate('app/src/main/java/com/blackcat/remote/BluetoothSessionService.kt', '.setOngoing(true)', '.setOngoing(false)')

    def test_revoked_grants_cannot_be_treated_as_initialized(self):
        self.mutate('app/src/main/java/com/blackcat/remote/BluetoothSetupController.kt',
                    'report("FAIL Bluetooth permission missing")\n            false',
                    'report("FAIL Bluetooth permission missing")\n            true')

if __name__ == '__main__':
    unittest.main()
