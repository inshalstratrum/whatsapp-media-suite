package com.inshal.wmsuite

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Decrypts the local end-to-end encrypted WhatsApp chat backup
 * (msgstore.db.crypt15) using the 64-digit key the user wrote down when
 * they enabled encrypted backups. Native port of the key derivation and
 * file format documented in ElDavoo/wa-crypt-tools (GPL-3.0).
 */
object WaCrypt15 {

    class CryptException(message: String) : Exception(message)

    /** Accepts 64 hex digits with optional spaces or dashes. */
    fun cleanKey(input: String): ByteArray? {
        val hex = input.trim().replace(" ", "").replace("-", "")
        if (hex.length != 64) return null
        val out = ByteArray(32)
        for (i in 0 until 32) {
            val v = hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
            out[i] = v.toByte()
        }
        return out
    }

    private fun readVarint(data: ByteArray, pos: IntArray): Long {
        var result = 0L
        var shift = 0
        while (pos[0] < data.size && shift < 64) {
            val b = data[pos[0]].toInt() and 0xFF
            pos[0] = pos[0] + 1
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift = shift + 7
        }
        return result
    }

    /** Reads the varint length + BackupPrefix protobuf and returns the IV and the offset of the ciphertext. */
    fun parseHeader(data: ByteArray): Pair<ByteArray, Int> {
        val pos = intArrayOf(0)
        val prefixLen = readVarint(data, pos).toInt()
        if (prefixLen <= 0 || pos[0] + prefixLen > data.size) {
            throw CryptException("Not a valid crypt15 backup header.")
        }
        val end = pos[0] + prefixLen
        var iv: ByteArray? = null
        while (pos[0] < end) {
            val key = readVarint(data, pos).toInt()
            val field = key ushr 3
            val wire = key and 7
            if (wire == 0) {
                readVarint(data, pos)
            } else if (wire == 1) {
                pos[0] = pos[0] + 8
            } else if (wire == 5) {
                pos[0] = pos[0] + 4
            } else if (wire == 2) {
                val len = readVarint(data, pos).toInt()
                val start = pos[0]
                if (field == 3 && len in 1..(end - start)) {
                    val sub = intArrayOf(start)
                    val subEnd = start + len
                    while (sub[0] < subEnd) {
                        val k2 = readVarint(data, sub).toInt()
                        val f2 = k2 ushr 3
                        val w2 = k2 and 7
                        if (w2 == 2) {
                            val l2 = readVarint(data, sub).toInt()
                            if (f2 == 1 && l2 == 16 && sub[0] + 16 <= subEnd) {
                                iv = data.copyOfRange(sub[0], sub[0] + 16)
                            }
                            sub[0] = sub[0] + l2
                        } else if (w2 == 0) {
                            readVarint(data, sub)
                        } else if (w2 == 1) {
                            sub[0] = sub[0] + 8
                        } else if (w2 == 5) {
                            sub[0] = sub[0] + 4
                        }
                    }
                }
                pos[0] = start + len
            }
        }
        if (iv == null) throw CryptException("Could not find the encryption header inside the backup.")
        return Pair(iv, end)
    }

    private fun hmac(key: ByteArray, msg: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(msg)
    }

    /** cipherKey = HMAC(HMAC(32 zero bytes, rootKey), "backup encryption" + 0x01) - single iteration. */
    fun deriveKey(rootKey: ByteArray): ByteArray {
        val priv = hmac(ByteArray(32), rootKey)
        val info = "backup encryption".toByteArray(Charsets.UTF_8) + byteArrayOf(1)
        return hmac(priv, info)
    }

    /** True when the trailing 16 bytes are the MD5 of ciphertext+tag (single-file backup). */
    private fun hasMd5Checksum(src: File, dataStart: Int): Boolean {
        val size = src.length()
        if (size < dataStart + 48) return false
        val md = MessageDigest.getInstance("MD5")
        FileInputStream(src).use { fin ->
            fin.skip(dataStart.toLong())
            val buf = ByteArray(65536)
            var remaining = size - 16 - dataStart
            while (remaining > 0) {
                val want = if (remaining > 65536) 65536 else remaining.toInt()
                val r = fin.read(buf, 0, want)
                if (r <= 0) break
                md.update(buf, 0, r)
                remaining -= r
            }
        }
        val tail = ByteArray(16)
        RandomAccessFile(src, "r").use { rf ->
            rf.seek(size - 16)
            rf.readFully(tail)
        }
        return md.digest().contentEquals(tail)
    }

    private class LimitedInputStream(val ins: InputStream, val limit: Long) : InputStream() {
        private var left = limit
        override fun read(): Int {
            if (left <= 0) return -1
            val r = ins.read()
            if (r >= 0) left = left - 1
            return r
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val want = if (len > left) left.toInt() else len
            val r = ins.read(b, off, want)
            if (r > 0) left = left - r
            return r
        }
        override fun close() {
            ins.close()
        }
    }

