"""Media organization engine.

Merges the "organize by conversation" engine of wa-sort-media (GPL-3.0,
Copyright chances190) with the safe-by-default organization concepts of
WhatsKeep (MIT, Copyright alissonlinneker).

Three modes:
* contact - needs a decrypted Android msgstore.db; sorts media into
  Contact-or-Group/Category/ folders (contacts from wa.db or VCF).
* date    - sorts into YYYY-MM/Category/ (no database needed).
* type    - sorts into Category/ (no database needed).
"""

from __future__ import annotations

import shutil
from pathlib import Path

from .contacts import load_contacts
from .db import match_files_to_chats
from .utils import (
    category_for_path,
    iter_media_files,
    sanitize_name,
    timestamp_for,
)

MODES = ("contact", "date", "type")


class Organizer:
    """Copy or move media files into an organized output tree."""

    def __init__(
        self,
        media_dir,
        out_dir,
        mode="date",
        copy=True,
        dry_run=False,
        msgstore=None,
        contacts_file=None,
        contacts=None,
        progress=None,
    ):
        if mode not in MODES:
            raise ValueError("mode must be one of %s" % (MODES,))
        self.media_dir = Path(media_dir)
        self.out_dir = Path(out_dir)
        self.mode = mode
        self.copy = copy
        self.dry_run = dry_run
        self.msgstore = Path(msgstore) if msgstore else None
        self.contacts_file = Path(contacts_file) if contacts_file else None
        self.contacts = contacts
        self.progress = progress

    def prepare(self):
        """Load database/contacts eagerly so failures surface early."""
        self._chat_map = {}
        if self.mode == "contact":
            if self.msgstore is None:
                raise ValueError(
                    "Organizing by contact requires a decrypted msgstore.db "
                    "(see README for the wa-crypt-tools workflow)."
                )
            self._chat_map = match_files_to_chats(self.msgstore)
            if self.contacts is None and self.contacts_file is not None:
                self.contacts = load_contacts(self.contacts_file)

    def run(self):
        """Run the organization; returns a stats dict."""
        self.prepare()
        files = list(iter_media_files(self.media_dir))
        stats = {
            "total": len(files),
            "organized": 0,
            "skipped_existing": 0,
            "unmatched": 0,
            "errors": 0,
        }
        total = len(files)
        for idx, src in enumerate(files, 1):
            try:
                rel = self._dest_for(src)
                if rel is None:
                    stats["unmatched"] += 1
                else:
                    dest = self.out_dir / rel
                    if dest.exists():
                        stats["skipped_existing"] += 1
                    elif self.dry_run:
                        stats["organized"] += 1
                    else:
                        dest.parent.mkdir(parents=True, exist_ok=True)
                        if self.copy:
                            shutil.copy2(str(src), str(dest))
                        else:
                            shutil.move(str(src), str(dest))
                        stats["organized"] += 1
            except Exception as e:  # keep the batch going
                stats["errors"] += 1
                if self.progress:
                    self.progress(idx, total, "ERROR %s: %s" % (src.name, e))
            if self.progress and (idx == total or idx % 25 == 0):
                self.progress(idx, total, str(src))
        return stats

    # ------------------------------------------------------------------ #

    def _dest_for(self, src):
        category = category_for_path(src, self.media_dir)
        if self.mode == "type":
            return Path(sanitize_name(category)) / src.name

        if self.mode == "date":
            ts = timestamp_for(src)
            return Path("%04d-%02d" % (ts.year, ts.month)) / sanitize_name(category) / src.name

        # contact mode
        chat = self._chat_map.get(src.name)
        if chat is None:
            return None  # counted as unmatched
        kind, cid, subject = chat
        if kind == "group":
            name = subject or ("Group %s" % cid)
        else:
            name = (self.contacts or {}).get(cid) or ("+%s" % cid)
        folder = sanitize_name(name)
        sub = "_Groups" if kind == "group" else "_Contacts"
        return Path(sub) / folder / sanitize_name(category) / src.name
