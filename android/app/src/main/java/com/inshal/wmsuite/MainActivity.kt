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
import android.provider.DocumentsContract
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
    private val COL_TEXT = 0xFFE8ECF1.toInt()
    private val COL_SUB = 0xFF97A5B4.toInt()
    private val COL_CARD = 0xFF161B22.toInt()
    private val COL_RED = 0xFFD32F2F.toInt()
    private val COL_STROKE = 0xFF232A35.toInt()
    private val COL_INPUT = 0xFF1E2530.toInt()
    private val COL_ROW = 0xFF11161E.toInt()
    private val COL_NAV = 0xFF0F2A22.toInt()

    private val busy = AtomicBoolean(false)
    private var days = 30

    private lateinit var dryRunCheck: CheckBox
    private lateinit var statusLine: TextView
    private lateinit var logPanel: LogPanel
    private lateinit var logChip: TextView
    private lateinit var permStatus: TextView
    private lateinit var grantBtn: Button
    private lateinit var profileContainer: LinearLayout
    private lateinit var scanSummary: TextView
    private lateinit var catContainer: LinearLayout
    private lateinit var daysLabel: TextView
    private lateinit var exifSummary: TextView
    private lateinit var exifViewBtn: Button
    private lateinit var exifApplyBtn: Button
    private lateinit var faceSummary: TextView
    private lateinit var faceRows: LinearLayout
    private lateinit var moveFaceCheck: CheckBox
    private lateinit var facePreviewBtn: Button
    private lateinit var faceGoBtn: Button
    private lateinit var keyInput: EditText
    private lateinit var useNamesCheck: CheckBox
    private lateinit var moveChatCheck: CheckBox
    private lateinit var chatStatus: TextView
    private lateinit var chatContactsContainer: LinearLayout
    private lateinit var chatPreviewBtn: Button
    private lateinit var chatGoBtn: Button
    private lateinit var exportFolderInput: EditText
    private lateinit var exportsContainer: LinearLayout

    private var profiles: List<Profiles.WaProfile> = emptyList()
    private var selected: Profiles.WaProfile? = null
    private var dupGroups: List<List<File>> = emptyList()
    private var exifItems: List<ExifRepair.Item> = emptyList()
    private var faceResult: FaceSorter.ScanResult? = null
    private var chatMap: Map<String, String>? = null
    private var chatGroups: Map<String, List<File>> = emptyMap()
    private var pendingChatMap = false

    private val pages = mutableListOf<ScrollView>()
    private val navButtons = mutableListOf<LinearLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wrap = FrameLayout(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF0E1116.toInt())
        wrap.addView(root)

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
        subtitle.text = "Clean, organize and rescue your WhatsApp media - safely."
        subtitle.textSize = 13f
        subtitle.setTextColor(0xFFD7EDE8.toInt())
        header.addView(subtitle)

        dryRunCheck = CheckBox(this)
        dryRunCheck.text = "DRY RUN - preview only, change nothing"
        dryRunCheck.textSize = 13f
        dryRunCheck.setTextColor(Color.WHITE)
        dryRunCheck.buttonTintList = ColorStateList.valueOf(COL_GREEN)
        dryRunCheck.isChecked = getSharedPreferences("wmsuite", MODE_PRIVATE).getBoolean("dryRun", true)
        dryRunCheck.setOnCheckedChangeListener { _, checked ->
            getSharedPreferences("wmsuite", MODE_PRIVATE).edit().putBoolean("dryRun", checked).apply()
            if (checked) log("DRY RUN is ON - destructive actions only preview.") else log("DRY RUN is OFF - actions now run for real.")
        }
        header.addView(dryRunCheck)

        statusLine = TextView(this)
        statusLine.text = "Ready"
        statusLine.textSize = 12f
        statusLine.setTextColor(0xFFB2DFDB.toInt())
        header.addView(statusLine)
        root.addView(header)

        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(0xFF0B0E13.toInt())
        val labels = arrayOf("Dashboard", "Clean", "Tools", "Faces", "Chats")
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
        content.addView(buildFacesPage())
        content.addView(buildChatsPage())
        root.addView(content)

        logPanel = LogPanel(this)
        logPanel.onClosed = { logChip.visibility = View.VISIBLE }
        wrap.addView(logPanel)

        logChip = TextView(this)
        logChip.text = "  ACTIVITY LOG  "
        logChip.textSize = 12f
        logChip.typeface = Typeface.DEFAULT_BOLD
        logChip.setTextColor(Color.WHITE)
        val chipBg = GradientDrawable()
        chipBg.setColor(COL_GREEN)
        chipBg.cornerRadius = dp(20).toFloat()
        logChip.background = chipBg
        val chipLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
        chipLp.setMargins(0, 0, dp(16), dp(16))
        logChip.layoutParams = chipLp
        logChip.visibility = View.GONE
        logChip.setOnClickListener {
            logChip.visibility = View.GONE
            logPanel.showPanel()
        }
        wrap.addView(logChip)

        setContentView(wrap)
        selectTab(0)
        logPanel.append("Welcome. Every action is previewed here before anything is changed.")
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
        l.background = roundedBg(COL_CARD, dp(12), COL_STROKE)
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
                navButtons[i].setBackgroundColor(COL_NAV)
                label.setTextColor(COL_GREEN)
            } else {
                navButtons[i].setBackgroundColor(Color.TRANSPARENT)
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
        logPanel.append(msg)
    }

    private fun isDry(): Boolean = dryRunCheck.isChecked

    private fun suffix(): String = if (isDry()) " [dry run - nothing changed]" else " [done]"

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
        permStatus.setTextColor(if (granted) COL_GREEN else COL_RED)
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
        tv(profCard, "WhatsApp and WhatsApp Business are detected automatically. If your folders are not found, tap 'Search entire storage' - or point the app at any folder yourself with 'Choose media folder manually'.", 13f, COL_SUB)
        val profRow = LinearLayout(this)
        profRow.orientation = LinearLayout.HORIZONTAL
        profRow.gravity = Gravity.CENTER_VERTICAL
        val profLabel = TextView(this)
        profLabel.text = "Profiles on this phone"
        profLabel.textSize = 13f
        profLabel.setTextColor(COL_GREEN)
        profLabel.setTypeface(null, Typeface.BOLD)
        profLabel.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        profRow.addView(profLabel)
        val refresh = Button(this)
        refresh.text = "Refresh"
        refresh.textSize = 12f
        refresh.isAllCaps = false
        refresh.backgroundTintList = ColorStateList.valueOf(COL_ROW)
        refresh.setTextColor(COL_GREEN)
        refresh.setOnClickListener { refreshProfiles(true) }
        profRow.addView(refresh)
        profCard.addView(profRow)
        profileContainer = LinearLayout(this)
        profileContainer.orientation = LinearLayout.VERTICAL
        profCard.addView(profileContainer)
        addBtn(profCard, "Search entire storage for media folders", COL_TEAL) { deepSearchNow() }
        addBtn(profCard, "Choose media folder manually", COL_DARK) { pickFolder() }
        inner.addView(profCard)

        val scanCard = card()
        tv(scanCard, "3. Scan storage", 15f, COL_TEXT, true)
        scanSummary = tv(scanCard, "Not scanned yet.", 13f, COL_SUB)
        addBtn(scanCard, "Scan WhatsApp storage", COL_GREEN) { runScan() }
        inner.addView(scanCard)

        tv(inner, "Categories (tap View for the gallery, Clean to choose all / received / sent)", 13f, COL_SUB)
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
        minus.backgroundTintList = ColorStateList.valueOf(COL_ROW)
        minus.setTextColor(COL_GREEN)
        minus.setOnClickListener { stepDays(-5) }
        stepper.addView(minus)
        daysLabel = TextView(this)
        daysLabel.text = days.toString() + " days"
        daysLabel.textSize = 16f
        daysLabel.setTextColor(COL_TEXT)
        daysLabel.setTypeface(null, Typeface.BOLD)
        daysLabel.gravity = Gravity.CENTER
        daysLabel.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        stepper.addView(daysLabel)
        val plus = Button(this)
        plus.text = "+ 5 days"
        plus.textSize = 12f
        plus.isAllCaps = false
        plus.backgroundTintList = ColorStateList.valueOf(COL_ROW)
        plus.setTextColor(COL_GREEN)
        plus.setOnClickListener { stepDays(5) }
        stepper.addView(plus)
        ageCard.addView(stepper)
        addBtn(ageCard, "View old media (gallery)", COL_TEAL) { viewOldMedia() }
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
        tv(dupCard, "Finds files with identical content (size + SHA-256 hash), shows every copy side by side with thumbnails, then deletes only the extra copies - the earliest original of every group is always kept.", 13f, COL_SUB)
        addBtn(dupCard, "Find duplicates", COL_GREEN) { runDuplicates() }
        inner.addView(dupCard)

        val exifCard = card()
        tv(exifCard, "Repair photo dates (EXIF)", 15f, COL_TEXT, true)
        tv(exifCard, "WhatsApp strips the capture date from photos. This reads the timestamp from the filename and writes it back into the photo EXIF, so your gallery shows photos on the right days.", 13f, COL_SUB)
        addBtn(exifCard, "Scan for photos needing repair", COL_GREEN) { runExifScan() }
        exifSummary = tv(exifCard, "Not scanned yet.", 13f, COL_SUB)
        exifViewBtn = addBtn(exifCard, "View photos (gallery)", COL_TEAL) { MediaViews.gallery(this, "Photos needing date repair", exifItems.map { it.file }, null) }
        exifViewBtn.visibility = View.GONE
        exifApplyBtn = addBtn(exifCard, "Apply repair", COL_GREEN) { applyExif() }
        exifApplyBtn.visibility = View.GONE
        inner.addView(exifCard)

        val statusCard = card()
        tv(statusCard, "Status saver", 15f, COL_TEXT, true)
        tv(statusCard, "Copies the 24-hour .Statuses into permanent dated folders before they expire.", 13f, COL_SUB)
        addBtn(statusCard, "View current statuses (gallery)", COL_TEAL) { viewStatuses() }
        addBtn(statusCard, "Save statuses now", COL_GREEN) { runSaveStatuses() }
        inner.addView(statusCard)

        val orgCard = card()
        tv(orgCard, "Organize by date", 15f, COL_TEXT, true)
        tv(orgCard, "Copies media into WMSuite-Organized/YYYY-MM/Category folders. Originals stay untouched.", 13f, COL_SUB)
        addBtn(orgCard, "Organize by date", COL_TEAL) { runOrganize() }
        inner.addView(orgCard)

        return scroll
    }

    private fun buildFacesPage(): ScrollView {
        val (scroll, inner) = page()
        pages.add(scroll)

        val intro = card()
        tv(intro, "Organize photos by faces", 15f, COL_TEXT, true)
        tv(intro, "Scans every photo with on-device face detection (Google ML Kit - fully offline, nothing is ever uploaded). Photos with people go to People, screenshots are recognized by name, folder and screen size, and everything else (scenery, memes, documents) goes to Non-human.", 13f, COL_SUB)
        tv(intro, "The first scan takes a while (roughly a second per photo). Results are cached, so later scans only process new photos.", 12f, COL_SUB)
        inner.addView(intro)

        val scanCard = card()
        tv(scanCard, "1. Scan", 15f, COL_TEXT, true)
        addBtn(scanCard, "Scan photos (face detection)", COL_GREEN) { runFaceScan() }
        faceSummary = tv(scanCard, "Not scanned yet.", 13f, COL_SUB)
        faceRows = LinearLayout(this)
        faceRows.orientation = LinearLayout.VERTICAL
        scanCard.addView(faceRows)
        inner.addView(scanCard)

        val orgCard = card()
        tv(orgCard, "2. Organize", 15f, COL_TEXT, true)
        moveFaceCheck = CheckBox(this)
        moveFaceCheck.text = "Move instead of copy (takes the photos out of WhatsApp)"
        moveFaceCheck.textSize = 13f
        moveFaceCheck.setTextColor(COL_TEXT)
        moveFaceCheck.buttonTintList = ColorStateList.valueOf(COL_GREEN)
        orgCard.addView(moveFaceCheck)
        facePreviewBtn = addBtn(orgCard, "Preview (dry run)", COL_TEAL) { runFaceOrganize(true) }
        faceGoBtn = addBtn(orgCard, "Organize now", COL_GREEN) { runFaceOrganize(false) }
        facePreviewBtn.isEnabled = false
        facePreviewBtn.alpha = 0.5f
        faceGoBtn.isEnabled = false
        faceGoBtn.alpha = 0.5f
        tv(orgCard, "Destination: WMSuite-Organized/By faces/People, /Screenshots, /Non-human", 12f, COL_SUB)
        inner.addView(orgCard)

        return scroll
    }

    private fun buildChatsPage(): ScrollView {
        val (scroll, inner) = page()
        pages.add(scroll)

        val guide = card()
        tv(guide, "Organize by contact (encrypted backup, no export needed)", 15f, COL_TEXT, true)
        tv(guide, "Reads the chat backup stored on your phone itself - like the desktop tools wa-sort-media and whatskeep, but fully offline on your phone.", 13f, COL_SUB)
        tv(guide, "1. In WhatsApp: Settings > Chats > Chat backup.", 13f, COL_TEXT)
        tv(guide, "2. Turn on End-to-end encrypted backup and choose 'Use 64-digit key instead of a password'.", 13f, COL_TEXT)
        tv(guide, "3. Write the 64-digit key down and tap Back up now.", 13f, COL_TEXT)
        tv(guide, "4. Paste the key below and tap Load chat map.", 13f, COL_TEXT)
        tv(guide, "Password-protected encrypted backups are not supported - switch to the 64-digit key option and make a fresh backup.", 12f, COL_SUB)
        inner.addView(guide)

        val keyCard = card()
        tv(keyCard, "Chat backup key", 15f, COL_TEXT, true)
        keyInput = EditText(this)
        keyInput.hint = "64-digit key (paste it here)"
        keyInput.textSize = 14f
        keyInput.singleLine = true
        keyInput.setTextColor(COL_TEXT)
        keyInput.setHintTextColor(COL_SUB)
        keyInput.background = roundedBg(COL_INPUT, dp(8), COL_STROKE)
        keyInput.setPadding(dp(12), dp(10), dp(12), dp(10))
        keyCard.addView(keyInput)
        keyInput.setText(getSharedPreferences("wmsuite", MODE_PRIVATE).getString("cryptKey", ""))
        useNamesCheck = CheckBox(this)
        useNamesCheck.text = "Use phone contact names (needs contacts permission)"
        useNamesCheck.textSize = 13f
        useNamesCheck.setTextColor(COL_TEXT)
        useNamesCheck.buttonTintList = ColorStateList.valueOf(COL_GREEN)
        keyCard.addView(useNamesCheck)
        moveChatCheck = CheckBox(this)
        moveChatCheck.text = "Move instead of copy (takes the media out of WhatsApp)"
        moveChatCheck.textSize = 13f
        moveChatCheck.setTextColor(COL_TEXT)
        moveChatCheck.buttonTintList = ColorStateList.valueOf(COL_GREEN)
        keyCard.addView(moveChatCheck)
        addBtn(keyCard, "Load chat map", COL_GREEN) { loadChatMap() }
        chatStatus = tv(keyCard, "Map not loaded yet.", 13f, COL_SUB)
        chatContactsContainer = LinearLayout(this)
        chatContactsContainer.orientation = LinearLayout.VERTICAL
        keyCard.addView(chatContactsContainer)
        chatPreviewBtn = addBtn(keyCard, "Preview (dry run)", COL_TEAL) { runChatOrganize(true) }
        chatPreviewBtn.isEnabled = false
        chatPreviewBtn.alpha = 0.5f
        chatGoBtn = addBtn(keyCard, "Organize now", COL_GREEN) { runChatOrganize(false) }
        chatGoBtn.isEnabled = false
        chatGoBtn.alpha = 0.5f
        tv(keyCard, "Destination: WMSuite-Organized/By contact/<contact name>", 12f, COL_SUB)
        inner.addView(keyCard)

        val findCard = card()
        tv(findCard, "Chat exports (fallback, no encrypted backup needed)", 15f, COL_TEXT, true)
        tv(findCard, "No encrypted backup? Export chats from WhatsApp (open the chat, tap the contact name, three-dot menu, More, Export chat, Include media) into a folder, then find them here. Media is copied into WMSuite-Organized/Conversations.", 13f, COL_SUB)
        exportFolderInput = EditText(this)
        exportFolderInput.hint = "Download"
        exportFolderInput.setText("Download")
        exportFolderInput.textSize = 14f
        exportFolderInput.singleLine = true
        exportFolderInput.setTextColor(COL_TEXT)
        exportFolderInput.setHintTextColor(COL_SUB)
        exportFolderInput.background = roundedBg(COL_INPUT, dp(8), COL_STROKE)
        exportFolderInput.setPadding(dp(12), dp(10), dp(12), dp(10))
        findCard.addView(exportFolderInput)
        addBtn(findCard, "Find chat exports", COL_GREEN) { findExports() }
        exportsContainer = LinearLayout(this)
        exportsContainer.orientation = LinearLayout.VERTICAL
        findCard.addView(exportsContainer)
        inner.addView(findCard)

        return scroll
    }

    private fun customRootPath(): String? {
        val path = getSharedPreferences("wmsuite", MODE_PRIVATE).getString("customRoot", null) ?: return null
        return if (File(path).isDirectory) path else null
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
                var list = Profiles.detect(Environment.getExternalStorageDirectory())
                list = Profiles.withCustom(list, customRootPath())
                runOnUiThread {
                    profiles = list
                    if (selected == null || !list.contains(selected)) selected = list.firstOrNull()
                    renderProfiles()
                    if (announce) {
                        if (list.isEmpty()) {
                            toast("No WhatsApp storage found - use Search or pick the folder manually.")
                            log("No WhatsApp storage found. Use 'Search entire storage' or 'Choose media folder manually' below.")
                        } else {
                            log("Found " + list.size + " profile(s). The most active one is preselected.")
                        }
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
            t.text = "No WhatsApp storage found yet. Grant storage access (above), then tap 'Search entire storage' or 'Choose media folder manually'."
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
                if (p == selected) COL_NAV else COL_ROW,
                dp(10),
                if (p == selected) COL_GREEN else COL_STROKE
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
                badge.setTextColor(Color.BLACK)
                badge.setBackgroundColor(COL_GREEN)
            } else {
                badge.setTextColor(COL_SUB)
                badge.setBackgroundColor(COL_ROW)
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
            val path = TextView(this)
            path.text = p.root.absolutePath
            path.textSize = 10f
            path.setTextColor(COL_SUB)
            row.addView(path)
            row.setOnClickListener {
                selected = p
                log("Selected profile: " + p.name + " [" + p.root.absolutePath + "]")
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

    private fun deepSearchNow() {
        if (!hasStorageAccess()) { toast("Grant storage permission first."); return }
        if (!busy.compareAndSet(false, true)) { toast("Another task is running, please wait."); return }
        status("Searching your storage for WhatsApp media folders...")
        Thread {
            try {
                val found = Profiles.deepSearch(Environment.getExternalStorageDirectory()) { msg -> log(msg) }
                runOnUiThread {
                    profiles = found
                    selected = found.firstOrNull()
                    renderProfiles()
                    scanSummary.text = "Not scanned yet."
                    catContainer.removeAllViews()
                }
                if (found.isEmpty()) {
                    log("Deep search: no WhatsApp media folders found anywhere. Use 'Choose media folder manually'.")
                    toast("No WhatsApp media folders found. Pick the folder manually.")
                } else {
                    log("Deep search found " + found.size + " folder(s). The most active one is selected - tap 'Scan storage'.")
                }
            } catch (e: Exception) {
                log("Deep search error: " + e.message)
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        try {
            startActivityForResult(intent, 7001)
        } catch (e: Exception) {
            toast("No folder picker available on this device.")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 7001 || resultCode != RESULT_OK || data == null) return
        val uri = data.data ?: return
        val dir = treeUriToPath(uri)
        if (dir == null || !dir.isDirectory) {
            toast("Could not read that location. Pick a folder on your device storage (not a cloud or recent folder).")
            return
        }
        if (dir.absolutePath == Environment.getExternalStorageDirectory().absolutePath) {
            toast("Please pick the specific WhatsApp or media folder, not the whole storage.")
            return
        }
        getSharedPreferences("wmsuite", MODE_PRIVATE).edit().putString("customRoot", dir.absolutePath).apply()
        if (!busy.compareAndSet(false, true)) {
            toast("Folder saved. It will appear in the profile list shortly.")
            return
        }
        status("Reading chosen folder...")
        Thread {
            try {
                val prof = Profiles.profileFrom("Chosen folder", dir)
                runOnUiThread {
                    if (prof == null) {
                        toast("That folder is empty or unreadable.")
                    } else {
                        profiles = Profiles.withCustom(profiles, dir.absolutePath)
                        selected = prof
                        renderProfiles()
                        scanSummary.text = "Not scanned yet."
                        catContainer.removeAllViews()
                        log("Using folder: " + dir.absolutePath)
                    }
                }
            } catch (e: Exception) {
                log("Folder pick error: " + e.message)
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7100 && pendingChatMap) {
            pendingChatMap = false
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                log("Contacts permission granted - using phone contact names.")
            } else {
                toast("Contacts permission denied - WhatsApp names will be used instead.")
            }
            loadChatMap()
        }
    }

    private fun treeUriToPath(uri: Uri): File? {
        return try {
            if (uri.authority != "com.android.externalstorage.documents") return null
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(":")
            if (parts.size < 2 || parts[1].isEmpty()) return null
            if (parts[0] == "primary") File(Environment.getExternalStorageDirectory(), parts[1])
            else File("/storage/" + parts[0] + "/" + parts[1])
        } catch (e: Exception) {
            null
        }
    }

    private fun catCard(c: Cleaner.CategoryStat): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(12), dp(10), dp(12), dp(10))
        card.background = roundedBg(COL_ROW, dp(10), COL_STROKE)
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
        val viewBtn = Button(this)
        viewBtn.text = "View"
        viewBtn.textSize = 12f
        viewBtn.isAllCaps = false
        viewBtn.setTextColor(Color.WHITE)
        viewBtn.backgroundTintList = ColorStateList.valueOf(COL_TEAL)
        viewBtn.setOnClickListener { viewCategory(c) }
        nameRow.addView(viewBtn)
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

    private fun viewCategory(c: Cleaner.CategoryStat) {
        val p = selected
        if (p == null) { toast("No WhatsApp profile selected."); return }
        if (!busy.compareAndSet(false, true)) { toast("Another task is running, please wait."); return }
        status("Collecting " + c.name + "...")
        Thread {
            try {
                val files = p.root.walkTopDown().filter {
                    it.isFile && Cleaner.categoryFor(it, p.root)?.first == c.name
                }.toList()
                runOnUiThread { MediaViews.gallery(this, c.name + " media", files, null) }
                log("Showing " + files.size + " " + c.name + " file(s).")
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
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
                    log("Scan: 0 categories in " + p.root.absolutePath + ". Tap 'Search entire storage' or 'Choose media folder manually' on the Dashboard.")
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
        val label = if (scope == Cleaner.SCOPE_RECEIVED) "received" else if (scope == Cleaner.SCOPE_SENT) "sent" else "all"
        confirmGo("Clean " + c.name + "?", (if (isDry()) "Dry run - nothing is deleted yet." else "Delete") + " the " + label + " files of " + c.name + "?") {
            runTask("Clean " + c.name) { pr ->
                val r = Cleaner.cleanFolder(c.folder, isDry(), scope)
                (if (isDry()) "Dry run: would delete " else "Deleted ") + r.count + " " + c.name + " file(s), " + Cleaner.humanSize(r.bytes) + " freed" + suffix()
            }
        }
    }

    private fun stepDays(delta: Int) {
        days = (days + delta).coerceIn(5, 365)
        daysLabel.text = days.toString() + " days"
    }

    private fun viewOldMedia() {
        val p = selected
        if (p == null) { toast("No WhatsApp profile selected."); return }
        if (!busy.compareAndSet(false, true)) { toast("Another task is running, please wait."); return }
        status("Collecting old media...")
        Thread {
            try {
                val cutoff = System.currentTimeMillis() - days * 86_400_000L
                val files = p.root.walkTopDown().filter {
                    it.isFile && it.lastModified() < cutoff && Cleaner.categoryFor(it, p.root) != null
                }.toList()
                runOnUiThread { MediaViews.gallery(this, "Media older than " + days + " days", files, null) }
                log("Old media: showing " + files.size + " file(s) older than " + days + " days.")
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
    }

    private fun runCleanOld() {
        confirmGo("Clean old media", (if (isDry()) "Dry run - nothing is deleted yet." else "Delete") + " all WhatsApp media older than " + days + " days?") {
            runTask("Clean old media") { p ->
                val r = Cleaner.deleteOlderThan(p.root, days, isDry())
                (if (isDry()) "Dry run: would delete " else "Deleted ") + r.count + " old file(s), " + Cleaner.humanSize(r.bytes) + " freed" + suffix()
            }
        }
    }

    private fun runPruneBackups() {
        confirmGo("Prune chat backups", "Delete all msgstore backup files except the 5 newest? Your current chats are not touched.") {
            runTask("Prune chat backups") { p ->
                var dir = File(p.root, "Backups")
                if (!dir.isDirectory) dir = File(p.root, "Databases")
                val r = Cleaner.pruneBackups(dir, 5, isDry())
                (if (isDry()) "Dry run: would delete " else "Deleted ") + r.count + " backup file(s), " + Cleaner.humanSize(r.bytes) + " freed" + suffix()
            }
        }
    }

    private fun runRemoveEmpty() {
        confirmGo("Remove empty folders", "Delete left-over folders with no real content?") {
            runTask("Remove empty folders") { p ->
                if (isDry()) {
                    val list = Cleaner.listEmptyDirs(p.root)
                    "Dry run: would remove " + list.size + " empty folder(s)."
                } else {
                    val removed = Cleaner.removeEmptyDirs(p.root)
                    "Removed " + removed.size + " empty folder(s)."
                }
            }
        }
    }

    private fun runDuplicates() {
        if (!hasStorageAccess()) { toast("Grant storage permission first."); return }
        val p = selected
        if (p == null) { toast("No WhatsApp profile selected - wait for the profile list to load."); return }
        if (!busy.compareAndSet(false, true)) { toast("Another task is running, please wait."); return }
        status("Hashing media for duplicates...")
        Thread {
            try {
                val groups = Cleaner.findDuplicateGroups(p.root)
                dupGroups = groups
                runOnUiThread {
                    if (groups.isEmpty()) {
                        toast("No duplicates found.")
                        log("Duplicate scan: no identical copies found.")
                    } else {
                        MediaViews.duplicateCompare(this, groups, isDry()) { msg -> log(msg) }
                    }
                }
                if (groups.isNotEmpty()) log("Duplicate scan: " + groups.size + " group(s) of identical copies found - review them side by side.")
            } catch (e: Exception) {
                log("Duplicate scan error: " + e.message)
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
                val items = ExifRepair.scan(p.root)
                exifItems = items
                runOnUiThread {
                    if (items.isEmpty()) {
                        exifSummary.text = "All photo dates already look correct - nothing to repair."
                        exifViewBtn.visibility = View.GONE
                        exifApplyBtn.visibility = View.GONE
                    } else {
                        exifSummary.text = items.size.toString() + " photos need a date repair."
                        exifViewBtn.visibility = View.VISIBLE
                        exifApplyBtn.visibility = View.VISIBLE
                    }
                }
                log("EXIF scan: " + items.size + " photo(s) need a date repair.")
            } catch (e: Exception) {
                log("EXIF scan error: " + e.message)
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
    }

    private fun applyExif() {
        if (exifItems.isEmpty()) { toast("Scan for photos needing repair first."); return }
        confirmGo("Repair photo dates", (if (isDry()) "Dry run - nothing is changed yet." else "Write the correct capture date into") + " " + exifItems.size + " photo(s)?") {
            runTask("Repair photo dates") { p ->
                val n = ExifRepair.apply(exifItems, isDry())
                (if (isDry()) "Dry run: would repair " else "Repaired ") + n + " photo date(s)" + suffix()
            }
        }
    }

    private fun viewStatuses() {
        val p = selected
        if (p == null) { toast("No WhatsApp profile selected."); return }
        if (!busy.compareAndSet(false, true)) { toast("Another task is running, please wait."); return }
        status("Collecting statuses...")
        Thread {
            try {
                val dir = File(p.root, ".Statuses")
                val files = if (dir.isDirectory) dir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") } ?: emptyList() else emptyList()
                runOnUiThread { MediaViews.gallery(this, "Current statuses", files, null) }
                log("Statuses: showing " + files.size + " file(s).")
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
    }

    private fun runSaveStatuses() {
        confirmGo("Save statuses", "Copy the current 24-hour statuses into WMSuite-Organized/Status Saver?") {
            runTask("Save statuses") { p ->
                val target = File(Environment.getExternalStorageDirectory(), "WMSuite-Organized/Status Saver")
                val r = Cleaner.saveStatuses(File(p.root, ".Statuses"), target, isDry())
                (if (isDry()) "Dry run: would save " else "Saved ") + r.count + " status file(s) (" + Cleaner.humanSize(r.bytes) + ") into " + target.absolutePath + suffix()
            }
        }
    }

    private fun runOrganize() {
        confirmGo("Organize by date", (if (isDry()) "Dry run - nothing is copied yet." else "Copy") + " media into WMSuite-Organized/YYYY-MM/Category folders?") {
            runTask("Organize by date") { p ->
                val out = File(Environment.getExternalStorageDirectory(), "WMSuite-Organized/By date")
                val r = Cleaner.organizeByDate(p.root, out, isDry())
                (if (isDry()) "Dry run: would organize " else "Organized ") + r.count + " file(s) (" + Cleaner.humanSize(r.bytes) + ") by month into " + out.absolutePath + suffix()
            }
        }
    }

    private fun runFaceScan() {
        if (!hasStorageAccess()) { toast("Grant storage permission first."); return }
        val p = selected
        if (p == null) { toast("No WhatsApp profile selected - wait for the profile list to load."); return }
        if (!busy.compareAndSet(false, true)) { toast("Another task is running, please wait."); return }
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels
        status("Scanning photos for faces...")
        faceSummary.text = "Scanning - keep the app open..."
        Thread {
            try {
                val res = FaceSorter.scan(this, p.root, sw, sh) { done, total ->
                    if (done % 25 == 0 || done == total) status("Faces: " + done + " / " + total + " photos")
                }
                faceResult = res
                runOnUiThread { renderFaceResults() }
                log("Face scan: " + res.people.size + " people photo(s), " + res.screenshots.size + " screenshot(s), " + res.others.size + " non-human photo(s), " + res.errors + " unreadable.")
            } catch (e: Exception) {
                log("Face scan error: " + e.message)
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
    }

    private fun renderFaceResults() {
        val res = faceResult
        faceRows.removeAllViews()
        if (res == null) return
        faceRows.addView(faceRow("People (photos with faces)", res.people))
        faceRows.addView(faceRow("Screenshots", res.screenshots))
        faceRows.addView(faceRow("Non-human (no faces found)", res.others))
        val total = res.people.size + res.screenshots.size + res.others.size
        faceSummary.text = total.toString() + " photos scanned - " + res.cachedHits + " from cache, " + res.errors + " unreadable. Tap a row's View button for the gallery."
        facePreviewBtn.isEnabled = total > 0
        facePreviewBtn.alpha = if (total > 0) 1f else 0.5f
        faceGoBtn.isEnabled = total > 0
        faceGoBtn.alpha = if (total > 0) 1f else 0.5f
    }

    private fun faceRow(label: String, files: List<File>): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12), dp(10), dp(12), dp(10))
        row.background = roundedBg(COL_ROW, dp(10), COL_STROKE)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(6)
        row.layoutParams = lp
        val name = TextView(this)
        name.text = label
        name.textSize = 14f
        name.setTextColor(COL_TEXT)
        name.setTypeface(null, Typeface.BOLD)
        name.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(name)
        var bytes = 0L
        for (f in files) bytes += f.length()
        val count = TextView(this)
        count.text = files.size.toString() + " - " + Cleaner.humanSize(bytes)
        count.textSize = 12f
        count.setTextColor(COL_SUB)
        row.addView(count)
        val viewBtn = Button(this)
        viewBtn.text = "View"
        viewBtn.textSize = 12f
        viewBtn.isAllCaps = false
        viewBtn.setTextColor(Color.WHITE)
        viewBtn.backgroundTintList = ColorStateList.valueOf(COL_TEAL)
        viewBtn.setOnClickListener { MediaViews.gallery(this, label, if (files.size > 300) files.subList(0, 300) else files, null) }
        row.addView(viewBtn)
        return row
    }

    private fun runFaceOrganize(preview: Boolean) {
        val res = faceResult
        val total = if (res == null) 0 else res.people.size + res.screenshots.size + res.others.size
        if (res == null || total == 0) {
            toast("Scan your photos first.")
            return
        }
        val move = moveFaceCheck.isChecked
        val outRoot = File(Environment.getExternalStorageDirectory(), "WMSuite-Organized/By faces")
        val byKind = mapOf(
            FaceSorter.KIND_PEOPLE to res.people,
            FaceSorter.KIND_SCREENSHOT to res.screenshots,
            FaceSorter.KIND_OTHER to res.others
        )
        if (preview) {
            runTask("Preview organize by faces") { p ->
                val r = FaceSorter.organize(byKind, outRoot, move, true) { m -> log(m) }
                "Dry run: would " + (if (move) "move" else "copy") + " " + r.count + " photo(s) (" + Cleaner.humanSize(r.bytes) + ") into People / Screenshots / Non-human."
            }
        } else {
            if (isDry()) {
                toast("DRY RUN is ON - previewing instead. Turn it off to organize for real.")
                runFaceOrganize(true)
                return
            }
            confirmGo("Organize by faces", (if (move) "MOVE" else "COPY") + " " + total + " photo(s) into People / Screenshots / Non-human folders under WMSuite-Organized/By faces?") {
                runTask("Organize by faces") { p ->
                    val r = FaceSorter.organize(byKind, outRoot, move, false) { m -> log(m) }
                    "Organized " + r.count + " photo(s) (" + Cleaner.humanSize(r.bytes) + ") into WMSuite-Organized/By faces."
                }
            }
        }
    }

    private fun loadChatMap() {
        if (!hasStorageAccess()) { toast("Grant storage permission first."); return }
        val p = selected
        if (p == null) { toast("No WhatsApp profile selected - wait for the profile list to load."); return }
        if (!busy.compareAndSet(false, true)) { toast("Another task is running, please wait."); return }
        val key = keyInput.text.toString().trim()
        getSharedPreferences("wmsuite", MODE_PRIVATE).edit().putString("cryptKey", key).apply()
        val useNames = useNamesCheck.isChecked
        if (useNames && checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            busy.set(false)
            pendingChatMap = true
            requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), 7100)
            return
        }
        status("Building the chat map...")
        Thread {
            try {
                val info = ContactOrganizer.buildMap(this, p.root, key, useNames) { m -> log(m) }
                if (info == null) {
                    runOnUiThread { chatStatus.text = "Could not build the chat map - check the activity log." }
                    return@Thread
                }
                ContactOrganizer.cleanupDecrypted(this)
                val byLabel = LinkedHashMap<String, MutableList<File>>()
                val queue = ArrayDeque<File>()
                queue.add(p.root)
                var visited = 0
                while (queue.isNotEmpty() && visited < 40000) {
                    val dir = queue.removeFirst()
                    val kids = try { dir.listFiles() } catch (e: Exception) { null } ?: continue
                    for (f in kids) {
                        if (f.isDirectory) {
                            queue.add(f)
                            visited++
                        } else {
                            val label = info.fileToLabel[f.name] ?: continue
                            if (Cleaner.categoryFor(f, p.root) == null) continue
                            byLabel.getOrPut(label) { mutableListOf() }.add(f)
                        }
                    }
                }
                chatMap = info.fileToLabel
                chatGroups = byLabel
                runOnUiThread { renderChatContacts() }
                log("Chat map ready: " + byLabel.size + " contact(s)/group(s), source: " + info.source + ".")
            } catch (e: Exception) {
                log("Chat map error: " + e.message)
            } finally {
                busy.set(false)
                status("Ready")
            }
        }.start()
    }

    private fun renderChatContacts() {
        chatContactsContainer.removeAllViews()
        if (chatGroups.isEmpty()) {
            chatStatus.text = "No mapped media found - check the activity log."
            return
        }
        var files = 0
        var bytes = 0L
        val sorted = chatGroups.entries.sortedByDescending { it.value.size }
        for (e in sorted) {
            files += e.value.size
            for (f in e.value) bytes += f.length()
            chatContactsContainer.addView(chatContactRow(e.key, e.value))
        }
        chatStatus.text = chatGroups.size.toString() + " contacts/groups - " + files + " files - " + Cleaner.humanSize(bytes) + " (source: chat backup)"
        chatPreviewBtn.isEnabled = true
        chatPreviewBtn.alpha = 1f
        chatGoBtn.isEnabled = true
        chatGoBtn.alpha = 1f
    }

    private fun chatContactRow(label: String, files: List<File>): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12), dp(10), dp(12), dp(10))
        row.background = roundedBg(COL_ROW, dp(10), COL_STROKE)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(6)
        row.layoutParams = lp
        val name = TextView(this)
        name.text = label
        name.textSize = 14f
        name.setTextColor(COL_TEXT)
        name.setTypeface(null, Typeface.BOLD)
        name.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(name)
        var bytes = 0L
        for (f in files) bytes += f.length()
        val count = TextView(this)
        count.text = files.size.toString() + " - " + Cleaner.humanSize(bytes)
        count.textSize = 12f
        count.setTextColor(COL_SUB)
        row.addView(count)
        val viewBtn = Button(this)
        viewBtn.text = "View"
        viewBtn.textSize = 12f
        viewBtn.isAllCaps = false
        viewBtn.setTextColor(Color.WHITE)
        viewBtn.backgroundTintList = ColorStateList.valueOf(COL_TEAL)
        viewBtn.setOnClickListener { MediaViews.gallery(this, label + " media", if (files.size > 300) files.subList(0, 300) else files, null) }
        row.addView(viewBtn)
        return row
    }

    private fun runChatOrganize(preview: Boolean) {
        val p = selected
        if (p == null) { toast("No WhatsApp profile selected - wait for the profile list to load."); return }
        val map = chatMap
        if (map == null) { toast("Load the chat map first."); return }
        val move = moveChatCheck.isChecked
        val outRoot = File(Environment.getExternalStorageDirectory(), "WMSuite-Organized/By contact")
        if (preview) {
            runTask("Preview organize by contact") { pr ->
                val r = ContactOrganizer.organize(pr.root, outRoot, map, move, true) { m -> log(m) }
                "Dry run: would " + (if (move) "move" else "copy") + " " + r.files + " file(s) (" + Cleaner.humanSize(r.bytes) + ") into " + r.folders + " contact folder(s). Skipped " + r.skipped + " unmapped file(s)."
            }
        } else {
            if (isDry()) {
                toast("DRY RUN is ON - previewing instead. Turn it off to organize for real.")
                runChatOrganize(true)
                return
            }
            confirmGo("Organize by contact", (if (move) "MOVE" else "COPY") + " the mapped media into folders named after each contact under WMSuite-Organized/By contact?") {
                runTask("Organize by contact") { pr ->
                    val r = ContactOrganizer.organize(pr.root, outRoot, map, move, false) { m -> log(m) }
                    "Organized " + r.files + " file(s) (" + Cleaner.humanSize(r.bytes) + ") into " + r.folders + " contact folder(s) under " + outRoot.absolutePath + ". Skipped " + r.skipped + " unmapped file(s)."
                }
            }
        }
    }

    private fun findExports() {
        if (!hasStorageAccess()) { toast("Grant storage permission first."); return }
        val folderName = exportFolderInput.text.toString().trim()
        if (folderName.isEmpty()) { toast("Type the folder where the exported chat ZIPs are (for example Download)."); return }
        val dir = File(Environment.getExternalStorageDirectory(), folderName)
        if (!dir.isDirectory) { toast("Folder not found: " + dir.absolutePath); return }
        exportsContainer.removeAllViews()
        val exports = ChatExportOrganizer.findExports(dir)
        if (exports.isEmpty()) {
            toast("No chat exports found in " + folderName + ".")
            return
        }
        for (e in exports) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(12), dp(10), dp(12), dp(10))
            row.background = roundedBg(COL_ROW, dp(10), COL_STROKE)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(6)
            row.layoutParams = lp
            val name = TextView(this)
            name.text = e.contact
            name.textSize = 14f
            name.setTextColor(COL_TEXT)
            name.setTypeface(null, Typeface.BOLD)
            name.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(name)
            val importBtn = Button(this)
            importBtn.text = "Import"
            importBtn.textSize = 12f
            importBtn.isAllCaps = false
            importBtn.setTextColor(Color.WHITE)
            importBtn.backgroundTintList = ColorStateList.valueOf(COL_TEAL)
            importBtn.setOnClickListener { importOne(e.zip) }
            row.addView(importBtn)
            exportsContainer.addView(row)
        }
        log("Found " + exports.size + " chat export(s) in " + folderName + ".")
    }

    private fun importOne(zip: File) {
        val outRoot = File(Environment.getExternalStorageDirectory(), "WMSuite-Organized")
        confirmGo("Import chat export", (if (isDry()) "Dry run - nothing is copied yet." else "Copy the media of this export") + " into " + outRoot.absolutePath + "?") {
            runTask("Import chat export") { p ->
                val r = ChatExportOrganizer.importExport(zip, outRoot, isDry())
                (if (isDry()) "Dry run: would copy " else "Copied ") + r.count + " file(s) (" + Cleaner.humanSize(r.bytes) + ") from the export of " + ChatExportOrganizer.contactName(zip) + suffix()
            }
        }
    }
}
