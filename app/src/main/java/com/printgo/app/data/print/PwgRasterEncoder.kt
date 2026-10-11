package com.printgo.app.data.print

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
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
        quality: Int
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
        int(452, 0)                 // TotalPageCount unknown
        int(456, 1); int(460, 1)
        int(484, quality)
        return b.array()
    }

    /** Streams the bitmap as one PWG raster page. */
    fun writePage(out: OutputStream, bitmap: Bitmap, header: ByteArray, grayscale: Boolean) {
        out.write(header)
        val w = bitmap.width
        val h = bitmap.height
        val bpp = if (grayscale) 1 else 3
        val pixels = IntArray(w)
        var prev = ByteArray(w * bpp)
        var cur = ByteArray(w * bpp)
        var repeat = 0 // number of additional times prev row repeats
        var havePrev = false

        fun flushPrev() {
            if (!havePrev) return
            out.write(repeat)
            encodeRow(out, prev, bpp)
        }

        for (y in 0 until h) {
            bitmap.getPixels(pixels, 0, w, 0, y, w, 1)
            var o = 0
            for (x in 0 until w) {
                val p = pixels[x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val bl = p and 0xFF
                if (grayscale) {
                    cur[o++] = ((r * 299 + g * 587 + bl * 114) / 1000).toByte()
                } else {
                    cur[o++] = r.toByte(); cur[o++] = g.toByte(); cur[o++] = bl.toByte()
                }
            }
            if (havePrev && repeat < 255 && cur.contentEquals(prev)) {
                repeat++
            } else {
                flushPrev()
                val t = prev; prev = cur; cur = t
                repeat = 0
                havePrev = true
            }
        }
        flushPrev()
    }

    /** Pixel-run encoding of one line (PackBits-like, in whole pixels). */
    internal fun encodeRow(out: OutputStream, row: ByteArray, bpp: Int) {
        val n = row.size / bpp
        var i = 0
        while (i < n) {
            // count run of identical pixels
            var run = 1
            while (i + run < n && run < 128 && samePixel(row, i, i + run, bpp)) run++
            if (run >= 2 || i == n - 1) {
                out.write(run - 1)
                out.write(row, i * bpp, bpp)
                i += run
            } else {
                // literal: collect pixels until a run of >=2 starts
                var lit = 1
                while (i + lit < n && lit < 128 && !(i + lit + 1 < n && samePixel(row, i + lit, i + lit + 1, bpp))) lit++
                if (lit == 1) out.write(0) else out.write(257 - lit)
                out.write(row, i * bpp, lit * bpp)
                i += lit
            }
        }
    }

    private fun samePixel(row: ByteArray, a: Int, b: Int, bpp: Int): Boolean {
        for (k in 0 until bpp) if (row[a * bpp + k] != row[b * bpp + k]) return false
        return true
    }

    /** Test helper: decode a single encoded line back to raw bytes. */
    internal fun decodeRow(data: ByteArray, startOffset: Int, widthPx: Int, bpp: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var p = startOffset
        var pixels = 0
        while (pixels < widthPx) {
            val c = data[p++].toInt() and 0xFF
            if (c <= 127) {
                val count = c + 1
                repeat(count) { out.write(data, p, bpp) }
                p += bpp
                pixels += count
            } else {
                val count = 257 - c
                out.write(data, p, count * bpp)
                p += count * bpp
                pixels += count
            }
        }
        return out.toByteArray()
    }

    fun toFile(file: File, pages: List<Pair<Bitmap, ByteArray>>, grayscale: Boolean) {
        file.outputStream().buffered().use { os ->
            writeDocumentStart(os)
            pages.forEach { (bmp, hdr) -> writePage(os, bmp, hdr, grayscale) }
        }
    }
}
