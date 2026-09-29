"""Contact loading from WhatsApp wa.db or a vCard (.vcf) export.

Ported from wa-sort-media (GPL-3.0, Copyright chances190):
https://github.com/chances190/wa-sort-media
"""

from __future__ import annotations

import quopri
import re
import sqlite3
from contextlib import closing
from pathlib import Path


def _digits(raw):
    return re.sub(r"\D", "", str(raw))


def load_wa_contacts(path):
    """Load a mapping of chat-id -> display name from WhatsApp's wa.db."""
    path = Path(path)
    if not path.exists():
        raise FileNotFoundError("wa.db not found: %s" % path)

    contacts = {}
    with closing(sqlite3.connect(str(path))) as conn:
        conn.row_factory = sqlite3.Row
        cur = conn.cursor()
        try:
            cur.execute("SELECT jid, display_name, wa_name FROM wa_contacts")
        except sqlite3.OperationalError as e:
            raise ValueError(
                "Invalid wa.db file. Use a VCF export instead (--contacts file.vcf)."
            ) from e
        for row in cur:
            jid = row["jid"]
            raw_name = row["display_name"] or row["wa_name"]
            if jid and raw_name:
                chat_id = str(jid).split("@", 1)[0]
                contacts[chat_id] = str(raw_name).strip()

    if not contacts:
        raise ValueError("'wa_contacts' table is empty.")
    return contacts


def load_vcf_contacts(path):
    """Load a mapping of chat-id -> display name from a vCard export."""
    path = Path(path)
    if not path.exists():
        raise FileNotFoundError("Contacts file not found: %s" % path)

    text = path.read_text(encoding="utf-8", errors="replace")
    contacts = {}
    for vcard in text.split("BEGIN:VCARD")[1:]:
        name, phones = _parse_vcard(vcard)
        if not name or not phones:
            continue
        for phone in phones:
            contacts.setdefault(phone, name)

    if not contacts:
        raise ValueError("No contacts with phone numbers found in %s" % path)
    return contacts


def load_contacts(path):
    """Auto-detect wa.db vs .vcf and load contacts."""
    suffix = Path(path).suffix.lower()
    if suffix == ".vcf":
        return load_vcf_contacts(path)
    return load_wa_contacts(path)


def _parse_vcard(text):
    """Parse one vCard entry -> (display_name, set_of_phone_digits)."""
    lines = text.splitlines()
    phones = set()
    name = None
    i = 0
    while i < len(lines):
        raw_line = lines[i].strip()
        # Handle RFC folded lines (continuation starts with space/tab).
        while i + 1 < len(lines) and lines[i + 1][:1] in (" ", "\t"):
            i += 1
            raw_line += lines[i].strip()
        line = raw_line.strip()
        lowered = line.lower()
        if lowered.startswith("fn") and ":" in line:
            header, value = line.split(":", 1)
            if value:
                if "quoted-printable" in header.lower():
                    try:
                        value = quopri.decodestring(value.encode("utf-8")).decode(
                            "utf-8", errors="replace"
                        )
                    except Exception:
                        pass
                name = value.strip()
        elif lowered.startswith("tel") and ":" in line:
            _, value = line.split(":", 1)
            digits = _digits(value)
            if digits:
                phones.update(_jid_variants(digits))
        i += 1
    return name, phones


def _jid_variants(digits):
    """All plausible WhatsApp JID prefixes for a phone number.

    Includes the Brazil (+55) mobile '9' variants, where WhatsApp sometimes
    stores the number with/without the extra leading 9.
    """
    variants = {digits}
    if digits.startswith("55"):
        if len(digits) == 13:
            variants.add(digits[:4] + digits[5:])  # strip the 9
        elif len(digits) == 12:
            variants.add(digits[:4] + "9" + digits[4:])  # add the 9
    return variants
