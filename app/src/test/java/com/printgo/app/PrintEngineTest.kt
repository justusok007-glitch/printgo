package com.printgo.app

import com.printgo.app.data.ipp.IppDecoder
import com.printgo.app.data.ipp.IppJobOptions
import com.printgo.app.data.ipp.IppPrintClient
import com.printgo.app.data.print.IppMedia
import com.printgo.app.data.print.PwgRasterEncoder
import com.printgo.app.model.DuplexMode
import com.printgo.app.model.PageRangeParser
import com.printgo.app.model.PageRangeResult
import com.printgo.app.model.PaperSize
import com.printgo.app.model.PrintColorMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class PrintEngineTest {

    @Test
    fun pwgRowEncodingRoundTrips() {
        val rnd = Random(42)
        for (bpp in listOf(1, 3)) {
            val widths = listOf(1, 2, 3, 17, 128, 129, 300)
            for (w in widths) {
                // mix of runs and noise
                val row = ByteArray(w * bpp)
                var i = 0
                while (i < w) {
                    val run = if (rnd.nextBoolean()) rnd.nextInt(1, 40) else 1
                    val px = ByteArray(bpp) { rnd.nextInt(256).toByte() }
                    for (r in 0 until run) {
                        if (i >= w) break
                        px.copyInto(row, i * bpp)
                        i++
                    }
                }
                val out = ByteArrayOutputStream()
                PwgRasterEncoder.encodeRow(out, row, bpp)
                val decoded = PwgRasterEncoder.decodeRow(out.toByteArray(), 0, w, bpp)
                assertArrayEquals("bpp=$bpp w=$w", row, decoded)
            }
        }
    }

    @Test
    fun pwgHeaderHasExpectedLayout() {
        val h = PwgRasterEncoder.buildPageHeader(
            widthPx = 2480, heightPx = 3508, dpi = 300, grayscale = false,
            duplexTwoSided = true, tumble = false, copies = 1, pageSizePtW = 595, pageSizePtH = 842, quality = 4
        )
        assertEquals(PwgRasterEncoder.HEADER_SIZE, h.size)
        assertEquals("PwgRaster", String(h, 0, 9, Charsets.US_ASCII))
        fun int(o: Int) = ((h[o].toInt() and 0xFF) shl 24) or ((h[o + 1].toInt() and 0xFF) shl 16) or
            ((h[o + 2].toInt() and 0xFF) shl 8) or (h[o + 3].toInt() and 0xFF)
        assertEquals(1, int(272))      // duplex
        assertEquals(300, int(276))    // HWResolution
        assertEquals(2480, int(372))   // width
        assertEquals(3508, int(376))   // height
        assertEquals(24, int(388))     // bits per pixel
        assertEquals(2480 * 3, int(392))
        assertEquals(19, int(400))     // sRGB
    }

    @Test
    fun printJobRequestCarriesSettings() {
        val client = IppPrintClient()
        val bytes = client.buildPrintJob(
            "ipp://printer.local:631/ipp/print", "application/pdf",
            IppJobOptions(
                jobName = "Report", copies = 3, duplex = DuplexMode.TWO_SIDED_LONG_EDGE,
                colorMode = PrintColorMode.GRAYSCALE, mediaKeyword = "iso_a4_210x297mm",
                supportedSides = listOf("one-sided", "two-sided-long-edge")
            )
        )
        val msg = IppDecoder.decode(bytes)
        assertEquals(IppPrintClient.OP_PRINT_JOB, msg.code)
        assertEquals("application/pdf", msg.getString("document-format"))
        assertEquals("Report", msg.getString("job-name"))
        assertEquals(3, msg.getInt("copies"))
        assertEquals("two-sided-long-edge", msg.getString("sides"))
        assertEquals("monochrome", msg.getString("print-color-mode"))
        assertEquals("iso_a4_210x297mm", msg.getString("media"))
    }

    @Test
    fun unsupportedDuplexIsNotRequested() {
        val bytes = IppPrintClient().buildPrintJob(
            "ipp://p/ipp/print", "application/pdf",
            IppJobOptions("x", duplex = DuplexMode.TWO_SIDED_SHORT_EDGE, supportedSides = listOf("one-sided"))
        )
        val msg = IppDecoder.decode(bytes)
        assertNull(msg.getString("sides"))
    }

    @Test
    fun mediaKeywordMapping() {
        assertEquals("iso_a4_210x297mm", IppMedia.keywordFor(PaperSize("A4", 210, 297)))
        assertEquals("na_letter_8.5x11in", IppMedia.keywordFor(PaperSize("Letter", 216, 279)))
        assertNull(IppMedia.keywordFor(PaperSize("Weird", 123, 456)))
    }

    @Test
    fun friendlyReasons() {
        assertNotNull(IppPrintClient.friendlyReason("media-empty-error"))
        assertNotNull(IppPrintClient.friendlyReason("media-jam-error"))
        assertNull(IppPrintClient.friendlyReason("toner-low-warning"))
        assertNull(IppPrintClient.friendlyReason("none"))
    }

    @Test
    fun pageRangeParsing() {
        val ok = PageRangeParser.parse("1-3,5,3", 10)
        assertTrue(ok is PageRangeResult.Success)
        assertEquals(listOf(1, 2, 3, 5), (ok as PageRangeResult.Success).pages)
        assertTrue(PageRangeParser.parse("0-2", 10) is PageRangeResult.Error)
        assertTrue(PageRangeParser.parse("5-2", 10) is PageRangeResult.Error)
        assertTrue(PageRangeParser.parse("1,,2", 10) is PageRangeResult.Error)
        assertTrue(PageRangeParser.parse("12", 10) is PageRangeResult.Error)
        assertTrue(PageRangeParser.parse("a", 10) is PageRangeResult.Error)
    }
}

