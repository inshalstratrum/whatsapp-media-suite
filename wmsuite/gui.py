"""WhatsApp Media Suite - master graphical interface (Tkinter).

One integrated window with every merged feature:

* Dashboard  - storage scan overview (WhatsAppCleaner concept)
* Clean      - delete media by category/age, prune backups (WhatsAppCleaner concept)
* Organize   - by contact (wa-sort-media) / by date / by type (whatskeep)
* Duplicates - find & delete duplicate media (whatsapp-media-tools)
* Dates      - restore EXIF/timestamps from filenames (whatsapp-media-tools)
* Status     - save statuses permanently (WhatsApp-Cleaner concept)
* Watcher    - live auto-organize incoming media (whatskeep)
"""

from __future__ import annotations

import os
import queue
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, scrolledtext, ttk

from . import __app_name__, __version__
from .utils import human_size


# --------------------------------------------------------------------------- #
#  Worker-queue plumbing
# --------------------------------------------------------------------------- #


class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("%s %s" % (__app_name__, __version__))
        self.geometry("1000x720")
        self.minsize(860, 600)
        self.q = queue.Queue()
        self._build()
        self.after(100, self._poll)

    # -- thread-safe GUI updates ---------------------------------------- #

    def post(self, fn):
        self.q.put(fn)

    def _poll(self):
        try:
            while True:
                (self.q.get_nowait())()
        except queue.Empty:
            pass
        self.after(80, self._poll)

    def log(self, msg):
        self.log_widget.configure(state="normal")
        self.log_widget.insert("end", str(msg) + "\n")
        self.log_widget.see("end")
        self.log_widget.configure(state="disabled")

    def status(self, msg):
        self.status_var.set(str(msg))

    # -- construction ---------------------------------------------------- #

    def _build(self):
        self.nb = ttk.Notebook(self)
        self.nb.pack(fill="both", expand=True, padx=6, pady=(6, 0))

        self.tabs = {}
        for cls in (DashboardTab, CleanTab, OrganizeTab, DuplicatesTab,
                    DatesTab, StatusTab, WatcherTab):
            frame = ttk.Frame(self.nb, padding=10)
            self.nb.add(frame, text=cls.TITLE)
            self.tabs[cls.TITLE] = cls(self, frame)

        # Shared log panel
        logwrap = ttk.LabelFrame(self, text="Activity log", padding=4)
        logwrap.pack(fill="both", padx=6, pady=6)
        self.log_widget = scrolledtext.ScrolledText(logwrap, height=8, state="disabled")
        self.log_widget.pack(fill="both", expand=True)

        self.status_var = tk.StringVar(value="Ready.")
        ttk.Label(self, textvariable=self.status_var, relief="sunken", anchor="w").pack(
            fill="x", side="bottom")

    def run_bg(self, fn, button=None, busy_msg="Working..."):
        """Run *fn* in a background thread; toggle *button* while running."""
        def worker():
            try:
                fn()
            except Exception as e:
                self.post(lambda: self.log("ERROR: %s" % e))
            finally:
                self.post(lambda: self.status("Ready."))
                if button is not None:
                    self.post(lambda: button.configure(state="normal"))

        if button is not None:
            button.configure(state="disabled")
        self.status(busy_msg)
        threading.Thread(target=worker, daemon=True).start()


# --------------------------------------------------------------------------- #
#  Reusable widgets
# --------------------------------------------------------------------------- #


def path_row(parent, label, row, var, width=64, kind="dir"):
    ttk.Label(parent, text=label).grid(column=0, row=row, sticky="w", pady=2)
    entry = ttk.Entry(parent, textvariable=var, width=width)
    entry.grid(column=1, row=row, sticky="we", pady=2, padx=4)

    def browse():
        if kind == "dir":
            chosen = filedialog.askdirectory()
        else:
            chosen = filedialog.askopenfilename()
        if chosen:
            var.set(chosen)

    ttk.Button(parent, text="Browse...", command=browse).grid(column=2, row=row, sticky="e")
    parent.columnconfigure(1, weight=1)


class BaseTab:
    TITLE = "Tab"

    def __init__(self, app, parent):
        self.app = app
        self.parent = parent


# --------------------------------------------------------------------------- #
#  Dashboard
# --------------------------------------------------------------------------- #


