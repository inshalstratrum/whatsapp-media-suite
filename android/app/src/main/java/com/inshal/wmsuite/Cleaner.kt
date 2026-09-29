package com.inshal.wmsuite

import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native port of the wmsuite scanner/cleaner: category scan with a
 * sent/received split, scoped cleaning, keep-earliest duplicate removal,
 * date organizing and status saving.
 */
object Cleaner {

    data class CategoryStat(
        val name: String,
        val folder: File,
        val files: Int,
        val bytes: Long,
        val sentFiles: Int,
        val sentBytes: Long
    )

    data class Result(val count: Int, val bytes: Long)

    /** Scope values for cleanFolder(). */
    const val SCOPE_ALL = 0
    const val SCOPE_RECEIVED = 1
    const val SCOPE_SENT = 2

    private val FOLDER_TO_CATEGORY = linkedMapOf(
        "WhatsApp Images" to "Images",
        "WhatsApp Video" to "Video",
        "WhatsApp Video Notes" to "Video Notes",
        "WhatsApp Audio" to "Audio",
        "WhatsApp Voice Notes" to "Voice Notes",
        "WhatsApp Documents" to "Documents",
        "WhatsApp Stickers" to "Stickers",
        "WhatsApp Animated GIFs" to "Animated Gifs",
        "WhatsApp Wallpapers" to "Wallpapers",
        ".Statuses" to "Statuses"
    )

    // Never offered as cleanable/organizable content on the phone.
    private val PROTECTED_FOLDERS = setOf("Databases", "Backups")

    private val LEGACY_RE = Regex("^(IMG|VID|DOC|AUD|PTT|STK)-(\\d{4})(\\d{2})(\\d{2})-WA\\d+\\.[A-Za-z0-9]+$")
    private val MODERN_RE = Regex("^WhatsApp[ _-](Image|Audio|Video|Ptt|Document|Sticker)[ _-](\\d{4})-(\\d{2})-(\\d{2})[ _-]at[ _-](\\d{2})\\.(\\d{2})\\.(\\d{2})(?: \\(\\d+\\))?\\.[A-Za-z0-9]+$")
    private val BACKUP_RE = Regex("^msgstore.*\\.db\\.crypt\\d+$", RegexOption.IGNORE_CASE)

    fun humanSize(n: Long): String {
        var v = n.toDouble()
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        for (u in units) {
            if (v < 1024 || u == "TB") {
                return if (u == "B") String.format(Locale.US, "%d B", n)
                else String.format(Locale.US, "%.1f %s", v, u)
            }
            v /= 1024
        }
        return n.toString() + " B"
    }

    /** True when the file lives inside a "Sent" folder of any level. */
    fun isSent(file: File): Boolean {
        var d = file.parentFile
        while (d != null) {
            if (d.name.equals("Sent", ignoreCase = true)) return true
            d = d.parentFile
        }
        return false
    }

    /** Category (name + owning folder) for a file inside the WhatsApp root, or null. */
    fun categoryFor(file: File, root: File): Pair<String, File>? {
        var d: File? = file.parentFile
        while (d != null) {
            val name = d.name
            if (FOLDER_TO_CATEGORY.containsKey(name)) return Pair(FOLDER_TO_CATEGORY[name]!!, d)
            if (PROTECTED_FOLDERS.contains(name)) return null
            if (name.startsWith("WhatsApp ")) return Pair(name.substring(9), d)
            if (d == root) break
            d = d.parentFile
        }
        val m = LEGACY_RE.find(file.name) ?: MODERN_RE.find(file.name)
        if (m != null && file.parentFile != null) {
            val type = m.groupValues[1].uppercase(Locale.US)
            val cat = when (type) {
                "IMG", "IMAGE" -> "Images"
                "VID", "VIDEO" -> "Video"
                "AUD", "AUDIO" -> "Audio"
                "PTT" -> "Voice Notes"
                "DOC", "DOCUMENT" -> "Documents"
                "STK", "STICKER" -> "Stickers"
                else -> null
            }
            if (cat != null) return Pair(cat, file.parentFile!!)
        }
        return null
    }

