"""Storage scanner and cleaner.

Implements the storage-audit / clean-up concepts of the two Android
"WhatsApp Cleaner" apps in native Python so the same functionality ships
inside the Windows desktop app:

* WhatsAppCleaner (VishnuSanal, GPL-3.0)
* WhatsApp-Cleaner (pawank0411, MIT)

Features: per-category size scan, delete-by-category, delete files older
than N days, prune old msgstore backups (keep newest), remove empty
folders, and clean .nomedia-only leftovers.
"""

from __future__ import annotations

import os
import re
import time
from pathlib import Path

from .utils import FOLDER_TO_CATEGORY, strip_whatsapp_prefix

# Additional top-level WhatsApp folders worth tracking.
_EXTRA_FOLDERS = {
    "Databases": "Databases",
    "Backups": "Backups",
    "Media": "Media (unsorted)",
}

_BACKUP_RE = re.compile(r"^msgstore.*\.db\.crypt\d+$", re.IGNORECASE)


def normalize_category(folder_name):
    """Map a raw folder name to a display category, when possible."""
    if folder_name in FOLDER_TO_CATEGORY:
        return FOLDER_TO_CATEGORY[folder_name]
    stripped = strip_whatsapp_prefix(folder_name)
    if stripped != folder_name:
        return stripped
    return folder_name


def scan_storage(root):
    """Scan a WhatsApp folder and return a dict of category -> stats.

    Each value has keys files, bytes and path.
    Categories are resolved from the deepest known WhatsApp folder name
    in each relative path (so Media/WhatsApp Images counts as Images).
    """
    root = Path(root)
    if not root.is_dir():
        raise ValueError("Folder not found: %s" % root)

    categories = {}
    for dirpath, dirnames, filenames in os.walk(root):
        rel = Path(dirpath).relative_to(root)
        cat = _category_for_rel(rel)
        entry = categories.setdefault(
            cat, {"files": 0, "bytes": 0, "path": str(dirpath)}
        )
        for name in filenames:
            if name.startswith("."):
                continue
            try:
                size = os.path.getsize(os.path.join(dirpath, name))
            except OSError:
                continue
            entry["files"] += 1
            entry["bytes"] += size
    return categories


def _category_for_rel(rel):
    parts = rel.parts
    if not parts:
        return "Root files"
    # Prefer the deepest recognisable folder name.
    for part in reversed(parts):
        if part in FOLDER_TO_CATEGORY:
            return FOLDER_TO_CATEGORY[part]
        if part in _EXTRA_FOLDERS:
            return _EXTRA_FOLDERS[part]
        stripped = strip_whatsapp_prefix(part)
        if stripped != part:
            return stripped
    return normalize_category(parts[0])


def delete_category(path, dry_run=False, progress=None):
    """Delete all non-hidden files under *path* (the category folder).

    Returns a dict: deleted, bytes_freed.
    """
    path = Path(path)
    deleted = 0
    freed = 0
    if not path.is_dir():
        return {"deleted": 0, "bytes_freed": 0}
    for dirpath, dirnames, filenames in os.walk(path):
        dirnames[:] = [d for d in dirnames if not d.startswith(".")]
        for name in filenames:
            if name.startswith("."):
                continue
            f = Path(dirpath) / name
            try:
                size = f.stat().st_size
                if not dry_run:
                    os.remove(str(f))
                deleted += 1
                freed += size
            except OSError as e:
                if progress:
                    progress("error", "Could not delete %s: %s" % (f, e))
    return {"deleted": deleted, "bytes_freed": freed}


def delete_files_older_than(path, days, dry_run=False, progress=None):
    """Delete files under *path* whose mtime is older than *days* days.

    days=0 deletes regardless of age.
    Returns a dict: deleted, bytes_freed.
    """
    path = Path(path)
    if not path.is_dir():
        return {"deleted": 0, "bytes_freed": 0}

    cutoff = time.time() - days * 86400 if days else None
    deleted = 0
    freed = 0
    for dirpath, dirnames, filenames in os.walk(path):
        dirnames[:] = [d for d in dirnames if not d.startswith(".")]
        for name in filenames:
            if name.startswith("."):
                continue
            f = Path(dirpath) / name
            try:
                st = f.stat()
                if cutoff is not None and st.st_mtime >= cutoff:
                    continue
                if not dry_run:
                    os.remove(str(f))
                deleted += 1
                freed += st.st_size
            except OSError as e:
                if progress:
                    progress("error", "Could not delete %s: %s" % (f, e))
    return {"deleted": deleted, "bytes_freed": freed}


def prune_backups(databases_dir, keep=1, dry_run=False, progress=None):
    """Delete dated msgstore backups (msgstore-YYYY-MM-DD.N.db.cryptN),
    keeping the newest *keep* of them. Never touches undated msgstore.db*."""
    databases_dir = Path(databases_dir)
    if not databases_dir.is_dir():
        return {"deleted": 0, "bytes_freed": 0}
    candidates = [
        p
        for p in databases_dir.glob("msgstore-*")
        if p.is_file() and _BACKUP_RE.match(p.name)
    ]
    candidates.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    victims = candidates[keep:] if keep else candidates
    deleted = 0
    freed = 0
    for p in victims:
        try:
            size = p.stat().st_size
            if not dry_run:
                os.remove(str(p))
            deleted += 1
            freed += size
            if progress:
                progress("pruned", str(p))
        except OSError as e:
            if progress:
                progress("error", "Could not delete %s: %s" % (p, e))
    return {"deleted": deleted, "bytes_freed": freed}


def remove_empty_dirs(root, dry_run=False):
    """Remove folders that contain no media files (bottom-up).

    A folder containing only hidden files such as .nomedia is considered
    empty. Returns the list of removed folder paths.
    """
    root = Path(root)
    removed = []
    for dirpath, dirnames, filenames in os.walk(root, topdown=False):
        real_files = [f for f in filenames if not f.startswith(".")]
        if real_files:
            continue
        # Only remove dirs that no longer contain anything meaningful.
        try:
            leftovers = os.listdir(dirpath)
        except OSError:
            continue
        if leftovers and any(not n.startswith(".") for n in leftovers):
            continue  # a subdirectory still holds content
        if Path(dirpath) == root:
            continue
        if not dry_run:
            for name in leftovers:
                try:
                    os.remove(os.path.join(dirpath, name))
                except OSError:
                    pass
            try:
                os.rmdir(dirpath)
            except OSError:
                continue
        removed.append(dirpath)
    return removed
