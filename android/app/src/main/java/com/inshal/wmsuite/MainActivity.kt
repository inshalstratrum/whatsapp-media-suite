package com.inshal.wmsuite

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    private lateinit var logView: TextView
    private lateinit var catContainer: LinearLayout
    private lateinit var dryRunCheck: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(14), dp(16), dp(14), dp(28))
        scroll.addView(root)

        val title = TextView(this)
        title.text = "WhatsApp Media Suite"
        title.textSize = 24f
        title.setTextColor(0xFF075E54.toInt())
        root.addView(title)

        val subtitle = TextView(this)
        subtitle.text = "Clean and organize the media WhatsApp stores on this phone. Nothing leaves your device."
        root.addView(subtitle)

        val permBtn = Button(this)
        permBtn.text = "1. Grant storage permission"
        permBtn.setOnClickListener { requestStorage() }
        root.addView(permBtn)

        dryRunCheck = CheckBox(this)
        dryRunCheck.text = "Dry run (preview only - delete/move nothing)"
        dryRunCheck.isChecked = true
        root.addView(dryRunCheck)

        val scanBtn = Button(this)
        scanBtn.text = "2. Scan WhatsApp storage"
        scanBtn.setOnClickListener { runScan() }
        root.addView(scanBtn)

        catContainer = LinearLayout(this)
        catContainer.orientation = LinearLayout.VERTICAL
        root.addView(catContainer)

        fun action(label: String, body: () -> Unit) {
            val b = Button(this)
            b.text = label
            b.setOnClickListener { body() }
            root.addView(b)
        }

        action("Clean media older than 30 days") { runCleanOld() }
        action("Prune old msgstore backups (keep 5 newest)") { runPruneBackups() }
        action("Remove empty folders") { runRemoveEmpty() }
        action("Save statuses to SavedStatuses") { runSaveStatuses() }
        action("Find and delete duplicate files") { runDuplicates() }
        action("Organize by date into WMSuite-Organized") { runOrganize() }

        logView = TextView(this)
        logView.text = "Ready. Tap Scan to see what WhatsApp stores.\n"
        logView.textSize = 12f
        logView.setPadding(0, dp(12), 0, 0)
        root.addView(logView)

        setContentView(scroll)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun log(msg: String) {
        runOnUiThread { logView.append(msg + "\n") }
    }

    private fun toast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
    }

    private fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStorage() {
        if (hasStorageAccess()) {
            toast("Storage permission already granted.")
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + packageName)
                    )
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    private fun whatsappRoot(): File? {
        val ext = Environment.getExternalStorageDirectory()
        val candidates = listOf(File(ext, "WhatsApp"), File(ext, "Android/media/com.whatsapp/WhatsApp"))
        for (c in candidates) if (c.isDirectory) return c
        toast("WhatsApp folder not found on this phone.")
        return null
    }

    private fun runScan() {
        if (!hasStorageAccess()) { toast("Grant storage permission first (button 1)."); return }
        val root = whatsappRoot() ?: return
        Thread {
            val cats = Cleaner.scan(root)
            var total = 0L
            for (c in cats) total += c.bytes
            runOnUiThread {
                catContainer.removeAllViews()
                for (c in cats.sortedByDescending { it.bytes }) {
                    val row = LinearLayout(this)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.setPadding(0, dp(6), 0, dp(6))
                    val info = TextView(this)
                    info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    info.text = c.name + "\n" + c.files + " files - " + Cleaner.humanSize(c.bytes)
                    row.addView(info)
                    val clean = Button(this)
                    clean.text = "Clean"
                    clean.setOnClickListener { confirmClean(c) }
                    row.addView(clean)
                    catContainer.addView(row)
                }
            }
            log("Scan finished: " + cats.size + " categories, " + Cleaner.humanSize(total) + " total.")
        }.start()
    }

    private fun confirmClean(c: Cleaner.CategoryStat) {
        val dry = dryRunCheck.isChecked
        AlertDialog.Builder(this)
            .setTitle("Clean " + c.name + "?")
            .setMessage(
                if (dry) "Dry run: nothing will be deleted. You will only see what would go."
                else "Permanently delete " + c.files + " files (" + Cleaner.humanSize(c.bytes) + ")?"
            )
            .setPositiveButton("Go") { _, _ ->
                Thread {
                    val res = Cleaner.cleanFolder(c.folder, dry)
                    log("Clean " + c.name + ": " + res.count + " files, " + Cleaner.humanSize(res.bytes) + (if (dry) " [dry run]" else " [deleted]"))
                    runScan()
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runCleanOld() {
        if (!hasStorageAccess()) { toast("Grant storage permission first (button 1)."); return }
        val root = whatsappRoot() ?: return
        val dry = dryRunCheck.isChecked
        Thread {
            val res = Cleaner.deleteOlderThan(root, 30, dry)
            log("Media older than 30 days: " + res.count + " files, " + Cleaner.humanSize(res.bytes) + (if (dry) " [dry run]" else " [deleted]"))
        }.start()
    }

    private fun runPruneBackups() {
        if (!hasStorageAccess()) { toast("Grant storage permission first (button 1)."); return }
        val root = whatsappRoot() ?: return
        val dry = dryRunCheck.isChecked
        Thread {
            val db = File(root, "Databases")
            val res = Cleaner.pruneBackups(db, 5, dry)
            log("msgstore backups pruned: " + res.count + " files, " + Cleaner.humanSize(res.bytes) + (if (dry) " [dry run]" else " [deleted]"))
        }.start()
    }

    private fun runRemoveEmpty() {
        if (!hasStorageAccess()) { toast("Grant storage permission first (button 1)."); return }
        val root = whatsappRoot() ?: return
        val dry = dryRunCheck.isChecked
        Thread {
            val removed = if (dry) Cleaner.listEmptyDirs(root) else Cleaner.removeEmptyDirs(root)
            log("Empty folders: " + removed.size + (if (dry) " [dry run]" else " [removed]"))
        }.start()
    }

    private fun runSaveStatuses() {
        if (!hasStorageAccess()) { toast("Grant storage permission first (button 1)."); return }
        val root = whatsappRoot() ?: return
        val dry = dryRunCheck.isChecked
        val src = File(File(root, "Media"), ".Statuses")
        val target = File(Environment.getExternalStorageDirectory(), "SavedStatuses")
        if (!src.isDirectory) { log("No .Statuses folder at " + src + " (statuses may already have expired)."); return }
        Thread {
            val res = Cleaner.saveStatuses(src, target, dry)
            log("Statuses saved: " + res.count + " files, " + Cleaner.humanSize(res.bytes) + (if (dry) " [dry run]" else " -> " + target))
        }.start()
    }

    private fun runDuplicates() {
        if (!hasStorageAccess()) { toast("Grant storage permission first (button 1)."); return }
        val root = whatsappRoot() ?: return
        val dry = dryRunCheck.isChecked
        Thread {
            log("Scanning for duplicates (size + SHA-256), this may take a while...")
            val res = Cleaner.findDuplicates(root, dry)
            log("Duplicates: " + res.count + " files, " + Cleaner.humanSize(res.bytes) + (if (dry) " [dry run]" else " [deleted]"))
        }.start()
    }

    private fun runOrganize() {
        if (!hasStorageAccess()) { toast("Grant storage permission first (button 1)."); return }
        val root = whatsappRoot() ?: return
        val dry = dryRunCheck.isChecked
        val out = File(Environment.getExternalStorageDirectory(), "WMSuite-Organized")
        Thread {
            val res = Cleaner.organizeByDate(root, out, dry)
            log("Organized: " + res.count + " files, " + Cleaner.humanSize(res.bytes) + (if (dry) " [dry run]" else " [moved] -> " + out))
        }.start()
    }
}
