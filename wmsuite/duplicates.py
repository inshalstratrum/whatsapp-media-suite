"""Find and remove duplicate media files.

Functionality reimplemented from whatsapp-media-tools (ikaruswill):
https://github.com/ikaruswill/whatsapp-media-tools

Three-stage heuristic for speed:
1. group files by size          (cheap)
2. hash the first chunk         (cheap-ish, only for size collisions)
3. hash the full file           (expensive, only for chunk collisions)
"""

from __future__ import annotations

import hashlib
import os
from collections import defaultdict
from pathlib import Path

from .utils import iter_media_files


def _first_chunk_hash(path, chunk_size, hash_ctor=hashlib.sha256):
    h = hash_ctor()
    with open(path, "rb") as fh:
        h.update(fh.read(chunk_size))
    return h.digest()


def _full_hash(path, chunk_size, hash_ctor=hashlib.sha256):
    h = hash_ctor()
    with open(path, "rb") as fh:
        while True:
            chunk = fh.read(chunk_size)
            if not chunk:
                break
            h.update(chunk)
    return h.digest()


def find_duplicates(root, recursive=True, chunk_size=4096, progress=None):
    """Return a list of duplicate groups.

    Each group is [keep_file, duplicate_1, duplicate_2, ...] where the
    keep-file is the shortest filename (ties broken alphabetically) - the
    same heuristic as whatsapp-media-tools.
    """
    root = Path(root)
    if not root.is_dir():
        raise ValueError("Folder not found: %s" % root)

    # Stage 1 - size groups
    by_size = defaultdict(list)
    for f in iter_media_files(root, recursive):
        try:
            by_size[os.path.getsize(str(f))].append(f)
        except OSError:
            continue
    if progress:
        progress("stage", "Grouped %d files by size" % sum(len(v) for v in by_size.values()))

    # Stage 2 - first-chunk hash for size collisions
    by_chunk = defaultdict(list)
    for size, files in by_size.items():
        if len(files) < 2:
            continue
        for f in files:
            try:
                by_chunk[(_first_chunk_hash(str(f), chunk_size), size)].append(f)
            except OSError:
                continue
    if progress:
        progress("stage", "Compared first-chunk hashes")

    # Stage 3 - full hash for chunk collisions
    full_seen = {}  # hash -> representative path
    raw_groups = defaultdict(list)
    for (chunk, _size), files in by_chunk.items():
        if len(files) < 2:
            continue
        for f in files:
            try:
                fh_ = _full_hash(str(f), chunk_size)
            except OSError:
                continue
            if fh_ in full_seen:
                raw_groups[full_seen[fh_]].append(f)
            else:
                full_seen[fh_] = f
    if progress:
        progress("stage", "Compared full hashes")

    groups = []
    for keep, dups in raw_groups.items():
        members = [keep] + list(dups)
        members.sort(key=lambda p: (len(p.name), str(p.name)))
        groups.append(members)
    groups.sort(key=lambda g: str(g[0]))
    return groups


def delete_duplicates(groups, dry_run=False, progress=None):
    """Delete every duplicate, keeping the first file of each group.

    Returns a dict: deleted, bytes_freed, dry_run.
    """
    deleted = 0
    freed = 0
    for group in groups:
        for dup in group[1:]:
            try:
                if not dry_run:
                    os.remove(str(dup))
                deleted += 1
                freed += os.path.getsize(str(dup))
            except OSError as e:
                if progress:
                    progress("error", "Could not delete %s: %s" % (dup, e))
    if progress:
        progress("done", "Deleted %d duplicates" % deleted)
    return {"deleted": deleted, "bytes_freed": freed, "dry_run": bool(dry_run)}
