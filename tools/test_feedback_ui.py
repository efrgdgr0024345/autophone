#!/usr/bin/env python3
import tempfile
import unittest
from pathlib import Path
from shutil import copytree, ignore_patterns
from check_feedback_ui import ROOT, verify


class FeedbackBoundaryTests(unittest.TestCase):
    def mutate(self, path, before, after):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'repo'
            copytree(ROOT, root, ignore=ignore_patterns('.git', '.gradle', 'build', '__pycache__', 'docs'))
            file = root / path
            original = file.read_text()
            self.assertIn(before, original)
            file.write_text(original.replace(before, after, 1))
            with self.assertRaises(AssertionError):
                verify(root)

    def test_current_feedback_tree(self):
        verify(ROOT)

    def test_microphone_cannot_leak_into_offline_variant(self):
        self.mutate('app/src/main/AndroidManifest.xml', '<uses-feature',
                    '<uses-permission android:name="android.permission.RECORD_AUDIO"/><uses-feature')

    def test_stale_plan_response_gate_cannot_be_removed(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AiEntry.kt',
                    'planRevision != requestRevision', 'false')

    def test_cancelled_photo_response_gate_cannot_be_removed(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/PhotoFeedbackPanel.kt',
                    'generation != analysisGeneration', 'false')

    def test_dictation_cannot_start_a_command_send(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/VoiceFeedbackController.kt',
                    'package com.blackcat.remote', 'package com.blackcat.remote\n// ApprovedCommandSender')

    def test_voice_cleanup_cannot_be_removed(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/VoiceFeedbackController.kt',
                    'speech?.destroy()', '// removed')

    def test_photo_review_cannot_bypass_plan_mode(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/PhotoFeedbackPanel.kt',
                    'if (onUseAnalysis == null)', 'if (true)')

    def test_invalid_key_cannot_reach_storage(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AiStandalonePanels.kt',
                    'if (!result.verified)', 'if (false)')

    def test_key_storage_must_default_to_unchecked(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AiStandalonePanels.kt',
                    'isChecked = false', 'isChecked = true')

    def test_old_key_response_cannot_be_accepted(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AiStandalonePanels.kt',
                    '!destroyed && generation == checkGeneration && dialog.isShowing && model == selectedModel()',
                    '!destroyed && dialog.isShowing && model == selectedModel()')

    def test_key_request_must_cancel(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AiStandalonePanels.kt',
                    'keyChecker.cancel()', '// cancellation removed')

    def test_pending_key_must_be_consumed_only_once(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/PendingApiKey.kt',
                    'keys.remove(activity).orEmpty()', 'keys[activity].orEmpty()')

    def test_pending_key_must_clear_on_backgrounding(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/PendingApiKey.kt',
                    'override fun onActivityPaused(a: Activity) { clear(a) }',
                    'override fun onActivityPaused(a: Activity) {}')

    def test_opening_settings_cannot_start_key_request(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AiStandalonePanels.kt',
                    'dialog.setContentView(build())\n        dialog.setOnDismissListener {\n            destroyed = true',
                    'dialog.setContentView(build())\n        checkAndSave()\n        dialog.setOnDismissListener {\n            destroyed = true')

    def test_verified_saved_key_clears_older_pending_key(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AiStandalonePanels.kt',
                    'else PendingApiKey.clear(activity)',
                    'else if (replacingKey && remember) PendingApiKey.clear(activity)')

    def test_forget_clears_pending_key_before_disk_operation(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AiStandalonePanels.kt',
                    '// Explicit forgetting clears the queued key even if encrypted deletion is interrupted.\n        PendingApiKey.clear(activity)',
                    '// queued key cleanup removed')

    def test_opening_camera_must_not_consume_pending_key(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AiEntry.kt',
                    'val key = oneShotKey\n        oneShotKey = ""',
                    'val key = oneShotKey.ifBlank { PendingApiKey.take(activity) }\n        oneShotKey = ""')

    def test_explicit_photo_upload_consumes_pending_key(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/PhotoFeedbackPanel.kt',
                    'requestKey.ifBlank { PendingApiKey.take(activity) }', 'requestKey')


if __name__ == '__main__':
    unittest.main()
