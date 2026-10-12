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

    /**
     * Opens page [pageIndex] as a [com.printgo.app.data.print.RowSource] for portrait paper
     * [paperWidthPx] pixels wide, rendered in bands so that even 600 dpi pages use little memory.
     * Landscape pages are turned 90° anticlockwise, exactly like [renderForPortraitPaper].
     * Close the returned source before rendering another page.
     */
    @Synchronized
    fun openRows(
        pageIndex: Int,
        paperWidthPx: Int,
        paperHeightPx: Int? = null,
        flip: com.printgo.app.data.print.SheetFlip = com.printgo.app.data.print.SheetFlip.NONE
    ): PageRows {
        clearCache()
        val page = renderer.openPage(pageIndex)
        return PageRows(page, paperWidthPx, paperHeightPx, flip)
    }

    /**
     * [heightPx] fixes the raster height to the paper's (raster formats must match the paper size exactly);
     * the page is then stretched by the tiny rounding difference between the PDF page and the paper.
     */
    class PageRows internal constructor(
        private val page: PdfRenderer.Page,
        override val width: Int,
        heightPx: Int?,
        private val flip: com.printgo.app.data.print.SheetFlip = com.printgo.app.data.print.SheetFlip.NONE
    ) : com.printgo.app.data.print.RowSource, Closeable {
        private val pw = page.width.toFloat()
        private val ph = page.height.toFloat()
        private val landscape = pw > ph
        private val scale = width / (if (landscape) ph else pw)
        override val height: Int = (heightPx ?: Math.round((if (landscape) pw else ph) * scale)).coerceAtLeast(1)
        private val scaleY = height / (if (landscape) pw else ph)
        private val bandHeight = (12_000_000 / (width.coerceAtLeast(1) * 4)).coerceIn(16, 1024).coerceAtMost(height)
        private val band: Bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), bandHeight, Bitmap.Config.ARGB_8888)
        private var bandTop = -1

        private fun renderBand(top: Int) {
            band.eraseColor(Color.WHITE)
            val m = Matrix()
            if (landscape) {
                m.postRotate(-90f)          // (x, y) -> (y, -x)
                m.postTranslate(0f, pw)     // -> (y, pw - x)
            }
            m.postScale(scale, scaleY)
            // Back sides some drivers expect turned (see SheetFlip); done on the whole sheet.
            when (flip) {
                com.printgo.app.data.print.SheetFlip.NONE -> {}
                com.printgo.app.data.print.SheetFlip.ROTATE_180 -> { m.postScale(-1f, -1f); m.postTranslate(width.toFloat(), height.toFloat()) }
                com.printgo.app.data.print.SheetFlip.MIRROR_X -> { m.postScale(-1f, 1f); m.postTranslate(width.toFloat(), 0f) }
                com.printgo.app.data.print.SheetFlip.MIRROR_Y -> { m.postScale(1f, -1f); m.postTranslate(0f, height.toFloat()) }
            }
            m.postTranslate(0f, -top.toFloat())
            page.render(band, null, m, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            bandTop = top
        }

        override fun readRow(y: Int, out: IntArray) {
            if (bandTop < 0 || y < bandTop || y >= bandTop + bandHeight) renderBand(y)
            band.getPixels(out, 0, width, 0, y - bandTop, width, 1)
        }

        override fun close() {
            try { band.recycle() } catch (_: Exception) { }
            try { page.close() } catch (_: Exception) { }
        }
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
