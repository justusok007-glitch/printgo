package com.printgo.app.data.print

import java.io.OutputStream

/**
 * One page of pixels handed over a row at a time (ARGB ints, top row first). Encoders read rows in
 * increasing order, so a page can be rendered in bands and a 600 dpi page never needs one huge bitmap.
 */
interface RowSource {
    val width: Int
    val height: Int

    /** Fills [out] (at least [width] long) with row [y]. Rows are requested in increasing order. */
    fun readRow(y: Int, out: IntArray)
}

/** An empty white page (for example the blank back of the last sheet when copies are printed two-sided). */
class BlankRowSource(override val width: Int, override val height: Int) : RowSource {
    override fun readRow(y: Int, out: IntArray) {
        java.util.Arrays.fill(out, 0, width, -1)
    }
}

/**
 * How a back side is turned before it goes to a driver that expects that (HPLIP and SpliX printers whose
 * description says cupsBackSide Rotated/Flipped; CUPS normally does this turning on a computer).
 */
enum class SheetFlip {
    NONE, ROTATE_180, MIRROR_X, MIRROR_Y;

    companion object {
        /** From pg_cupsraster's "backside=" answer. */
        fun fromQuery(value: String?): SheetFlip = when (value?.trim()) {
            "rotate" -> ROTATE_180
            "flipx" -> MIRROR_X
            "flipy" -> MIRROR_Y
            else -> NONE
        }

        /** Where pixel (x, y) of the turned page comes from in the original page of size w x h. */
        fun sourceOf(flip: SheetFlip, x: Int, y: Int, w: Int, h: Int): Pair<Int, Int> = when (flip) {
            NONE -> x to y
            ROTATE_180 -> (w - 1 - x) to (h - 1 - y)
            MIRROR_X -> (w - 1 - x) to y
            MIRROR_Y -> x to (h - 1 - y)
        }
    }
}

/** A plain in-memory page; used by tests and small pictures. */
class IntArrayRowSource(override val width: Int, override val height: Int, private val pixels: IntArray) : RowSource {
    override fun readRow(y: Int, out: IntArray) {
        System.arraycopy(pixels, y * width, out, 0, width)
    }
}

/**
 * The line format shared by PWG Raster (PWG 5102.4) and Apple Raster (URF): every line starts with a
 * "repeat this line n more times" byte, followed by PackBits-style runs counted in whole pixels
 * (0..127 = repeat the next pixel n+1 times, 129..255 = 257-n literal pixels).
 */
object RasterRows {

    fun write(out: OutputStream, src: RowSource, grayscale: Boolean) {
        val w = src.width
        val bpp = if (grayscale) 1 else 3
        val pixels = IntArray(w)
        var prev = ByteArray(w * bpp)
        var cur = ByteArray(w * bpp)
        var repeat = 0
        var havePrev = false

        fun flushPrev() {
            if (!havePrev) return
            out.write(repeat)
            encodeRow(out, prev, bpp)
        }

        for (y in 0 until src.height) {
            src.readRow(y, pixels)
            toBytes(pixels, w, cur, grayscale)
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

    /** ARGB → 8-bit gray (sGray) or 24-bit RGB (sRGB). Transparent areas count as white paper. */
    fun toBytes(pixels: IntArray, w: Int, dst: ByteArray, grayscale: Boolean) {
        var o = 0
        for (x in 0 until w) {
            val p = pixels[x]
            val a = (p ushr 24) and 0xFF
            var r = (p shr 16) and 0xFF
            var g = (p shr 8) and 0xFF
            var b = p and 0xFF
            if (a != 0xFF) {
                r = (r * a + 255 * (255 - a)) / 255
                g = (g * a + 255 * (255 - a)) / 255
                b = (b * a + 255 * (255 - a)) / 255
            }
            if (grayscale) {
                dst[o++] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
            } else {
                dst[o++] = r.toByte(); dst[o++] = g.toByte(); dst[o++] = b.toByte()
            }
        }
    }

    /** Pixel-run encoding of one line (PackBits-like, in whole pixels). Never writes 128. */
    fun encodeRow(out: OutputStream, row: ByteArray, bpp: Int) {
        val n = row.size / bpp
        var i = 0
        while (i < n) {
            var run = 1
            while (i + run < n && run < 128 && samePixel(row, i, i + run, bpp)) run++
            if (run >= 2 || i == n - 1) {
                out.write(run - 1)
                out.write(row, i * bpp, bpp)
                i += run
            } else {
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

    /** Decodes one encoded line (used by tests to prove the encoder is lossless). Returns (bytes, next offset). */
    fun decodeRow(data: ByteArray, startOffset: Int, widthPx: Int, bpp: Int): Pair<ByteArray, Int> {
        val out = java.io.ByteArrayOutputStream()
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
        return out.toByteArray() to p
    }

    fun writeInt(out: OutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF); out.write((v ushr 16) and 0xFF); out.write((v ushr 8) and 0xFF); out.write(v and 0xFF)
    }
}
