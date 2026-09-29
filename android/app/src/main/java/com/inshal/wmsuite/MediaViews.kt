package com.inshal.wmsuite

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.text.TextUtils
import android.view.Gravity
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Visual media browsing: thumbnail galleries, single file previews and
 * the side-by-side duplicate comparison before deleting anything.
 */
object MediaViews {

    private val IMAGES = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    private val VIDEOS = setOf("mp4", "3gp", "mkv", "webm", "avi", "mov", "m4v", "ts")
    private val AUDIOS = setOf("mp3", "ogg", "opus", "aac", "m4a", "amr", "wav", "w4a")

    fun isImage(f: File): Boolean = IMAGES.contains(f.extension.lowercase(Locale.US))
    fun isVideo(f: File): Boolean = VIDEOS.contains(f.extension.lowercase(Locale.US))
    fun isAudio(f: File): Boolean = AUDIOS.contains(f.extension.lowercase(Locale.US))

    fun dp(context: Context, v: Int): Int {
        val d = context.resources.displayMetrics.density
        return (v * d + 0.5f).toInt()
    }

    fun mimeType(f: File): String {
        val ext = f.extension.lowercase(Locale.US)
        val known = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return known ?: "application/octet-stream"
    }

    fun dateLabel(f: File): String {
        val ts = Cleaner.parseTimestamp(f.name)
        return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(ts ?: f.lastModified()))
    }

    /** Decoded thumbnail (images via sampling, videos via first frame) or null. */
    fun loadThumb(f: File, size: Int): Bitmap? {
        return try {
            if (isImage(f)) {
                val opts = BitmapFactory.Options()
                opts.inJustDecodeBounds = true
                BitmapFactory.decodeFile(f.absolutePath, opts)
                var s = 1
                while (opts.outWidth / (s * 2) >= size && opts.outHeight / (s * 2) >= size) s = s * 2
                val o2 = BitmapFactory.Options()
                o2.inSampleSize = s
                BitmapFactory.decodeFile(f.absolutePath, o2)
            } else if (isVideo(f)) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(f.absolutePath)
                    r.getFrameAtTime()
                } finally {
                    r.release()
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun openWith(activity: Activity, f: File) {
        try {
            val uri = FileProvider.getUriForFile(activity, "com.inshal.wmsuite.fileprovider", f)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, mimeType(f))
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "No app found that can open " + f.name, Toast.LENGTH_SHORT).show()
        }
    }

    fun iconTile(activity: Activity, f: File, w: Int): TextView {
        val t = TextView(activity)
        t.layoutParams = FrameLayout.LayoutParams(w, w)
        t.gravity = Gravity.CENTER
        t.setTextColor(Color.WHITE)
        t.typeface = Typeface.DEFAULT_BOLD
        val label: String
        val color: Int
        if (isImage(f)) {
            label = "IMG"
            color = 0xFF2E7D32.toInt()
        } else if (isVideo(f)) {
            label = "VID"
            color = 0xFF6A1B9A.toInt()
        } else if (isAudio(f)) {
            label = "AUD"
            color = 0xFF37474F.toInt()
        } else {
            label = "DOC"
            color = 0xFF546E7A.toInt()
        }
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(activity, 8).toFloat()
        t.background = g
        t.text = label
        return t
    }

    /** Full preview of one file: image/video frame, size, date, and open-with. */
    fun previewOne(activity: Activity, f: File) {
        Thread {
            val bm = loadThumb(f, 1080)
            activity.runOnUiThread {
                try {
                    val box = LinearLayout(activity)
                    box.orientation = LinearLayout.VERTICAL
                    if (bm != null) {
                        val iv = ImageView(activity)
                        iv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        iv.setImageBitmap(bm)
                        iv.adjustViewBounds = true
                        box.addView(iv)
                    }
                    val info = TextView(activity)
                    info.text = f.name + "\n" + Cleaner.humanSize(f.length()) + " - " + dateLabel(f)
                    info.textSize = 13f
                    info.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12))
                    info.setTextColor(0xFFE8ECF1.toInt())
                    box.addView(info)
                    AlertDialog.Builder(activity)
                        .setTitle("Preview")
                        .setView(box)
                        .setPositiveButton("Open with") { d, w -> openWith(activity, f) }
                        .setNegativeButton("Close", null)
                        .show()
                } catch (e: Exception) {
                }
            }
        }.start()
    }

    /** Grid gallery dialog with background-loaded thumbnails. */
    fun gallery(activity: Activity, title: String, files: List<File>, note: String?) {
        if (files.isEmpty()) {
            Toast.makeText(activity, "Nothing to show", Toast.LENGTH_SHORT).show()
            return
        }
        val list = if (files.size > 300) files.subList(0, 300) else files
        val grid = GridLayout(activity)
        grid.columnCount = 3
        val w = max(96, activity.resources.displayMetrics.widthPixels / 3 - dp(activity, 16))
        val tiles = ArrayList<Pair<ImageView, File>>()
        for (i in list.indices) {
            val f = list[i]
            val cell = FrameLayout(activity)
            val glp = GridLayout.LayoutParams()
            glp.width = w
            glp.height = w + dp(activity, 30)
            glp.setMargins(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2))
            cell.layoutParams = glp
            cell.addView(iconTile(activity, f, w))
            val iv = ImageView(activity)
            iv.layoutParams = FrameLayout.LayoutParams(w, w)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            cell.addView(iv)
            tiles.add(Pair(iv, f))
            val name = TextView(activity)
            name.text = f.name
            name.textSize = 9f
            name.maxLines = 2
            name.ellipsize = TextUtils.TruncateAt.END
            name.setTextColor(0xFF97A5B4.toInt())
            name.layoutParams = FrameLayout.LayoutParams(w, dp(activity, 30), Gravity.BOTTOM)
            cell.addView(name)
            cell.setOnClickListener { previewOne(activity, f) }
            grid.addView(cell)
        }
        val scroll = ScrollView(activity)
        scroll.addView(grid)
        val shown = if (files.size > 300) "Showing first 300 of " + files.size + " files" else files.size.toString() + " files"
        val msg = if (note == null) shown else shown + " - " + note
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(msg)
            .setView(scroll)
            .setPositiveButton("Close", null)
            .create()
        dialog.show()
        Thread {
            for (i in tiles.indices) {
                if (!dialog.isShowing) return@Thread
                val bm = loadThumb(tiles[i].second, 200)
                if (bm != null) {
                    activity.runOnUiThread {
                        try {
                            if (dialog.isShowing) tiles[i].first.setImageBitmap(bm)
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }.start()
    }

    /**
     * Visual duplicate review: every group is shown side by side with
     * thumbnails; the earliest copy is marked as the kept original, the
     * others get a pre-checked Delete box with a live size counter.
     */
    fun duplicateCompare(activity: Activity, groups: List<List<File>>, dry: Boolean, onDone: (String) -> Unit) {
        val shown = if (groups.size > 40) groups.subList(0, 40) else groups
        val victims = ArrayList<File>()
        val thumbJobs = ArrayList<Pair<ImageView, File>>()
        val footer = TextView(activity)
        footer.textSize = 13f
        footer.typeface = Typeface.DEFAULT_BOLD
        footer.setTextColor(0xFF25D366.toInt())
        footer.setPadding(0, dp(activity, 8), 0, dp(activity, 8))
        fun refreshFooter() {
            var bytes = 0L
            for (f in victims) bytes += f.length()
            footer.text = victims.size.toString() + " copies selected for deletion - " + Cleaner.humanSize(bytes) + " recoverable"
        }
        val root = LinearLayout(activity)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10))
        for (gi in shown.indices) {
            val sorted = shown[gi].sortedBy { Cleaner.parseTimestamp(it.name) ?: it.lastModified() }
            val head = TextView(activity)
            head.text = "Group " + (gi + 1) + " - " + sorted.size + " identical copies - " + Cleaner.humanSize(sorted[0].length()) + " each"
            head.textSize = 13f
            head.typeface = Typeface.DEFAULT_BOLD
            head.setTextColor(0xFFE8ECF1.toInt())
            root.addView(head)
            val hs = HorizontalScrollView(activity)
            val row = LinearLayout(activity)
            row.orientation = LinearLayout.HORIZONTAL
            hs.addView(row)
            for (mi in sorted.indices) {
                val f = sorted[mi]
                val card = LinearLayout(activity)
                card.orientation = LinearLayout.VERTICAL
                card.setPadding(dp(activity, 6), dp(activity, 6), dp(activity, 6), dp(activity, 6))
                val w = dp(activity, 96)
                val cell = FrameLayout(activity)
                cell.addView(iconTile(activity, f, w))
                val iv = ImageView(activity)
                iv.layoutParams = FrameLayout.LayoutParams(w, w)
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                cell.addView(iv)
                thumbJobs.add(Pair(iv, f))
                card.addView(cell)
                val name = TextView(activity)
                name.text = f.name
                name.textSize = 9f
                name.maxLines = 2
                name.ellipsize = TextUtils.TruncateAt.END
                name.setTextColor(0xFF97A5B4.toInt())
                name.layoutParams = LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT)
                card.addView(name)
                if (mi == 0) {
                    val keep = TextView(activity)
                    keep.text = "ORIGINAL - KEPT"
                    keep.textSize = 10f
                    keep.typeface = Typeface.DEFAULT_BOLD
                    keep.setTextColor(0xFF25D366.toInt())
                    card.addView(keep)
                } else {
                    val cb = CheckBox(activity)
                    cb.text = "Delete"
                    cb.textSize = 11f
                    cb.setTextColor(0xFFFF8A80.toInt())
                    cb.isChecked = true
                    victims.add(f)
                    cb.setOnCheckedChangeListener { b, checked ->
                        if (checked) {
                            if (!victims.contains(f)) victims.add(f)
                        } else {
                            victims.remove(f)
                        }
                        refreshFooter()
                    }
                    card.addView(cb)
                }
                card.setOnClickListener { previewOne(activity, f) }
                row.addView(card)
            }
            root.addView(hs)
        }
        refreshFooter()
        val scroll = ScrollView(activity)
        scroll.addView(root)
        val builder = AlertDialog.Builder(activity)
            .setTitle("Duplicates - review side by side")
            .setView(scroll)
            .setNegativeButton("Close", null)
        if (!dry) {
            builder.setPositiveButton("Delete selected") { d, w ->
                val toDelete = ArrayList(victims)
                AlertDialog.Builder(activity)
                    .setTitle("Confirm deletion")
                    .setMessage("Delete " + toDelete.size + " duplicate copies permanently? The originals stay untouched.")
                    .setPositiveButton("Delete") { dd, ww ->
                        var ok = 0
                        var bytes = 0L
                        for (f in toDelete) {
                            bytes += f.length()
                            if (f.delete()) ok++
                        }
                        onDone("Deleted " + ok + " duplicate copies (" + Cleaner.humanSize(bytes) + "). The originals are kept.")
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            builder.setPositiveButton("Apply (dry run)") { d, w ->
                var bytes = 0L
                for (f in victims) bytes += f.length()
                onDone("Dry run: would delete " + victims.size + " duplicate copies (" + Cleaner.humanSize(bytes) + "). Turn the DRY chip off to delete for real.")
            }
        }
        val dialog = builder.create()
        dialog.show()
        Thread {
            for (i in thumbJobs.indices) {
                if (!dialog.isShowing) return@Thread
                val bm = loadThumb(thumbJobs[i].second, 180)
                if (bm != null) {
                    activity.runOnUiThread {
                        try {
                            if (dialog.isShowing) thumbJobs[i].first.setImageBitmap(bm)
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }.start()
    }
}