package com.inshal.wmsuite

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.max

class MainActivity : Activity() {

    companion object {
        private val COL_BG = 0xFF0E1116.toInt()
        private val COL_CARD = 0xFF161B23.toInt()
        private val COL_CARD2 = 0xFF1C222D.toInt()
        private val COL_TEXT = 0xFFE8ECF1.toInt()
        private val COL_SUB = 0xFF97A5B4.toInt()
        private val COL_GREEN = 0xFF25D366.toInt()
        private val COL_DIM = 0xFF232B37.toInt()
        private val COL_RED = 0xFFFF8A80.toInt()
        private val COL_WARN = 0xFFF59E0B.toInt()
    }

    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private val pages = ArrayList<ScrollView>()
    private val navBtns = ArrayList<TextView>()
    private lateinit var dryBtn: TextView
    private lateinit var logPanel: LogPanel
    private lateinit var logChip: TextView
    private lateinit var permStatus: TextView
    private lateinit var profilesBox: LinearLayout
    private lateinit var cleanBox: LinearLayout
    private lateinit var toolsBox: LinearLayout
    private lateinit var exportsBox: LinearLayout
    private lateinit var keyInput: EditText
    private lateinit var useContactsCb: CheckBox
    private lateinit var moveCb: CheckBox
    private lateinit var mapStatus: TextView
    private lateinit var contactListBox: LinearLayout

    private var dryRun = true
    private var activeProfile: Profiles.WaProfile? = null
    private var contactMap: ContactOrganizer.MapInfo? = null
    private val scanStats = LinkedHashMap<String, Cleaner.CategoryStat>()
    private var exifItems: List<ExifRepair.Item> = emptyList()
    private var oldDays = 90
    private var useContacts = false

    private fun ext(): File = Environment.getExternalStorageDirectory()

    private fun prefs() = getSharedPreferences("wmsuite", MODE_PRIVATE)

    private fun customRootPath(): String? {
        val p = prefs().getString("customRoot", null)
        if (p != null && File(p).isDirectory) return p
        return null
    }

    private fun keyPref(): String = prefs().getString("e2eKey", "") ?: ""

