"""Command-line interface for WhatsApp Media Suite.

Subcommands: scan, clean, organize, duplicates, exif, status, watch, gui.
Run with no arguments to launch the graphical interface.
"""

from __future__ import annotations

import argparse
import os
import sys

from . import __version__


def _add_common(p):
    p.add_argument("-r", "--recursive", action="store_true", help="Process folders recursively")


def build_parser():
    ap = argparse.ArgumentParser(
        prog="wmsuite",
        description="WhatsApp Media Suite - organize, deduplicate, fix dates, clean and save WhatsApp media.",
    )
    ap.add_argument("--version", action="version", version="wmsuite %s" % __version__)
    sub = ap.add_subparsers(dest="cmd")

    p = sub.add_parser("scan", help="Scan a WhatsApp folder and report sizes per category")
    p.add_argument("root")

    p = sub.add_parser("clean", help="Clean storage (empty folders / old backups)")
    p.add_argument("root", help="WhatsApp root folder (the one containing Media/ and Databases/)")
    p.add_argument("--empty-dirs", action="store_true", help="Remove empty folders")
    p.add_argument("--prune-backups", type=int, metavar="KEEP", default=0,
                   help="Keep only the newest KEEP dated msgstore backups")
    p.add_argument("--dry-run", action="store_true")

    p = sub.add_parser("organize", help="Organize media files")
    p.add_argument("media", help="Media folder (e.g. WhatsApp/Media)")
    p.add_argument("out", nargs="?", help="Output folder (default: <media>_organized)")
    p.add_argument("--mode", choices=["contact", "date", "type"], default="date")
    p.add_argument("--msgstore", help="Decrypted msgstore.db (contact mode)")
    p.add_argument("--contacts", help="wa.db or contacts.vcf (contact mode, optional)")
    p.add_argument("--move", action="store_true", help="Move instead of copy")
    p.add_argument("--dry-run", action="store_true")

    p = sub.add_parser("duplicates", help="Find/delete duplicate media")
    p.add_argument("root")
    _add_common(p)
    p.add_argument("--delete", action="store_true", help="Delete duplicates (keeps shortest filename)")
    p.add_argument("--dry-run", action="store_true")

    p = sub.add_parser("exif", help="Restore EXIF dates / timestamps from filenames")
    p.add_argument("root")
    _add_common(p)
    p.add_argument("--force", action="store_true", help="Overwrite existing EXIF dates")

    p = sub.add_parser("status", help="Save statuses from a .Statuses folder")
    p.add_argument("status_dir", help="WhatsApp/Media/.Statuses folder")
    p.add_argument("target", help="Where to save statuses")
    p.add_argument("--move", action="store_true", help="Move instead of copy")

    p = sub.add_parser("watch", help="Watch a folder and auto-file new WhatsApp media")
    p.add_argument("watch_dir")
    p.add_argument("target")
    p.add_argument("--interval", type=float, default=5.0)
    p.add_argument("--copy", action="store_true", help="Copy instead of move")

    sub.add_parser("gui", help="Launch the graphical interface (default)")
    return ap


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    if not argv:
        argv = ["gui"]
    ap = build_parser()
    args = ap.parse_args(argv)

    if args.cmd == "gui":
        from .gui import run

        return run()

    if args.cmd == "scan":
        from .scanner import scan_storage
        from .utils import human_size

        cats = scan_storage(args.root)
        total_b = 0
        total_f = 0
        for cat, info in sorted(cats.items(), key=lambda kv: -kv[1]["bytes"]):
            print("%-16s %8s  %6d files  %s" % (cat, human_size(info["bytes"]), info["files"], info["path"]))
            total_b += info["bytes"]
            total_f += info["files"]
        print("-" * 60)
        print("TOTAL: %s in %d files" % (human_size(total_b), total_f))
        return 0

    if args.cmd == "clean":
        from .scanner import prune_backups, remove_empty_dirs
        from .utils import human_size

        if args.empty_dirs:
            removed = remove_empty_dirs(args.root, dry_run=args.dry_run)
            print("Removed %d empty folders%s" % (len(removed), " (dry run)" if args.dry_run else ""))
            for d in removed:
                print("  -", d)
        if args.prune_backups:
            db_dir = args.root if args.root.endswith("Databases") else args.root.rstrip("/\\") + "/Databases"
            res = prune_backups(db_dir, keep=args.prune_backups, dry_run=args.dry_run)
            print("Pruned %d backups, freed %s%s" % (
                res["deleted"], human_size(res["bytes_freed"]), " (dry run)" if args.dry_run else ""))
        return 0

    if args.cmd == "organize":
        from .organizer import Organizer

        out = args.out or (args.media.rstrip("/\\") + "_organized")

        def progress(i, n, path):
            if i == n or i % 100 == 0:
                print("  %d/%d" % (i, n), flush=True)

        org = Organizer(
            args.media, out, mode=args.mode, copy=not args.move,
            dry_run=args.dry_run, msgstore=args.msgstore, contacts_file=args.contacts,
            progress=progress,
        )
        stats = org.run()
        print("Done: %(organized)d organized, %(skipped_existing)d already existed, "
              "%(unmatched)d unmatched, %(errors)d errors." % stats)
        if args.dry_run:
            print("(dry run - nothing was changed)")
        return 0

    if args.cmd == "duplicates":
        from .duplicates import delete_duplicates, find_duplicates
        from .utils import human_size

        def progress(kind, msg):
            print(msg, flush=True)

        groups = find_duplicates(args.root, recursive=args.recursive, progress=progress)
        dup_count = sum(len(g) - 1 for g in groups)
        dup_bytes = sum(
            os.path.getsize(str(d)) for g in groups for d in g[1:]
        )
        print("Found %d duplicate files in %d groups (%s reclaimable)" % (
            dup_count, len(groups), human_size(dup_bytes)))
        if args.delete or args.dry_run:
            res = delete_duplicates(groups, dry_run=args.dry_run, progress=progress)
            print("Deleted %d duplicates, freed %s%s" % (
                res["deleted"], human_size(res["bytes_freed"]),
                " (dry run)" if args.dry_run else ""))
        return 0

    if args.cmd == "exif":
        from .exif_restore import restore_dates

        def progress(i, n, path):
            if i == n or i % 100 == 0:
                print("  %d/%d" % (i, n), flush=True)

        stats = restore_dates(args.root, recursive=args.recursive, force=args.force, progress=progress)
        print("Restored dates on %d/%d files (%d skipped, %d errors)%s" % (
            stats["dates_set"], stats["files_seen"], stats["skipped"], stats["errors"],
            " - install piexif to also fix EXIF tags" if stats["piexif_missing"] else ""))
        return 0

    if args.cmd == "status":
        from .status_saver import save_statuses

        res = save_statuses(args.status_dir, args.target, move=args.move,
                            progress=lambda kind, msg: print(msg, flush=True))
        print("Saved %d statuses (%d errors)" % (res["saved"], res["errors"]))
        return 0

    if args.cmd == "watch":
        from .watcher import FolderWatcher

        w = FolderWatcher(args.watch_dir, args.target, interval=args.interval,
                          move=not args.copy, on_event=print)
        try:
            w.run()
        except KeyboardInterrupt:
            w.stop()
            print("Stopped.")
        return 0

    ap.print_help()
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
