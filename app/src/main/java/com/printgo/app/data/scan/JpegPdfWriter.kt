package com.printgo.app.data.scan

import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import kotlin.math.min

/** Size and colour layout of a JPEG file, read from its frame header. */
data class JpegInfo(val width: Int, val height: Int, val components: Int) {
    companion object {
        /** Reads the SOF marker; null if the bytes are not a JPEG we understand. */
        fun read(bytes: ByteArray): JpegInfo? {
            fun u8(i: Int) = bytes[i].toInt() and 0xFF
            if (bytes.size < 4 || u8(0) != 0xFF || u8(1) != 0xD8) return null
            var i = 2
            while (i + 3 < bytes.size) {
                if (u8(i) != 0xFF) { i++; continue }
                val marker = u8(i + 1)
                if (marker == 0xFF) { i++; continue }
                if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) { i += 2; continue }
                val len = (u8(i + 2) shl 8) or u8(i + 3)
                val isSof = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
                if (isSof && i + 9 < bytes.size) {
                    val h = (u8(i + 5) shl 8) or u8(i + 6)
                    val w = (u8(i + 7) shl 8) or u8(i + 8)
                    return JpegInfo(w, h, u8(i + 9))
                }
                if (marker == 0xDA) return null
                i += 2 + len
            }
            return null
        }

        fun read(file: File): JpegInfo? {
            // The frame header is near the start, after the EXIF/ICC blocks (which can be up to 64 KB each).
            val head = ByteArray(min(file.length(), 512L * 1024).toInt())
            var n = 0
            file.inputStream().use { s ->
                while (n < head.size) {
                    val r = s.read(head, n, head.size - n)
                    if (r < 0) break
                    n += r
                }
            }
            return read(head.copyOf(n))
        }
    }
}

/**
 * Writes a PDF where every page shows one JPEG picture, fitted inside the page with a margin and centred.
 * The JPEG data is copied unchanged (DCTDecode), so the file stays small and sharp. Pages of landscape
 * pictures are turned to landscape. Pure Kotlin; read back by Android's PdfRenderer for printing.
 */
object JpegPdfWriter {

    /** Page sizes in points (1/72 inch). */
    fun mmToPt(mm: Double) = mm * 72.0 / 25.4

    fun write(out: OutputStream, images: List<File>, pageWidthMm: Double, pageHeightMm: Double, marginMm: Double) {
        require(images.isNotEmpty()) { "No pages" }
        val infos = images.map { JpegInfo.read(it) ?: throw IllegalArgumentException("Not a JPEG picture: ${it.name}") }
        val counter = CountingStream(out)
        val offsets = HashMap<Int, Long>()
        fun ascii(s: String) = counter.write(s.toByteArray(Charsets.ISO_8859_1))
        fun startObj(n: Int) { offsets[n] = counter.count; ascii("$n 0 obj\n") }

        ascii("%PDF-1.4\n%âãÏÓ\n")
        val pageObjs = images.indices.map { 3 + it * 3 }
        startObj(1); ascii("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        startObj(2)
        ascii("<< /Type /Pages /Count ${images.size} /Kids [${pageObjs.joinToString(" ") { "$it 0 R" }}] >>\nendobj\n")

        images.forEachIndexed { idx, file ->
            val info = infos[idx]
            val landscape = info.width > info.height
            val shortPt = mmToPt(min(pageWidthMm, pageHeightMm)); val longPt = mmToPt(maxOf(pageWidthMm, pageHeightMm))
            val pw = if (landscape) longPt else shortPt
            val ph = if (landscape) shortPt else longPt
            val m = mmToPt(marginMm)
            val box = ScanGeometry.fitInside(info.width.toDouble(), info.height.toDouble(), pw - 2 * m, ph - 2 * m)
            val x = m + box[0]; val y = m + box[1]; val w = box[2]; val h = box[3]
            val pageObj = 3 + idx * 3; val contentObj = pageObj + 1; val imageObj = pageObj + 2
            startObj(pageObj)
            ascii(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${f(pw)} ${f(ph)}] " +
                    "/Resources << /XObject << /Im0 $imageObj 0 R >> >> /Contents $contentObj 0 R >>\nendobj\n"
            )
            val content = "q ${f(w)} 0 0 ${f(h)} ${f(x)} ${f(y)} cm /Im0 Do Q\n".toByteArray(Charsets.ISO_8859_1)
            startObj(contentObj)
            ascii("<< /Length ${content.size} >>\nstream\n")
            counter.write(content)
            ascii("endstream\nendobj\n")
            val cs = when (info.components) { 1 -> "/DeviceGray"; 4 -> "/DeviceCMYK"; else -> "/DeviceRGB" }
            startObj(imageObj)
            ascii(
                "<< /Type /XObject /Subtype /Image /Width ${info.width} /Height ${info.height} /ColorSpace $cs " +
                    "/BitsPerComponent 8 /Filter /DCTDecode /Length ${file.length()} >>\nstream\n"
            )
            file.inputStream().use { it.copyTo(counter) }
            ascii("\nendstream\nendobj\n")
        }

        val count = 3 + images.size * 3
        val xref = counter.count
        ascii("xref\n0 $count\n0000000000 65535 f \n")
        for (n in 1 until count) ascii("%010d 00000 n \n".format(offsets.getValue(n)))
        ascii("trailer\n<< /Size $count /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        counter.flush()
    }

    private fun f(v: Double): String = "%.2f".format(java.util.Locale.US, v)

    private class CountingStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    }
}
