"""Restore EXIF dates and file timestamps from WhatsApp filenames.

Functionality reimplemented from whatsapp-media-tools (ikaruswill):
https://github.com/ikaruswill/whatsapp-media-tools

WhatsApp strips EXIF data from images it sends, but keeps the capture
date in the filename. This module restores:
* images: EXIF DateTimeOriginal (via piexif, if installed) + optionally mtime
* videos: file created/modified timestamps (os.utime)

piexif is optional - without it, images still get their file timestamps
fixed, and a note is returned.
"""

from __future__ import annotations

import logging
import os
from pathlib import Path

from .patterns import parse_whatsapp_filename
from .utils import iter_media_files

logger = logging.getLogger(__name__)

try:
    import piexif  # type: ignore

    HAS_PIEXIF = True
except ImportError:  # pragma: no cover - optional dependency
    piexif = None
    HAS_PIEXIF = False

_IMAGE_EXTS = {".jpg", ".jpeg"}
_VIDEO_EXTS = {".mp4", ".3gp", ".mov", ".avi"}


def restore_dates(root, recursive=True, force=False, also_set_mtime=True, progress=None):
    """Walk *root* and restore dates from WhatsApp filenames.

    Returns a stats dict: files_seen, dates_set, skipped, errors,
    piexif_missing (bool).
    """
    root = Path(root)
    if not root.is_dir():
        raise ValueError("Folder not found: %s" % root)

    stats = {
        "files_seen": 0,
        "dates_set": 0,
        "skipped": 0,
        "errors": 0,
        "piexif_missing": False,
    }
    files = list(iter_media_files(root, recursive))
    total = len(files)
    for idx, path in enumerate(files, 1):
        parsed = parse_whatsapp_filename(path.name)
        if parsed is None or parsed.timestamp.year <= 1970:
            stats["skipped"] += 1
        else:
            ts = parsed.timestamp
            try:
                ext = path.suffix.lower()
                if ext in _IMAGE_EXTS:
                    if HAS_PIEXIF:
                        _fix_image_exif(path, ts, force)
                        if also_set_mtime:
                            os.utime(str(path), (ts.timestamp(), ts.timestamp()))
                        stats["dates_set"] += 1
                    else:
                        os.utime(str(path), (ts.timestamp(), ts.timestamp()))
                        stats["dates_set"] += 1
                        stats["piexif_missing"] = True
                elif ext in _VIDEO_EXTS:
                    os.utime(str(path), (ts.timestamp(), ts.timestamp()))
                    stats["dates_set"] += 1
                else:
                    stats["skipped"] += 1
            except Exception as e:
                logger.warning("Error processing %s: %s", path, e)
                stats["errors"] += 1
        if progress and (idx == total or idx % 50 == 0):
            progress(idx, total, str(path))
    return stats


def _fix_image_exif(path, ts, force):
    exif_date = ts.strftime("%Y:%m:%d %H:%M:%S")
    try:
        exif_dict = piexif.load(str(path))
        existing = exif_dict.get("Exif", {}).get(piexif.ExifIFD.DateTimeOriginal)
        if existing and not force:
            return  # already has a real date - respect it
        exif_dict.setdefault("Exif", {})[piexif.ExifIFD.DateTimeOriginal] = exif_date.encode()
        exif_bytes = piexif.dump(exif_dict)
        piexif.insert(exif_bytes, str(path))
    except Exception:
        # Invalid image data or invalid exif: rewrite with a fresh minimal exif.
        exif_dict = {"Exif": {piexif.ExifIFD.DateTimeOriginal: exif_date.encode()}}
        piexif.insert(piexif.dump(exif_dict), str(path))
