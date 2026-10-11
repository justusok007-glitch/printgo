package com.printgo.app.data.converter

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.Closeable
import java.io.File

class PdfPageRenderer(val pdfFile: File) : Closeable {

    private val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(
        pdfFile,
        ParcelFileDescriptor.MODE_READ_ONLY
    )
    private val renderer: PdfRenderer = PdfRenderer(pfd)

    val pageCount: Int
        get() = renderer.pageCount

    // LRU Cache for up to 12 rendered page bitmaps
    private val bitmapCache = object : LruCache<Int, Bitmap>(12) {
        override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Let GC collect recycled bitmaps
        }
    }

    @Synchronized
    fun renderPage(pageIndex: Int, targetWidth: Int = 1080): Bitmap {
        require(pageIndex in 0 until pageCount) { "Invalid page index: $pageIndex (count: $pageCount)" }

        bitmapCache.get(pageIndex)?.let { cached ->
            if (!cached.isRecycled) return cached
        }

        val page = renderer.openPage(pageIndex)
        val aspectRatio = page.height.toFloat() / page.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt()

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        // White background for the page
        bitmap.eraseColor(Color.WHITE)

        val matrix = Matrix().apply {
            setScale(targetWidth.toFloat() / page.width.toFloat(), targetHeight.toFloat() / page.height.toFloat())
        }

        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        bitmapCache.put(pageIndex, bitmap)
        return bitmap
    }

    fun clearCache() {
        bitmapCache.evictAll()
    }

    /** Page size in PDF points (width, height). */
    @Synchronized
    fun pageSizePt(pageIndex: Int): Pair<Int, Int> {
        val page = renderer.openPage(pageIndex)
        try { return page.width to page.height } finally { page.close() }
    }

    /**
     * Renders the part of page [pageIndex] that falls inside [visible] when the whole page, turned by
     * [rotation] degrees clockwise, is stretched over [dest]. Both rectangles are in the caller's units
     * (points on a sheet); [pxPerUnit] sets the output resolution. Used for scaling, positioning and
     * clipping pages on a sheet without first rendering a huge full-page bitmap.
     */
    @Synchronized
    fun renderRegion(pageIndex: Int, dest: RectF, visible: RectF, rotation: Int, pxPerUnit: Float): Bitmap {
        val outW = (visible.width() * pxPerUnit).toInt().coerceAtLeast(1)
        val outH = (visible.height() * pxPerUnit).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val page = renderer.openPage(pageIndex)
        try {
            val pw = page.width.toFloat()
            val ph = page.height.toFloat()
            val rot = ((rotation % 360) + 360) % 360
            val m = Matrix()
            m.postRotate(rot.toFloat())
            when (rot) {
                90 -> m.postTranslate(ph, 0f)
                180 -> m.postTranslate(pw, ph)
                270 -> m.postTranslate(0f, pw)
            }
            val rw = if (rot % 180 == 0) pw else ph
            val rh = if (rot % 180 == 0) ph else pw
            m.postScale(dest.width() / rw * pxPerUnit, dest.height() / rh * pxPerUnit)
            m.postTranslate((dest.left - visible.left) * pxPerUnit, (dest.top - visible.top) * pxPerUnit)
            page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        } finally {
            page.close()
        }
        return bmp
    }

    /**
     * Renders a page for a portrait sheet of paper [paperWidthPx] pixels wide. Landscape pages are turned
     * 90° anticlockwise (the usual printer convention) so they fill the paper instead of shrinking into
     * its top half.
     */
    fun renderForPortraitPaper(pageIndex: Int, paperWidthPx: Int): Bitmap {
        val (w, h) = pageSizePt(pageIndex)
        if (w <= h) return renderPage(pageIndex, paperWidthPx)
        val longSidePx = (paperWidthPx.toFloat() * w / h).toInt()
        val src = renderPage(pageIndex, longSidePx)
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(-90f) }, false)
        bitmapCache.remove(pageIndex)
        return rotated
    }

    override fun close() {
        try {
            clearCache()
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            // Ignore close exceptions
        }
    }
}
