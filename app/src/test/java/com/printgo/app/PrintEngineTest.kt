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
