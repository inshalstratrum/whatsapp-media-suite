package com.inshal.wmsuite

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.ContactsContract
import java.io.File
import java.util.Locale

/**
 * Maps WhatsApp media files to the chat they belong to, using the phone
 * own encrypted chat backup - no chat export needed. Native port of the
 * approach used by the wa-sort-media / whatskeep tools (GPL-3.0).
 */
object ContactOrganizer {

    class MapInfo(val fileToLabel: Map<String, String>, val source: String)

    class OrgResult(val files: Int, val bytes: Long, val skipped: Int, val folders: Int, val dry: Boolean)

    /** Finds the newest plain or encrypted chat database in a Databases folder. */
    fun findMsgstore(dbDir: File): File? {
        if (!dbDir.isDirectory) return null
        val files = try { dbDir.listFiles() } catch (e: Exception) { null } ?: return null
        val plain = files.filter { it.isFile && it.name == "msgstore.db" }
        if (plain.isNotEmpty()) return plain[0]
        val crypts = files.filter { it.isFile && it.name.endsWith(".crypt15") }
        val newest = crypts.maxByOrNull { it.lastModified() }
        if (newest != null) return newest
        return files.filter { it.isFile && it.name.startsWith("msgstore") && it.name.endsWith(".db") }
            .maxByOrNull { it.lastModified() }
    }

