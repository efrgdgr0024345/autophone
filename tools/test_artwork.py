#!/usr/bin/env python3
"""Decode actual APK artwork inputs, rather than treating a file size as image validation."""
import unittest
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]


class ArtworkTests(unittest.TestCase):
    def test_all_checked_in_cat_assets_decode(self):
        files = list((ROOT / 'app/src/main/res/drawable-nodpi').glob('black_cat_*.webp'))
        files += list((ROOT / 'app/src/ai/res/drawable-nodpi').glob('black_cat_*.webp'))
        self.assertEqual(6, len(files))
        for path in files:
            with self.subTest(asset=str(path.relative_to(ROOT))):
                with Image.open(path) as image:
                    image.load()
                    self.assertGreater(image.width, 100)
                    self.assertGreater(image.height, 100)


if __name__ == '__main__':
    unittest.main()
