# WhatsApp Media Suite

**One suite for everything WhatsApp does to your media - Windows desktop, Android app, and CLI.**

WhatsApp Media Suite is a single integrated desktop application (GUI + CLI) that
intelligently merges the best features of five open-source WhatsApp tools —
organizing, deduplicating, date-repairing, cleaning and live-watching your
media, without duplicate features and without touching your account. The Android app supports **both WhatsApp and WhatsApp Business**.

```
┌─────────────────────────────────────────────────────┐
│ WhatsApp Media Suite                                 │
├───────────┬─────────────────────────────────────────┤
│ Dashboard │  Clean │  Organize │  Duplicates │  Dates │
│ Status    │  Watcher                                │
├───────────┴─────────────────────────────────────────┤
│                 Activity log                         │
└─────────────────────────────────────────────────────┘
```

## Features

| Tab | What it does | Merged from |
|---|---|---|
| **Dashboard** | Scan any WhatsApp folder and get a per-category size report (Images, Video, Audio, Documents, Databases, Statuses…), remove empty folders, prune old `msgstore` backups | *WhatsAppCleaner* & *WhatsApp-Cleaner* (Android apps) — reimplemented natively for desktop |
| **Clean** | Tick categories and delete files by category and/or age (older-than-N-days), with dry-run preview first | *WhatsAppCleaner* / *WhatsApp-Cleaner* |
| **Organize** | Sort the whole media folder into `Contact or Group / Category /` folders using your decrypted `msgstore.db` + contacts (`wa.db` or `contacts.vcf`); or organize by `YYYY-MM / Category` with no database at all; copy or move, dry-run first | *wa-sort-media* + *whatskeep* (both organizers unified into one engine) |
| **Duplicates** | Fast three-stage duplicate finder (size → first-chunk hash → full hash), keeps the shortest filename, preview then delete | *whatsapp-media-tools* (`find-duplicates.py`) |
| **Dates** | Restore
 the capture date WhatsApp strips from photos: EXIF `DateTimeOrigi
nal` for images, created/modified 
stamps for images + videos, straight from the filename | *whatsapp-media-tools* (`restore-exif.py`) |
| **Status** | Copy `.Statuses` (24-hour disappearing statuses) into permanent `YYYY-MM-DD` folders before they expire | *WhatsApp-Cleaner* (status saver) |
| **Watcher** | Watch a folder (e.g. Downloads) and instantly file any incoming WhatsApp media into `YYYY-MM / Category /` — content-fingerprinted so nothing is processed twice | *whatskeep* (real-time monitoring) |

Smart de-duplication of features: the two "organize by contact" engines
(wa-sort-media and whatskeep) are one engine here; the two Android
cleaners' overlapping features (scan, clean, status) are one Cleaner; and
duplicate detection is shared by both the Cleaner and the Duplicates tab.

## Downloads

Get both apps from the [v2.1.0 Release](../../releases):

- **`WhatsAppMediaSuite.exe`** - the Windows desktop app (GUI + CLI), no
  Python needed. Windows SmartScreen may warn on first launch (unsigned
  binary) - choose *More info -> Run anyway*.
- **`WhatsAppMediaSuite.apk`** - the Android app v2.1.0 with the new professional UI (see below).

Every push to `main` rebuilds both automatically and re-uploads them to the
Release; builds are also kept as artifacts in the
[Actions tab](../../actions) (`WhatsAppMediaSuite-windows` and
`WhatsAppMediaSuite-android`).

**Building the .exe yourself:**

```bat
pip install -r requirements.txt pyinstaller
build_exe.bat
```

Or directly:

```bat
pyinstaller --noconfirm --onefile --windowed --name WhatsAppMediaSuite wmsuite_launch.py
```


## Running from source

```bash
pip install -r requirements.txt
python -m wmsuite            # GUI
python -m wmsuite scan PATH   # CLI (see below)
```

## CLI

```text
wmsuite scan <whatsapp-folder>                       # size report per category
wmsuite clean <folder> --empty-dirs --prune-backups 3 --dry-run
wmsuite organize <Me
dia-folder> [--mode contact|date|type]
                 [--msgstore msgstore.db] [--contacts contacts.v
cf]
                 [--move] [--dry-run]
wmsuite duplicates <folder> -r [--delete | --dry-run]
wmsuite exif <folder> -r [--force]
wmsuite status <Media/.Statuses> <save-folder> 
[--move]
wmsuite watch <Downloads> <target-folder> [--interval 5] [--copy]
```

## Organizing by contact (Android chat database)

WhatsApp's Android backup database (`msgstore.db.crypt14/15`) links every
media file to the chat that received it, so the Organizer can build
`John Doe/Images/…` folders. Quick outline:

1. In WhatsApp: **Settings → Chats → Chat backup → End-to-end encrypted
   backup → Use a 64-digit key instead of a password** — and **save the key**.
