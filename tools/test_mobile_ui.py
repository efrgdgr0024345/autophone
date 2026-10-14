#!/usr/bin/env python3
import tempfile
import unittest
from pathlib import Path
from shutil import copytree, ignore_patterns
from check_mobile_ui import ROOT, verify


class MobileUiBoundaryTests(unittest.TestCase):
    def clone(self):
        temp = tempfile.TemporaryDirectory()
        dst = Path(temp.name) / 'repo'
        copytree(ROOT, dst, ignore=ignore_patterns('.git', '.gradle', 'build', '__pycache__', 'docs'))
        return temp, dst

    def mutate(self, path, before, after):
        temp, dst = self.clone()
        try:
            p = dst / path
            original = p.read_text(encoding='utf-8')
            self.assertIn(before, original, 'Mutation fixture must change actual source')
            p.write_text(original.replace(before, after, 1), encoding='utf-8')
            with self.assertRaises(AssertionError): verify(dst)
        finally: temp.cleanup()

    def test_current_tree_passes(self):
        verify(ROOT)

    def test_checkbox_rejected(self):
        temp, dst = self.clone()
        try:
            p = dst / 'app/src/ai/java/com/blackcat/remote/AiEntry.kt'
            p.write_text(p.read_text() + '\n// CheckBox\n')
            with self.assertRaises(AssertionError): verify(dst)
        finally: temp.cleanup()

    def test_api_transport_mutation_rejected(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/OpenAiPlanner.kt',
                    'http.instanceFollowRedirects = false', 'http.instanceFollowRedirects = true')

    def test_frozen_camera_source_mutation_rejected(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/PhotoCaptureController.kt',
                    'package com.blackcat.remote', 'package com.blackcat.remote\n// unreviewed change')

    def test_model_output_cannot_be_stored(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/CommandPlan.kt',
                    '.put("store", false)', '.put("store", true)')

    def test_generation_tools_cannot_be_enabled(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/CommandPlan.kt',
                    '.put("tool_choice", "none")', '.put("tool_choice", "auto")')

    def test_clarification_schema_cannot_include_commands(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/CommandPlan.kt',
                    '.put(branch("clarify", 1, 8, 0))', '.put(branch("clarify", 1, 8, 12))')

    def test_mixed_decision_rejection_cannot_be_removed(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/CommandPlan.kt',
                    'if (questions.isNotEmpty() && array.length() > 0)', 'if (false)')

    def test_command_control_character_gate_cannot_be_removed(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/CommandPlan.kt',
                    'if (CommandPolicy.problem(command) != null)', 'if (false)')

    def test_incomplete_generation_cannot_be_accepted(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/CommandPlan.kt',
                    'if (root.optString("status") != "completed")', 'if (false)')

    def test_key_check_cannot_generate_response(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/OpenAiKeyChecker.kt',
                    'http.requestMethod = "GET"', 'http.requestMethod = "POST"')

    def test_key_check_cannot_accept_arbitrary_model_metadata(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/OpenAiKeyChecker.kt',
                    'value.optString("id") == model && value.optString("object") == "model"', 'true')

    def test_opening_panel_cannot_start_request(self):
        temp, dst = self.clone()
        try:
            p = dst / 'app/src/ai/java/com/blackcat/remote/AiEntry.kt'
            text = p.read_text().replace('dialog.setContentView(buildShell())', 'planner.propose(\"x\",\"y\",\"z\",\"k\")\n        dialog.setContentView(buildShell())', 1)
            p.write_text(text)
            with self.assertRaises(AssertionError): verify(dst)
        finally: temp.cleanup()

    def test_asset_mutation_rejected(self):
        temp, dst = self.clone()
        try:
            p = dst / 'app/src/ai/res/drawable-nodpi/black_cat_peek.webp'
            p.write_bytes(p.read_bytes() + b'x')
            with self.assertRaises(AssertionError): verify(dst)
        finally: temp.cleanup()

    def test_target_dropdown_contract_rejected(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AiStandalonePanels.kt',
                    'Edit / Custom target…', 'Other')

    def test_large_cat_header_cannot_return_to_ai_tasks(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AiEntry.kt',
                    'root.addView(buildBottomNav(), matchWrap())',
                    'root.addView(ImageView(activity), matchWrap())\n        root.addView(buildBottomNav(), matchWrap())')

    def test_hidden_draft_must_invalidate_approvals(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AiEntry.kt',
                    'libraryPanel?.dismiss()\n        workspace.touch()',
                    'libraryPanel?.dismiss()\n        // approval invalidation removed')

    def test_navigation_cannot_clear_the_users_draft(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AiEntry.kt',
                    'fun hideForNavigation() {',
                    'fun hideForNavigation() {\n        workspace.clear()')

    def test_shared_main_style_rejected_if_removed(self):
        temp, dst = self.clone()
        try:
            p = dst / 'app/src/main/java/com/blackcat/remote/BlackCatStyle.kt'
            p.write_text(p.read_text(encoding='utf-8').replace('22, 97, 70', '0, 0, 0'), encoding='utf-8')
            with self.assertRaises(AssertionError): verify(dst)
        finally: temp.cleanup()

    def test_system_bar_inset_removal_rejected(self):
        temp, dst = self.clone()
        try:
            p = dst / 'app/src/main/java/com/blackcat/remote/BlackCatStyle.kt'
            p.write_text(p.read_text(encoding='utf-8').replace('WindowInsets.Type.systemBars()', 'WindowInsets.Type.statusBars()'), encoding='utf-8')
            with self.assertRaises(AssertionError): verify(dst)
        finally: temp.cleanup()

    def test_flow_order_regression_rejected(self):
        temp, dst = self.clone()
        try:
            p = dst / 'app/src/ai/java/com/blackcat/remote/AiEntry.kt'
            text = p.read_text(encoding='utf-8')
            mutated = text.replace(
                'add(Tab.PREVIEW, "Preview")\n        add(Tab.FEEDBACK, "Result")',
                'add(Tab.FEEDBACK, "Result")\n        add(Tab.PREVIEW, "Preview")',
                1,
            )
            self.assertNotEqual(text, mutated, 'The UI09 tab-order fixture was not found')
            p.write_text(mutated, encoding='utf-8')
            with self.assertRaises(AssertionError): verify(dst)
        finally: temp.cleanup()

    def test_broad_photo_permission_rejected(self):
        temp, dst = self.clone()
        try:
            p = dst / 'app/src/ai/AndroidManifest.xml'
            text = p.read_text(encoding='utf-8').replace(
                '<uses-permission android:name="android.permission.CAMERA" />',
                '<uses-permission android:name="android.permission.CAMERA" />\n'
                '<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />'
            )
            p.write_text(text, encoding='utf-8')
            with self.assertRaises(AssertionError): verify(dst)
        finally: temp.cleanup()

    def test_untrusted_photo_evidence_rule_rejected_if_removed(self):
        temp, dst = self.clone()
        try:
            p = dst / 'app/src/ai/java/com/blackcat/remote/PhotoAnalysis.kt'
            p.write_text(p.read_text(encoding='utf-8').replace('UNTRUSTED EVIDENCE', 'trusted evidence'), encoding='utf-8')
            with self.assertRaises(AssertionError): verify(dst)
        finally: temp.cleanup()

    def test_photo_direct_hid_bypass_rejected(self):
        temp, dst = self.clone()
        try:
            p = dst / 'app/src/ai/java/com/blackcat/remote/PhotoFeedbackPanel.kt'
            p.write_text(p.read_text(encoding='utf-8') + '\n// sendKeyboard(0,40)\n', encoding='utf-8')
            with self.assertRaises(AssertionError): verify(dst)
        finally: temp.cleanup()


if __name__ == '__main__':
    unittest.main()