    /**
     * Decrypts src (msgstore.db or msgstore.db.crypt15) into outDir and
     * returns the decrypted SQLite database file. Handles single-file and
     * multi-file backups, zlib streams and ZIP containers.
     */
    fun decrypt(src: File, keyHex: String, outDir: File): File {
        val key = cleanKey(keyHex)
        if (key == null) {
            throw CryptException("The key must be exactly 64 hex digits. Copy it from WhatsApp > Settings > Chats > Chat backup > End-to-end encrypted backup.")
        }
        if (!src.exists() || src.length() < 128) {
            throw CryptException("The backup file is missing or too small: " + src.name)
        }
        outDir.mkdirs()
        val need = src.length() * 2
        if (outDir.usableSpace in 1..need) {
            throw CryptException("Not enough free space to decrypt the backup (need about " + Cleaner.humanSize(need) + "). Free some space and try again.")
        }
        val head = ByteArray(4096)
        val n = FileInputStream(src).use { it.read(head) }
        if (n < 64) throw CryptException("Not a valid crypt15 backup header.")
        val header = parseHeader(head)
        val iv = header.first
        val dataStart = header.second
        val size = src.length()
        val single = hasMd5Checksum(src, dataStart)
        val cipherLen = if (single) size - 16 - dataStart else size - dataStart
        if (cipherLen <= 16) throw CryptException("The backup file is truncated or damaged.")
        val cipherKey = deriveKey(key)
        val raw = File(outDir, "_decrypted.raw")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(cipherKey, "AES"), GCMParameterSpec(128, iv))
        val fin = FileInputStream(src)
        var cis: CipherInputStream? = null
        var fout: FileOutputStream? = null
        try {
            fin.skip(dataStart.toLong())
            cis = CipherInputStream(LimitedInputStream(fin, cipherLen), cipher)
            fout = FileOutputStream(raw)
            val buf = ByteArray(65536)
            while (true) {
                val r = cis.read(buf)
                if (r < 0) break
                fout.write(buf, 0, r)
            }
            fout.flush()
        } catch (e: Exception) {
            throw CryptException("Decryption failed - the key is wrong or the backup is damaged. (" + e.javaClass.simpleName + ")")
        } finally {
            try { cis?.close() } catch (e: Exception) { }
            try { fout?.close() } catch (e: Exception) { }
            fin.close()
        }
        try {
            val peek = ByteArray(16)
            FileInputStream(raw).use { it.read(peek) }
            if (peek[0] == 'P'.toByte() && peek[1] == 'K'.toByte()) {
                val db = extractZip(raw, outDir)
                return db
            }
            if (String(peek, 0, 15, Charsets.US_ASCII) == "SQLite format 3" && peek[15] == 0.toByte()) {
                val db = File(outDir, "msgstore.db")
                if (!raw.renameTo(db)) throw CryptException("Could not write the decrypted database.")
                return db
            }
            val db = File(outDir, "msgstore.db")
            val zin = InflaterInputStream(FileInputStream(raw))
            val fo = FileOutputStream(db)
            try {
                val buf = ByteArray(65536)
                while (true) {
                    val r = zin.read(buf)
                    if (r < 0) break
                    fo.write(buf, 0, r)
                }
            } catch (e: Exception) {
                throw CryptException("The decrypted backup is not a valid database (inflate failed).")
            } finally {
                zin.close()
                fo.close()
            }
            val p2 = ByteArray(16)
            FileInputStream(db).use { it.read(p2) }
            if (p2[0] == 'P'.toByte() && p2[1] == 'K'.toByte()) {
                val zr = extractZip(db, outDir)
                return zr
            }
            val p3 = ByteArray(16)
            FileInputStream(db).use { it.read(p3) }
            if (String(p3, 0, 15, Charsets.US_ASCII) != "SQLite format 3") {
                throw CryptException("The decrypted backup is not a msgstore database. Is this really a chat backup file?")
            }
            return db
        } finally {
            raw.delete()
        }
    }

    /** Extracts a decrypted multi-file backup ZIP and returns the msgstore database inside it. */
    fun extractZip(zip: File, outDir: File): File {
        var msgstore: File? = null
        ZipFile(zip).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val e: ZipEntry = entries.nextElement()
                if (e.isDirectory) continue
                val base = File(e.name).name
                if (base.isEmpty() || base.startsWith(".")) continue
                val target = File(outDir, base)
                zf.getInputStream(e).use { ins ->
                    FileOutputStream(target).use { outs -> ins.copyTo(outs) }
                }
                if (base.startsWith("msgstore") && base.endsWith(".db")) {
                    msgstore = target
                }
            }
        }
        return msgstore ?: throw CryptException("The backup container did not include a msgstore database.")
    }
}