    private class Tally(var files: Int, var bytes: Long, var sentFiles: Int, var sentBytes: Long)

    fun scan(root: File): List<CategoryStat> {
        val tally = LinkedHashMap<String, Tally>()
        val folderBy = HashMap<String, File>()
        val files = root.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.toList()
        for (f in files) {
            val c = categoryFor(f, root) ?: continue
            val t = tally.getOrPut(c.first) { Tally(0, 0, 0, 0) }
            t.files++
            t.bytes += f.length()
            if (isSent(f)) {
                t.sentFiles++
                t.sentBytes += f.length()
            }
            if (!folderBy.containsKey(c.first)) folderBy[c.first] = c.second
        }
        val out = mutableListOf<CategoryStat>()
        for ((name, t) in tally) {
            out.add(CategoryStat(name, folderBy[name]!!, t.files, t.bytes, t.sentFiles, t.sentBytes))
        }
        return out
    }

    /** Deletes files in the category folder. Scope: all / received only / sent only. */
    fun cleanFolder(folder: File, dry: Boolean, scope: Int): Result {
        val files = folder.walkTopDown().filter { f ->
            f.isFile && !f.name.startsWith(".") &&
                (scope == SCOPE_ALL ||
                    (scope == SCOPE_RECEIVED && !isSent(f)) ||
                    (scope == SCOPE_SENT && isSent(f)))
        }.toList()
        var bytes = 0L
        for (f in files) bytes += f.length()
        if (!dry) {
            for (f in files) f.delete()
            removeEmptyDirs(folder)
        }
        return Result(files.size, bytes)
    }

    fun deleteOlderThan(root: File, days: Int, dry: Boolean): Result {
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        val victims = root.walkTopDown().filter { f ->
            f.isFile && !f.name.startsWith(".") &&
                f.lastModified() < cutoff &&
                categoryFor(f, root) != null
        }.toList()
        var bytes = 0L
        for (f in victims) bytes += f.length()
        if (!dry) for (f in victims) f.delete()
        return Result(victims.size, bytes)
    }

    fun pruneBackups(dir: File, keep: Int, dry: Boolean): Result {
        if (!dir.isDirectory) return Result(0, 0)
        val cands = dir.listFiles()?.filter { it.isFile && BACKUP_RE.matches(it.name) } ?: emptyList()
        val sorted = cands.sortedByDescending { it.lastModified() }
        var count = 0
        var bytes = 0L
        for (i in keep until sorted.size) {
            bytes += sorted[i].length()
            count++
            if (!dry) sorted[i].delete()
        }
        return Result(count, bytes)
    }

    fun removeEmptyDirs(root: File): List<File> {
        val removed = mutableListOf<File>()
        root.walkBottomUp().forEach { d ->
            if (!d.isDirectory || d == root) return@forEach
            val entries = d.list() ?: return@forEach
            val meaningful = entries.any { !it.startsWith(".") }
            if (!meaningful) {
                for (e in entries) File(d, e).delete()
                if (d.delete()) removed.add(d)
            }
        }
        return removed
    }

    fun listEmptyDirs(root: File): List<File> {
        val result = mutableListOf<File>()
        fun isEmptyDir(d: File): Boolean {
            val entries = d.list() ?: return false
            for (e in entries) {
                val child = File(d, e)
                if (child.isDirectory) {
                    if (!isEmptyDir(child)) return false
                } else if (!e.startsWith(".")) {
                    return false
                }
            }
            return true
        }
        root.walkTopDown().filter { it.isDirectory && it != root && isEmptyDir(it) }.forEach { result.add(it) }
        return result
    }

    fun saveStatuses(src: File, target: File, dry: Boolean): Result {
        if (!src.isDirectory) return Result(0, 0)
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val files = src.listFiles()?.filter { it.isFile && !it.name.startsWith(".") } ?: emptyList()
        var count = 0
        var bytes = 0L
        for (f in files) {
            val dayDir = File(target, dayFmt.format(Date(f.lastModified())))
            var dest = File(dayDir, f.name)
            var n = 1
            while (dest.exists()) {
                dest = File(dayDir, f.nameWithoutExtension + " (" + n + ")." + f.extension)
                n++
            }
            bytes += f.length()
            count++
            if (!dry) {
                dayDir.mkdirs()
                f.copyTo(dest, overwrite = false)
            }
        }
        return Result(count, bytes)
    }