    /** Phone contacts keyed by the last 7-12 digits of each number. */
    fun loadDeviceContacts(context: Context): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            val cur: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null, null, null
            )
            if (cur != null) {
                while (cur.moveToNext()) {
                    val num = cur.getString(0) ?: continue
                    val name = cur.getString(1) ?: continue
                    val digits = num.filter { it.isDigit() }
                    for (len in 12 downTo 7) {
                        if (digits.length >= len) {
                            val k = digits.takeLast(len)
                            if (!map.containsKey(k)) map[k] = name
                        }
                    }
                }
                cur.close()
            }
        } catch (e: Exception) {
            // permission denied - caller falls back to wa.db names
        }
        return map
    }

    private fun lookupPhone(device: Map<String, String>, user: String): String? {
        for (len in 12 downTo 7) {
            if (user.length >= len) {
                val v = device[user.takeLast(len)]
                if (v != null) return v
            }
        }
        return null
    }

    fun sanitize(name: String): String {
        var s = name.replace(Regex("[\\/:*?\"<>|]"), " ").trim()
        if (s.length > 60) s = s.substring(0, 60).trim()
        s = s.replace(Regex("\\s+"), " ")
        return if (s.isEmpty()) "Unknown contact" else s
    }

    private fun clearDir(dir: File) {
        try {
            dir.mkdirs()
            dir.listFiles()?.forEach { it.deleteRecursively() }
        } catch (e: Exception) {
            // best effort
        }
    }

    fun cleanupDecrypted(context: Context) {
        clearDir(File(context.cacheDir, "wa-decrypted"))
    }

    private fun readWaContacts(db: File, out: HashMap<String, String>) {
        if (!db.isFile) return
        try {
            val sqlite = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val cur = sqlite.rawQuery("SELECT jid, display_name FROM wa_contacts", null)
                while (cur.moveToNext()) {
                    val jid = cur.getString(0) ?: continue
                    val name = cur.getString(1) ?: continue
                    val user = jid.substringBefore("@")
                    if (user.isNotBlank() && name.isNotBlank() && !out.containsKey(user)) {
                        out[user] = name
                    }
                }
                cur.close()
            } finally {
                sqlite.close()
            }
        } catch (e: Exception) {
            // table missing or unreadable
        }
    }

    private fun addEntry(path: String?, user: String?, server: String?, subject: String?, out: HashMap<String, String>, waNames: Map<String, String>, device: Map<String, String>) {
        if (path == null) return
        val name = File(path).name
        if (name.isEmpty()) return
        val label: String?
        if (server == "s.whatsapp.net") {
            val digits = user ?: ""
            val wa = if (digits.isNotBlank()) waNames[digits] else null
            val dev = if (digits.isNotBlank()) lookupPhone(device, digits) else null
            label = when {
                !wa.isNullOrBlank() -> wa
                !dev.isNullOrBlank() -> dev
                digits.isNotBlank() -> "+" + digits
                else -> null
            }
        } else if (server == "g.us") {
            label = if (subject.isNullOrBlank()) null else subject
        } else {
            label = null
        }
        if (label == null) return
        val clean = sanitize(label)
        if (!out.containsKey(name)) out[name] = clean
    }

    private fun queryModern(db: File, out: HashMap<String, String>, waNames: Map<String, String>, device: Map<String, String>): Boolean {
        return try {
            val sqlite = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val cur = sqlite.rawQuery(
                    "SELECT media.file_path, jid.user, jid.server, chat.subject " +
                        "FROM message_media media " +
                        "JOIN message ON media.message_row_id = message._id " +
                        "JOIN chat ON message.chat_row_id = chat._id " +
                        "JOIN jid ON chat.jid_row_id = jid._id " +
                        "WHERE media.file_path IS NOT NULL", null)
                while (cur.moveToNext()) {
                    addEntry(cur.getString(0), cur.getString(1), cur.getString(2), cur.getString(3), out, waNames, device)
                }
                cur.close()
            } finally {
                sqlite.close()
            }
            out.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun queryLegacy(db: File, out: HashMap<String, String>, waNames: Map<String, String>, device: Map<String, String>): Boolean {
        val queries = listOf(
            "SELECT messages.media_name, jid.user, jid.server, chat.subject " +
                "FROM messages " +
                "JOIN chat ON messages.chat_row_id = chat._id " +
                "JOIN jid ON chat.jid_row_id = jid._id " +
                "WHERE messages.media_name IS NOT NULL",
            "SELECT messages.media_name, jid.user, jid.server, chat_list.subject " +
                "FROM messages " +
                "JOIN chat_list ON messages.chat_row_id = chat_list._id " +
                "JOIN jid ON chat_list.jid_row_id = jid._id " +
                "WHERE messages.media_name IS NOT NULL"
        )
        for (q in queries) {
            try {
                val sqlite = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY)
                try {
                    val cur = sqlite.rawQuery(q, null)
                    while (cur.moveToNext()) {
                        addEntry(cur.getString(0), cur.getString(1), cur.getString(2), cur.getString(3), out, waNames, device)
                    }
                    cur.close()
                } finally {
                    sqlite.close()
                }
                if (out.isNotEmpty()) return true
            } catch (e: Exception) {
                // try the next legacy schema
            }
        }
        return false
    }

    /**
     * Builds a media file name to contact/group label map from the chat
     * backup found under profileRoot/Databases. Returns null on failure,
     * with diagnostics sent to log.
     */
    fun buildMap(context: Context, profileRoot: File, keyHex: String, useDeviceContacts: Boolean, log: (String) -> Unit): MapInfo? {
        val dbDir = File(profileRoot, "Databases")
        if (!dbDir.isDirectory) {
            log("No Databases folder found under " + profileRoot.absolutePath)
            return null
        }
        val msgstore = findMsgstore(dbDir)
        if (msgstore == null) {
            log("No msgstore database found in " + dbDir.absolutePath)
            return null
        }
        log("Chat backup: " + msgstore.name + " (" + Cleaner.humanSize(msgstore.length()) + ")")
        var dbFile = msgstore
        if (msgstore.name.endsWith(".crypt15")) {
            if (keyHex.isBlank()) {
                log("This backup is end-to-end encrypted - enter your 64-digit key first.")
                return null
            }
            val workDir = File(context.cacheDir, "wa-decrypted")
            clearDir(workDir)
            log("Decrypting the backup ...")
            dbFile = try {
                WaCrypt15.decrypt(msgstore, keyHex, workDir)
            } catch (e: WaCrypt15.CryptException) {
                log("Decryption failed: " + e.message)
                null
            } ?: return null
            log("Decrypted OK")
        }
        val waNames = HashMap<String, String>()
        readWaContacts(File(dbFile.parentFile, "wa.db"), waNames)
        readWaContacts(dbFile, waNames)
        log("Contact names available: " + waNames.size)
        val device = if (useDeviceContacts) loadDeviceContacts(context) else HashMap<String, String>()
        if (useDeviceContacts) log("Phone contact entries loaded: " + device.size)
        val fileToLabel = HashMap<String, String>()
        var source = "chat database (modern schema)"
        if (!queryModern(dbFile, fileToLabel, waNames, device)) {
            fileToLabel.clear()
            source = "chat database (legacy schema)"
            if (!queryLegacy(dbFile, fileToLabel, waNames, device)) {
                log("Could not read chat media references from the database.")
                return null
            }
        }
        log("Media files mapped to chats: " + fileToLabel.size)
        return MapInfo(fileToLabel, source)
    }

    /**
     * Copies (or moves) every mapped media file into outRoot/<contact>/.
     * A dry run only previews what would happen.
     */
    fun organize(mediaRoot: File, outRoot: File, map: Map<String, String>, move: Boolean, dry: Boolean, log: (String) -> Unit): OrgResult {
        var files = 0
        var skipped = 0
        var folders = 0
        var bytes = 0L
        var shown = 0
        if (!dry) outRoot.mkdirs()
        val queue = ArrayDeque<File>()
        queue.add(mediaRoot)
        var visited = 0
        while (queue.isNotEmpty() && visited < 40000) {
            val dir = queue.removeFirst()
            val kids = try { dir.listFiles() } catch (e: Exception) { null } ?: continue
            for (f in kids) {
                if (f.isDirectory) {
                    queue.add(f)
                    visited++
                } else if (Cleaner.categoryFor(f, mediaRoot) != null) {
                    val label = map[f.name]
                    if (label == null) {
                        skipped++
                        continue
                    }
                    bytes += f.length()
                    if (dry) {
                        files++
                        if (shown < 15) {
                            shown++
                            log("Would " + (if (move) "move" else "copy") + ": " + f.name + "  ->  " + label + "/")
                        }
                    } else {
                        val dirOut = File(outRoot, label)
                        if (!dirOut.isDirectory && dirOut.mkdirs()) folders++
                        try {
                            val target = uniqueTarget(dirOut, f.name)
                            if (move) {
                                if (!f.renameTo(target)) {
                                    f.copyTo(target, overwrite = false)
                                    f.delete()
                                }
                            } else {
                                f.copyTo(target, overwrite = false)
                            }
                            files++
                        } catch (e: Exception) {
                            skipped++
                        }
                    }
                }
            }
        }
        return OrgResult(files, bytes, skipped, folders, dry)
    }

    private fun uniqueTarget(dirOut: File, name: String): File {
        var t = File(dirOut, name)
        if (!t.exists()) return t
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            t = File(dirOut, stem + " (" + n + ")" + ext)
            if (!t.exists()) return t
            n++
        }
    }
}