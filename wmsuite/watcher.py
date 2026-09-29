"""Live folder watcher - auto-organizes WhatsApp media the moment it lands.

Concept ported from WhatsKeep (MIT, Copyright alissonlinneker):
https://github.com/alissonlinneker/whatskeep

Uses dependency-free polling (works everywhere, including the frozen
.exe). Files matching WhatsApp naming patterns are moved into
target/YYYY-MM/Category/. Content fingerprints prevent re-processing of
the same file.
"""

from __future__ import annotations

import hashlib
import os
import shutil
import threading
from pathlib import Path

from .patterns import is_whatsapp_file
from .utils import category_for_path, sanitize_name, timestamp_for


class FolderWatcher(threading.Thread):
    """Watch a folder and file new WhatsApp media into an organized tree."""

    def __init__(
        self,
        watch_dir,
        target_dir,
        interval=5.0,
        move=True,
        on_event=None,
    ):
        super().__init__(daemon=True)
        self.watch_dir = Path(watch_dir)
        self.target_dir = Path(target_dir)
        self.interval = max(1.0, float(interval))
        self.move = move
        self.on_event = on_event
        self._stop_event = threading.Event()
        self._seen = {}  # fingerprint -> path

    def stop(self):
        self._stop_event.set()

    # ------------------------------------------------------------------ #

    def run(self):
        self._emit("Watching %s (every %.0fs)..." % (self.watch_dir, self.interval))
        while not self._stop_event.is_set():
            try:
                self._sweep()
            except Exception as e:
                self._emit("ERROR: %s" % e)
            self._stop_event.wait(self.interval)
        self._emit("Watcher stopped.")

    def _sweep(self):
        if not self.watch_dir.is_dir():
            return
        for entry in sorted(self.watch_dir.iterdir()):
            if not entry.is_file() or entry.name.startswith("."):
                continue
            if not is_whatsapp_file(entry.name):
                continue
            fp = self._fingerprint(entry)
            if fp in self._seen:
                continue
            self._file(entry)
            self._seen[fp] = entry

    def _fingerprint(self, path):
        h = hashlib.sha256()
        try:
            size = os.path.getsize(str(path))
            with open(str(path), "rb") as fh:
                h.update(fh.read(65536))
        except OSError:
            return None
        return (h.digest(), size)

    def _file(self, src):
        category = category_for_path(src)
        ts = timestamp_for(src)
        dest = (
            self.target_dir
            / sanitize_name("%04d-%02d" % (ts.year, ts.month))
            / sanitize_name(category)
            / src.name
        )
        if dest.exists():
            self._emit("Already filed: %s" % src.name)
            return
        dest.parent.mkdir(parents=True, exist_ok=True)
        if self.move:
            shutil.move(str(src), str(dest))
        else:
            shutil.copy2(str(src), str(dest))
        self._emit("Filed %s -> %s" % (src.name, dest.relative_to(self.target_dir)))

    def _emit(self, msg):
        if self.on_event:
            self.on_event(msg)