class RawLanguageTest {
    @Test
    fun deviceIdCommandSetsAreParsed() {
        val id = "MFG:HP;MDL:LaserJet Pro M404;CMD:PJL,PCL,PCLXL,POSTSCRIPT,PDF;CLS:PRINTER;"
        val cmd = com.printgo.app.data.print.PclEncoder.parseCmd(id)
        assertTrue("PDF" in cmd)
        assertTrue("POSTSCRIPT" in cmd)
        assertTrue("PCL" in cmd)
        assertTrue(com.printgo.app.data.print.PclEncoder.parseCmd("MFG:X;MDL:Y;").isEmpty())
        assertTrue(com.printgo.app.data.print.PclEncoder.parseCmd(null).isEmpty())
    }

    @Test
    fun pclPaperCodes() {
        assertEquals(26, com.printgo.app.data.print.PclEncoder.paperSizeCode(210, 297))
        assertEquals(2, com.printgo.app.data.print.PclEncoder.paperSizeCode(216, 279))
    }
}

class UsbSetupTest {
    @Test
    fun deviceIdFieldsAreParsed() {
        val f = com.printgo.app.data.print.UsbAutoSetup.parseDeviceId("MFG:Hewlett-Packard;MDL:HP LaserJet M404;CMD:PJL,PCL;SN:ABC123;")
        assertEquals("Hewlett-Packard", f["MFG"])
        assertEquals("HP LaserJet M404", f["MDL"])
        assertEquals("ABC123", f["SN"])
        assertTrue(com.printgo.app.data.print.UsbAutoSetup.parseDeviceId(null).isEmpty())
    }
}

class FormatChoiceTest {
    @Test
    fun rasterIsPreferredOverPdf() {
        // The order used by the sender: raster formats first so non-PDF printers never get PDF source.
        val ordered = listOf("image/pwg-raster", "image/jpeg", "application/pdf")
        val live = listOf("application/pdf", "image/jpeg")
        assertEquals(listOf("image/jpeg", "application/pdf"), ordered.filter { it in live })
    }
}

class NetworkTransportTest {

    /** Fake RFC 1179 server: acks every step and records what arrives. */
    @Test
    fun lpdSendsQueueDataAndControlFile() {
        val server = java.net.ServerSocket(0)
        val received = java.util.concurrent.atomic.AtomicReference<ByteArray>()
        val t = Thread {
            server.accept().use { c ->
                val inp = java.io.DataInputStream(c.getInputStream())
                val out = c.getOutputStream()
                val log = ByteArrayOutputStream()
                fun line(): String { val sb = StringBuilder(); while (true) { val b = inp.read(); if (b < 0 || b == '\n'.code) break; sb.append(b.toChar()) }; return sb.toString() }
                val cmd = line(); log.write((cmd + "\n").toByteArray()); out.write(0)
                repeat(2) {
                    val sub = line(); log.write((sub + "\n").toByteArray()); out.write(0)
                    val len = sub.substring(1).substringBefore(' ').toInt()
                    val data = ByteArray(len); inp.readFully(data); log.write(data)
                    check(inp.read() == 0); out.write(0)
                }
                received.set(log.toByteArray())
            }
        }
        t.start()
        val f = java.io.File.createTempFile("job", ".pcl")
        val payload = byteArrayOf(0x1B, 'E'.code.toByte(), 0, 10, 13, 0x0C, -1)
        f.writeBytes(payload)
        com.printgo.app.data.print.Transports.sendLpd("127.0.0.1", server.localPort, "raw", "Test job", f) { }
        t.join(5000)
        server.close()
        val text = String(received.get(), Charsets.ISO_8859_1)
        assertTrue(text.startsWith("\u0002raw\n"))
        assertTrue(text.contains("\u0003${payload.size} dfA"))
        assertTrue(String(received.get(), Charsets.ISO_8859_1).contains(String(payload, Charsets.ISO_8859_1)))
        assertTrue(text.contains("\nldfA"))
        assertTrue(text.contains("JTest job\n"))
        f.delete()
    }

    @Test
    fun pclHeaderClearsTopMarginAndOffsetsMatchPaper() {
        val out = ByteArrayOutputStream()
        com.printgo.app.data.print.PclEncoder.start(out, 1, 0, 26, "x")
        val s = String(out.toByteArray(), Charsets.ISO_8859_1)
        assertTrue(s.startsWith("\u001BE"))
        assertTrue(s.contains("\u001B&l26A"))
        assertTrue(s.indexOf("\u001B&l0E") > s.indexOf("\u001B&l0O"))
        assertEquals(71, com.printgo.app.data.print.PclEncoder.logicalOffset300(210))
        assertEquals(75, com.printgo.app.data.print.PclEncoder.logicalOffset300(216))
    }
}
