import unittest
from check_policy import ROOT, HOOK, verify_activity

class BaselineBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.activity = (ROOT / 'app/src/main/java/com/blackcat/remote/MainActivity.kt').read_bytes()

    def test_current_reviewed_ui_is_accepted(self):
        verify_activity(self.activity)

    def test_manual_send_gate_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity.replace(b'private fun sendText(v:String){', b'private fun sendText(v:String){if(false)return;'))

    def test_pause_override_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity + b'\noverride fun onPause() {}\n')

    def test_duplicate_hook_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity + HOOK)

    def test_pairing_rewrite_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity.replace(b'hid.discoverableIntent(300)', b'hid.discoverableIntent(120)'))

    def test_callback_gate_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity.replace(b'{event(it)}', b'{if(false)event(it)}'))

    def test_touchpad_hid_mapping_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity.replace(b'hid.sendMouse(0,(e.x-x).toInt(),(e.y-y).toInt())', b'hid.sendMouse(0,0,0)'))

    def test_keyboard_mapping_rejected(self):
        with self.assertRaises(AssertionError):
            verify_activity(self.activity.replace(b'k("ENTER",40)', b'k("ENTER",41)'))

if __name__ == '__main__':
    unittest.main()