class DashboardTab(BaseTab):
    TITLE = "Dashboard"

    def __init__(self, app, parent):
        super().__init__(app, parent)
        self.root_var = tk.StringVar()
        top = ttk.Frame(parent)
        top.pack(fill="x")
        path_row(top, "WhatsApp folder:", 0, self.root_var)

        btns = ttk.Frame(parent)
        btns.pack(fill="x", pady=6)
        self.scan_btn = ttk.Button(btns, text="Scan storage", command=self.scan)
        self.scan_btn.pack(side="left")
        self.total_var = tk.StringVar(value="")

        ttk.Label(parent, text="Note: point this at the folder that contains Media/, Databases/, Backups/ - e.g. Android/media/com.whatsapp/WhatsApp").pack(fill="x")

        wrap = ttk.LabelFrame(parent, text="Storage by category")
        wrap.pack(fill="both", expand=True, pady=6)
        cols = ("category", "files", "size", "path")
        self.tree = ttk.Treeview(wrap, columns=cols, show="headings")
        for col, w, txt in zip(cols, (180, 70, 90, 420), ("Category", "Files", "Size", "Location")):
            self.tree.heading(col, text=txt)
            self.tree.column(col, width=w, anchor="w")
        self.tree.pack(side="left", fill="both", expand=True)
        sb = ttk.Scrollbar(wrap, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=sb.set)
        sb.pack(side="left", fill="y")

        ttk.Label(parent, textvariable=self.total_var, font=("", 11, "bold")).pack(anchor="w")

        ops = ttk.LabelFrame(parent, text="Maintenance", padding=6)
        ops.pack(fill="x", pady=4)
        ttk.Button(ops, text="Remove empty folders", command=self.clean_empty).pack(side="left", padx=4)
        ttk.Button(ops, text="Prune old backups (keep newest 3)", command=self.prune).pack(side="left", padx=4)

    def scan(self):
        def work():
            from wmsuite.scanner import scan_storage

            cats = scan_storage(self.root_var.get() or ".")
            total_b = sum(c["bytes"] for c in cats.values())
            total_f = sum(c["files"] for c in cats.values())

            def fill(cats=cats, total_b=total_b, total_f=total_f):
                self.tree.delete(*self.tree.get_children())
                for cat, info in sorted(cats.items(), key=lambda kv: -kv[1]["bytes"]):
                    self.tree.insert("", "end", values=(
                        cat, info["files"], human_size(info["bytes"]), info["path"]))
                self.total_var.set(
                    "Total: %s in %d files across %d categories"
                    % (human_size(total_b), total_f, len(cats)))

            self.app.post(fill)
            self.app.post(lambda: self.app.log(
                "Scan complete: %d files, %s" % (total_f, human_size(total_b))))

        self.app.run_bg(work, self.scan_btn, "Scanning...")

    def clean_empty(self):
        def work():
            from wmsuite.scanner import remove_empty_dirs

            removed = remove_empty_dirs(self.root_var.get() or ".")
            self.app.post(lambda: self.app.log(
                "Removed %d empty folders." % len(removed)))
            for d in removed[:20]:
                self.app.post(lambda d=d: self.app.log("  - %s" % d))

        self.app.run_bg(work, None, "Removing empty folders...")

    def prune(self):
        def work():
            from pathlib import Path

            from wmsuite.scanner import prune_backups

            root = Path(self.root_var.get() or ".")
            db_dir = root / "Databases" if (root / "Databases").is_dir() else root
            res = prune_backups(db_dir, keep=3)
            self.app.post(lambda: self.app.log(
                "Pruned %d old backups, freed %s." % (res["deleted"], human_size(res["bytes_freed"]))))

        self.app.run_bg(work, None, "Pruning backups...")


# --------------------------------------------------------------------------- #
#  Clean
# --------------------------------------------------------------------------- #


