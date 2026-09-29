"""Map media filenames to the chat that originated them, via msgstore.db.

Ported from wa-sort-media (GPL-3.0, Copyright chances190) with a legacy
schema fallback for older databases.

A chat is a tuple (kind, id, subject) where kind is "group" or "dm":
* group: id is the group JID prefix, subject is the group name (or None)
* dm:    id is the contact's phone digits (or username)
"""

from __future__ import annotations

import os
import sqlite3
from pathlib import Path

_MODERN_SQL = """
    SELECT
        media.file_path,
        jid.user,
        jid.server,
        chat.subject
    FROM message_media media
        JOIN message ON media.message_row_id = message._id
        JOIN chat ON message.chat_row_id = chat._id
        JOIN jid ON chat.jid_row_id = jid._id
    WHERE media.file_path IS NOT NULL
"""

_LEGACY_SQL = """
    SELECT
        messages.media_url,
        chat.jid,
        chat.subject
    FROM messages
        JOIN chat ON messages.chat_row_id = chat._id
    WHERE messages.media_url IS NOT NULL
"""


def match_files_to_chats(db_path):
    """Return a dict of filename -> (kind, id, subject) from a decrypted msgstore.db."""
    db_path = Path(db_path)
    if not db_path.exists():
        raise FileNotFoundError("msgstore.db not found: %s" % db_path)

    mapping = {}
    conn = sqlite3.connect(str(db_path))
    try:
        try:
            for file_path, user, server, subject in conn.execute(_MODERN_SQL):
                chat = _chat_from_jid(str(user), str(server), subject)
                if chat is not None:
                    mapping.setdefault(os.path.basename(str(file_path)), chat)
        except sqlite3.OperationalError:
            # Older msgstore schema.
            for file_path, jid, subject in conn.execute(_LEGACY_SQL):
                user, _, server = str(jid).partition("@")
                chat = _chat_from_jid(user, server, subject)
                if chat is not None:
                    mapping.setdefault(os.path.basename(str(file_path)), chat)
    except sqlite3.OperationalError as e:
        raise ValueError(
            "Invalid WhatsApp database (is it decrypted? Use wa-crypt-tools first)."
        ) from e
    finally:
        conn.close()
    return mapping


def _chat_from_jid(user, server, subject):
    if server == "g.us":
        return ("group", user, subject)
    if server == "s.whatsapp.net":
        return ("dm", user, None)
    return None  # broadcast and other servers are skipped
