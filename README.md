# WhatsApp Media Suite

**One suite for everything WhatsApp does to your media - Windows desktop and a native Android app. 100% offline, dry-run by default, your data never leaves the device.**

## Features

- Scans **WhatsApp and WhatsApp Business** media folders: images, video, video notes, audio, voice notes, documents, stickers, GIFs, wallpapers and statuses.
- **Finds your media anywhere on the phone**: automatic profile detection, a deep **search of the entire storage** for media folders, and a **manual folder picker** as a fallback for unusual devices.
- Safe cleaning with a sent/received split, keep-earliest duplicate removal, old-media cleanup and backup pruning.
- **EXIF date repair** so the gallery shows photos on the right days; status saver; organize-by-date; per-contact organizing via chat exports.
- Nothing is ever uploaded - the app works completely offline.

## Downloads

Get the latest release here: <https://github.com/inshalstratrum/whatsapp-media-suite/releases/latest>

- **WhatsAppMediaSuite.exe** - the Windows desktop app (GUI + CLI), no installation needed.
- **WhatsAppMediaSuite.apk** - the Android app v2.1.0 with the professional UI (see below).

## Running from source

- **Windows**: pip install -r requirements.txt, then python wmsuite_launch.py.
- **Android**: open the android/ folder in Android Studio, or run gradle assembleDebug inside it.

## CLI

The Windows build also exposes a CLI, for example:

    python -m wmsuite scan /sdcard/WhatsApp
    python -m wmsuite organize <Media-folder> [--mode copy] [--dry-run]

## Organizing by contact (Android chat database)

WhatsApp's chat database is end-to-end encrypted and unreadable without root, so media cannot be linked to contacts automatically on a normal phone. WhatsApp Media Suite instead uses WhatsApp's built-in **Export chat** (with media): the exported ZIP contains the contact name, and the app copies the attachments into per-contact folders. Your chat, the exported ZIP and WhatsApp itself are never modified.

## Android app

Four tabs, everything previewed in a fixed activity log before anything is deleted:

- **Dashboard** - grant storage access; pick the detected WhatsApp / WhatsApp Business profile (the active one is preselected, each profile shows its full folder path). If nothing is found: **Search entire storage** walks the whole shared storage for anything that looks like a WhatsApp media folder, and **Choose media folder manually** lets you point the app at any folder yourself (remembered across restarts). Then scan and review the categories.
- **Clean** - clean any category with an All / Received / Sent choice and confirmation, clean media older than N days (adjustable stepper), prune old msgstore backups (keep 5 newest), remove empty folders.
- **Tools** - duplicate finder (size + SHA-256) that **always keeps the earliest original** of each group; **EXIF date repair** that reads the timestamp from the filename and writes it back into the photo EXIF so the gallery shows photos on the right days; status saver; organize-by-date.
- **Chats** - guided **per-contact organizing**: export chats with media from WhatsApp or WhatsApp Business, and the app copies their attachments into WMSuite-Organized/Conversations/&lt;Contact&gt;/.

A global **dry-run** toggle previews every destructive action first. Databases and Backups are never offered as cleanable categories - your chat history stays safe.

## Safety

- Every destructive operation has a **dry-run** (preview) mode, on by default.
- Every real deletion asks for confirmation first.
- Duplicate removal on Android **always keeps the earliest original** of every group.
- Contact organizing **copies** by default; originals stay untouched.
- It only reads files you point it at and never connects to WhatsApp.
- Nothing is ever uploaded anywhere - the app is 100% offline.

## Project layout

- android/ - the native Android app (Kotlin, programmatic UI).
- wmsuite/ and wmsuite_launch.py - the Python/Windows app.
- .github/workflows/ - CI that builds the APK and the Windows EXE and attaches them to the release.

## License & credits

This project builds on ideas and code from several open-source WhatsApp tools:

- [wa-sort-media](https://github.com/chances190/wa-sort-media) (GPL-3.0)
- [whatskeep](https://github.com/alissonlinneker/whatskeep) (MIT)
- [whatsapp-media-tools](https://github.com/ikaruswill/whatsapp-media-tools)
- [WhatsAppCleaner](https://github.com/VishnuSanal/WhatsAppCleaner) (GPL-3.0)

WhatsApp Media Suite is a third-party tool and is not affiliated with or endorsed by WhatsApp or Meta.
