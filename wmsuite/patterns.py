"""Filename parsing for WhatsApp media files.

Ported from WhatsKeep (MIT License, Copyright alissonlinneker):
https://github.com/alissonlinneker/whatskeep

Recognises the three WhatsApp naming conventions:

* Modern (Desktop):   "WhatsApp Image 2026-04-08 at 14.20.43.jpeg"
* Legacy (Android):  "IMG-20240101-WA0001.jpg"
* Chat export:       "WhatsApp Chat - Contact Name.zip"
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import datetime

_MODERN_TYPE_MAP = {
    "image": "image",
    "audio": "audio",
    "video": "video",
    "ptt": "voice_note",
    "document": "document",
    "sticker": "sticker",
}

_LEGACY_TYPE_MAP = {
    "IMG": "image",
    "VID": "video",
    "DOC": "document",
    "AUD": "audio",
    "PTT": "voice_note",
    "STK": "sticker",
}

# Modern pattern (Desktop macOS/Windows) - spaces, hyphens or underscores.
_MODERN_RE = re.compile(
    r"^WhatsApp"
    r"(?P<sep>[\s_-])"
    r"(?P<type>Image|Audio|Video|Ptt|Document|Sticker)"
    r"(?P=sep)"
    r"(?P<year>\d{4})-(?P<month>\d{2})-(?P<day>\d{2})"
    r"(?P=sep)at(?P=sep)"
    r"(?P<hour>\d{2})\.(?P<minute>\d{2})\.(?P<second>\d{2})"
    r"(?:\s\((?P<dup>\d+)\))?"
    r"\.(?P<ext>[a-zA-Z0-9]+)$",
)

# Legacy Android pattern: IMG-20220331-WA0076.jpg
_LEGACY_RE = re.compile(
    r"^(?P<type>IMG|VID|DOC|AUD|PTT|STK)"
    r"-(?P<year>\d{4})(?P<month>\d{2})(?P<day>\d{2})"
    r"-WA(?P<seq>\d+)"
    r"\.(?P<ext>[a-zA-Z0-9]+)$",
)

# Chat export pattern: WhatsApp Chat - Contact Name.zip
_CHAT_RE = re.compile(r"^WhatsApp Chat - (?P<contact>.+)\.zip$")


@dataclass(frozen=True)
class ParsedFile:
    """Structured result of parsing a WhatsApp filename."""

    media_type: str
    timestamp: datetime
    extension: str
    duplicate_index: int | None = None
    is_chat_export: bool = False
    contact_name: str | None = None


def parse_whatsapp_filename(filename):
    """Parse a WhatsApp media filename.

    Returns a ParsedFile, or None when filename does not match any known
    WhatsApp naming convention.
    """
    m = _MODERN_RE.match(filename)
    if m:
        media_type = _MODERN_TYPE_MAP[m.group("type").lower()]
        ts = datetime(
            int(m.group("year")),
            int(m.group("month")),
            int(m.group("day")),
            int(m.group("hour")),
            int(m.group("minute")),
            int(m.group("second")),
        )
        dup = int(m.group("dup")) if m.group("dup") else None
        return ParsedFile(
            media_type=media_type,
            timestamp=ts,
            extension=m.group("ext").lower(),
            duplicate_index=dup,
        )

    m = _LEGACY_RE.match(filename)
    if m:
        media_type = _LEGACY_TYPE_MAP[m.group("type")]
        ts = datetime(int(m.group("year")), int(m.group("month")), int(m.group("day")))
        return ParsedFile(
            media_type=media_type,
            timestamp=ts,
            extension=m.group("ext").lower(),
        )

    m = _CHAT_RE.match(filename)
    if m:
        return ParsedFile(
            media_type="document",
            timestamp=datetime(1970, 1, 1),
            extension="zip",
            is_chat_export=True,
            contact_name=m.group("contact"),
        )

    return None


def is_whatsapp_file(filename):
    """Return True if filename looks like a WhatsApp media file."""
    return parse_whatsapp_filename(filename) is not None
