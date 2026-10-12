package com.printgo.app.data.print

import java.io.OutputStream

/**
 * Apple Raster ("URF", image/urf): the raster format of AirPrint printers. Many printers (most Epson,
 * Canon and HP inkjets) accept it even when they take no PWG raster or PDF. Layout as written by CUPS
 * (cups/raster-stream.c): "UNIRAST\0" + big-endian page count, then per page a 32-byte header and the
 * same packed rows as PWG raster.
 */
object UrfEncoder {

    const val PAGE_HEADER_SIZE = 32

    fun writeDocumentStart(out: OutputStream, pageCount: Int) {
        out.write("UNIRAST".toByteArray(Charsets.US_ASCII))
        out.write(0)
        RasterRows.writeInt(out, pageCount.coerceAtLeast(0))
    }

    /**
     * @param duplex 0 = one-sided, 1 = two-sided long edge, 2 = two-sided short edge
     * @param quality IPP print-quality: 3 draft, 4 normal, 5 high
     */
    fun buildPageHeader(widthPx: Int, heightPx: Int, dpi: Int, grayscale: Boolean, duplex: Int, quality: Int): ByteArray {
        val h = ByteArray(PAGE_HEADER_SIZE)
        h[0] = (if (grayscale) 8 else 24).toByte()            // bits per pixel
        h[1] = (if (grayscale) 0 else 1).toByte()             // colour space: 0 = sGray, 1 = sRGB
        h[2] = when (duplex) { 1 -> 3; 2 -> 2; else -> 1 }.toByte() // 1 simplex, 2 short edge, 3 long edge
        h[3] = quality.coerceIn(3, 5).toByte()
        h[4] = 0                                             // media type: auto
        h[5] = 0                                             // media position: auto
        putInt(h, 12, widthPx)
        putInt(h, 16, heightPx)
        putInt(h, 20, dpi)
        return h
    }

    fun writePage(out: OutputStream, src: RowSource, dpi: Int, grayscale: Boolean, duplex: Int, quality: Int) {
        out.write(buildPageHeader(src.width, src.height, dpi, grayscale, duplex, quality))
        RasterRows.write(out, src, grayscale)
    }

    private fun putInt(b: ByteArray, o: Int, v: Int) {
        b[o] = (v ushr 24).toByte(); b[o + 1] = (v ushr 16).toByte(); b[o + 2] = (v ushr 8).toByte(); b[o + 3] = v.toByte()
    }
}
