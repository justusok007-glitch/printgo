package com.printgo.app.data.print

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Epson ESC/P-R: the raster language of Epson inkjet and ink-tank printers (L-series, EcoTank, XP, WF…)
 * when they are reached over USB or raw port 9100. The printer does its own colour conversion and
 * halftoning; we send plain 24-bit RGB lines.
 *
 * Command framing (from Epson's open-source ESC/P-R library and the MIT "python-epson" encoder):
 *  - ESC/P-R command: ESC, class letter, 4-byte little-endian data length, 4-letter name, data
 *    (numbers inside the data are big-endian).
 *  - Remote-mode command: 2-letter name, 2-byte little-endian length (data + 1), 0x00, data.
 */
object EscprEncoder {

    private const val ESC = 0x1B

    /** Leaves IEEE-1284.4 packet mode, in which Epson printers start on USB. */
    private val EXIT_PACKET_MODE = byteArrayOf(0x00, 0x00, 0x00, 0x1B, 0x01) +
        "@EJL 1284.4\n@EJL     \n".toByteArray(Charsets.US_ASCII)
    private val INIT_PRINTER = byteArrayOf(0x1B, 0x40)
    private val ENTER_REMOTE = byteArrayOf(0x1B, 0x28, 0x52, 0x08, 0x00, 0x00) + "REMOTE1".toByteArray(Charsets.US_ASCII)
    private val EXIT_REMOTE = byteArrayOf(0x1B, 0x00, 0x00, 0x00)
    private val ENTER_ESCPR = byteArrayOf(0x1B, 0x28, 0x52, 0x06, 0x00, 0x00) + "ESCPR".toByteArray(Charsets.US_ASCII)

    const val QUALITY_DRAFT = 0
    const val QUALITY_NORMAL = 1
    const val QUALITY_HIGH = 2
    const val MEDIA_PLAIN = 0

    /** Paper and printable area in dots at [dpi]. Bordered printing with the usual 3 mm margins. */
    data class Geometry(
        val dpi: Int,
        val paperW: Int,
        val paperH: Int,
        val top: Int,
        val left: Int,
        val printW: Int,
        val printH: Int
    )

    fun geometry(widthMm: Int, heightMm: Int, dpi: Int = 360, marginMm: Float = 3f): Geometry {
        val pw = (widthMm * dpi / 25.4f).roundToInt()
        val ph = (heightMm * dpi / 25.4f).roundToInt()
        val m = (marginMm * dpi / 25.4f).toInt().coerceAtLeast(0)
        return Geometry(dpi, pw, ph, m, m, (pw - 2 * m).coerceAtLeast(1), (ph - 2 * m).coerceAtLeast(1))
    }

    fun dpiCode(dpi: Int): Int = when (dpi) { 720 -> 1; 300 -> 2; 600 -> 3; else -> 0 } // 0 = 360 dpi

    /**
     * Job start. [time] = year, month (1-12), day, hour, minute, second.
     */
    fun startJob(out: OutputStream, g: Geometry, color: Boolean, quality: Int, time: IntArray, mediaType: Int = MEDIA_PLAIN) {
        out.write(EXIT_PACKET_MODE)
        out.write(INIT_PRINTER)
        out.write(ENTER_REMOTE)
        val year = time.getOrElse(0) { 2026 }
        remote(out, "TI", byteArrayOf((year shr 8).toByte(), year.toByte(), time.getOrElse(1) { 1 }.toByte(),
            time.getOrElse(2) { 1 }.toByte(), time.getOrElse(3) { 0 }.toByte(), time.getOrElse(4) { 0 }.toByte(), time.getOrElse(5) { 0 }.toByte()))
        remote(out, "JS", byteArrayOf(0, 0, 0))
        remote(out, "JH", byteArrayOf(0, 0, 0, 0, 0) + "ESCPRLib".toByteArray(Charsets.US_ASCII))
        remote(out, "HD", byteArrayOf(3, 4))
        remote(out, "PP", byteArrayOf(1, 0))
        out.write(EXIT_REMOTE)
        out.write(ENTER_ESCPR)
        // setq: media type, quality, colour mode (0 colour, 1 mono), brightness, contrast, saturation,
        // colour plane (0 = full colour RGB), palette size (2 bytes, none).
        command(out, 'q', "setq", byteArrayOf(mediaType.toByte(), quality.coerceIn(0, 2).toByte(), (if (color) 0 else 1).toByte(), 0, 0, 0, 0, 0, 0))
        val j = ByteArrayOutputStream()
        be32(j, g.paperW); be32(j, g.paperH)
        be16(j, g.top); be16(j, g.left)
        be32(j, g.printW); be32(j, g.printH)
        j.write(dpiCode(g.dpi))
        j.write(0) // print direction: bidirectional
        command(out, 'j', "setj", j.toByteArray())
    }

    /**
     * One page. [src] covers the whole sheet at the job resolution (paperW × paperH, or close to it);
     * only the printable area inside the margins is sent. [pagesAfter] = pages still to come in this job.
     */
    fun writePage(out: OutputStream, src: RowSource, g: Geometry, pageNumber: Int, pagesAfter: Int, grayscale: Boolean) {
        command(out, 'p', "sttp", ByteArray(0))
        command(out, 'p', "setn", byteArrayOf(pageNumber.coerceIn(1, 99).toByte()))
        val w = g.printW
        val px = IntArray(maxOf(src.width, 1))
        val rgb = ByteArray(w * 3)
        val packed = ByteArray(w * 3 + w / 128 + 8)
        for (y in 0 until g.printH) {
            val sy = y + g.top
            if (sy < src.height) {
                src.readRow(sy, px)
                // Columns outside the source (paper a few dots narrower than the sheet) stay white.
                for (x in 0 until w) {
                    val sx = x + g.left
                    val p = if (sx < src.width) px[sx] else -1
                    val r = (p shr 16) and 0xFF; val gr = (p shr 8) and 0xFF; val b = p and 0xFF
                    if (grayscale) {
                        val l = ((77 * r + 150 * gr + 29 * b) shr 8).toByte()
                        rgb[3 * x] = l; rgb[3 * x + 1] = l; rgb[3 * x + 2] = l
                    } else {
                        rgb[3 * x] = r.toByte(); rgb[3 * x + 1] = gr.toByte(); rgb[3 * x + 2] = b.toByte()
                    }
                }
            } else {
                for (x in 0 until w) { rgb[3 * x] = -1; rgb[3 * x + 1] = -1; rgb[3 * x + 2] = -1 }
            }
            val n = rle(rgb, w, packed)
            sendLine(out, 0, y, packed, n, compressed = true)
        }
        command(out, 'p', "endp", byteArrayOf(pagesAfter.coerceIn(0, 99).toByte()))
    }

    fun endJob(out: OutputStream) {
        command(out, 'j', "endj", ByteArray(0))
        out.write(INIT_PRINTER)
        out.write(ENTER_REMOTE)
        out.write(byteArrayOf('L'.code.toByte(), 'D'.code.toByte(), 0, 0))
        remote(out, "JE", ByteArray(0))
        out.write(EXIT_REMOTE)
    }

    /** dsnd: x, y (big-endian 16-bit), compression flag, payload length (big-endian 16-bit), payload. */
    internal fun sendLine(out: OutputStream, x: Int, y: Int, data: ByteArray, len: Int, compressed: Boolean) {
        val body = ByteArrayOutputStream(len + 7)
        be16(body, x); be16(body, y)
        body.write(if (compressed) 1 else 0)
        be16(body, len)
        body.write(data, 0, len)
        command(out, 'd', "dsnd", body.toByteArray())
    }

    internal fun command(out: OutputStream, cls: Char, name: String, data: ByteArray) {
        out.write(ESC)
        out.write(cls.code)
        val n = data.size
        out.write(n and 0xFF); out.write((n shr 8) and 0xFF); out.write((n shr 16) and 0xFF); out.write((n shr 24) and 0xFF)
        out.write(name.toByteArray(Charsets.US_ASCII))
        out.write(data)
    }

    internal fun remote(out: OutputStream, name: String, data: ByteArray) {
        out.write(name.toByteArray(Charsets.US_ASCII))
        val n = data.size + 1
        out.write(n and 0xFF); out.write((n shr 8) and 0xFF)
        out.write(0)
        out.write(data)
    }

    /**
     * Run-length encoding in whole RGB pixels, as ESC/P-R expects: a control byte 0..127 means the next
     * n+1 pixels are literal; 129..255 means the next pixel repeats 257-n times. Runs are capped at 128
     * so the value 128 is never used. Returns the number of bytes written to [dst].
     */
    fun rle(src: ByteArray, pixels: Int, dst: ByteArray): Int {
        var i = 0
        var o = 0
        fun same(a: Int, b: Int) = src[3 * a] == src[3 * b] && src[3 * a + 1] == src[3 * b + 1] && src[3 * a + 2] == src[3 * b + 2]
        while (i < pixels) {
            var run = 1
            while (i + run < pixels && run < 128 && same(i, i + run)) run++
            if (run >= 2) {
                dst[o++] = (257 - run).toByte()
                dst[o++] = src[3 * i]; dst[o++] = src[3 * i + 1]; dst[o++] = src[3 * i + 2]
                i += run
            } else {
                var lit = 1
                while (i + lit < pixels && lit < 128 && !(i + lit + 1 < pixels && same(i + lit, i + lit + 1))) lit++
                dst[o++] = (lit - 1).toByte()
                System.arraycopy(src, 3 * i, dst, o, 3 * lit)
                o += 3 * lit
                i += lit
            }
        }
        return o
    }

    /** Decoder for the line format above (tests). */
    fun unrle(src: ByteArray, len: Int, pixels: Int): ByteArray {
        val out = ByteArray(pixels * 3)
        var p = 0
        var o = 0
        while (p < len && o < out.size) {
            val c = src[p++].toInt() and 0xFF
            if (c <= 127) {
                val n = (c + 1) * 3
                System.arraycopy(src, p, out, o, n); p += n; o += n
            } else {
                val n = 257 - c
                repeat(n) { out[o++] = src[p]; out[o++] = src[p + 1]; out[o++] = src[p + 2] }
                p += 3
            }
        }
        return out
    }

    private fun be16(o: OutputStream, v: Int) { o.write((v shr 8) and 0xFF); o.write(v and 0xFF) }
    private fun be32(o: OutputStream, v: Int) { o.write((v ushr 24) and 0xFF); o.write((v shr 16) and 0xFF); o.write((v shr 8) and 0xFF); o.write(v and 0xFF) }
}