    /** Groups of files with identical content (same size + same SHA-256). */
    fun findDuplicateGroups(root: File): List<List<File>> {
        val files = root.walkTopDown().filter {
            it.isFile && !it.name.startsWith(".") && categoryFor(it, root) != null
        }.toList()
        val bySize = HashMap<Long, MutableList<File>>()
        for (f in files) bySize.getOrPut(f.length()) { mutableListOf() }.add(f)
        val md = MessageDigest.getInstance("SHA-256")
        val groups = mutableListOf<List<File>>()
        for (sizeGroup in bySize.values) {
            if (sizeGroup.size < 2) continue
            val byHash = HashMap<String, MutableList<File>>()
            for (f in sizeGroup) byHash.getOrPut(sha256(md, f)) { mutableListOf() }.add(f)
            for (dups in byHash.values) {
                if (dups.size >= 2) groups.add(dups.toList())
            }
        }
        return groups
    }

    /**
     * Removes the extra copies of every duplicate group, ALWAYS keeping
     * the earliest file (filename timestamp, else file modification date)
     * as the original.
     */
    fun deleteDuplicateGroups(groups: List<List<File>>, dry: Boolean): Result {
        var count = 0
        var bytes = 0L
        for (g in groups) {
            if (g.size < 2) continue
            val sorted = g.sortedBy { parseTimestamp(it.name) ?: it.lastModified() }
            for (i in 1 until sorted.size) {
                val f = sorted[i]
                bytes += f.length()
                count++
                if (!dry) f.delete()
            }
        }
        return Result(count, bytes)
    }

    /** Timestamp encoded in a WhatsApp media filename, or null. */
    fun parseTimestamp(name: String): Long? {
        val m = LEGACY_RE.find(name)
        if (m != null) {
            try {
                val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
                fmt.isLenient = false
                return fmt.parse(m.groupValues[2] + m.groupValues[3] + m.groupValues[4])?.time
            } catch (e: Exception) {
                // fall through
            }
        }
        val mm = MODERN_RE.find(name)
        if (mm != null) {
            try {
                val fmt = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US)
                fmt.isLenient = false
                return fmt.parse(
                    mm.groupValues[2] + "-" + mm.groupValues[3] + "-" + mm.groupValues[4] +
                        " " + mm.groupValues[5] + "." + mm.groupValues[6] + "." + mm.groupValues[7]
                )?.time
            } catch (e: Exception) {
                // fall through
            }
        }
        return null
    }

    private fun timestampFor(f: File): Date = Date(parseTimestamp(f.name) ?: f.lastModified())

    fun organizeByDate(root: File, out: File, dry: Boolean): Result {
        val monthFmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val files = root.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.toList()
        var count = 0
        var bytes = 0L
        for (f in files) {
            val c = categoryFor(f, root) ?: continue
            if (c.second == root) continue
            val dest = File(File(out, monthFmt.format(timestampFor(f))), c.first)
            val target = File(dest, f.name)
            if (target.exists()) continue
            bytes += f.length()
            count++
            if (!dry) {
                dest.mkdirs()
                if (!f.renameTo(target)) f.copyTo(target, overwrite = false)
            }
        }
        return Result(count, bytes)
    }

    private fun sha256(md: MessageDigest, f: File): String {
        md.reset()
        f.inputStream().use { ins ->
            val buf = ByteArray(65536)
            while (true) {
                val r = ins.read(buf)
                if (r < 0) break
                md.update(buf, 0, r)
            }
        }
        val sb = StringBuilder()
        for (b in md.digest()) {
            val v = b.toInt() and 0xff
            if (v < 16) sb.append('0')
            sb.append(v.toString(16))
        }
        return sb.toString()
    }
}
