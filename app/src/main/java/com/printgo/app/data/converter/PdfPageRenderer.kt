package com.printgo.app.data.converter

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
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
