package com.inshal.wmsuite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Sorts photos into people / screenshots / non-human categories with
 * on-device face detection (Google ML Kit, bundled model - no internet
 * needed, nothing ever leaves the phone). Results are cached per file
 * (path + size + mtime), so rescans only process new photos.
 */
object FaceSorter {

    const val KIND_PEOPLE = 0
    const val KIND_SCREENSHOT = 1
    const val KIND_OTHER = 2

    class ScanResult(
        val people: MutableList<File>,
        val screenshots: MutableList<File>,
        val others: MutableList<File>,
        var errors: Int,
        var cachedHits: Int
    )

    private val detector: FaceDetector by lazy {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.1f)
            .build()
        FaceDetection.getClient(opts)
    }

    fun kindLabel(kind: Int): String = when (kind) {
        KIND_PEOPLE -> "People"
        KIND_SCREENSHOT -> "Screenshots"
        else -> "Non-human"
    }

    private fun cacheFile(context: Context): File = File(context.filesDir, "face_cache.json")

    private fun loadCache(context: Context): HashMap<String, Int> {
        val map = HashMap<String, Int>()
        try {
            val f = cacheFile(context)
            if (f.isFile) {
                val obj = JSONObject(f.readText())
                for (k in obj.keys()) map[k] = obj.getInt(k)
            }
        } catch (e: Exception) {
            // unreadable cache - start fresh
        }
        return map
    }

    private fun saveCache(context: Context, map: Map<String, Int>) {
        try {
            val obj = JSONObject()
            for ((k, v) in map) obj.put(k, v as Int)
            cacheFile(context).writeText(obj.toString())
        } catch (e: Exception) {
            // cache is best-effort
        }
    }

    private fun keyOf(f: File): String = f.absolutePath + "|" + f.length() + "|" + f.lastModified()

    fun isScreenshotName(name: String): Boolean {
        val n = name.lowercase()
        return n.contains("screenshot") || n.contains("screen_shot") ||
            n.contains("screen-shot") || n.contains("screen shot")
    }

    /** Screenshot heuristics: file name, parent folder, or exact screen dimensions. */
    private fun looksLikeScreenshot(f: File, w: Int, h: Int, screenW: Int, screenH: Int): Boolean {
        if (isScreenshotName(f.name)) return true
        val parent = f.parentFile
        if (parent != null && parent.name.lowercase().contains("screenshot")) return true
        if (screenW > 0 && screenH > 0 && w > 0 && h > 0) {
            if ((w == screenW && h == screenH) || (w == screenH && h == screenW)) return true
        }
        return false
    }

    private fun rotateByExif(f: File, bm: Bitmap): Bitmap {
        try {
            val deg = when (ExifInterface(f.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (deg == 0f) return bm
            val m = Matrix()
            m.postRotate(deg)
            return Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, m, true)
        } catch (e: Exception) {
            return bm
        }
    }

    private fun decodeScaled(f: File, maxDim: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options()
            opts.inJustDecodeBounds = true
            BitmapFactory.decodeFile(f.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            var s = 1
            while (opts.outWidth / (s * 2) >= maxDim || opts.outHeight / (s * 2) >= maxDim) s = s * 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            val bm = BitmapFactory.decodeFile(f.absolutePath, o2) ?: return null
            rotateByExif(f, bm)
        } catch (e: Exception) {
            null
        }
    }

    /** Blocking face check - call from a worker thread. */
    private fun hasFaces(bm: Bitmap): Boolean {
        val latch = CountDownLatch(1)
        var found = false
        try {
            detector.process(InputImage.fromBitmap(bm, 0))
                .addOnSuccessListener { faces ->
                    found = faces.isNotEmpty()
                    latch.countDown()
                }
                .addOnFailureListener { latch.countDown() }
        } catch (e: Exception) {
            latch.countDown()
        }
        latch.await(20, TimeUnit.SECONDS)
        return found
    }

    /**
     * Scans every photo under root: screenshots are recognized first,
     * the rest runs through face detection. onProgress gets
     * (done, total) so the UI can show progress.
     */
    fun scan(context: Context, root: File, screenW: Int, screenH: Int, onProgress: (Int, Int) -> Unit): ScanResult {
        val res = ScanResult(mutableListOf(), mutableListOf(), mutableListOf(), 0, 0)
        val cache = loadCache(context)
        val files = root.walkTopDown().filter {
            it.isFile && !it.name.startsWith(".") &&
                MediaViews.isImage(it) &&
                !it.extension.equals("gif", true) &&
                it.length() > 8192L
        }.toList()
        var done = 0
        for (f in files) {
            done++
            onProgress(done, files.size)
            val ck = keyOf(f)
            val hit = cache[ck]
            if (hit != null) {
                res.cachedHits++
                when (hit) {
                    KIND_PEOPLE -> res.people.add(f)
                    KIND_SCREENSHOT -> res.screenshots.add(f)
                    else -> res.others.add(f)
                }
                continue
            }
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            if (looksLikeScreenshot(f, bounds.outWidth, bounds.outHeight, screenW, screenH)) {
                cache[ck] = KIND_SCREENSHOT
                res.screenshots.add(f)
                continue
            }
            val bm = decodeScaled(f, 1280)
            if (bm == null) {
                res.errors++
                res.others.add(f)
                continue
            }
            val kind = if (hasFaces(bm)) KIND_PEOPLE else KIND_OTHER
            cache[ck] = kind
            when (kind) {
                KIND_PEOPLE -> res.people.add(f)
                KIND_SCREENSHOT -> res.screenshots.add(f)
                else -> res.others.add(f)
            }
        }
        saveCache(context, cache)
        return res
    }

    /** Copies (or moves) the categorized photos into outRoot/<category>/. */
    fun organize(
        filesByKind: Map<Int, List<File>>,
        outRoot: File,
        move: Boolean,
        dry: Boolean,
        log: (String) -> Unit
    ): Cleaner.Result {
        var count = 0
        var bytes = 0L
        var skipped = 0
        var shown = 0
        if (!dry) outRoot.mkdirs()
        for ((kind, files) in filesByKind) {
            val dirName = kindLabel(kind)
            for (f in files) {
                bytes += f.length()
                if (dry) {
                    count++
                    if (shown < 12) {
                        shown++
                        log("Would " + (if (move) "move" else "copy") + ": " + f.name + "  ->  " + dirName + "/")
                    }
                } else {
                    val dest = File(outRoot, dirName)
                    if (!dest.isDirectory && !dest.mkdirs()) {
                        skipped++
                        continue
                    }
                    try {
                        val target = uniqueTarget(dest, f.name)
                        if (move) {
                            if (!f.renameTo(target)) {
                                f.copyTo(target, overwrite = false)
                                f.delete()
                            }
                        } else {
                            f.copyTo(target, overwrite = false)
                        }
                        count++
                    } catch (e: Exception) {
                        skipped++
                    }
                }
            }
        }
        if (skipped > 0) log("Skipped " + skipped + " file(s) that could not be copied.")
        return Cleaner.Result(count, bytes)
    }

    private fun uniqueTarget(dirOut: File, name: String): File {
        var t = File(dirOut, name)
        if (!t.exists()) return t
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            t = File(dirOut, stem + " (" + n + ")" + ext)
            if (!t.exists()) return t
            n++
        }
    }
}
