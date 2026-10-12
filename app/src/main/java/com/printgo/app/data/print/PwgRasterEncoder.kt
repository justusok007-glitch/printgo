package com.printgo.app.data.print

import android.graphics.Bitmap
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer

/** Writes PWG Raster (PWG 5102.4) documents: "RaS2" sync word + 1796-byte page headers + packed rows. */
object PwgRasterEncoder {

    const val HEADER_SIZE = 1796

    fun writeDocumentStart(out: OutputStream) {
        out.write(byteArrayOf('R'.code.toByte(), 'a'.code.toByte(), 'S'.code.toByte(), '2'.code.toByte()))
    }

    fun buildPageHeader(
        widthPx: Int,
        heightPx: Int,
        dpi: Int,
        grayscale: Boolean,
        duplexTwoSided: Boolean,
        tumble: Boolean,
        copies: Int,
        pageSizePtW: Int,
        pageSizePtH: Int,
        quality: Int,
        totalPages: Int = 0,
        pageSizeName: String? = null
    ): ByteArray {
        val b = ByteBuffer.allocate(HEADER_SIZE) // big endian, zero-filled
        fun str(offset: Int, s: String) {
            val bytes = s.toByteArray(Charsets.US_ASCII)
            for (i in bytes.indices.take(63)) b.put(offset + i, bytes[i])
        }
        fun int(offset: Int, v: Int) { b.putInt(offset, v) }

        str(0, "PwgRaster")
        str(64, "")                 // MediaColor
        str(128, "stationery")      // MediaType
        str(192, "")                // PrintContentOptimize
        int(272, if (duplexTwoSided) 1 else 0)
        int(276, dpi); int(280, dpi)
        int(340, copies.coerceAtLeast(1))
        int(344, 0)                 // Orientation: portrait
        int(352, pageSizePtW); int(356, pageSizePtH)
        int(368, if (tumble) 1 else 0)
        int(372, widthPx); int(376, heightPx)
        val colors = if (grayscale) 1 else 3
        int(384, 8)                 // BitsPerColor
        int(388, 8 * colors)        // BitsPerPixel
        int(392, widthPx * colors)  // BytesPerLine
        int(396, 0)                 // ColorOrder: chunky
        int(400, if (grayscale) 18 else 19) // sGray / sRGB
        int(420, colors)
        int(452, totalPages.coerceAtLeast(0))
        int(456, 1); int(460, 1)    // CrossFeedTransform / FeedTransform
        int(484, quality)
        pageSizeName?.let { str(1732, it) }
        return b.array()
    }

    /** Streams one page (header + rows) from any [RowSource]. */
    fun writePage(out: OutputStream, src: RowSource, header: ByteArray, grayscale: Boolean) {
        out.write(header)
        RasterRows.write(out, src, grayscale)
    }

    /** Streams the bitmap as one PWG raster page. */
    fun writePage(out: OutputStream, bitmap: Bitmap, header: ByteArray, grayscale: Boolean) =
        writePage(out, BitmapRowSource(bitmap), header, grayscale)

    /** Pixel-run encoding of one line (PackBits-like, in whole pixels). */
    internal fun encodeRow(out: OutputStream, row: ByteArray, bpp: Int) = RasterRows.encodeRow(out, row, bpp)

    /** Test helper: decode a single encoded line back to raw bytes. */
    internal fun decodeRow(data: ByteArray, startOffset: Int, widthPx: Int, bpp: Int): ByteArray =
        RasterRows.decodeRow(data, startOffset, widthPx, bpp).first

    fun toFile(file: File, pages: List<Pair<Bitmap, ByteArray>>, grayscale: Boolean) {
        file.outputStream().buffered().use { os ->
            writeDocumentStart(os)
            pages.forEach { (bmp, hdr) -> writePage(os, bmp, hdr, grayscale) }
        }
    }
}

/** Reads rows straight out of a bitmap. */
class BitmapRowSource(private val bmp: Bitmap) : RowSource {
    override val width: Int get() = bmp.width
    override val height: Int get() = bmp.height
    override fun readRow(y: Int, out: IntArray) = bmp.getPixels(out, 0, bmp.width, 0, y, bmp.width, 1)
}
