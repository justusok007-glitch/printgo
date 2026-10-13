package com.printgo.app.data.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Reading, straightening and filtering pictures for the scanner. Editing uses small copies; the final page is
 * made once from a downscaled decode of the original (never more pixels than the phone can hold), with EXIF
 * rotation, the user's turns and the crop folded into one drawing step, and the filter applied in bands.
 * The user's original picture is only ever read.
 */
object ScanImageIO {

    /** Folder for scanner files (camera photos, copies of picked pictures, finished pages and PDFs). */
    fun scanDir(context: Context, sub: String): File = File(context.filesDir, "scan/$sub").apply { mkdirs() }

    /** Deletes scanner files older than [maxAgeMs] (finished jobs keep their own copy of the document). */
    fun cleanOldFiles(context: Context, maxAgeMs: Long = 3L * 24 * 3600 * 1000) {
        val root = File(context.filesDir, "scan")
        val limit = System.currentTimeMillis() - maxAgeMs
        root.walkBottomUp().forEach { f -> if (f.isFile && f.lastModified() < limit) f.delete() }
    }

    /** Copies a picked picture into the app (picker permissions are temporary). */
    fun copyIn(context: Context, uri: Uri): File? = try {
        val f = File(scanDir(context, "src"), "pick_${System.currentTimeMillis()}_${(Math.random() * 1e6).toInt()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
        if (f.length() > 0) f else { f.delete(); null }
    } catch (e: Exception) {
        null
    }

    /** EXIF rotation of the picture in degrees clockwise (0, 90, 180, 270). */
    fun exifRotation(context: Context, uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { s ->
            when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }

    /** Stored (not turned) size of the picture, or null if it cannot be read. */
    fun bounds(context: Context, uri: Uri): Pair<Int, Int>? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
    } catch (e: Exception) {
        null
    }

    /** Decodes the picture with the smallest power-of-two reduction so it has at most [maxPixels] pixels. */
    fun decodeSampled(context: Context, uri: Uri, maxPixels: Long): Bitmap? {
        val (w, h) = bounds(context, uri) ?: return null
        var sample = 1
        while ((w.toLong() / sample) * (h.toLong() / sample) > maxPixels) sample *= 2
        val o = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        } catch (e: OutOfMemoryError) {
            if (maxPixels > 2_000_000) decodeSampled(context, uri, maxPixels / 4) else null
        } catch (e: Exception) {
            null
        }
    }

    /** Picture upright (EXIF applied) with the long side at most [maxLong] pixels, for editing. */
    fun loadUpright(context: Context, uri: Uri, maxLong: Int = 1600): Bitmap? {
        val raw = decodeSampled(context, uri, maxLong.toLong() * maxLong * 2) ?: return null
        val scaled = scaleToFit(raw, maxLong)
        return rotate(scaled, exifRotation(context, uri))
    }

    fun scaleToFit(b: Bitmap, maxLong: Int): Bitmap {
        val long = max(b.width, b.height)
        if (long <= maxLong) return b
        val k = maxLong.toFloat() / long
        return Bitmap.createScaledBitmap(b, max(1, (b.width * k).roundToInt()), max(1, (b.height * k).roundToInt()), true)
    }

