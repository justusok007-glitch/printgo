package com.printgo.app.data.print

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** PJL + PCL 5 raster output, understood by nearly every HP-compatible laser printer and many inkjets. */
object PclEncoder {
    private const val ESC = 0x1B

    private fun OutputStream.esc(s: String) { write(ESC); write(s.toByteArray(Charsets.US_ASCII)) }

    fun paperSizeCode(widthMm: Int, heightMm: Int): Int = when {
        kotlin.math.abs(widthMm - 210) <= 3 && kotlin.math.abs(heightMm - 297) <= 3 -> 26  // A4
        kotlin.math.abs(widthMm - 216) <= 3 && kotlin.math.abs(heightMm - 279) <= 3 -> 2   // Letter
        kotlin.math.abs(widthMm - 216) <= 3 && kotlin.math.abs(heightMm - 356) <= 3 -> 3   // Legal
        kotlin.math.abs(widthMm - 148) <= 3 && kotlin.math.abs(heightMm - 210) <= 3 -> 25  // A5
        kotlin.math.abs(widthMm - 297) <= 3 && kotlin.math.abs(heightMm - 420) <= 3 -> 27  // A3
        else -> 26
    }

    fun start(out: OutputStream, copies: Int, duplex: Int, paperCode: Int, jobName: String) {
        out.esc("%-12345X")
        out.write("@PJL JOB NAME=\"${jobName.replace('"', '\'').take(40)}\"\r\n".toByteArray())
        out.write("@PJL ENTER LANGUAGE=PCL\r\n".toByteArray())
        out.esc("E")
        out.esc("&l${paperCode}A")
        out.esc("&l0O")
        out.esc("&l${copies.coerceIn(1, 99)}X")
        out.esc("&l${duplex}S")   // 0 simplex, 1 long-edge, 2 short-edge
    }

    /** Writes one page as 1-bit (ordered-dither) raster at [dpi]. */
    fun writePage(out: OutputStream, bmp: Bitmap, dpi: Int) {
        out.esc("*t${dpi}R")
        out.esc("*r0F")
        out.esc("*p0x0Y")
        out.esc("*r1A")
        out.esc("*b0M")
        val w = bmp.width
        val bytesPerRow = (w + 7) / 8
        val px = IntArray(w)
        val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
        for (y in 0 until bmp.height) {
            bmp.getPixels(px, 0, w, 0, y, w, 1)
            val line = ByteArray(bytesPerRow)
            for (x in 0 until w) {
                val p = px[x]
                val lum = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                val threshold = (bayer[(y and 3) * 4 + (x and 3)] * 16) + 8
                if (lum < threshold) line[x shr 3] = (line[x shr 3].toInt() or (0x80 shr (x and 7))).toByte()
            }
            out.esc("*b${bytesPerRow}W")
            out.write(line)
        }
        out.esc("*rB")
        out.write(0x0C) // form feed
    }

    fun end(out: OutputStream) {
        out.esc("E")
        out.esc("%-12345X")
        out.write("@PJL EOJ\r\n".toByteArray())
        out.esc("%-12345X")
    }

    fun parseCmd(deviceId: String?): Set<String> {
        if (deviceId == null) return emptySet()
        val m = Regex("(?:CMD|COMMAND SET|CLS_CMD):([^;]*)", RegexOption.IGNORE_CASE).find(deviceId) ?: return emptySet()
        return m.groupValues[1].split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
    }
}

/** Minimal PostScript level 2 image output for PostScript printers. */
object PostScriptEncoder {
    fun start(out: OutputStream, title: String) {
        out.write("%!PS-Adobe-3.0\n%%Title: ${title.replace('\n', ' ').take(60)}\n%%Pages: (atend)\n%%EndComments\n".toByteArray())
    }

    fun writePage(out: OutputStream, bmp: Bitmap, widthPt: Int, heightPt: Int, grayscale: Boolean, copies: Int, duplex: Int) {
        val w = bmp.width
        val h = bmp.height
        val comps = if (grayscale) 1 else 3
        val dup = when (duplex) { 1 -> "/Duplex true /Tumble false"; 2 -> "/Duplex true /Tumble true"; else -> "/Duplex false" }
        out.write("<< /PageSize [$widthPt $heightPt] /NumCopies $copies $dup >> setpagedevice\n".toByteArray())
        out.write("gsave $widthPt $heightPt scale\n".toByteArray())
        out.write("/DeviceRGB setcolorspace\n".toByteArray().takeIf { !grayscale } ?: "/DeviceGray setcolorspace\n".toByteArray())
        out.write(
            ("<< /ImageType 1 /Width $w /Height $h /BitsPerComponent 8 /Decode ${if (grayscale) "[0 1]" else "[0 1 0 1 0 1]"} " +
                "/ImageMatrix [$w 0 0 -$h 0 $h] /DataSource currentfile /ASCIIHexDecode filter >> image\n").toByteArray()
        )
        val hex = "0123456789ABCDEF".toByteArray()
        val px = IntArray(w)
        val buf = ByteArrayOutputStream()
        var col = 0
        for (y in 0 until h) {
            bmp.getPixels(px, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val p = px[x]
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                if (grayscale) {
                    val l = (r * 299 + g * 587 + b * 114) / 1000
                    buf.write(hex[l shr 4].toInt()); buf.write(hex[l and 15].toInt())
                } else {
                    buf.write(hex[r shr 4].toInt()); buf.write(hex[r and 15].toInt())
                    buf.write(hex[g shr 4].toInt()); buf.write(hex[g and 15].toInt())
                    buf.write(hex[b shr 4].toInt()); buf.write(hex[b and 15].toInt())
                }
                col += comps * 2
                if (col >= 72) { buf.write('\n'.code); col = 0 }
            }
            if (buf.size() > 64 * 1024) { buf.writeTo(out); buf.reset() }
        }
        buf.write(">\n".toByteArray())
        buf.writeTo(out)
        out.write("grestore showpage\n".toByteArray())
    }

    fun end(out: OutputStream) { out.write("%%Trailer\n%%EOF\n".toByteArray()) }
}
