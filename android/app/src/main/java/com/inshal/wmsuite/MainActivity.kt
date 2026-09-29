package com.inshal.wmsuite

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {

    private val COL_DARK = 0xFF075E54.toInt()
    private val COL_TEAL = 0xFF128C7E.toInt()
    private val COL_GREEN = 0xFF25D366.toInt()
    private val COL_TEXT = 0xFF212121.toInt()
    private val COL_SUB = 0xFF757575.toInt()
    private val COL_CARD = 0xFFFFFFFF.toInt()
    private val COL_RED = 0xFFD32F2F.toInt()

    private val busy = AtomicBoolean(false)
    private var days = 30

    private lateinit var dryRunCheck: CheckBox
    private lateinit var statusLine: TextView
    private lateinit var logView: TextView
    private lateinit var permStatus: TextView
    private lateinit var grantBtn: Button
    private lateinit var profileContainer: LinearLayout
    private lateinit var scanSummary: TextView
    private lateinit var catContainer: LinearLayout
    private lateinit var daysLabel: TextView
    private lateinit var exportFolderInput: EditText
    private lateinit var exportsContainer: LinearLayout

    private var profiles: List<Profiles.WaProfile> = emptyList()
    private var selected: Profiles.WaProfile? = null
    private var dupGroups: List<List<File>> = emptyList()
    private var exifItems: List<ExifRepair.Item> = emptyList()

    private val pages = mutableListOf<ScrollView>()
    private val navButtons = mutableListOf<LinearLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFFF0F2F5.toInt())

        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        header.setPadding(dp(20), dp(18), dp(20), dp(14))
        val headerBg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(COL_DARK, COL_TEAL))
        header.background = headerBg

        val title = TextView(this)
        title.text = "WhatsApp Media Suite"
        title.textSize = 22f
        title.setTextColor(Color.WHITE)
        title.setTypeface(null, Typeface.BOLD)
        header.addView(title)

        val subtitle = TextView(this)
        subtitle.text = "Clean, organize and rescue your WhatsApp and WhatsApp Business media - safely."
        subtitle.textSize = 13f
        subtitle.setTextColor(0xFFD7EDE8.toInt())
        header.addView(subtitle)

        dryRunCheck = CheckBox(this)
        dryRunCheck.text = "Dry run - preview only, delete nothing"
        dryRunCheck.textSize = 13f
        dryRunCheck.setTextColor(Color.WHITE)
        dryRunCheck.isChecked = true
        dryRunCheck.buttonTintList = ColorStateList.valueOf(COL_GREEN)
        header.addView(dryRunCheck)

        statusLine = TextView(this)
        statusLine.text = "Ready"
        statusLine.textSize = 12f
        statusLine.setTextColor(0xFFB2DFDB.toInt())
        header.addView(statusLine)
        root.addView(header)

        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(Color.WHITE)
        val labels = arrayOf("Dashboard", "Clean", "Tools", "Chats")
        for (i in labels.indices) {
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.setPadding(dp(4), dp(12), dp(4), dp(12))
            val t = TextView(this)
            t.text = labels[i]
            t.textSize = 13f
            t.setTypeface(null, Typeface.BOLD)
            t.gravity = Gravity.CENTER
            item.addView(t)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            item.layoutParams = lp
            item.setOnClickListener { selectTab(i) }
            navButtons.add(item)
            nav.addView(item)
        }
        root.addView(nav)

        val content = FrameLayout(this)
        content.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        content.addView(buildDashboardPage())
        content.addView(buildCleanPage())
        content.addView(buildToolsPage())
        content.addView(buildChatsPage())
        root.addView(content)

        val logPanel = LinearLayout(this)
        logPanel.orientation = LinearLayout.VERTICAL
        logPanel.setBackgroundColor(0xFF16302B.toInt())
        logPanel.setPadding(dp(14), dp(8), dp(14), dp(10))
        val logTitle = TextView(this)
        logTitle.text = "ACTIVITY LOG"
        logTitle.textSize = 11f
        logTitle.setTextColor(0xFF80CBC4.toInt())
        logTitle.setTypeface(null, Typeface.BOLD)
        logPanel.addView(logTitle)
        logView = TextView(this)
        logView.text = "Welcome. Every action is previewed here before anything is deleted."
        logView.textSize = 11f
        logView.setTextColor(0xFFE0F2F1.toInt())
        logView.setTypeface(Typeface.MONOSPACE)
        logPanel.addView(logView)
        root.addView(logPanel)

        setContentView(root)
        selectTab(0)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionCard()
        refreshProfiles(false)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun page(): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(this)
        scroll.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL
        inner.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(inner)
        return Pair(scroll, inner)
    }

    private fun roundedBg(color: Int, radius: Int, stroke: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = radius.toFloat()
        g.setStroke(dp(1), stroke)
        return g
    }

    private fun card(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(16), dp(14), dp(16), dp(14))
        l.background = roundedBg(COL_CARD, dp(12), 0xFFE3E6E8.toInt())
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, 0, dp(12))
        l.layoutParams = lp
        return l
    }

    private fun tv(parent: LinearLayout, text: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        parent.addView(t)
        return t
    }

    private fun addBtn(parent: LinearLayout, text: String, color: Int, body: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.textSize = 14f
        b.setTextColor(Color.WHITE)
        b.backgroundTintList = ColorStateList.valueOf(color)
        b.setOnClickListener { body() }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10)
        b.layoutParams = lp
        parent.addView(b)
        return b
    }

    private fun selectTab(index: Int) {
        for (i in pages.indices) pages[i].visibility = if (i == index) View.VISIBLE else View.GONE
        for (i in navButtons.indices) {
            val label = navButtons[i].getChildAt(0) as TextView
            if (i == index) {
                navButtons[i].setBackgroundColor(0xFFE8F5E9.toInt())
                label.setTextColor(COL_DARK)
            } else {
                navButtons[i].setBackgroundColor(Color.WHITE)
                label.setTextColor(COL_SUB)
            }
        }
    }

    private fun status(msg: String) {
        runOnUiThread { statusLine.text = msg }
    }

    private fun toast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
    }

    private fun log(msg: String) {
        runOnUiThread {
            if (logView.text.length > 12000) logView.text = ""
            logView.append("- " + msg + "\n")
        }
    }

    private fun isDry(): Boolean = dryRunCheck.isChecked

    private fun suffix(): String = if (isDry()) " [dry run - nothing deleted]" else " [done]"

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

    private fun refreshPermissionCard() {
        val granted = hasStorageAccess()
        permStatus.text = if (granted) "Granted - the app can see your WhatsApp storage." else "Not granted - the app cannot see your media yet."
        permStatus.setTextColor(if (granted) 0xFF2E7D32.toInt() else COL_RED)
        grantBtn.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun confirmGo(title: String, message: String, run: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Go") { _, _ -> run() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runTask(job: String, body: (Profiles.WaProfile) -> String) {
        if (!hasStorageAccess()) { toast("Grant storage permission first."); return }
        val p = selected
        if (p == null) { toast("No WhatsApp profile selected - wait for the profile list to load."); return }
        if (!busy.compareAndSet(false, true)) { toast("Another task is running, please wait."); return }
        status(job + "...")
        Thread {
            try {
                val msg = body(p)
                log(msg)
            } catch (e: Exception) {
                log(job + " error: " + e.message)
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
    }

    private fun buildDashboardPage(): ScrollView {
        val (scroll, inner) = page()
        pages.add(scroll)

        val permCard = card()
        tv(permCard, "1. Storage permission", 15f, COL_TEXT, true)
        permStatus = tv(permCard, "Checking...", 13f, COL_SUB)
        grantBtn = addBtn(permCard, "Grant storage access", COL_TEAL) { requestStorage() }
        inner.addView(permCard)

        val profCard = card()
        tv(profCard, "2. Choose your WhatsApp", 15f, COL_TEXT, true)
        tv(profCard, "WhatsApp and WhatsApp Business are detected automatically. The active one (used in the last 30 days) is preselected - pick the other one if your media lives there.", 13f, COL_SUB)
        val profRow = LinearLayout(this)
        profRow.orientation = LinearLayout.HORIZONTAL
        profRow.gravity = Gravity.CENTER_VERTICAL
        val profLabel = TextView(this)
        profLabel.text = "Profiles on this phone"
        profLabel.textSize = 13f
        profLabel.setTextColor(COL_DARK)
        profLabel.setTypeface(null, Typeface.BOLD)
        profLabel.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        profRow.addView(profLabel)
        val refresh = Button(this)
        refresh.text = "Refresh"
        refresh.textSize = 12f
        refresh.isAllCaps = false
        refresh.backgroundTintList = ColorStateList.valueOf(0xFFECEFF1.toInt())
        refresh.setTextColor(COL_DARK)
        refresh.setOnClickListener { refreshProfiles(true) }
        profRow.addView(refresh)
        profCard.addView(profRow)
        profileContainer = LinearLayout(this)
        profileContainer.orientation = LinearLayout.VERTICAL
        profCard.addView(profileContainer)
        inner.addView(profCard)

        val scanCard = card()
        tv(scanCard, "3. Scan storage", 15f, COL_TEXT, true)
        scanSummary = tv(scanCard, "Not scanned yet.", 13f, COL_SUB)
        addBtn(scanCard, "Scan WhatsApp storage", COL_GREEN) { runScan() }
        inner.addView(scanCard)

        tv(inner, "Categories (tap Clean to choose all / received / sent)", 13f, COL_SUB)
        catContainer = LinearLayout(this)
        catContainer.orientation = LinearLayout.VERTICAL
        inner.addView(catContainer)

        return scroll
    }

    private fun buildCleanPage(): ScrollView {
        val (scroll, inner) = page()
        pages.add(scroll)

        val info = card()
        tv(info, "Safe cleaning", 15f, COL_TEXT, true)
        tv(info, "Dry run is ON by default: every action below only previews what it would do. Your chat databases (Databases / Backups) are never touched.", 13f, COL_SUB)
        inner.addView(info)

        val ageCard = card()
        tv(ageCard, "Media older than N days", 15f, COL_TEXT, true)
        tv(ageCard, "Removes received and sent media older than the selected age.", 13f, COL_SUB)
        val stepper = LinearLayout(this)
        stepper.orientation = LinearLayout.HORIZONTAL
        stepper.gravity = Gravity.CENTER_VERTICAL
        val minus = Button(this)
        minus.text = "- 5 days"
        minus.textSize = 12f
        minus.isAllCaps = false
        minus.backgroundTintList = ColorStateList.valueOf(0xFFECEFF1.toInt())
        minus.setTextColor(COL_DARK)
        minus.setOnClickListener { stepDays(-5) }
        stepper.addView(minus)
        daysLabel = TextView(this)
        daysLabel.text = days.toString() + " days"
        daysLabel.textSize = 16f
        daysLabel.setTextColor(COL_DARK)
        daysLabel.setTypeface(null, Typeface.BOLD)
        daysLabel.gravity = Gravity.CENTER
        daysLabel.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        stepper.addView(daysLabel)
        val plus = Button(this)
        plus.text = "+ 5 days"
        plus.textSize = 12f
        plus.isAllCaps = false
        plus.backgroundTintList = ColorStateList.valueOf(0xFFECEFF1.toInt())
        plus.setTextColor(COL_DARK)
        plus.setOnClickListener { stepDays(5) }
        stepper.addView(plus)
        ageCard.addView(stepper)
        addBtn(ageCard, "Clean old media", COL_RED) { runCleanOld() }
        inner.addView(ageCard)

        val backupCard = card()
        tv(backupCard, "msgstore backups", 15f, COL_TEXT, true)
        tv(backupCard, "WhatsApp keeps daily chat-backup files. Keep only the newest ones to free space.", 13f, COL_SUB)
        addBtn(backupCard, "Prune backups (keep 5 newest)", COL_TEAL) { runPruneBackups() }
        inner.addView(backupCard)

        val emptyCard = card()
        tv(emptyCard, "Empty folders", 15f, COL_TEXT, true)
        tv(emptyCard, "Left-over folders with no real content. Remove them to keep storage tidy.", 13f, COL_SUB)
        addBtn(emptyCard, "Remove empty folders", COL_TEAL) { runRemoveEmpty() }
        inner.addView(emptyCard)

        return scroll
    }

    private fun buildToolsPage(): ScrollView {
        val (scroll, inner) = page()
        pages.add(scroll)

        val dupCard = card()
        tv(dupCard, "Duplicate media", 15f, COL_TEXT, true)
        tv(dupCard, "Finds files with identical content (size + SHA-256 hash), shows a preview, then deletes only the extra copies - the earliest original of every group is always kept.", 13f, COL_SUB)
        addBtn(dupCard, "Find duplicates", COL_GREEN) { runDuplicates() }
        inner.addView(dupCard)

        val exifCard = card()
        tv(exifCard, "Repair photo dates (EXIF)", 15f, COL_TEXT, true)
        tv(exifCard, "WhatsApp strips the capture date from photos. This reads the timestamp from the filename and writes it back into the photo EXIF, so your gallery shows photos on the right days.", 13f, COL_SUB)
        addBtn(exifCard, "Scan for photos needing repair", COL_GREEN) { runExifScan() }
        inner.addView(exifCard)

        val statusCard = card()
        tv(statusCard, "Status saver", 15f, COL_TEXT, true)
        tv(statusCard, "Copies the 24-hour .Statuses into permanent dated folders before they expire.", 13f, COL_SUB)
        addBtn(statusCard, "Save statuses now", COL_TEAL) { runSaveStatuses() }
        inner.addView(statusCard)

        val orgCard = card()
        tv(orgCard, "Organize by date", 15f, COL_TEXT, true)
        tv(orgCard, "Copies media into WMSuite-Organized/YYYY-MM/Category folders. Originals stay untouched.", 13f, COL_SUB)
        addBtn(orgCard, "Organize by date", COL_TEAL) { runOrganize() }
        inner.addView(orgCard)

        return scroll
    }

    private fun buildChatsPage(): ScrollView {
        val (scroll, inner) = page()
        pages.add(scroll)

        val guide = card()
        tv(guide, "Organize by contact", 15f, COL_TEXT, true)
        tv(guide, "WhatsApp media cannot be linked to a contact on a normal (non-rooted) phone - the chat database is end-to-end encrypted. Instead, export chats from WhatsApp and the app copies their media into per-contact folders:", 13f, COL_SUB)
        tv(guide, "1. In WhatsApp or WhatsApp Business, open the chat.", 13f, COL_TEXT)
        tv(guide, "2. Tap the contact name at the top, then the three-dot menu, then More, then Export chat.", 13f, COL_TEXT)
        tv(guide, "3. Choose Include media and save the ZIP into your Downloads folder.", 13f, COL_TEXT)
        tv(guide, "4. Come back here, set the folder below and tap Find chat exports.", 13f, COL_TEXT)
        tv(guide, "Your chat, the exported ZIP and WhatsApp itself are never modified - media is only copied.", 12f, COL_SUB)
        inner.addView(guide)

        val findCard = card()
        tv(findCard, "Chat exports", 15f, COL_TEXT, true)
        exportFolderInput = EditText(this)
        exportFolderInput.hint = "Download"
        exportFolderInput.setText("Download")
        exportFolderInput.textSize = 14f
        findCard.addView(exportFolderInput)
        addBtn(findCard, "Find chat exports", COL_GREEN) { findExports() }
        exportsContainer = LinearLayout(this)
        exportsContainer.orientation = LinearLayout.VERTICAL
        findCard.addView(exportsContainer)
        inner.addView(findCard)

        return scroll
    }

    private fun refreshProfiles(announce: Boolean) {
        if (!hasStorageAccess()) {
            profiles = emptyList()
            renderProfiles()
            return
        }
        if (!busy.compareAndSet(false, true)) { return }
        status("Looking for WhatsApp profiles...")
        Thread {
            try {
                val list = Profiles.detect(Environment.getExternalStorageDirectory())
                runOnUiThread {
                    profiles = list
                    if (selected == null || !list.contains(selected)) selected = list.firstOrNull()
                    renderProfiles()
                    if (announce) {
                        if (list.isEmpty()) toast("No WhatsApp or WhatsApp Business storage found.")
                        else log("Found " + list.size + " profile(s). The most active one is preselected.")
                    }
                }
            } catch (e: Exception) {
                log("Profile detection error: " + e.message)
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
    }

    private fun renderProfiles() {
        profileContainer.removeAllViews()
        if (profiles.isEmpty()) {
            val t = TextView(this)
            t.text = "No WhatsApp or WhatsApp Business storage found yet. Grant storage access (above), then tap Refresh."
            t.textSize = 13f
            t.setTextColor(COL_SUB)
            profileContainer.addView(t)
            return
        }
        for (p in profiles) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(dp(12), dp(10), dp(12), dp(10))
            row.background = roundedBg(
                if (p == selected) 0xFFE8F5E9.toInt() else 0xFFFAFAFA.toInt(),
                dp(10),
                if (p == selected) COL_GREEN else 0xFFEEEEEE.toInt()
            )
            val nameRow = LinearLayout(this)
            nameRow.orientation = LinearLayout.HORIZONTAL
            nameRow.gravity = Gravity.CENTER_VERTICAL
            val name = TextView(this)
            name.text = p.name
            name.textSize = 14f
            name.setTextColor(COL_TEXT)
            name.setTypeface(null, Typeface.BOLD)
            nameRow.addView(name)
            val badge = TextView(this)
            badge.text = if (p.isActive()) "  ACTIVE  " else "  inactive  "
            badge.textSize = 10f
            badge.typeface = Typeface.DEFAULT_BOLD
            if (p.isActive()) {
                badge.setTextColor(Color.WHITE)
                badge.setBackgroundColor(COL_GREEN)
            } else {
                badge.setTextColor(0xFF9E9E9E.toInt())
                badge.setBackgroundColor(0xFFEEEEEE.toInt())
            }
            badge.setPadding(dp(6), dp(2), dp(6), dp(2))
            val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            blp.leftMargin = dp(8)
            badge.layoutParams = blp
            nameRow.addView(badge)
            row.addView(nameRow)
            val info = TextView(this)
            info.text = p.files.toString() + " files - " + Cleaner.humanSize(p.bytes) + " - " + p.ageLabel()
            info.textSize = 12f
            info.setTextColor(COL_SUB)
            row.addView(info)
            row.setOnClickListener {
                selected = p
                log("Selected profile: " + p.name)
                scanSummary.text = "Not scanned yet."
                catContainer.removeAllViews()
                renderProfiles()
            }
            val rlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            rlp.topMargin = dp(6)
            row.layoutParams = rlp
            profileContainer.addView(row)
        }
    }

    private fun catCard(c: Cleaner.CategoryStat): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(12), dp(10), dp(12), dp(10))
        card.background = roundedBg(COL_CARD, dp(10), 0xFFE3E6E8.toInt())
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8)
        card.layoutParams = lp

        val nameRow = LinearLayout(this)
        nameRow.orientation = LinearLayout.HORIZONTAL
        nameRow.gravity = Gravity.CENTER_VERTICAL
        val name = TextView(this)
        name.text = c.name
        name.textSize = 14f
        name.setTextColor(COL_TEXT)
        name.setTypeface(null, Typeface.BOLD)
        name.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        nameRow.addView(name)
        val cleanBtn = Button(this)
        cleanBtn.text = "Clean"
        cleanBtn.textSize = 12f
        cleanBtn.isAllCaps = false
        cleanBtn.setTextColor(Color.WHITE)
        cleanBtn.backgroundTintList = ColorStateList.valueOf(COL_RED)
        cleanBtn.setOnClickListener { scopeDialog(c) }
        nameRow.addView(cleanBtn)
        card.addView(nameRow)

        val received = c.files - c.sentFiles
        val info = TextView(this)
        info.text = c.files.toString() + " files - " + Cleaner.humanSize(c.bytes) + "\n" + received + " received - " + c.sentFiles + " sent"
        info.textSize = 12f
        info.setTextColor(COL_SUB)
        card.addView(info)
        return card
    }

    private fun runScan() {
        if (!hasStorageAccess()) { toast("Grant storage permission first."); return }
        val p = selected
        if (p == null) { toast("No WhatsApp profile selected - wait for the profile list to load."); return }
        if (!busy.compareAndSet(false, true)) { toast("Another task is running, please wait."); return }
        doScan(p)
    }

    private fun doScan(p: Profiles.WaProfile) {
        status("Scanning " + p.name + "...")
        Thread {
            try {
                val cats = Cleaner.scan(p.root)
                var files = 0
                var bytes = 0L
                for (c in cats) {
                    files += c.files
                    bytes += c.bytes
                }
                runOnUiThread {
                    catContainer.removeAllViews()
                    val sorted = cats.sortedByDescending { it.bytes }
                    for (c in sorted) catContainer.addView(catCard(c))
                    scanSummary.text = cats.size.toString() + " categories - " + files + " files - " + Cleaner.humanSize(bytes)
                }
                if (cats.isEmpty()) {
                    log("Scan: 0 categories. Your media may be in the other profile - check the profile list above.")
                } else {
                    log("Scan finished: " + cats.size + " categories, " + files + " files, " + Cleaner.humanSize(bytes) + " total.")
                }
            } catch (e: Exception) {
                log("Scan error: " + e.message)
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
    }

    private fun scopeDialog(c: Cleaner.CategoryStat) {
        val received = c.files - c.sentFiles
        val options = arrayOf(
            "All " + c.name + " files",
            "Received only (" + received + ")",
            "Sent only (" + c.sentFiles + ")"
        )
        val choice = intArrayOf(0)
        AlertDialog.Builder(this)
            .setTitle("Clean " + c.name + "?")
            .setSingleChoiceItems(options, 0) { _, which -> choice[0] = which }
            .setPositiveButton("Continue") { _, _ -> confirmClean(c, choice[0]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClean(c: Cleaner.CategoryStat, scope: Int) {
        val label = if (scope == Cleaner.SCOPE_SENT) "sent" else if (scope == Cleaner.SCOPE_RECEIVED) "received" else "all"
        val msg = if (isDry())
            "Dry run: nothing will be deleted. You will see exactly what would go."
            else "Permanently delete " + label + " " + c.name + " files? This cannot be undone."
        AlertDialog.Builder(this)
            .setTitle("Confirm")
            .setMessage(msg)
            .setPositiveButton("Go") { _, _ -> doClean(c, scope) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doClean(c: Cleaner.CategoryStat, scope: Int) {
        if (!busy.compareAndSet(false, true)) { toast("Another task is running, please wait."); return }
        val label = if (scope == Cleaner.SCOPE_SENT) "sent" else if (scope == Cleaner.SCOPE_RECEIVED) "received" else "all"
        status("Cleaning " + c.name + "...")
        Thread {
            try {
                val res = Cleaner.cleanFolder(c.folder, isDry(), scope)
                log("Clean " + c.name + " (" + label + "): " + res.count + " files, " + Cleaner.humanSize(res.bytes) + (if (isDry()) " [dry run - nothing deleted]" else " [deleted]"))
            } catch (e: Exception) {
                log("Clean error: " + e.message)
            } finally {
                busy.set(false)
                status("Ready")
            }
            val p = selected
            if (p != null && busy.compareAndSet(false, true)) doScan(p)
        }.start()
    }

    private fun stepDays(d: Int) {
        days = Math.max(1, Math.min(3650, days + d))
        daysLabel.text = days.toString() + " days"
    }

    private fun runCleanOld() {
        confirmGo(
            "Clean media older than " + days + " days",
            if (isDry()) "Dry run: nothing will be deleted, you will only see what would go."
            else "Permanently delete all media older than " + days + " days? This cannot be undone."
        ) {
            runTask("Cleaning old media") { p ->
                val res = Cleaner.deleteOlderThan(p.root, days, isDry())
                "Old media (older than " + days + " days): " + res.count + " files, " + Cleaner.humanSize(res.bytes) + suffix()
            }
        }
    }

    private fun runPruneBackups() {
        confirmGo(
            "Prune msgstore backups",
            if (isDry()) "Dry run: nothing will be deleted, you will only see what would go."
            else "Keep only the 5 newest msgstore backup files and delete the rest?"
        ) {
            runTask("Pruning backups") { p ->
                val res = Cleaner.pruneBackups(File(p.root, "Databases"), 5, isDry())
                "msgstore backups pruned: " + res.count + " files, " + Cleaner.humanSize(res.bytes) + suffix()
            }
        }
    }

    private fun runRemoveEmpty() {
        confirmGo(
            "Remove empty folders",
            if (isDry()) "Dry run: nothing will be deleted, you will only see what would go."
            else "Remove all empty folders inside " + (selected?.name ?: "the profile") + "?"
        ) {
            runTask("Removing empty folders") { p ->
                val removed = if (isDry()) Cleaner.listEmptyDirs(p.root) else Cleaner.removeEmptyDirs(p.root)
                "Empty folders: " + removed.size + (if (isDry()) " would be removed [dry run]" else " removed")
            }
        }
    }

    private fun runDuplicates() {
        if (!hasStorageAccess()) { toast("Grant storage permission first."); return }
        val p = selected
        if (p == null) { toast("No WhatsApp profile selected - wait for the profile list to load."); return }
        if (!busy.compareAndSet(false, true)) { toast("Another task is running, please wait."); return }
        status("Scanning for duplicates...")
        Thread {
            try {
                dupGroups = Cleaner.findDuplicateGroups(p.root)
                var extra = 0
                var bytes = 0L
                for (g in dupGroups) {
                    extra += g.size - 1
                    bytes += g[0].length() * (g.size - 1)
                }
                if (dupGroups.isEmpty()) {
                    log("Duplicates: none found.")
                    toast("No duplicate media found.")
                } else {
                    runOnUiThread {
                        AlertDialog.Builder(this)
                            .setTitle("Duplicate media found")
                            .setMessage(dupGroups.size.toString() + " duplicate groups. " + extra + " extra copies can free " + Cleaner.humanSize(bytes) + ". The earliest original in each group is always kept.")
                            .setPositiveButton(if (isDry()) "Preview delete" else "Delete now") { _, _ -> doDeleteDups() }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                log("Duplicates error: " + e.message)
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
    }

    private fun doDeleteDups() {
        if (!busy.compareAndSet(false, true)) { toast("Another task is running, please wait."); return }
        status("Removing duplicates...")
        Thread {
            try {
                val res = Cleaner.deleteDuplicateGroups(dupGroups, isDry())
                log("Duplicates removed: " + res.count + " files, " + Cleaner.humanSize(res.bytes) + " freed" + (if (isDry()) " [dry run - nothing deleted]" else ""))
            } catch (e: Exception) {
                log("Duplicates delete error: " + e.message)
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
    }

    private fun runExifScan() {
        if (!hasStorageAccess()) { toast("Grant storage permission first."); return }
        val p = selected
        if (p == null) { toast("No WhatsApp profile selected - wait for the profile list to load."); return }
        if (!busy.compareAndSet(false, true)) { toast("Another task is running, please wait."); return }
        status("Scanning photo dates...")
        Thread {
            try {
                exifItems = ExifRepair.scan(p.root)
                if (exifItems.isEmpty()) {
                    log("EXIF repair: all photos already have correct dates.")
                    toast("No photos need EXIF repair.")
                } else {
                    runOnUiThread {
                        AlertDialog.Builder(this)
                            .setTitle("Photos needing date repair")
                            .setMessage(exifItems.size.toString() + " photos have a missing or wrong capture date. Repair writes the filename timestamp into the photo EXIF and file date. The image itself is not changed.")
                            .setPositiveButton(if (isDry()) "Preview repair" else "Repair now") { _, _ -> doExifApply() }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                log("EXIF scan error: " + e.message)
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
    }

    private fun doExifApply() {
        if (!busy.compareAndSet(false, true)) { toast("Another task is running, please wait."); return }
        status("Repairing photo dates...")
        Thread {
            try {
                val n = ExifRepair.apply(exifItems, isDry())
                log("EXIF repair: " + n + " photos updated" + (if (isDry()) " [dry run]" else ""))
            } catch (e: Exception) {
                log("EXIF repair error: " + e.message)
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
    }

    private fun runSaveStatuses() {
        runTask("Saving statuses") { p ->
            val src = File(File(p.root, "Media"), ".Statuses")
            val target = File(Environment.getExternalStorageDirectory(), "SavedStatuses")
            if (!src.isDirectory) {
                "No .Statuses folder at " + src.path + " (statuses may have already expired)."
            } else {
                val res = Cleaner.saveStatuses(src, target, isDry())
                "Statuses saved: " + res.count + " files, " + Cleaner.humanSize(res.bytes) + (if (isDry()) " [dry run]" else " -> " + target.path)
            }
        }
    }

    private fun runOrganize() {
        runTask("Organizing by date") { p ->
            val out = File(Environment.getExternalStorageDirectory(), "WMSuite-Organized")
            val res = Cleaner.organizeByDate(p.root, out, isDry())
            "Organized: " + res.count + " files, " + Cleaner.humanSize(res.bytes) + (if (isDry()) " [dry run]" else " -> " + out.path)
        }
    }

    private fun findExports() {
        if (!hasStorageAccess()) { toast("Grant storage permission first."); return }
        val name = exportFolderInput.text.toString().trim()
        val folder = if (name.startsWith("/")) File(name) else File(Environment.getExternalStorageDirectory(), name)
        val exports = ChatExportOrganizer.findExports(folder)
        exportsContainer.removeAllViews()
        if (exports.isEmpty()) {
            val t = TextView(this)
            t.text = "No chat exports found in " + folder.path + ". Export a chat with media from WhatsApp first (steps above)."
            t.textSize = 12f
            t.setTextColor(COL_SUB)
            exportsContainer.addView(t)
            log("Chat exports: none found in " + folder.path)
            return
        }
        for (e in exports) exportsContainer.addView(exportRow(e))
        log("Chat exports found: " + exports.size + " in " + folder.path)
    }

    private fun exportRow(e: ChatExportOrganizer.Export): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8)
        row.layoutParams = lp
        val info = TextView(this)
        info.text = e.contact + "\n" + e.zip.name + " (" + Cleaner.humanSize(e.zip.length()) + ")"
        info.textSize = 13f
        info.setTextColor(COL_TEXT)
        info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(info)
        val imp = Button(this)
        imp.text = "Import"
        imp.textSize = 12f
        imp.isAllCaps = false
        imp.setTextColor(Color.WHITE)
        imp.backgroundTintList = ColorStateList.valueOf(COL_GREEN)
        imp.setOnClickListener { importExport(e) }
        row.addView(imp)
        return row
    }

    private fun importExport(e: ChatExportOrganizer.Export) {
        if (!hasStorageAccess()) { toast("Grant storage permission first."); return }
        if (!busy.compareAndSet(false, true)) { toast("Another task is running, please wait."); return }
        status("Importing chat with " + e.contact + "...")
        Thread {
            try {
                val out = File(Environment.getExternalStorageDirectory(), "WMSuite-Organized")
                val res = ChatExportOrganizer.importExport(e.zip, out, isDry())
                if (res.count == 0) {
                    log("Chat import (" + e.contact + "): no media found in the export.")
                } else {
                    log("Chat import (" + e.contact + "): " + res.count + " files, " + Cleaner.humanSize(res.bytes) + (if (isDry()) " [dry run]" else " -> WMSuite-Organized/Conversations/" + e.contact))
                }
            } catch (ex: Exception) {
                log("Chat import error: " + ex.message)
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
    }
}