    fun rotate(b: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return b
        val m = Matrix().apply { postRotate(d.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    /**
     * Straightens the crop [quad] (relative to [src] turned by [rotationDegrees]) into a flat picture with the
     * long side at most [maxLong] pixels.
     */
    fun warp(src: Bitmap, rotationDegrees: Int, quad: Quad, maxLong: Int): Bitmap {
        val (tw, th) = ScanGeometry.turnedSize(src.width, src.height, rotationDegrees)
        val (fw, fh) = ScanGeometry.flatSize(quad, tw.toDouble(), th.toDouble())
        val (ow, oh) = ScanGeometry.outputSize(max(fw, 1.0), max(fh, 1.0), maxLong)
        val pts = quad.points.map { ScanGeometry.toSourcePixels(it, rotationDegrees, src.width, src.height) }
        val from = FloatArray(8).also { a -> pts.forEachIndexed { i, p -> a[2 * i] = p.x.toFloat(); a[2 * i + 1] = p.y.toFloat() } }
        val to = floatArrayOf(0f, 0f, ow.toFloat(), 0f, ow.toFloat(), oh.toFloat(), 0f, oh.toFloat())
        val m = Matrix()
        if (!m.setPolyToPoly(from, 0, to, 0, 4)) {
            // Degenerate corners: fall back to the box around them
            val b = quad.bounds().points.map { ScanGeometry.toSourcePixels(it, rotationDegrees, src.width, src.height) }
            val fb = FloatArray(8).also { a -> b.forEachIndexed { i, p -> a[2 * i] = p.x.toFloat(); a[2 * i + 1] = p.y.toFloat() } }
            m.setPolyToPoly(fb, 0, to, 0, 4)
        }
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        }
        return out
    }

    /** Applies the filter in place, a band of rows at a time (small memory use even at 300 dpi). */
    fun applyFilter(bmp: Bitmap, filter: ScanFilter, strength: Float) {
        if (filter == ScanFilter.ORIGINAL || strength <= 0f) return
        val w = bmp.width; val h = bmp.height
        val bg = if (filter.needsBackground) {
            val small = scaleToFit(bmp, 160)
            val px = IntArray(small.width * small.height)
            small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
            BackgroundMap.estimate(px, small.width, small.height)
        } else null
        val band = 128
        var pending: Triple<IntArray, Int, Int>? = null // result, first row, rows (offset of 1 halo row handled below)
        var pendingOffset = 0
        var y = 0
        while (y < h) {
            val y0 = max(0, y - 1)
            val y1 = min(h, y + band + 1)
            val rows = y1 - y0
            val arr = IntArray(w * rows)
            bmp.getPixels(arr, 0, w, 0, y0, w, rows)
            val res = ScanFilterEngine.apply(arr, w, rows, filter, strength, bg, y0, h)
            // Write the previous band only now, so this band's halo row above was read unfiltered.
            pending?.let { (r, first, n) -> bmp.setPixels(r, pendingOffset, w, 0, first, w, n) }
            pending = Triple(res, y, min(band, h - y))
            pendingOffset = (y - y0) * w
            y += band
        }
        pending?.let { (r, first, n) -> bmp.setPixels(r, pendingOffset, w, 0, first, w, n) }
    }

    /** Filtered copy of a small picture (thumbnails and the live preview). */
    fun filtered(src: Bitmap, filter: ScanFilter, strength: Float): Bitmap {
        val copy = src.copy(Bitmap.Config.ARGB_8888, true)
        applyFilter(copy, filter, strength)
        return copy
    }

    /** Grey values of a small copy (long side ≤ [maxLong]) for page detection. */
    fun luma(b: Bitmap, maxLong: Int = 640): Triple<IntArray, Int, Int> {
        val s = scaleToFit(b, maxLong)
        val px = IntArray(s.width * s.height)
        s.getPixels(px, 0, s.width, 0, 0, s.width, s.height)
        val lum = IntArray(px.size) { i ->
            val p = px[i]
            ScanFilterEngine.luma(((p shr 16) and 0xFF).toFloat(), ((p shr 8) and 0xFF).toFloat(), (p and 0xFF).toFloat()).toInt()
        }
        return Triple(lum, s.width, s.height)
    }

    /** Pixels the phone can safely decode for one page. */
    fun maxSourcePixels(): Long {
        val mb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        return when {
            mb >= 400 -> 16_000_000L
            mb >= 250 -> 12_000_000L
            else -> 6_000_000L
        }
    }

    /**
     * Makes the finished page at print resolution (long side ≤ [maxLong] px, about 300 dpi for the paper) and
     * saves it as JPEG in [outFile].
     */
    fun renderPage(context: Context, page: ScanPage, maxLong: Int, outFile: File): File {
        val uri = Uri.parse(page.source)
        val src = decodeSampled(context, uri, maxSourcePixels())
            ?: throw java.io.IOException("Could not open the picture.")
        val rotation = exifRotation(context, uri) + page.quarterTurns * 90
        val flat = try {
            warp(src, rotation, page.quad, maxLong)
        } catch (e: OutOfMemoryError) {
            src.recycle()
            System.gc()
            val smaller = decodeSampled(context, uri, maxSourcePixels() / 4)
                ?: throw java.io.IOException("Not enough memory for this picture.")
            warp(smaller, rotation, page.quad, maxLong * 2 / 3).also { smaller.recycle() }
        }
        if (!src.isRecycled) src.recycle()
        applyFilter(flat, page.filter, page.strength)
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { flat.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        flat.recycle()
        return outFile
    }
}
