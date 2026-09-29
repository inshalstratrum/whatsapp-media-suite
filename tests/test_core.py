"""Unit tests for the core modules (stdlib-only, run with: python -m unittest discover -s tests)."""

import os
import shutil
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

from wmsuite.duplicates import delete_duplicates, find_duplicates
from wmsuite.organizer import Organizer
from wmsuite.patterns import is_whatsapp_file, parse_whatsapp_filename
from wmsuite.scanner import prune_backups, remove_empty_dirs, scan_storage
from wmsuite.status_saver import save_statuses
from wmsuite.utils import category_for_path, human_size, sanitize_name


class PatternTests(unittest.TestCase):
    def test_modern(self):
        p = parse_whatsapp_filename("WhatsApp Image 2026-04-08 at 14.20.43.jpeg")
        self.assertIsNotNone(p)
        self.assertEqual(p.media_type, "image")
        self.assertEqual(p.timestamp, datetime(2026, 4, 8, 14, 20, 43))

    def test_modern_underscore(self):
        p = parse_whatsapp_filename("WhatsApp_Video_2024-06-24_at_18.54.07.mp4")
        self.assertIsNotNone(p)
        self.assertEqual(p.media_type, "video")

    def test_modern_duplicate_index(self):
        p = parse_whatsapp_filename("WhatsApp Image 2026-04-08 at 14.20.43 (1).jpeg")
        self.assertIsNotNone(p)
        self.assertEqual(p.duplicate_index, 1)

    def test_legacy(self):
        p = parse_whatsapp_filename("IMG-20240101-WA0001.jpg")
        self.assertIsNotNone(p)
        self.assertEqual(p.media_type, "image")
        self.assertEqual(p.timestamp, datetime(2024, 1, 1))
        self.assertTrue(is_whatsapp_file("PTT-20220331-WA0076.opus"))

    def test_chat_export(self):
        p = parse_whatsapp_filename("WhatsApp Chat - John Doe.zip")
        self.assertIsNotNone(p)
        self.assertTrue(p.is_chat_export)
        self.assertEqual(p.contact_name, "John Doe")

    def test_non_whatsapp(self):
        self.assertIsNone(parse_whatsapp_filename("photo.jpg"))
        self.assertFalse(is_whatsapp_file("IMG-not-a-date.jpg"))


class UtilTests(unittest.TestCase):
    def test_sanitize(self):
        self.assertEqual(sanitize_name("John: <Doe>?"), "John_ _Doe__")
        self.assertEqual(sanitize_name("CON"), "_CON")
        self.assertEqual(sanitize_name("folder."), "folder")

    def test_human_size(self):
        self.assertEqual(human_size(0), "0 B")
        self.assertEqual(human_size(1024), "1.0 KB")

    def test_category(self):
        self.assertEqual(category_for_path(Path("/m/WhatsApp Images/x.jpg"), Path("/m")), "Images")
        self.assertEqual(category_for_path(Path("/d/WhatsApp Image 2026-04-08 at 14.20.43.jpeg")), "Images")


class DuplicateTests(unittest.TestCase):
    def test_find_and_delete(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            data = b"hello world" * 100
            (tmp / "IMG-20240101-WA0001.jpg").write_bytes(data)
            (tmp / "IMG-20240102-WA0002.jpg").write_bytes(data)
            (tmp / "other.bin").write_bytes(b"different")
            groups = find_duplicates(tmp)
            self.assertEqual(len(groups), 1)
            self.assertEqual(len(groups[0]), 2)
            res = delete_duplicates(groups)
            self.assertEqual(res["deleted"], 1)
            remaining = sorted(p.name for p in tmp.iterdir())
            self.assertEqual(remaining, ["IMG-20240101-WA0001.jpg", "other.bin"])


class OrganizerTests(unittest.TestCase):
    def test_date_mode_dry_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            media = Path(tmp) / "WhatsApp Images"
            media.mkdir()
            f = media / "IMG-20240101-WA0001.jpg"
            f.write_bytes(b"data")
            out = Path(tmp) / "organized"
            org = Organizer(media, out, mode="date", copy=True, dry_run=True)
            stats = org.run()
            self.assertEqual(stats["organized"], 1)
            self.assertFalse(out.exists())
            org2 = Organizer(media, out, mode="date", copy=True, dry_run=False)
            stats = org2.run()
            self.assertEqual(stats["organized"], 1)
            self.assertTrue((out / "2024-01" / "Images" / f.name).exists())


class ScannerTests(unittest.TestCase):
    def test_scan_and_clean(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            media = tmp / "Media" / "WhatsApp Images"
            media.mkdir(parents=True)
            (media / "IMG-20240101-WA0001.jpg").write_bytes(b"x" * 10)
            (tmp / "Databases").mkdir()
            for i in range(5):
                p = tmp / "Databases" / ("msgstore-2026-01-0%d.1.db.crypt15" % i)
                p.write_bytes(b"y" * 10)
                os.utime(str(p), (1000 + i, 1000 + i))
            cats = scan_storage(tmp)
            self.assertEqual(cats["Images"]["files"], 1)
            self.assertEqual(cats["Databases"]["files"], 5)
            res = prune_backups(tmp / "Databases", keep=2)
            self.assertEqual(res["deleted"], 3)
            empty = tmp / "Media" / "WhatsApp Stickers"
            empty.mkdir()
            removed = remove_empty_dirs(tmp)
            # Both the empty "Stickers" folder and the now-empty "Media" folder go.
            self.assertEqual(len(removed), 2)


class StatusSaverTests(unittest.TestCase):
    def test_save(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            src = tmp / ".Statuses"
            src.mkdir()
            f = src / "IMG-20240101-WA0001.jpg"
            f.write_bytes(b"x")
            res = save_statuses(src, tmp / "saved")
            self.assertEqual(res["saved"], 1)
            self.assertTrue(list((tmp / "saved").rglob("*.jpg")))


if __name__ == "__main__":
    unittest.main()
