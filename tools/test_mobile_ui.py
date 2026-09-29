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

    def test_camera_scope_creep_rejected(self):
        temp, dst = self.clone()
        try:
            p = dst / 'app/src/ai/AndroidManifest.xml'
            p.write_text(p.read_text().replace('<manifest', '<manifest\n<!-- CAMERA -->', 1))
            with self.assertRaises(AssertionError): verify(dst)
        finally: temp.cleanup()


if __name__ == '__main__':
    unittest.main()