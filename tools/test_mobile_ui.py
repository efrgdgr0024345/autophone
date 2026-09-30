#!/usr/bin/env python3
import tempfile
import unittest
from pathlib import Path
from shutil import copytree
from check_mobile_ui import ROOT, verify


class MobileUiBoundaryTests(unittest.TestCase):
    def clone(self):
        temp = tempfile.TemporaryDirectory()
        dst = Path(temp.name) / 'repo'
        copytree(ROOT, dst)
        return temp, dst

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
        temp, dst = self.clone()
        try:
            p = dst / 'app/src/ai/java/com/blackcat/remote/OpenAiPlanner.kt'
            p.write_text(p.read_text() + '\n// changed\n')
            with self.assertRaises(AssertionError): verify(dst)
        finally: temp.cleanup()

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
        temp, dst = self.clone()
        try:
            p = dst / 'app/src/ai/java/com/blackcat/remote/AiEntry.kt'
            p.write_text(p.read_text(encoding='utf-8').replace('Edit / custom…', 'Other'), encoding='utf-8')
            with self.assertRaises(AssertionError): verify(dst)
        finally: temp.cleanup()

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
            text = text.replace(
                'add(Tab.FEEDBACK, "Feedback")\n        add(Tab.PREVIEW, "Preview")',
                'add(Tab.PREVIEW, "Preview")\n        add(Tab.FEEDBACK, "Feedback")'
            )
            p.write_text(text, encoding='utf-8')
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