class CleanTab(BaseTab):
    TITLE = "Clean"

    def __init__(self, app, parent):
        super().__init__(app, parent)
        self.root_var = tk.StringVar()
        self.cat_vars = {}
        top = ttk.Frame(parent)
        top.pack(fill="x")
        path_row(top, "WhatsApp folder (or any category folder):", 0, self.root_var)
        ttk.Label(parent, text="Categories appear here after you press Scan.").pack(anchor="w", pady=(6, 0))

        box = ttk.LabelFrame(parent, text="Categories", padding=6)
        box.pack(fill="both", expand=True, pady=6)
        self.box = box

        ctrl = ttk.Frame(parent)
        ctrl.pack(fill="x")
        ttk.Label(ctrl, text="Delete files older than (days, 0 = all):").pack(side="left")
        self.days_var = tk.IntVar(value=0)
        ttk.Spinbox(ctrl, from_=0, to=3650, width=6, textvariable=self.days_var).pack(side="left", padx=4)
        self.dry_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(ctrl, text="Dry run (preview only)", variable=self.dry_var).pack(side="left", padx=8)
        self.scan_btn = ttk.Button(ctrl, text="Scan", command=self.scan)
        self.scan_btn.pack(side="left", padx=4)
        self.preview_btn = ttk.Button(ctrl, text="Preview deletion", command=lambda: self.delete(preview=True))
        self.preview_btn.pack(side="left", padx=4)
        self.delete_btn = ttk.Button(ctrl, text="Delete!", command=lambda: self.delete(preview=False))
        self.delete_btn.pack(side="left", padx=4)

    def _cats(self):
        from wmsuite.scanner import scan_storage

        return scan_storage(self.root_var.get() or ".")

    def scan(self):
        def work():
            cats = self._cats()

            def fill(cats=cats):
                for w in self.box.winfo_children():
                    w.destroy()
                for cat, info in sorted(cats.items(), key=lambda kv: -kv[1]["bytes"]):
                    var = tk.BooleanVar(value=False)
                    self.cat_vars[cat] = (var, info["path"])
                    row = ttk.Frame(self.box)
                    row.pack(fill="x")
                    ttk.Checkbutton(row, variable=var).pack(side="left")
                    ttk.Label(row, text="%-18s %8s  %6d files" % (
                        cat, human_size(info["bytes"]), info["files"])).pack(side="left")

            self.app.post(fill)
            self.app.post(lambda: self.app.log(
                "Clean scan: %d categories found. Tick the ones to clean." % len(cats)))

        self.app.run_bg(work, self.scan_btn, "Scanning...")

    def delete(self, preview=False):
        selected = [
            (cat, path) for cat, (var, path) in self.cat_vars.items() if var.get()
        ]
        if not selected:
            messagebox.showinfo("Nothing selected", "Tick at least one category.")
            return
        if not preview:
            if not messagebox.askyesno(
                "Confirm deletion",
                "Delete files in %d categories older than %d days?\nThis cannot be undone."
                % (len(selected), self.days_var.get()),
            ):
                return
        days = self.days_var.get()
        dry = self.dry_var.get()

        def work():
            from wmsuite.scanner import delete_category, delete_files_older_than

            tot_files = 0
            tot_bytes = 0
            for cat, path in selected:
                if days:
                    res = delete_files_older_than(path, days, dry_run=dry)
                else:
                    res = delete_category(path, dry_run=dry)
                tot_files += res["deleted"]
                tot_bytes += res["bytes_freed"]
                self.app.post(lambda cat=cat, res=res: self.app.log(
                    "%s: %d files, %s%s" % (cat, res["deleted"], human_size(res["bytes_freed"]),
                                            " (dry run)" if dry else "")))
            self.app.post(lambda: self.app.log(
                "Total: %d files, %s%s" % (tot_files, human_size(tot_bytes), " (dry run)" if dry else "")))

        self.app.run_bg(work, None, "Cleaning...")


# --------------------------------------------------------------------------- #
#  Organize
# --------------------------------------------------------------------------- #