2. Run a local **Back up now** (so `Databases/msgstore.db.crypt15` is fresh).
3. Copy `Android/media/com.whatsapp/WhatsApp/` (the `Media` and `Databases`
   folders) from the phone to your PC, plus a `contacts.vcf`
   from [contacts.google.com](https://contacts.google.com) (optional, for
   names instead of phone numbers).
4. Decrypt the database once:

   ```bash
   pip install wa-crypt-tools
   wadecrypt decrypt encrypted_backup.key Databases/msgstore.db.crypt15 msgstore.db
   ```

5. In the **Organize** tab, set the Media folder, output folder and
   `msgstore.db`, choose *Organize by: Contact*, tick **Dry run** to preview,
   then run for real.

`date` and `type` modes need no database at all — point them at any folder
of WhatsApp media (including your Downloads folder).

## Android app

The repo ships a native Android app (in `android/`) with a professional, guided interface - the successor of the two Android cleaner apps merged into this project. It detects **both WhatsApp and WhatsApp Business** automatically (`Android/media/com.whatsapp`, `Android/media/com.whatsapp.w4b` and the legacy `/sdcard` locations), shows which profile is active, and preselects the most recently used one - so scanning always finds your media.

- *
*Dashboard** - guided setup: storage permission, profile picker (WhatsApp vs WhatsApp Business with file counts, sizes and last-activity badges), storage scan with per-category cards showing a **sent vs received** split.
- **Clean** - clean any category with an *All / Received / Sent* choice and confirmation, clean media older than N days (adjustable stepper), prune old msgstore backups (keep 5 newest), remove empty folders.
- **Tools** - duplicate finder (size + SHA-256) that **always keeps the earliest original** of each group; **EXIF date repair** that reads the timestamp from the filename and writes it back into the photo EXIF so the gallery shows photos on the right days; status saver; organize-by-date.
- **Chats** - guided **per-contact organizing**: export chats with media from WhatsApp or WhatsApp Business, and the app copies their attachments into `WMSuite-Organized/Conversations/<Contact>/`. (The chat database is end-to-end encrypted, so chat exports are the safe way to link media to a contact without root - your chat and the exported ZIP are never modified.)
- If your media folders are not found automatically, the Dashboard can **search the entire storage** for them, or let you **pick the folder manually**.
- A global **dry-run** toggle previews every destructive action first.
- `Databases` and `Backups` are never offered as cleanable categories - your chat history stays safe.

Install: download `WhatsAppMediaSuite.apk` from the [v2.1.0 Release](../../releases), allow "install unknown apps" for your browser or file manager when prompted, and install. On first launch tap **Grant storage access** on the Dashboard and allow *All files access*. The app is 100% offline and never uploads anything.

### CLI on Android (Termux)

Power users can run the exact same Python suite on the phone:

```bash
pkg install python git
termux-setup-storage
git clone https://github.com/inshalstratrum/whatsapp-media-suite
cd whatsapp-media-suite
python -m wmsuite scan /sdcard/WhatsApp
python -m wmsuite clean /sdcard/WhatsApp/Media --days 30 --dry-run
```

## Safety

- Every destructive operation has a **dry-run** (preview) mode,
 and dry run
  is ON by default in the GUI.
- Contact organizing **copies** by default; originals stay untouched.
- Duplicate removal on Android **always keeps the earliest original** of every group.
- Nothing is ever uploaded anywhere — the app is 100% offline.
- It only reads files you point it at and never connects to WhatsApp.

## Project layout

```
wmsuite/
├── gui.py           master Tkinter interface (all 7 tabs)
├── cli.py           full-featured command line
├── patterns.py      WhatsApp filename parsing (modern + legacy + chat export)
├── organizer.py     unified organize engine (contact / date / type)
├── db.py            msgstore.db → chat mapping (modern + legacy schema)
├── contacts.py      wa.db + vCard contact loading
├── duplicat
es.py    3-stage duplicate finder
├── exif_restore.py  EXIF / timestamp restoration
├── scanner.py       storage scan & cleaner
├── status_saver.py  status saver
└── watcher.py       live folder watcher
```

## License & credits

GPL-3.0. This pro
ject merges and reimplements code and ideas from:

- [wa-sort-media](https://github.com/chances190/wa-sort-media) (GPL-3.0) —
  msgstore→chat mapping, wa.db/vcf contacts, organize-by-conversation
- [whatskeep](https://github.com/alissonlinneker/whatskeep) (MIT) —
  filename patterns, live watcher concept
- [whatsapp-media-tools](https://github.com/ikaruswill/whatsapp-media-tools) —
  duplicate finding and EXIF date restoration (reimplemented)
- [WhatsAppCleaner](https://github.com/VishnuSanal/WhatsAppCleaner) (GPL-3.0) and
  [WhatsApp-Cleaner](https://github.com/pawank0411/WhatsApp-Cleaner) (MIT) —
  the Android cleaners whose scan/clean/status-saver features were rebuilt here
  as native desktop modules

WhatsApp Media Suite is a third-party tool and is not affiliated with or
endorsed by WhatsApp or Meta.