    // ---------- UI helpers ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun roundedBg(color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radiusDp).toFloat()
        return g
    }

    private fun page(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.setPadding(dp(14), dp(10), dp(14), dp(10))
        return p
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = roundedBg(COL_CARD, 12)
        c.setPadding(dp(14), dp(14), dp(14), dp(14))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(12)
        c.layoutParams = lp
        return c
    }

    private fun tv(text: String, size: Float, color: Int): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        return t
    }

    private fun titleRow(text: String): TextView {
        val t = tv(text, 15f, COL_TEXT)
        t.typeface = Typeface.DEFAULT_BOLD
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(6)
        t.layoutParams = lp
        return t
    }

    private fun btn(label: String, onClick: (View) -> Unit): TextView {
        val b = TextView(this)
        b.text = label
        b.textSize = 13f
        b.typeface = Typeface.DEFAULT_BOLD
        b.setTextColor(Color.WHITE)
        b.background = roundedBg(COL_GREEN, 18)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(14), dp(10), dp(14), dp(10))
        b.setOnClickListener(onClick)
        return b
    }

    private fun btn2(label: String, onClick: (View) -> Unit): TextView {
        val b = TextView(this)
        b.text = label
        b.textSize = 13f
        b.typeface = Typeface.DEFAULT_BOLD
        b.setTextColor(COL_TEXT)
        b.background = roundedBg(COL_DIM, 18)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(14), dp(10), dp(14), dp(10))
        b.setOnClickListener(onClick)
        return b
    }

    private fun rowH(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun weighted(v: View): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun spacer(): View {
        val s = View(this)
        s.layoutParams = LinearLayout.LayoutParams(dp(8), 1)
        return s
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun log(msg: String) {
        logPanel.append(msg)
    }

    private fun titleFor(stat: Cleaner.CategoryStat): String {
        return stat.name + "  (" + Cleaner.humanSize(stat.bytes) + ")"
    }

    // ---------- lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dryRun = prefs().getBoolean("dryRun", true)

        val shell = LinearLayout(this)
        shell.orientation = LinearLayout.VERTICAL
        shell.setBackgroundColor(COL_BG)
        shell.addView(buildHeader())
        shell.addView(buildNav())
        content = FrameLayout(this)
        shell.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val labels = arrayOf("Dashboard", "Clean", "Tools", "Chats")
        for (i in labels.indices) {
            val scroll = ScrollView(this)
            scroll.setFillViewport(true)
            val p = page()
            if (i == 0) buildDashboard(p)
            if (i == 1) buildClean(p)
            if (i == 2) buildTools(p)
            if (i == 3) buildChats(p)
            scroll.addView(p)
            scroll.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            scroll.visibility = View.GONE
            content.addView(scroll)
            pages.add(scroll)
        }

        root = FrameLayout(this)
        root.addView(shell, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        logPanel = LogPanel(this)
        logPanel.onClosed = { logChip.visibility = View.VISIBLE }
        root.addView(logPanel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        logChip = TextView(this)
        logChip.text = "Activity log"
        logChip.textSize = 12f
        logChip.typeface = Typeface.DEFAULT_BOLD
        logChip.setTextColor(Color.WHITE)
        logChip.background = roundedBg(0xFF1F6FEB.toInt(), 20)
        logChip.setPadding(dp(16), dp(10), dp(16), dp(10))
        val chipLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
        chipLp.marginEnd = dp(16)
        chipLp.bottomMargin = dp(16)
        logChip.layoutParams = chipLp
        logChip.visibility = View.GONE
        logChip.setOnClickListener {
            logChip.visibility = View.GONE
            logPanel.showPanel()
        }
        root.addView(logChip)

        setContentView(root)
        selectTab(0)
        refreshPermissionCard()
        refreshProfiles()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionCard()
        if (hasStorageAccess() && profilesBox.childCount == 0) refreshProfiles()
    }

    private fun buildHeader(): View {
        val h = LinearLayout(this)
        h.orientation = LinearLayout.VERTICAL
        h.setPadding(dp(18), dp(14), dp(18), dp(8))
        val title = tv("WhatsApp Media Suite", 22f, COL_TEXT)
        title.typeface = Typeface.DEFAULT_BOLD
        h.addView(title)
        h.addView(tv("v2.2.0 - manage, preview and organize your media", 12f, COL_SUB))
        return h
    }

    private fun buildNav(): View {
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setPadding(dp(14), 0, dp(14), dp(8))
        val labels = arrayOf("Dashboard", "Clean", "Tools", "Chats")
        for (i in labels.indices) {
            val b = TextView(this)
            b.text = labels[i]
            b.textSize = 12f
            b.typeface = Typeface.DEFAULT_BOLD
            b.gravity = Gravity.CENTER
            b.setPadding(dp(4), dp(10), dp(4), dp(10))
            b.setOnClickListener { selectTab(i) }
            nav.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            navBtns.add(b)
        }
        dryBtn = TextView(this)
        dryBtn.textSize = 10f
        dryBtn.typeface = Typeface.DEFAULT_BOLD
        dryBtn.gravity = Gravity.CENTER
        dryBtn.setPadding(dp(8), 0, dp(8), 0)
        val dlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
        dlp.leftMargin = dp(6)
        dryBtn.layoutParams = dlp
        dryBtn.setOnClickListener {
            dryRun = !dryRun
            prefs().edit().putBoolean("dryRun", dryRun).apply()
            refreshDryBtn()
            if (dryRun) toast("Dry-run mode ON - nothing will be deleted")
            else toast("Careful - dry-run OFF: actions now modify your files!")
        }
        nav.addView(dryBtn)
        refreshDryBtn()
        return nav
    }

    private fun refreshDryBtn() {
        dryBtn.text = if (dryRun) "DRY RUN: ON" else "DRY RUN: OFF"
        dryBtn.setTextColor(if (dryRun) COL_WARN else Color.WHITE)
        dryBtn.background = roundedBg(if (dryRun) 0xFF2E2410.toInt() else 0xFF7A1F1F.toInt(), 14)
    }

    private fun selectTab(i: Int) {
        for (j in pages.indices) pages[j].visibility = if (j == i) View.VISIBLE else View.GONE
        for (j in navBtns.indices) {
            val active = j == i
            navBtns[j].setTextColor(if (active) COL_GREEN else COL_SUB)
            navBtns[j].background = roundedBg(if (active) COL_CARD2 else COL_BG, 14)
        }
        if (i == 1) rebuildClean()
        if (i == 2) rebuildTools()
    }

    // ---------- permissions ----------

    private fun hasStorageAccess(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStorage() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:com.inshal.wmsuite")))
            } catch (e: Exception) {
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            refreshPermissionCard()
            refreshProfiles()
        }
        if (requestCode == 7100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            useContacts = true
            loadContactMap()
        }
    }

    private fun refreshPermissionCard() {
        val ok = hasStorageAccess()
        if (ok) {
            permStatus.text = "Granted - full storage access."
            permStatus.setTextColor(COL_GREEN)
        } else {
            permStatus.text = "Required: WhatsApp media lives in restricted folders. Allow All files access to this app."
            permStatus.setTextColor(COL_WARN)
        }
    }

    // ---------- task runner ----------

    private fun runTask(label: String, work: () -> Unit) {
        log("Started: " + label)
        toast(label)
        Thread {
            try {
                work()
                log("Done: " + label)
            } catch (e: Exception) {
                log("Error in " + label + ": " + (e.message ?: e.toString()))
            }
        }.start()
    }

    private fun confirmGo(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Continue") { d, w -> action() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dryNote(): String {
        return if (dryRun) "DRY RUN - nothing will be changed. " else "WARNING: this modifies your files permanently. "
    }

    // ---------- Dashboard ----------

    private fun buildDashboard(p: LinearLayout) {
        val c1 = card()
        c1.addView(titleRow("Storage access"))
        permStatus = tv("", 13f, COL_SUB)
        c1.addView(permStatus)
        c1.addView(btn("Grant access") { requestStorage() })
        p.addView(c1)

        val c2 = card()
        c2.addView(titleRow("WhatsApp profiles"))
        profilesBox = LinearLayout(this)
        profilesBox.orientation = LinearLayout.VERTICAL
        c2.addView(profilesBox)
        val r = rowH()
        r.addView(btn2("Refresh") { refreshProfiles() }, weighted(r))
        r.addView(spacer())
        r.addView(btn("Search entire storage") { deepSearchNow() }, weighted(r))
        c2.addView(r)
        val r2 = rowH()
        r2.addView(btn2("Choose media folder manually") { pickFolder() }, weighted(r2))
        c2.addView(r2)
        p.addView(c2)

        val c3 = card()
        c3.addView(titleRow("How it works"))
        val tips = listOf(
            "Everything is a dry run until you tap the DRY chip in the top bar.",
            "Pick a profile here, then scan it on the Clean tab.",
            "Every result has a View button - see the actual media before you delete anything.",
            "The activity log can be dragged, collapsed or closed from its header."
        )
        for (t in tips) c3.addView(bullet(t))
        p.addView(c3)
    }

    private fun bullet(text: String): TextView {
        val t = tv("- " + text, 12f, COL_SUB)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(4)
        t.layoutParams = lp
        return t
    }

    private fun refreshProfiles() {
        profilesBox.removeAllViews()
        if (!hasStorageAccess()) {
            profilesBox.addView(tv("Grant storage access first.", 13f, COL_SUB))
            return
        }
        runTask("Scanning for WhatsApp profiles") {
            val found = Profiles.withCustom(Profiles.detect(ext()), customRootPath())
            log("Found " + found.size + " profile(s)")
            runOnUiThread {
                profilesBox.removeAllViews()
                if (found.isEmpty()) {
                    profilesBox.addView(tv("No WhatsApp media found yet. Try Search entire storage, or pick the folder manually.", 13f, COL_SUB))
                }
                for (prof in found) profilesBox.addView(profileRow(prof))
            }
        }
    }

    private fun profileRow(prof: Profiles.WaProfile): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = roundedBg(COL_CARD2, 10)
        box.setPadding(dp(12), dp(10), dp(12), dp(10))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(8)
        box.layoutParams = lp
        val t1 = tv(prof.name, 14f, COL_TEXT)
        t1.typeface = Typeface.DEFAULT_BOLD
        box.addView(t1)
        box.addView(tv(prof.root.absolutePath, 11f, COL_SUB))
        box.addView(tv(prof.files.toString() + " files - " + Cleaner.humanSize(prof.bytes) + " - " + prof.ageLabel(), 12f, COL_SUB))
        val use = btn(if (activeProfile == prof) "SELECTED" else "Use this profile") { selectProfile(prof) }
        val ulp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        ulp.topMargin = dp(6)
        use.layoutParams = ulp
        box.addView(use)
        return box
    }

    private fun selectProfile(prof: Profiles.WaProfile) {
        activeProfile = prof
        log("Active profile: " + prof.name + "  [" + prof.root.absolutePath + "]")
        toast("Profile selected: " + prof.name)
    }

    private fun deepSearchNow() {
        if (!hasStorageAccess()) {
            toast("Grant storage access first")
            return
        }
        runTask("Deep storage search") {
            val found = Profiles.deepSearch(ext()) { m -> log(m) }
            log("Deep search finished: " + found.size + " candidate folder(s)")
            runOnUiThread {
                val merged = Profiles.withCustom(found, customRootPath())
                profilesBox.removeAllViews()
                if (merged.isEmpty()) profilesBox.addView(tv("Still nothing found. Use Choose media folder manually.", 13f, COL_SUB))
                for (prof in merged) profilesBox.addView(profileRow(prof))
            }
        }
    }

    private fun pickFolder() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 7001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val treeUri = data.data ?: return
        if (requestCode == 7001) {
            val path = treeUriToPath(treeUri)
            if (path == null) {
                toast("Could not resolve that folder")
                return
            }
            val f = File(path)
            if (f.absolutePath == ext().absolutePath) {
                toast("That is the whole storage root - pick the WhatsApp media folder instead")
                return
            }
            prefs().edit().putString("customRoot", path).apply()
            toast("Folder saved")
            log("Manual media folder: " + path)
            refreshProfiles()
        } else if (requestCode == 7002) {
            val path = treeUriToPath(treeUri)
            if (path == null) {
                toast("Could not resolve that folder")
                return
            }
            prefs().edit().putString("exportsDir", path).apply()
            refreshExports(path)
        }
    }

    private fun treeUriToPath(treeUri: Uri): String? {
        return try {
            if (treeUri.authority == "com.android.externalstorage.documents") {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val parts = docId.split(":")
                if (parts.size >= 2 && parts[0].contains("primary")) {
                    Environment.getExternalStorageDirectory().absolutePath + "/" + parts[1]
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Clean tab ----------

    private fun buildClean(p: LinearLayout) {
        cleanBox = LinearLayout(this)
        cleanBox.orientation = LinearLayout.VERTICAL
        p.addView(cleanBox)
        rebuildClean()
    }

    private fun rebuildClean() {
        cleanBox.removeAllViews()
        val prof = activeProfile
        if (prof == null) {
            val c = card()
            c.addView(titleRow("No profile selected"))
            c.addView(tv("Go to the Dashboard tab and pick a WhatsApp profile, or search your storage for it.", 13f, COL_SUB))
            cleanBox.addView(c)
            return
        }
        val hc = card()
        hc.addView(titleRow("Profile: " + prof.name))
        hc.addView(tv(prof.root.absolutePath, 11f, COL_SUB))
        hc.addView(tv(prof.files.toString() + " files - " + Cleaner.humanSize(prof.bytes), 12f, COL_SUB))
        val slp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        slp.topMargin = dp(8)
        val scanBtn = btn("Scan media") { runScan() }
        scanBtn.layoutParams = slp
        hc.addView(scanBtn)
        cleanBox.addView(hc)

        for (stat in scanStats.values) cleanBox.addView(catCard(stat))
        if (scanStats.isNotEmpty()) {
            cleanBox.addView(ageCard(prof))
            cleanBox.addView(backupsCard(prof))
            cleanBox.addView(emptyCard(prof))
        }
    }

    private fun runScan() {
        val prof = activeProfile ?: return
        runTask("Scanning " + prof.name) {
            val stats = Cleaner.scan(prof.root)
            scanStats.clear()
            var total = 0L
            var files = 0
            for (s in stats) {
                scanStats[s.name] = s
                total += s.bytes
                files += s.files
            }
            log("Scan finished: " + stats.size + " categories, " + files + " files, " + Cleaner.humanSize(total))
            if (stats.isEmpty()) log("Nothing recognized - is this a WhatsApp media folder?")
            runOnUiThread { rebuildClean() }
        }
    }

    private fun catCard(stat: Cleaner.CategoryStat): View {
        val c = card()
        c.addView(titleRow(titleFor(stat)))
        c.addView(tv(stat.files.toString() + " files - received " + (stat.files - stat.sentFiles) + ", sent " + stat.sentFiles, 12f, COL_SUB))
        val r = rowH()
        r.addView(btn2("View media") { viewMedia(stat) }, weighted(r))
        r.addView(spacer())
        r.addView(btn("Clean") { scopeDialog(stat) }, weighted(r))
        c.addView(r)
        return c
    }

    private fun viewMedia(stat: Cleaner.CategoryStat) {
        runTask("Loading " + stat.name) {
            val files = ArrayList<File>()
            stat.folder.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.forEach {
                if (files.size < 300) files.add(it)
            }
            runOnUiThread {
                if (files.isEmpty()) toast("No files here")
                else MediaViews.gallery(this@MainActivity, stat.name, files, stat.folder.absolutePath)
            }
        }
    }

    private fun scopeDialog(stat: Cleaner.CategoryStat) {
        val options = arrayOf(
            "Everything (" + stat.files + " files)",
            "Received only (" + (stat.files - stat.sentFiles) + " files)",
            "Sent only (" + stat.sentFiles + " files)"
        )
        AlertDialog.Builder(this)
            .setTitle("Clean " + stat.name + " - what should go?")
            .setItems(options) { d, which -> confirmClean(stat, which) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClean(stat: Cleaner.CategoryStat, scope: Int) {
        val scopeName = if (scope == Cleaner.SCOPE_RECEIVED) "received media" else if (scope == Cleaner.SCOPE_SENT) "sent media" else "everything"
        confirmGo("Clean " + stat.name, dryNote() + "Delete " + scopeName + " from " + stat.name + "?") { runClean(stat, scope) }
    }

    private fun runClean(stat: Cleaner.CategoryStat, scope: Int) {
        runTask((if (dryRun) "Dry-run clean " else "Clean ") + stat.name) {
            val res = Cleaner.cleanFolder(stat.folder, dryRun, scope)
            log((if (dryRun) "Would delete " else "Deleted ") + res.count + " files (" + Cleaner.humanSize(res.bytes) + ") from " + stat.name)
            if (!dryRun) log("Tip: run Scan media again to refresh the numbers.")
        }
    }

    private fun ageCard(prof: Profiles.WaProfile): View {
        val c = card()
        c.addView(titleRow("Old media cleanup"))
        c.addView(tv("Remove media older than a number of days - the classic WhatsApp space saver.", 12f, COL_SUB))
        val daysLabel = tv(oldDays.toString() + " days", 14f, COL_TEXT)
        daysLabel.gravity = Gravity.CENTER
        val minus = btn2(" - ") { oldDays = max(7, oldDays - 30); daysLabel.text = oldDays.toString() + " days" }
        val plus = btn2(" + ") { oldDays = oldDays + 30; daysLabel.text = oldDays.toString() + " days" }
        val r = rowH()
        r.gravity = Gravity.CENTER_VERTICAL
        r.addView(minus, weighted(r))
        r.addView(daysLabel, weighted(r))
        r.addView(plus, weighted(r))
        c.addView(r)
        val r2 = rowH()
        r2.addView(btn2("Preview old media") { previewOld(prof) }, weighted(r2))
        r2.addView(spacer())
        r2.addView(btn("Delete old media") { confirmOld(prof) }, weighted(r2))
        c.addView(r2)
        return c
    }

    private fun previewOld(prof: Profiles.WaProfile) {
        runTask("Finding old media") {
            val cutoff = System.currentTimeMillis() - oldDays * 86_400_000L
            val files = ArrayList<File>()
            prof.root.walkTopDown().filter { f ->
                f.isFile && !f.name.startsWith(".") && f.lastModified() < cutoff && Cleaner.categoryFor(f, prof.root) != null
            }.forEach {
                if (files.size < 300) files.add(it)
            }
            runOnUiThread {
                if (files.isEmpty()) toast("Nothing older than " + oldDays + " days")
                else MediaViews.gallery(this@MainActivity, "Older than " + oldDays + " days", files, "preview before deleting")
            }
        }
    }

    private fun confirmOld(prof: Profiles.WaProfile) {
        confirmGo("Delete old media", dryNote() + "Delete media older than " + oldDays + " days?") {
            runTask((if (dryRun) "Dry-run delete old media" else "Deleting old media")) {
                val res = Cleaner.deleteOlderThan(prof.root, oldDays, dryRun)
                log((if (dryRun) "Would delete " else "Deleted ") + res.count + " old files (" + Cleaner.humanSize(res.bytes) + ")")
            }
        }
    }

    private fun backupsCard(prof: Profiles.WaProfile): View {
        val c = card()
        c.addView(titleRow("Chat backup pruning"))
        c.addView(tv("WhatsApp piles up msgstore backups. This keeps the newest 5 in Databases and Backups and deletes the rest.", 12f, COL_SUB))
        val pruneBtn = btn("Prune old backups") {
            confirmGo("Prune old backups", dryNote() + "Delete every msgstore backup except the newest 5?") {
                runTask((if (dryRun) "Dry-run prune backups" else "Pruning backups")) {
                    var count = 0
                    var bytes = 0L
                    for (dir in listOf(File(prof.root, "Databases"), File(prof.root, "Backups"))) {
                        val res = Cleaner.pruneBackups(dir, 5, dryRun)
                        count += res.count
                        bytes += res.bytes
                    }
                    log((if (dryRun) "Would delete " else "Deleted ") + count + " old backups (" + Cleaner.humanSize(bytes) + "), keeping the newest 5.")
                }
            }
        }
        c.addView(pruneBtn)
        return c
    }

    private fun emptyCard(prof: Profiles.WaProfile): View {
        val c = card()
        c.addView(titleRow("Empty folder cleanup"))
        c.addView(tv("Finds folders left behind by WhatsApp, for example after cleaning.", 12f, COL_SUB))
        val r = rowH()
        r.addView(btn2("List empty folders") {
            runTask("Listing empty folders") {
                val dirs = Cleaner.listEmptyDirs(prof.root)
                log("Empty folders found: " + dirs.size)
                var i = 0
                for (d in dirs) {
                    if (i >= 20) break
                    i++
                    log("  - " + d.absolutePath)
                }
                if (dirs.isEmpty()) log("No empty folders.")
            }
        }, weighted(r))
        r.addView(spacer())
        r.addView(btn("Remove empty folders") {
            confirmGo("Remove empty folders", dryNote() + "Delete every now-empty folder inside the WhatsApp folders?") {
                runTask((if (dryRun) "Dry-run remove empty folders" else "Removing empty folders")) {
                    if (dryRun) {
                        val dirs = Cleaner.listEmptyDirs(prof.root)
                        log("Dry run: would remove " + dirs.size + " empty folders.")
                    } else {
                        val removed = Cleaner.removeEmptyDirs(prof.root)
                        log("Removed " + removed.size + " empty folders.")
                    }
                }
            }
        }, weighted(r))
        c.addView(r)
        return c
    }

    // ---------- Tools tab ----------

    private fun buildTools(p: LinearLayout) {
        toolsBox = LinearLayout(this)
        toolsBox.orientation = LinearLayout.VERTICAL
        p.addView(toolsBox)
        rebuildTools()
    }

    private fun rebuildTools() {
        toolsBox.removeAllViews()
        val prof = activeProfile
        if (prof == null) {
            val c = card()
            c.addView(titleRow("No profile selected"))
            c.addView(tv("Pick a WhatsApp profile on the Dashboard tab first.", 13f, COL_SUB))
            toolsBox.addView(c)
            return
        }
        toolsBox.addView(dupCard(prof))
        toolsBox.addView(exifCard(prof))
        toolsBox.addView(statusCard(prof))
        toolsBox.addView(organizeCard(prof))
    }

    private fun dupCard(prof: Profiles.WaProfile): View {
        val c = card()
        c.addView(titleRow("Duplicate finder"))
        c.addView(tv("Finds files with identical content and shows them side by side, so you can compare with your own eyes before deleting. The earliest copy is always marked as the original.", 12f, COL_SUB))
        c.addView(btn("Find duplicates") { runDuplicates(prof) })
        return c
    }

    private fun runDuplicates(prof: Profiles.WaProfile) {
        runTask("Finding duplicates") {
            val groups = Cleaner.findDuplicateGroups(prof.root)
            log("Duplicate scan: " + groups.size + " groups")
            runOnUiThread {
                if (groups.isEmpty()) {
                    toast("No duplicates found")
                    log("No duplicates found - nothing to clean.")
                } else {
                    MediaViews.duplicateCompare(this@MainActivity, groups, dryRun) { msg ->
                        log(msg)
                        toast(msg)
                    }
                }
            }
        }
    }

    private fun exifCard(prof: Profiles.WaProfile): View {
        val c = card()
        c.addView(titleRow("Gallery date repair (EXIF)"))
        c.addView(tv("WhatsApp strips the capture date from photos. This reads the date from each filename and writes it back, so your gallery sorts photos correctly again.", 12f, COL_SUB))
        c.addView(btn("Scan photos") { runExifScan(prof) })
        if (exifItems.isNotEmpty()) {
            c.addView(tv("Last scan: " + exifItems.size + " photos need a date repair.", 12f, COL_GREEN))
            val r = rowH()
            r.addView(btn2("Preview photos") { previewExif() }, weighted(r))
            r.addView(spacer())
            r.addView(btn("Repair dates") { confirmExif() }, weighted(r))
            c.addView(r)
        }
        return c
    }

    private fun runExifScan(prof: Profiles.WaProfile) {
        runTask("Scanning photo dates") {
            val items = ExifRepair.scan(prof.root)
            exifItems = items
            log("EXIF scan: " + items.size + " photos have a missing or wrong capture date.")
            runOnUiThread { rebuildTools() }
        }
    }

    private fun previewExif() {
        val files = ArrayList<File>()
        for (item in exifItems) {
            if (files.size < 300) files.add(item.file)
        }
        if (files.isEmpty()) {
            toast("Nothing to preview")
            return
        }
        MediaViews.gallery(this, "Photos needing date repair", files, null)
    }

    private fun confirmExif() {
        confirmGo("Repair photo dates", dryNote() + "Write the filename date into " + exifItems.size + " photos?") {
            runTask((if (dryRun) "Dry-run EXIF repair" else "EXIF repair")) {
                val n = ExifRepair.apply(exifItems, dryRun)
                log((if (dryRun) "Would repair " else "Repaired ") + n + " photos.")
            }
        }
    }

    private fun statusCard(prof: Profiles.WaProfile): View {
        val c = card()
        c.addView(titleRow("Status saver"))
        c.addView(tv("Save the current statuses of your contacts (they disappear after 24h) into Download/WMSuite Statuses, grouped by day.", 12f, COL_SUB))
        val dir = File(prof.root, ".Statuses")
        val r = rowH()
        r.addView(btn2("View statuses") { viewStatuses(dir) }, weighted(r))
        r.addView(spacer())
        r.addView(btn("Save statuses") {
            confirmGo("Save statuses", "Copy the current statuses into Download/WMSuite Statuses?") {
                runTask((if (dryRun) "Dry-run save statuses" else "Saving statuses")) {
                    val target = File(File(ext(), "Download"), "WMSuite Statuses")
                    val res = Cleaner.saveStatuses(dir, target, dryRun)
                    log((if (dryRun) "Would save " else "Saved ") + res.count + " statuses (" + Cleaner.humanSize(res.bytes) + ") to " + target.absolutePath)
                }
            }
        }, weighted(r))
        c.addView(r)
        return c
    }

    private fun viewStatuses(dir: File) {
        runTask("Loading statuses") {
            val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
            runOnUiThread {
                if (files.isEmpty()) toast("No statuses available right now")
                else MediaViews.gallery(this@MainActivity, "Statuses", files, "expires after 24h")
            }
        }
    }

    private fun organizeCard(prof: Profiles.WaProfile): View {
        val c = card()
        c.addView(titleRow("Organize by month"))
        c.addView(tv("Moves media into yyyy-mm/<category> folders under WMSuite-Organized - handy before copying media to a computer.", 12f, COL_SUB))
        c.addView(btn("Organize by date") {
            confirmGo("Organize by date", dryNote() + "Sort media into monthly folders?") {
                runTask((if (dryRun) "Dry-run organize by date" else "Organizing by date")) {
                    val out = File(ext(), "WMSuite-Organized")
                    val res = Cleaner.organizeByDate(prof.root, out, dryRun)
                    log((if (dryRun) "Would move " else "Moved ") + res.count + " files (" + Cleaner.humanSize(res.bytes) + ") into " + out.absolutePath)
                }
            }
        })
        return c
    }

    // ---------- Chats tab ----------

    private fun buildChats(p: LinearLayout) {
        val c = card()
        c.addView(titleRow("Organize media by contact - no chat export needed"))
        c.addView(tv("This reads the encrypted WhatsApp chat backup that is already on your phone (Databases folder) and maps every media file to the chat it belongs to - like the desktop tools wa-sort-media and whatskeep, but fully on your device and offline.", 12f, COL_SUB))
        c.addView(tv("One-time setup: in WhatsApp open Settings > Chats > Chat backup > End-to-end encrypted backup, turn it ON, choose the 64-digit key option, write the key down, then tap Back up now. Then paste that key below.", 12f, COL_SUB))
        keyInput = EditText(this)
        keyInput.hint = "64-digit key (a-z 0-9)"
        keyInput.setText(keyPref())
        keyInput.textSize = 13f
        keyInput.background = roundedBg(COL_CARD2, 8)
        keyInput.setPadding(dp(10), dp(8), dp(10), dp(8))
        val klp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        klp.topMargin = dp(6)
        klp.bottomMargin = dp(6)
        keyInput.layoutParams = klp
        c.addView(keyInput)
        useContactsCb = CheckBox(this)
        useContactsCb.text = "Use phone contact names (needs Contacts permission)"
        useContactsCb.textSize = 12f
        useContactsCb.setTextColor(COL_SUB)
        c.addView(useContactsCb)
        moveCb = CheckBox(this)
        moveCb.text = "Move files instead of copying (faster, but removes them from WhatsApp folders)"
        moveCb.textSize = 12f
        moveCb.setTextColor(COL_SUB)
        c.addView(moveCb)
        c.addView(btn("Load chat map") { loadContactMap() })
        mapStatus = tv("", 12f, COL_SUB)
        c.addView(mapStatus)
        contactListBox = LinearLayout(this)
        contactListBox.orientation = LinearLayout.VERTICAL
        c.addView(contactListBox)
        val r = rowH()
        r.addView(btn2("Preview (dry run)") { organizeContacts(true) }, weighted(r))
        r.addView(spacer())
        r.addView(btn("Organize now") { organizeContacts(false) }, weighted(r))
        c.addView(r)
        p.addView(c)

        val c2 = card()
        c2.addView(titleRow("Fallback: organize exported chats"))
        c2.addView(tv("If you prefer not to use the encrypted backup: export chats from WhatsApp (contact chat > menu > More > Export chat > Include media), put the ZIPs in one folder, and choose it below. Media gets copied into WMSuite-Organized/Conversations/<contact>.", 12f, COL_SUB))
        c2.addView(btn2("Choose exports folder") { pickExportsFolder() })
        exportsBox = LinearLayout(this)
        exportsBox.orientation = LinearLayout.VERTICAL
        val elp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        elp.topMargin = dp(6)
        exportsBox.layoutParams = elp
        c2.addView(exportsBox)
        p.addView(c2)
    }

    private fun loadContactMap() {
        val prof = activeProfile
        if (prof == null) {
            toast("Pick a WhatsApp profile on the Dashboard tab first")
            return
        }
        val key = keyInput.text.toString().trim()
        prefs().edit().putString("e2eKey", key).apply()
        useContacts = useContactsCb.isChecked
        if (useContacts && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 7100)
            return
        }
        runTask("Loading chat map") {
            val map = ContactOrganizer.buildMap(this@MainActivity, prof.root, key, useContacts) { m -> log(m) }
            contactMap = map
            runOnUiThread {
                if (map == null) {
                    mapStatus.text = "Could not load the chat map - see the activity log for details."
                    mapStatus.setTextColor(COL_RED)
                    contactListBox.removeAllViews()
                } else {
                    mapStatus.text = "Loaded " + map.fileToLabel.size + " media references from the " + map.source + "."
                    mapStatus.setTextColor(COL_GREEN)
                    renderContactList(map)
                }
            }
        }
    }

    private fun renderContactList(map: ContactOrganizer.MapInfo) {
        contactListBox.removeAllViews()
        val byLabel = LinkedHashMap<String, Int>()
        for (label in map.fileToLabel.values) {
            val n = byLabel[label] ?: 0
            byLabel[label] = n + 1
        }
        val sorted = byLabel.entries.sortedByDescending { it.value }
        var i = 0
        for (entry in sorted) {
            if (i >= 60) break
            i++
            val row = TextView(this)
            row.text = entry.key + "  -  " + entry.value + " files"
            row.textSize = 13f
            row.setTextColor(COL_TEXT)
            row.background = roundedBg(COL_CARD2, 8)
            row.setPadding(dp(10), dp(8), dp(10), dp(8))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(6)
            row.layoutParams = lp
            row.setOnClickListener { previewContact(map, entry.key) }
            contactListBox.addView(row)
        }
        if (sorted.size > 60) {
            contactListBox.addView(tv("... and " + (sorted.size - 60) + " more contacts. Tap Preview to see the full dry run in the log.", 12f, COL_SUB))
        }
    }

    private fun previewContact(map: ContactOrganizer.MapInfo, label: String) {
        val prof = activeProfile ?: return
        runTask("Loading " + label) {
            val files = ArrayList<File>()
            prof.root.walkTopDown().filter { it.isFile && map.fileToLabel[it.name] == label }.forEach {
                if (files.size < 300) files.add(it)
            }
            runOnUiThread {
                if (files.isEmpty()) toast("No media files found for this contact")
                else MediaViews.gallery(this@MainActivity, label, files, null)
            }
        }
    }

    private fun organizeContacts(forceDry: Boolean) {
        val prof = activeProfile
        val map = contactMap
        if (prof == null) {
            toast("Pick a WhatsApp profile on the Dashboard tab first")
            return
        }
        if (map == null) {
            toast("Load the chat map first")
            return
        }
        val dry = dryRun || forceDry
        val move = moveCb.isChecked
        val action = if (dry) "Preview the dry run" else if (move) "MOVE files into contact folders" else "Copy files into contact folders"
        confirmGo("Organize by contact", action + "? Files will be sorted into WMSuite-Organized/By contact/<contact or group>.") {
            runTask((if (dry) "Dry-run organize by contact" else "Organizing by contact")) {
                val out = File(File(ext(), "WMSuite-Organized"), "By contact")
                val res = ContactOrganizer.organize(prof.root, out, map.fileToLabel, move, dry) { m -> log(m) }
                log((if (dry) "Would sort " else "Sorted ") + res.files + " files into contact folders (" + res.skipped + " unmatched, " + Cleaner.humanSize(res.bytes) + " total).")
                if (!dry) {
                    ContactOrganizer.cleanupDecrypted(this@MainActivity)
                    log("Done - check WMSuite-Organized/By contact with your file manager.")
                }
            }
        }
    }

    private fun pickExportsFolder() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 7002)
    }

    private fun refreshExports(dirPath: String) {
        exportsBox.removeAllViews()
        val dir = File(dirPath)
        val exports = ChatExportOrganizer.findExports(dir)
        if (exports.isEmpty()) {
            exportsBox.addView(tv("No chat export ZIPs found in " + dirPath, 12f, COL_SUB))
            return
        }
        for (e in exports) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.background = roundedBg(COL_CARD2, 8)
            row.setPadding(dp(10), dp(6), dp(10), dp(6))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(6)
            row.layoutParams = lp
            row.addView(tv(e.contact + "  -  " + Cleaner.humanSize(e.zip.length()), 12f, COL_TEXT), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(btn2("Import") { importExport(e) })
            exportsBox.addView(row)
        }
    }

    private fun importExport(e: ChatExportOrganizer.Export) {
        confirmGo("Import " + e.contact, (if (dryRun) "DRY RUN. " else "Media will be copied into WMSuite-Organized/Conversations. ") + "Continue with " + e.zip.name + "?") {
            runTask("Importing " + e.contact) {
                val out = File(ext(), "WMSuite-Organized")
                val res = ChatExportOrganizer.importExport(e.zip, out, dryRun)
                log((if (dryRun) "Would import " else "Imported ") + res.count + " files (" + Cleaner.humanSize(res.bytes) + ") from " + e.zip.name)
            }
        }
    }
}