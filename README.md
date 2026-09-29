# WhatsApp Media Suite

All-in-one WhatsApp and WhatsApp Business media manager for Android and Windows. 100% offline, dry-run by default.

Current version: **v2.2.0** - download the APK (Android) or EXE (Windows) from [Releases](https://github.com/inshalstratrum/whatsapp-media-suite/releases).

## Features

- Finds your WhatsApp / WhatsApp Business media automatically: standard folders, deep full-storage search, or a manually chosen folder
- Category dashboard with sizes and sent/received split
- Safe cleaning (dry run first), old-media cleanup, chat backup pruning, empty folder removal
- **Duplicate finder with side-by-side visual comparison** - see the actual copies with thumbnails before deleting anything
- **Visual galleries everywhere** - every scan result has a View button with image/video thumbnails
- Status saver (saves the 24h statuses), EXIF gallery-date repair, organize by month
- **Organize media by contact** - reads the encrypted chat backup on the phone itself, no chat export needed
- Collapsible, draggable activity log that stays out of your way

## Organize by contact (Android, new in v2.2.0)

This works like the desktop tools wa-sort-media and whatskeep, but fully on your phone, offline:

1. In WhatsApp: Settings > Chats > Chat backup > End-to-end encrypted backup > turn it ON, choose **Use 64-digit key instead of a password**, write the key down, then tap **Back up now**.
2. In the app: Chats tab, paste the 64-digit key, tap **Load chat map**.
3. Review the detected chats and file counts, tap a contact to preview the media, then **Preview (dry run)** or **Organize now**.

Media gets sorted into folders named after each contact or group under WMSuite-Organized/By contact. Nothing inside WhatsApp is modified when copying.

Note: password-protected encrypted backups are not supported - switch to the 64-digit key option and make a fresh backup.

## Install (Android)

Download WhatsAppMediaSuite.apk from Releases and install it. If Android refuses to install over the old version, uninstall the old app first (different signing keys), then allow **All files access** when asked.

## Safety

Every destructive action starts as a dry run. Turn the DRY RUN chip in the top bar off only when you are sure.

## Credits

- [wa-crypt-tools](https://github.com/ElDavoo/wa-crypt-tools) (ElDavoo) - crypt15 key derivation and backup format research (GPL-3.0)
- [wa-sort-media](https://github.com/chances190/wa-sort-media) and [whatskeep](https://github.com/alissonlinneker/whatskeep) - media-to-chat mapping approach
- [whatsapp-backup-tools](https://github.com/auanasgheps/whatsapp-backup-tools) - backup tooling inspiration

Licensed under GPL-3.0.