class OrganizeTab(BaseTab):
    TITLE = "Organize"

    def __init__(self, app, parent):
        super().__init__(app, parent)
        self.media_var = tk.StringVar()
        self.out_var = tk.StringVar()
        self.mode_var = tk.StringVar(value="date")
        self.msgstore_var = tk.StringVar()
        self.contacts_var = tk.StringVar()
        self.copy_var = tk.BooleanVar(value=True)
        self.dry_var = tk.BooleanVar(value=True)
        self.progress_var = tk.StringVar(value="")

        f = ttk.Frame(parent)
        f.pack(fill="x")
        path_row(f, "Media folder (WhatsApp/Media):", 0, self.media_var)
        path_row(f, "Output folder:", 1, self.out_var)
        path_row(f, "msgstore.db (decrypted, for contact mode):", 2, self.msgstore_var, kind="file")
        path_row(f, "Contacts: wa.db or contacts.vcf (optional):", 3, self.contacts_var, kind="file")

        opts = ttk.LabelFrame(parent, text="Options", padding=6)
        opts.pack(fill="x", pady=6)
        ttk.Label(opts, text="Organize by:").grid(row=0, column=0, sticky="w")
        for val, txt in (("contact", "Contact / group (needs msgstore.db)"),
                         ("date", "Month + category (no database needed)"),
                         ("type", "Category only")):
            ttk.Radiobutton(opts, value=val, text=txt, variable=self.mode_var).grid(
                row=0, column=1 + ["contact", "date", "type"].index(val), sticky="w", padx=6)
        ttk.Checkbutton(opts, text="Copy (keep originals)", variable=self.copy_var).grid(
            row=1, column=0, sticky="w", pady=4)
        ttk.Checkbutton(opts, text="Dry run (preview only)", variable=self.dry_var).grid(
            row=1, column=1, sticky="w")
        self.run_btn = ttk.Button(opts, text="Organize!", command=self.run)
        self.run_btn.grid(row=1, column=2, sticky="e", padx=10)

        ttk.Label(parent, textvariable=self.progress_var, font=("", 10, "bold")).pack(anchor="w")

    def run(self):
        from wmsuite.organizer import Organizer

        org = Organizer(
            self.media_var.get() or ".",
            self.out_var.get() or (self.media_var.get().rstrip("/\\") + "_organized"),
            mode=self.mode_var.get(),
            copy=self.copy_var.get(),
            dry_run=self.dry_var.get(),
            msgstore=self.msgstore_var.get() or None,
            contacts_file=self.contacts_var.get() or None,
        )
        mode_txt = self.mode_var.get()
        dry = self.dry_var.get()

        def progress(i, n, path):
            if i == n or i % 25 == 0:
                self.app.post(lambda i=i, n=n: self.progress_var.set(
                    "Processed %d / %d files..." % (i, n)))

        org.progress = progress

        def work():
            stats = org.run()
            self.app.post(lambda: self.progress_var.set("Done."))
            self.app.post(lambda: self.app.log(
                "Organized by %s%s: %d/%d filed, %d already existed, %d unmatched, %d errors." % (
                    mode_txt, " (dry run)" if dry else "",
                    stats["organized"], stats["total"],
                    stats["skipped_existing"], stats["unmatched"], stats["errors"])))

        self.app.run_bg(work, self.run_btn, "Organizing...")


# --------------------------------------------------------------------------- #
#  Duplicates
# --------------------------------------------------------------------------- #


