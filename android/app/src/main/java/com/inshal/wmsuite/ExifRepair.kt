package com.inshal.wmsuite

import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Restores the capture date WhatsApp strips from photos: reads the
 * timestamp encoded in the filename and writes it back into the EXIF
 * DateTimeOriginal / DateTime tags (and the file date), so the phone
 * gallery shows photos on the right days.
 */
object ExifRepair {

    data class Item(val file: File, val ts: Long)

    private fun fmt(): SimpleDateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)

    /** Photos whose EXIF capture date is missing or differs from the filename timestamp. */
    fun scan(root: File): List<Item> {
        val out = mutableListOf<Item>()
        val files = root.walkTopDown().filter {
            it.isFile && (it.extension.equals("jpg", true) || it.extension.equals("jpeg", true))
        }.toList()
        val f = fmt()
        for (file in files) {
            val ts = Cleaner.parseTimestamp(file.name) ?: continue
            val expected = f.format(Date(ts))
            val existing = try {
                ExifInterface(file).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            } catch (e: Exception) {
                continue
            }
            if (existing != expected) out.add(Item(file, ts))
        }
        return out
    }

    /** Writes the repaired dates. Returns the number of photos updated. */
    fun apply(items: List<Item>, dry: Boolean): Int {
        val f = fmt()
        var count = 0
        for (item in items) {
            if (dry) {
                count++
                continue
            }
            try {
                val value = f.format(Date(item.ts))
                val exif = ExifInterface(item.file)
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, value)
                exif.setAttribute(ExifInterface.TAG_DATETIME, value)
                exif.saveAttributes()
                item.file.setLastModified(item.ts)
                count++
            } catch (e: Exception) {
                // skip unreadable file
            }
        }
        return count
    }
}
