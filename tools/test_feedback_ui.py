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
            copytree(ROOT, root, ignore=ignore_patterns('.git', '.gradle', 'build', '__pycache__'))
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


if __name__ == '__main__':
    unittest.main()
