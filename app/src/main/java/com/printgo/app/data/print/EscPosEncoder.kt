package com.printgo.app.data.print

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/** ESC/POS raster image output for thermal receipt/label printers (Bluetooth / USB). */
object EscPosEncoder {

    val INIT = byteArrayOf(0x1B, 0x40)

    /** Scales to [targetWidthPx] (multiple of 8) and returns bitmap suitable for [rasterBytes]. */
    fun scaleForPaper(src: Bitmap, targetWidthPx: Int): Bitmap {
        val w = (targetWidthPx / 8) * 8
        if (src.width == w) return src
        val h = (src.height.toLong() * w / src.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    /** GS v 0 raster bit image commands, split into bands so small buffers do not overflow. */
    fun rasterBytes(bitmap: Bitmap, band: Int = 128): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val bytesPerRow = (w + 7) / 8
        val out = ByteArrayOutputStream()
        val pixels = IntArray(w)
        var y = 0
        while (y < h) {
            val rows = minOf(band, h - y)
            out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00))
            out.write(bytesPerRow and 0xFF); out.write((bytesPerRow shr 8) and 0xFF)
            out.write(rows and 0xFF); out.write((rows shr 8) and 0xFF)
            for (r in 0 until rows) {
                bitmap.getPixels(pixels, 0, w, 0, y + r, w, 1)
                val line = ByteArray(bytesPerRow)
                for (x in 0 until w) {
                    val p = pixels[x]
                    val lum = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                    if (lum < 140) line[x shr 3] = (line[x shr 3].toInt() or (0x80 shr (x and 7))).toByte()
                }
                out.write(line)
            }
            y += rows
        }
        return out.toByteArray()
    }

    fun feedAndCut(): ByteArray = byteArrayOf(0x0A, 0x0A, 0x0A, 0x1D, 0x56, 0x42, 0x00)
}