class DuplicatesTab(BaseTab):
    TITLE = "Duplicates"

    def __init__(self, app, parent):
        super().__init__(app, parent)
        self.root_var = tk.StringVar()
        self.recursive_var = tk.BooleanVar(value=True)
        self.groups = []

        top = ttk.Frame(parent)
        top.pack(fill="x")
        path_row(top, "Folder to scan:", 0, self.root_var)
        ctrl = ttk.Frame(parent)
        ctrl.pack(fill="x", pady=6)
        ttk.Checkbutton(ctrl, text="Recursive", variable=self.recursive_var).pack(side="left")
        self.find_btn = ttk.Button(ctrl, text="Find duplicates", command=self.find)
        self.find_btn.pack(side="left", padx=6)
        self.delete_btn = ttk.Button(ctrl, text="Delete duplicates (keep 1 per group)", command=self.delete)
        self.delete_btn.pack(side="left", padx=6)

        wrap = ttk.LabelFrame(parent, text="Duplicate groups (child rows will be deleted)")
        wrap.pack(fill="both", expand=True)
        cols = ("file", "size")
        self.tree = ttk.Treeview(wrap, columns=cols, show="tree headings")
        self.tree.heading("#0", text="Group")
        self.tree.heading("file", text="File")
        self.tree.heading("size", text="Size")
        self.tree.column("#0", width=240)
        self.tree.column("file", width=440)
        self.tree.column("size", width=90, anchor="e")
        self.tree.pack(side="left", fill="both", expand=True)
        sb = ttk.Scrollbar(wrap, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=sb.set)
        sb.pack(side="left", fill="y")

    def find(self):
        root = self.root_var.get()
        recursive = self.recursive_var.get()

        def progress(kind, msg):
            self.app.post(lambda msg=msg: self.app.status(msg))

        def work():
            from wmsuite.duplicates import find_duplicates

            groups = find_duplicates(root, recursive=recursive, progress=progress)
            self.groups = groups

            def fill(groups=groups):
                self.tree.delete(*self.tree.get_children())
                for gi, group in enumerate(groups):
                    parent = self.tree.insert("", "end",
                                              text="Group %d - %d duplicates" % (gi + 1, len(group) - 1),
                                              open=False)
                    for i, path in enumerate(group):
                        self.tree.insert(parent, "end",
                                         text=("KEEP " if i == 0 else "DELETE ") + path.name,
                                         values=(str(path), human_size(os.path.getsize(str(path)))))

            self.app.post(fill)
            total_dups = sum(len(g) - 1 for g in groups)
            self.app.post(lambda: self.app.log(
                "Found %d duplicate files in %d groups." % (total_dups, len(groups))))

        self.app.run_bg(work, self.find_btn, "Scanning for duplicates...")

    def delete(self):
        if not self.groups:
            messagebox.showinfo("Nothing found", "Find duplicates first.")
            return
        total = sum(len(g) - 1 for g in self.groups)
        if not messagebox.askyesno("Confirm", "Delete %d duplicate files (one keeper per group stays)?" % total):
            return
        groups = self.groups

        def work():
            from wmsuite.duplicates import delete_duplicates

            res = delete_duplicates(groups)
            self.app.post(lambda: self.app.log(
                "Deleted %d duplicates, freed %s." % (res["deleted"], human_size(res["bytes_freed"]))))
            self.groups = []
            self.app.post(lambda: self.tree.delete(*self.tree.get_children()))

        self.app.run_bg(work, None, "Deleting...")


# --------------------------------------------------------------------------- #
#  Dates (EXIF)
# --------------------------------------------------------------------------- #


class DatesTab(BaseTab):
    TITLE = "Dates"

    def __init__(self, app, parent):
        super().__init__(app, parent)
        self.root_var = tk.StringVar()
        self.recursive_var = tk.BooleanVar(value=True)
        self.force_var = tk.BooleanVar(value=False)

        top = ttk.Frame(parent)
        top.pack(fill="x")
        path_row(top, "Media folder:", 0, self.root_var)
        ctrl = ttk.Frame(parent)
        ctrl.pack(fill="x", pady=6)
        ttk.Checkbutton(ctrl, text="Recursive", variable=self.recursive_var).pack(side="left")
        ttk.Checkbutton(ctrl, text="Overwrite existing EXIF dates", variable=self.force_var).pack(side="left", padx=6)
        self.run_btn = ttk.Button(ctrl, text="Restore dates", command=self.run)
        self.run_btn.pack(side="left", padx=6)
        ttk.Label(parent, text=(
            "Restores capture dates from WhatsApp filenames: EXIF DateTimeOriginal for images "
            "(with piexif installed) and file created/modified timestamps for images and videos."
        )).pack(fill="x", pady=4)

    def run(self):
        from wmsuite.exif_restore import restore_dates

        root = self.root_var.get()
        recursive = self.recursive_var.get()
        force = self.force_var.get()

        def progress(i, n, path):
            if i == n or i % 50 == 0:
                self.app.post(lambda i=i, n=n: self.app.status("Fixing dates: %d / %d" % (i, n)))

        def work():
            stats = restore_dates(root, recursive=recursive, force=force, progress=progress)
            self.app.post(lambda: self.app.log(
                "Dates restored on %d/%d files (%d skipped, %d errors)%s" % (
                    stats["dates_set"], stats["files_seen"], stats["skipped"], stats["errors"],
                    " - install piexif for full EXIF support" if stats["piexif_missing"] else "")))

        self.app.run_bg(work, self.run_btn, "Restoring dates...")


# --------------------------------------------------------------------------- #
#  Status saver
# --------------------------------------------------------------------------- #


