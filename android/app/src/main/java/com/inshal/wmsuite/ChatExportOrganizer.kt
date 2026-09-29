package com.inshal.wmsuite

import java.io.File
import java.util.zip.ZipFile

/**
 * Organizes media from exported WhatsApp chats ("Export chat" with media)
 * into per-contact folders. Copies only - the exported ZIP and the chat
 * itself are never modified.
 */
object ChatExportOrganizer {

    data class Export(val zip: File, val contact: String)

    private val PREFIXES = listOf(
        "WhatsApp Business Chat with ",
        "WhatsApp Business Chat - ",
        "WhatsApp Chat with ",
        "WhatsApp Chat - "
    )

    fun isChatExport(name: String): Boolean {
        for (p in PREFIXES) if (name.startsWith(p)) return true
        return false
    }

    fun contactName(zip: File): String {
        var n = zip.name
        if (n.endsWith(".zip")) n = n.substring(0, n.length - 4)
        for (p in PREFIXES) {
            if (n.startsWith(p)) {
                n = n.substring(p.length)
                break
            }
        }
        val trimmed = n.trim()
        return if (trimmed.isEmpty()) "Unknown contact" else trimmed
    }

    fun findExports(folder: File): List<Export> {
        if (!folder.isDirectory) return emptyList()
        val zips = folder.listFiles() ?: arrayOf<File>()
        val out = mutableListOf<Export>()
        for (z in zips) {
            if (!z.isFile || !z.name.endsWith(".zip")) continue
            if (!isChatExport(z.name)) continue
            out.add(Export(z, contactName(z)))
        }
        return out.sortedBy { it.contact.lowercase() }
    }

    /**
     * Copies every media / attachment entry of the export into
     * outRoot/Conversations/<Contact>/. Zip-slip safe (base names only).
     */
    fun importExport(zip: File, outRoot: File, dry: Boolean): Cleaner.Result {
        val dest = File(File(outRoot, "Conversations"), contactName(zip))
        var count = 0
        var bytes = 0L
        try {
            ZipFile(zip).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.isDirectory) continue
                    val b = e.name.substringAfterLast('/')
                    if (b.isEmpty() || b.startsWith(".") || b == "_chat.txt") continue
                    bytes += e.size
                    count++
                    if (!dry) {
                        dest.mkdirs()
                        var target = File(dest, b)
                        var n = 1
                        while (target.exists()) {
                            val dot = b.lastIndexOf('.')
                            val stem = if (dot > 0) b.substring(0, dot) else b
                            val ext = if (dot > 0) b.substring(dot) else ""
                            target = File(dest, stem + " (" + n + ")" + ext)
                            n++
                        }
                        zf.getInputStream(e).use { ins ->
                            target.outputStream().use { outs -> ins.copyTo(outs) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return Cleaner.Result(0, 0)
        }
        return Cleaner.Result(count, bytes)
    }
}
