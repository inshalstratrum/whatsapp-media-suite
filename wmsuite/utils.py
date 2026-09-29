"""Shared helpers: name sanitising, size formatting, file iteration, categories."""

from __future__ import annotations

import os
import re
from pathlib import Path

from .patterns import parse_whatsapp_filename

# Canonical category names used for output folders.
_TYPE_TO_CATEGORY = {
    "image": "Images",
    "video": "Video",
    "audio": "Audio",
    "voice_note": "Voice Notes",
    "document": "Documents",
    "sticker": "Stickers",
    "gif": "Animated Gifs",
}

# Android WhatsApp media folder names -> canonical categories.
FOLDER_TO_CATEGORY = {
    "WhatsApp Images": "Images",
    "WhatsApp Video": "Video",
    "WhatsApp Video Notes": "Video Notes",
    "WhatsApp Audio": "Audio",
    "WhatsApp Voice Notes": "Voice Notes",
    "WhatsApp Documents": "Documents",
    "WhatsApp Stickers": "Stickers",
    "WhatsApp Animated GIFs": "Animated Gifs",
    "WhatsApp Wallpapers": "Wallpapers",
    ".Statuses": "Statuses",
}

_WINDOWS_ILLEGAL = re.compile(r'[<>:"/\\|?*\x00-\x1f]')
_WINDOWS_RESERVED = re.compile(r"^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\..*)?$", re.IGNORECASE)


def sanitize_name(name):
    """Make a string safe to use as a file/folder name on Windows and POSIX."""
    name = _WINDOWS_ILLEGAL.sub("_", str(name))
    name = name.rstrip(". ")
    if _WINDOWS_RESERVED.match(name) or name.startswith((".", "-")):
        name = "_" + name
    return name[:255] or "_"


def human_size(num_bytes):
    """Format a byte count for humans: e.g. 1234567 -> '1.2 MB'."""
    n = float(num_bytes)
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if n < 1024 or unit == "TB":
            return ("%d %s" % (n, unit)) if unit == "B" else ("%.1f %s" % (n, unit))
        n /= 1024
    return "%d B" % num_bytes


def strip_whatsapp_prefix(folder_name):
    """'WhatsApp Images' -> 'Images'; '.Statuses' stays as-is."""
    if folder_name.startswith("WhatsApp "):
        return folder_name[len("WhatsApp "):]
    return folder_name


def category_for_path(path, media_root=None):
    """Best-effort canonical category for *path*.

    First tries to detect the category from the enclosing Android folder name
    (relative to *media_root*), then falls back to parsing the filename.
    """
    if media_root is not None:
        try:
            rel = Path(path).relative_to(Path(media_root))
        except ValueError:
            rel = None
        if rel is not None:
            for part in reversed(rel.parts):
                if part in FOLDER_TO_CATEGORY:
                    return FOLDER_TO_CATEGORY[part]
                stripped = strip_whatsapp_prefix(part)
                if part != stripped and stripped in _TYPE_TO_CATEGORY.values():
                    return stripped
    parsed = parse_whatsapp_filename(Path(path).name)
    if parsed is not None:
        return _TYPE_TO_CATEGORY.get(parsed.media_type, "Other")
    return "Other"


def timestamp_for(path):
    """Best-effort timestamp for *path*: from filename if WhatsApp-style, else mtime."""
    parsed = parse_whatsapp_filename(Path(path).name)
    if parsed is not None and parsed.timestamp.year > 1970:
        return parsed.timestamp
    import datetime as _dt

    try:
        return _dt.datetime.fromtimestamp(Path(path).stat().st_mtime)
    except OSError:
        return _dt.datetime.now()


def iter_media_files(root, recursive=True):
    """Yield media file paths under *root*, skipping hidden entries."""
    root = Path(root)
    if not root.is_dir():
        return
    if recursive:
        for dirpath, dirnames, filenames in os.walk(root):
            dirnames[:] = [d for d in dirnames if not d.startswith(".")]
            for name in filenames:
                if not name.startswith("."):
                    yield Path(dirpath) / name
    else:
        for entry in root.iterdir():
            if entry.is_file() and not entry.name.startswith("."):
                yield entry
