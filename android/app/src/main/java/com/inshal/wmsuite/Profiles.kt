package com.inshal.wmsuite

import java.io.File

/**
 * Detects installed WhatsApp / WhatsApp Business storage profiles and
 * identifies the active one, so the app never scans the wrong (idle)
 * folder - the fix for "Scan finished: 0 categories".
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

    fun detect(ext: File): List<WaProfile> {
        val candidates = listOf(
            "WhatsApp" to File(File(ext, "Android/media/com.whatsapp"), "WhatsApp"),
            "WhatsApp Business" to File(File(ext, "Android/media/com.whatsapp.w4b"), "WhatsApp"),
            "WhatsApp (legacy)" to File(ext, "WhatsApp"),
            "WhatsApp Business (legacy)" to File(ext, "WhatsApp Business")
        )
        val out = mutableListOf<WaProfile>()
        for ((name, root) in candidates) {
            if (!root.isDirectory) continue
            var files = 0
            var bytes = 0L
            var last = 0L
            root.walkTopDown().forEach {
                if (it.isFile && !it.name.startsWith(".")) {
                    files++
                    bytes += it.length()
                    val m = it.lastModified()
                    if (m > last) last = m
                }
            }
            if (files == 0) continue
            out.add(WaProfile(name, root, files, bytes, last))
        }
        return out.sortedByDescending { it.lastActivity }
    }
}
