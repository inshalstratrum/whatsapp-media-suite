package com.inshal.wmsuite

import java.io.File

/**
 * Detects WhatsApp / WhatsApp Business storage profiles: the modern
 * Android/media package folders (any WhatsApp package - including the
 * "WhatsApp Business" subfolder of com.whatsapp.w4b), the legacy top-level
 * folders, a deep full-storage search for renamed or unusual locations,
 * and a manually chosen folder - so the app finds the media on any phone.
 */
object Profiles {

    data class WaProfile(
        val name: String,
        val root: File,
        val files: Int,
        val bytes: Long,
        val lastActivity: Long
    ) {
        fun isActive(): Boolean = System.currentTimeMillis() - lastActivity < 30L * 86_400_000L

        fun ageLabel(): String {
            val days = (System.currentTimeMillis() - lastActivity) / 86_400_000L
            return when {
                days <= 0L -> "active today"
                days == 1L -> "active yesterday"
                else -> "last activity " + days + " days ago"
            }
        }
    }

    /** A real WhatsApp data root always contains one of these subfolders. */
    private fun looksLikeWaRoot(d: File): Boolean {
        if (!d.isDirectory) return false
        val children = try { d.listFiles() } catch (e: Exception) { null } ?: return false
        for (c in children) {
            val n = c.name
            if (n.equals("Media", ignoreCase = true) ||
                n.equals(".Statuses", ignoreCase = true) ||
                n.equals("Databases", ignoreCase = true) ||
                n.equals("Backups", ignoreCase = true)
            ) return true
        }
        return false
    }

    private fun canonical(d: File): String {
        return try { d.canonicalPath } catch (e: Exception) { d.absolutePath }
    }

    private fun measure(name: String, root: File): WaProfile? {
        if (!root.isDirectory) return null
        var files = 0
        var bytes = 0L
        var last = 0L
        try {
            root.walkTopDown().forEach {
                if (it.isFile && !it.name.startsWith(".")) {
                    files++
                    bytes += it.length()
                    val m = it.lastModified()
                    if (m > last) last = m
                }
            }
        } catch (e: Exception) {
            // unreadable parts are skipped
        }
        if (files == 0) return null
        return WaProfile(name, root, files, bytes, last)
    }

    private fun addIfNew(out: MutableList<WaProfile>, seen: HashSet<String>, name: String, root: File): WaProfile? {
        val key = canonical(root)
        if (seen.contains(key)) return null
        seen.add(key)
        val p = measure(name, root)
        if (p != null) out.add(p)
        return p
    }

    /**
     * Standard detection: every WhatsApp-related package folder under
     * Android/media (com.whatsapp, com.whatsapp.w4b, ...), checking each
     * app folder inside it, plus the legacy top-level folders.
     */
    fun detect(ext: File): List<WaProfile> {
        val out = mutableListOf<WaProfile>()
        val seen = HashSet<String>()
        val mediaDir = File(ext, "Android/media")
        val pkgs = try { mediaDir.listFiles() } catch (e: Exception) { null } ?: arrayOf<File>()
        for (pkg in pkgs) {
            if (!pkg.isDirectory) continue
            val low = pkg.name.lowercase()
            if (!low.contains("whatsapp") && !low.contains("w4b")) continue
            val children = try { pkg.listFiles() } catch (e: Exception) { null } ?: continue
            for (child in children) {
                if (!child.isDirectory) continue
                if (!looksLikeWaRoot(child) && !child.name.lowercase().contains("whatsapp")) continue
                addIfNew(out, seen, child.name + " (" + pkg.name + ")", child)
            }
        }
        addIfNew(out, seen, "WhatsApp (legacy)", File(ext, "WhatsApp"))
        addIfNew(out, seen, "WhatsApp Business (legacy)", File(ext, "WhatsApp Business"))
        return out.sortedByDescending { it.lastActivity }
    }

    /**
     * Deep search: walks the whole shared storage (bounded) and reports
     * every folder that looks like a WhatsApp data root, wherever it is -
     * renamed folders, SD cards, manual copies, anything.
     */
    fun deepSearch(ext: File, log: (String) -> Unit): List<WaProfile> {
        val out = mutableListOf<WaProfile>()
        val seen = HashSet<String>()
        val fast = mutableListOf<File>()
        val mediaDir = File(ext, "Android/media")
        val pkgs = try { mediaDir.listFiles() } catch (e: Exception) { null } ?: arrayOf<File>()
        for (pkg in pkgs) {
            if (!pkg.isDirectory) continue
            val children = try { pkg.listFiles() } catch (e: Exception) { null } ?: arrayOf<File>()
            for (child in children) {
                if (child.isDirectory && looksLikeWaRoot(child)) fast.add(child)
            }
        }
        fast.add(File(ext, "WhatsApp"))
        fast.add(File(ext, "WhatsApp Business"))
        for (f in fast) {
            val p = addIfNew(out, seen, f.name, f)
            if (p != null) log("Found: " + f.name + "  [" + f.absolutePath + "]")
        }
        log("Standard locations checked - now searching the rest of your storage...")
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.addLast(Pair(ext, 0))
        var visited = 0
        while (queue.isNotEmpty() && visited < 30000) {
            val (dir, depth) = queue.removeFirst()
            if (depth >= 7) continue
            val children = try { dir.listFiles() } catch (e: Exception) { null } ?: continue
            for (c in children) {
                if (!c.isDirectory || c.name.startsWith(".")) continue
                val path = c.absolutePath
                if (path.endsWith("/Android/data") || path.endsWith("/Android/obb")) continue
                visited++
                if (looksLikeWaRoot(c)) {
                    val p = addIfNew(out, seen, c.name, c)
                    if (p != null) log("Found: " + c.name + "  [" + c.absolutePath + "]")
                } else {
                    queue.addLast(Pair(c, depth + 1))
                }
            }
        }
        return out.sortedByDescending { it.lastActivity }
    }

    /** Prepends the manually chosen folder (if set and still valid) to the list. */
    fun withCustom(list: List<WaProfile>, path: String?): List<WaProfile> {
        if (path == null) return list
        val dir = File(path)
        if (!dir.isDirectory) return list
        val key = canonical(dir)
        for (p in list) {
            if (canonical(p.root) == key) {
                if (p == list.first()) return list
                return listOf(p) + list.filter { it != p }
            }
        }
        val prof = measure("Chosen folder", dir) ?: return list
        return listOf(prof) + list
    }

    fun profileFrom(name: String, dir: File): WaProfile? = measure(name, dir)
}
