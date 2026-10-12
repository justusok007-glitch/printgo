package com.printgo.app.data.print

import java.io.OutputStream

/**
 * PrintGo raster stream ("PGR"), read by the driver programs pg_gutenprint and pg_cupsraster
 * (native/src/pgr.h):
 *
 *   "PGR1", then per page "PAGE" + width, height, xdpi, ydpi, channels, flags (little-endian uint32)
 *   followed by height rows of width*channels bytes, then "DONE".
 *   channels 3 = RGB, 1 = grey (0 black .. 255 white); flags bit 0 = back side of a two-sided sheet.
 */
object PgrWriter {
    fun start(out: OutputStream) = out.write(byteArrayOf('P'.b, 'G'.b, 'R'.b, '1'.b))

    fun end(out: OutputStream) = out.write(byteArrayOf('D'.b, 'O'.b, 'N'.b, 'E'.b))

    fun pageHeader(width: Int, height: Int, dpi: Int, grayscale: Boolean, backSide: Boolean): ByteArray {
        val h = ByteArray(28)
        h[0] = 'P'.b; h[1] = 'A'.b; h[2] = 'G'.b; h[3] = 'E'.b
        le32(h, 4, width); le32(h, 8, height); le32(h, 12, dpi); le32(h, 16, dpi)
        le32(h, 20, if (grayscale) 1 else 3); le32(h, 24, if (backSide) 1 else 0)
        return h
    }

    fun writePage(out: OutputStream, src: RowSource, dpi: Int, grayscale: Boolean, backSide: Boolean = false) {
        out.write(pageHeader(src.width, src.height, dpi, grayscale, backSide))
        val px = IntArray(src.width)
        val line = ByteArray(src.width * if (grayscale) 1 else 3)
        for (y in 0 until src.height) {
            src.readRow(y, px)
            packRow(px, src.width, grayscale, line)
            out.write(line)
        }
    }

    /** ARGB pixels to RGB bytes, or to grey (0 black .. 255 white). */
    fun packRow(px: IntArray, width: Int, grayscale: Boolean, line: ByteArray) {
        if (grayscale) {
            for (x in 0 until width) {
                val p = px[x]
                line[x] = ((77 * ((p shr 16) and 0xFF) + 150 * ((p shr 8) and 0xFF) + 29 * (p and 0xFF)) shr 8).toByte()
            }
        } else {
            var o = 0
            for (x in 0 until width) {
                val p = px[x]
                line[o++] = (p shr 16).toByte(); line[o++] = (p shr 8).toByte(); line[o++] = p.toByte()
            }
        }
    }

    private fun le32(b: ByteArray, at: Int, v: Int) {
        b[at] = v.toByte(); b[at + 1] = (v shr 8).toByte(); b[at + 2] = (v shr 16).toByte(); b[at + 3] = (v shr 24).toByte()
    }

    private val Char.b get() = code.toByte()
}

/**
 * Input of Epson's own "epson-escpr" filter program (pg_escpr), as its wrapper sends it: one byte with
 * the number of pages in the first batch (1 or 2), then per page one byte saying how many pages follow
 * (99 = more than two) and the page's rows: width*3 RGB bytes, or width grey bytes in MONO mode.
 * Arguments: model width height dpi COLOR|MONO PageSize MediaType_Quality Duplex InputSlot
 *            Brightness Contrast Saturation QuietMode.
 */
object EscprStream {
    const val DPI = 360

    fun args(widthPx: Int, heightPx: Int, color: Boolean, pageSize: String, high: Boolean, duplex: String): List<String> =
        listOf(
            "PrintGo", widthPx.toString(), heightPx.toString(), DPI.toString(), if (color) "COLOR" else "MONO", pageSize,
            if (high) "PLAIN_HIGH" else "PLAIN_NORMAL", duplex, "Auto", "0", "0", "0", "Off"
        )

    fun start(out: OutputStream, totalPages: Int) = out.write(if (totalPages >= 2) 2 else 1)

    fun pagesAfterByte(pagesAfter: Int): Int = if (pagesAfter > 2) 99 else pagesAfter.coerceAtLeast(0)

    fun writePage(out: OutputStream, src: RowSource, pagesAfter: Int, color: Boolean) {
        out.write(pagesAfterByte(pagesAfter))
        val px = IntArray(src.width)
        val line = ByteArray(src.width * if (color) 3 else 1)
        for (y in 0 until src.height) {
            src.readRow(y, px)
            PgrWriter.packRow(px, src.width, !color, line)
            out.write(line)
        }
    }

    /** Epson's paper names (src/optBase.h of the driver) for the sizes PrintGo offers. */
    fun pageSizeName(widthMm: Int, heightMm: Int): String {
        val w = minOf(widthMm, heightMm)
        val h = maxOf(widthMm, heightMm)
        fun near(a: Int, b: Int) = kotlin.math.abs(w - a) <= 2 && kotlin.math.abs(h - b) <= 2
        return when {
            near(210, 297) -> "A4"
            near(216, 279) -> "Letter"
            near(216, 356) -> "Legal"
            near(148, 210) -> "A5"
            near(105, 148) -> "A6"
            near(182, 257) -> "B5"
            near(184, 267) -> "EXE"
            near(297, 420) -> "A3"
            near(257, 364) -> "B4"
            near(102, 152) -> "4X6"
            near(127, 178) -> "2L"
            near(89, 127) -> "L"
            near(279, 432) -> "USB"
            w >= 214 -> "Letter"
            else -> "A4"
        }
    }

    /** Pixel size of the whole sheet at 360 dpi, as the wrapper derives it from the PPD page size. */
    fun sheetPixels(widthMm: Int, heightMm: Int): Pair<Int, Int> {
        val w = minOf(widthMm, heightMm)
        val h = maxOf(widthMm, heightMm)
        return (w * DPI / 25.4f).toInt() to (h * DPI / 25.4f).toInt()
    }
}