class StatusTab(BaseTab):
    TITLE = "Status"

    def __init__(self, app, parent):
        super().__init__(app, parent)
        self.src_var = tk.StringVar()
        self.dst_var = tk.StringVar()
        self.move_var = tk.BooleanVar(value=False)

        f = ttk.Frame(parent)
        f.pack(fill="x")
        path_row(f, ".Statuses folder (WhatsApp/Media/.Statuses):", 0, self.src_var)
        path_row(f, "Save statuses to:", 1, self.dst_var)

        ctrl = ttk.Frame(parent)
        ctrl.pack(fill="x", pady=6)
        ttk.Checkbutton(ctrl, text="Move (remove from .Statuses)", variable=self.move_var).pack(side="left")
        self.run_btn = ttk.Button(ctrl, text="Save statuses", command=self.run)
        self.run_btn.pack(side="left", padx=6)
        ttk.Label(parent, text="Statuses normally disappear after 24 hours - this copies them into dated folders before they expire.").pack(fill="x")

    def run(self):
        from wmsuite.status_saver import save_statuses

        src, dst, move = self.src_var.get(), self.dst_var.get(), self.move_var.get()

        def progress(kind, msg):
            self.app.post(lambda msg=msg: self.app.status(msg))

        def work():
            res = save_statuses(src, dst, move=move, progress=progress)
            self.app.post(lambda: self.app.log(
                "Saved %d statuses (%d errors)." % (res["saved"], res["errors"])))

        self.app.run_bg(work, self.run_btn, "Saving statuses...")


# --------------------------------------------------------------------------- #
#  Watcher
# --------------------------------------------------------------------------- #


class WatcherTab(BaseTab):
    TITLE = "Watcher"

    def __init__(self, app, parent):
        super().__init__(app, parent)
        self.watch_var = tk.StringVar()
        self.target_var = tk.StringVar()
        self.interval_var = tk.DoubleVar(value=5.0)
        self.move_var = tk.BooleanVar(value=True)
        self.watcher = None

        f = ttk.Frame(parent)
        f.pack(fill="x")
        path_row(f, "Watch folder (e.g. Downloads):", 0, self.watch_var)
        path_row(f, "File new media into:", 1, self.target_var)

        ctrl = ttk.Frame(parent)
        ctrl.pack(fill="x", pady=6)
        ttk.Label(ctrl, text="Check every (seconds):").pack(side="left")
        ttk.Spinbox(ctrl, from_=1, to=3600, width=6, textvariable=self.interval_var).pack(side="left", padx=4)
        ttk.Checkbutton(ctrl, text="Move files out of watch folder", variable=self.move_var).pack(side="left", padx=6)
        self.start_btn = ttk.Button(ctrl, text="Start", command=self.start)
        self.start_btn.pack(side="left", padx=6)
        self.stop_btn = ttk.Button(ctrl, text="Stop", command=self.stop, state="disabled")
        self.stop_btn.pack(side="left")

        ttk.Label(parent, text=(
            "Keeps an eye on the watch folder and instantly files any WhatsApp media "
            "(WhatsApp Image 2026-04-08 at 14.20.43.jpeg, IMG-20240101-WA0001.jpg, ...) "
            "into target/YYYY-MM/Category/. Leave it running while you use WhatsApp Desktop."
        )).pack(fill="x", pady=4)

    def start(self):
        from wmsuite.watcher import FolderWatcher

        def on_event(msg):
            self.app.post(lambda msg=msg: self.app.log("WATCHER: " + msg))

        self.watcher = FolderWatcher(
            self.watch_var.get(),
            self.target_var.get(),
            interval=self.interval_var.get(),
            move=self.move_var.get(),
            on_event=on_event,
        )
        self.watcher.start()
        self.start_btn.configure(state="disabled")
        self.stop_btn.configure(state="normal")
        self.app.log("Watcher started on %s" % self.watch_var.get())

    def stop(self):
        if self.watcher:
            self.watcher.stop()
            self.watcher = None
        self.start_btn.configure(state="normal")
        self.stop_btn.configure(state="disabled")


# --------------------------------------------------------------------------- #


def run():
    app = App()
    app.log("Welcome to %s %s" % (__app_name__, __version__))
    app.log("All operations are non-destructive by default: dry run is ON until you untick it.")
    app.mainloop()
    return 0


if __name__ == "__main__":
    raise SystemExit(run())
