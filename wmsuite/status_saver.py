"""Status saver - copy WhatsApp statuses (.Statuses folder) into a
permanent, date-organized folder.

Feature ported from WhatsApp-Cleaner (pawank0411, MIT):
https://github.com/pawank0411/WhatsApp-Cleaner
"""

from __future__ import annotations

import shutil
from datetime import datetime
from pathlib import Path

from .utils import sanitize_name


def save_statuses(status_dir, target_dir, move=False, dry_run=False, progress=None):
    """Copy (or move) every status file into target/YYYY-MM-DD/.

    Returns a dict: saved, errors.
    """
    status_dir = Path(status_dir)
    target_dir = Path(target_dir)
    if not status_dir.is_dir():
        raise ValueError(".Statuses folder not found: %s" % status_dir)

    saved = 0
    errors = 0
    files = [
        p for p in status_dir.iterdir() if p.is_file() and not p.name.startswith(".")
    ]
    for idx, src in enumerate(files, 1):
        try:
            ts = datetime.fromtimestamp(src.stat().st_mtime)
            day_dir = target_dir / sanitize_name("%04d-%02d-%02d" % (ts.year, ts.month, ts.day))
            dest = day_dir / src.name
            n = 1
            while dest.exists():
                dest = day_dir / ("%s (%d)%s" % (src.stem, n, src.suffix))
                n += 1
            if not dry_run:
                day_dir.mkdir(parents=True, exist_ok=True)
                if move:
                    shutil.move(str(src), str(dest))
                else:
                    shutil.copy2(str(src), str(dest))
            saved += 1
        except OSError as e:
            errors += 1
            if progress:
                progress("error", "Could not save %s: %s" % (src, e))
        if progress and (idx == len(files) or idx % 10 == 0):
            progress(idx, len(files), str(src))
    return {"saved": saved, "errors": errors}
