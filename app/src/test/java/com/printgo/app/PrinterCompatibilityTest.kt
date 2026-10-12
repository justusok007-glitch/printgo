package com.printgo.app

import com.printgo.app.data.discovery.SnmpDeviceId
import com.printgo.app.data.ipp.IppDecoder
import com.printgo.app.data.ipp.IppJobOptions
import com.printgo.app.data.ipp.IppPrintClient
import com.printgo.app.data.ipp.IppProbe
import com.printgo.app.data.print.EscprEncoder
import com.printgo.app.data.print.IntArrayRowSource
import com.printgo.app.data.print.IppFormatPlanner
import com.printgo.app.data.print.LanguagePicker
import com.printgo.app.data.print.PwgRasterEncoder
import com.printgo.app.data.print.RasterRows
import com.printgo.app.data.print.RawLanguage
import com.printgo.app.data.print.SendKind
import com.printgo.app.data.print.UrfEncoder
import com.printgo.app.model.DuplexMode
import com.printgo.app.model.PrintColorMode
import com.printgo.app.model.PrintSettings
import com.printgo.app.model.PrinterCapabilities
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Checks for the formats and choices that let PrintGo work with printers other than the owner's HP P2055dn. */
class PrinterCompatibilityTest {

    private fun be32(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
        ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
    private fun le32(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
        ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
    private fun be16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun testPage(w: Int, h: Int): IntArray {
        val r = kotlin.random.Random(7)
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            when {
                y < 3 -> -1
                y % 17 == 0 -> 0xFF000000.toInt()
                x in 10..20 -> 0xFF336699.toInt()
                y > h - 5 -> -1
                else -> (0xFF shl 24) or r.nextInt(0xFFFFFF)
            }
        }
    }

    /** Decodes PWG/URF rows (line repeat byte + pixel runs), checks them against the source, returns the end offset. */
    private fun checkRows(data: ByteArray, start: Int, w: Int, h: Int, src: IntArray, gray: Boolean): Int {
        val bpp = if (gray) 1 else 3
        var p = start
        var y = 0
        val expected = ByteArray(w * bpp)
        while (y < h) {
            val rep = data[p++].toInt() and 0xFF
            val (row, next) = RasterRows.decodeRow(data, p, w, bpp)
            p = next
            repeat(rep + 1) {
                RasterRows.toBytes(src.copyOfRange(y * w, (y + 1) * w), w, expected, gray)
                assertArrayEquals("row $y", expected, row)
                y++
            }
        }
        return p
    }

    @Test
    fun appleRasterIsLosslessWithCupsHeaderLayout() {
        for (gray in listOf(true, false)) {
            val w = 301; val h = 97
            val px = testPage(w, h)
            val out = ByteArrayOutputStream()
            UrfEncoder.writeDocumentStart(out, 2)
            repeat(2) { UrfEncoder.writePage(out, IntArrayRowSource(w, h, px), 300, gray, 1, 4) }
            val b = out.toByteArray()
            assertEquals("UNIRAST", String(b, 0, 7, Charsets.US_ASCII))
            assertEquals(0, b[7].toInt())
            assertEquals(2, be32(b, 8))
            val hd = 12
            assertEquals(if (gray) 8 else 24, b[hd].toInt() and 0xFF)
            assertEquals(if (gray) 0 else 1, b[hd + 1].toInt())
            assertEquals(3, b[hd + 2].toInt())   // long-edge duplex
            assertEquals(4, b[hd + 3].toInt())   // normal quality
            assertEquals(w, be32(b, hd + 12)); assertEquals(h, be32(b, hd + 16)); assertEquals(300, be32(b, hd + 20))
            val end1 = checkRows(b, hd + 32, w, h, px, gray)
            val end2 = checkRows(b, end1 + 32, w, h, px, gray)
            assertEquals(b.size, end2)
        }
    }

    @Test
    fun pwgRasterPageIsLossless() {
        val w = 250; val h = 60
        val px = testPage(w, h)
        val out = ByteArrayOutputStream()
        PwgRasterEncoder.writeDocumentStart(out)
        val hdr = PwgRasterEncoder.buildPageHeader(w, h, 600, true, false, false, 1, 595, 841, 4, 1, "iso_a4_210x297mm")
        PwgRasterEncoder.writePage(out, IntArrayRowSource(w, h, px), hdr, true)
        val b = out.toByteArray()
        assertEquals("RaS2", String(b, 0, 4, Charsets.US_ASCII))
        assertEquals(600, be32(b, 4 + 276))
        assertEquals(18, be32(b, 4 + 400))
        assertEquals("iso_a4_210x297mm", String(b, 4 + 1732, 16, Charsets.US_ASCII))
        assertEquals(b.size, checkRows(b, 4 + PwgRasterEncoder.HEADER_SIZE, w, h, px, true))
    }

    @Test
    fun escprJobHasEpsonCommandSequence() {
        val a4 = EscprEncoder.geometry(210, 297, 360)
        assertEquals(2976, a4.paperW); assertEquals(4209, a4.paperH); assertEquals(42, a4.top); assertEquals(2892, a4.printW)

        val sw = 200; val sh = 120
        val g = EscprEncoder.Geometry(360, sw, sh, 5, 7, sw - 14, sh - 10)
        val px = testPage(sw, sh)
        val out = ByteArrayOutputStream()
        EscprEncoder.startJob(out, g, true, EscprEncoder.QUALITY_NORMAL, intArrayOf(2026, 10, 12, 9, 30, 5))
        EscprEncoder.writePage(out, IntArrayRowSource(sw, sh, px), g, 1, 1, false)
        EscprEncoder.writePage(out, IntArrayRowSource(sw, sh, px), g, 2, 0, true)
        EscprEncoder.endJob(out)
        val b = out.toByteArray()

        val exitPacket = byteArrayOf(0, 0, 0, 0x1B, 1) + "@EJL 1284.4\n@EJL     \n".toByteArray()
        assertArrayEquals(exitPacket, b.copyOfRange(0, exitPacket.size))
        var p = exitPacket.size + 2
        assertEquals("\u001B(R\u0008\u0000\u0000REMOTE1", String(b, p, 13, Charsets.ISO_8859_1)); p += 13
        val remote = mutableListOf<String>()
        while (!(b[p] == 0x1B.toByte() && b[p + 1] == 0.toByte())) {
            val len = (b[p + 2].toInt() and 0xFF) or ((b[p + 3].toInt() and 0xFF) shl 8)
            remote += String(b, p, 2, Charsets.US_ASCII) + "/" + len
            p += 4 + len
        }
        assertEquals(listOf("TI/8", "JS/4", "JH/14", "HD/3", "PP/3"), remote)
        p += 4
        assertEquals("\u001B(R\u0006\u0000\u0000ESCPR", String(b, p, 11, Charsets.ISO_8859_1)); p += 11

        val names = mutableListOf<String>()
        var page = 0; var lines = 0
        val linesPerPage = mutableListOf<Int>()
        while (b[p + 1] != 0x40.toByte()) {
            assertEquals(0x1B, b[p].toInt())
            val len = le32(b, p + 2)
            val name = String(b, p + 6, 4, Charsets.US_ASCII)
            val d = b.copyOfRange(p + 10, p + 10 + len)
            when (name) {
                "setq" -> { assertEquals(9, len); assertEquals(1, d[1].toInt()); assertEquals(0, d[2].toInt()) }
                "setj" -> {
                    assertEquals(22, len)
                    assertEquals(sw, be32(d, 0)); assertEquals(sh, be32(d, 4))
                    assertEquals(5, be16(d, 8)); assertEquals(7, be16(d, 10))
                    assertEquals(sw - 14, be32(d, 12)); assertEquals(sh - 10, be32(d, 16))
                    assertEquals(0, d[20].toInt()) // 360 dpi
                }
                "sttp" -> { page++; lines = 0 }
                "setn" -> assertEquals(page, d[0].toInt())
                "endp" -> { linesPerPage += lines; assertEquals(if (page == 1) 1 else 0, d[0].toInt()) }
                "dsnd" -> {
                    assertEquals(lines, be16(d, 2))
                    assertEquals(1, d[4].toInt())
                    val plen = be16(d, 5)
                    assertEquals(len - 7, plen)
                    val rgb = EscprEncoder.unrle(d.copyOfRange(7, d.size), plen, g.printW)
                    val sx = g.left + 3; val s = px[(lines + g.top) * sw + sx]
                    if (page == 1) assertEquals((s shr 16) and 0xFF, rgb[9].toInt() and 0xFF)
                    lines++
                }
            }
            if (name != "dsnd") names += name
            p += 10 + len
        }
        assertEquals(listOf("setq", "setj", "sttp", "setn", "endp", "sttp", "setn", "endp", "endj"), names)
        assertEquals(listOf(g.printH, g.printH), linesPerPage)
        assertEquals("\u001B@\u001B(R\u0008\u0000\u0000REMOTE1LD\u0000\u0000JE\u0001\u0000\u0000\u001B\u0000\u0000\u0000",
            String(b, p, b.size - p, Charsets.ISO_8859_1))
    }

    @Test
    fun escprRunLengthRoundTrips() {
        val rnd = kotlin.random.Random(3)
        for (w in listOf(1, 2, 127, 128, 129, 300, 1000)) {
            val row = ByteArray(w * 3)
            var i = 0
            while (i < w) {
                val run = if (rnd.nextBoolean()) rnd.nextInt(1, 300) else 1
                val c = ByteArray(3) { rnd.nextInt(256).toByte() }
                repeat(run) { if (i < w) { c.copyInto(row, i * 3); i++ } }
            }
            val packed = ByteArray(row.size * 2 + 8)
            val n = EscprEncoder.rle(row, w, packed)
            assertArrayEquals("w=$w", row, EscprEncoder.unrle(packed, n, w))
            assertTrue((0 until n).none { false })
        }
    }

    @Test
    fun rawLanguageComesFromThePrinterNotTheBrand() {
        assertEquals(RawLanguage.ESCPR, LanguagePicker.pick("MFG:EPSON;CMD:ESCPL2,BDC,D4,D4PX,ESCPR7,END4,GENEP,URF;MDL:L455 Series;", "EPSON L455", "AUTO", strict = true))
        assertEquals(RawLanguage.PCL, LanguagePicker.pick("MFG:Hewlett-Packard;CMD:PJL,MLC,BIDI-ECP,PCL,PCLXL,POSTSCRIPT,PDF;MDL:HP LaserJet P3010 Series;", "HP", "AUTO", strict = true))
        assertEquals(RawLanguage.PCL, LanguagePicker.pick("MFG:HP;MDL:LaserJet Pro M404;CMD:PJL,PCL,PCLXL,POSTSCRIPT,PDF;", "HP", "AUTO", strict = true))
        // HP inkjets list PCL3GUI / PCLm, which are not PCL 5: never send them PCL.
        assertNull(LanguagePicker.pick("MFG:HP;CMD:PCL3GUI,PJL,Automatic,JPEG,PCLM,AppleRaster,PWGRaster;MDL:DeskJet 2700;", "HP DeskJet", "AUTO", strict = true))
        // The verified HP P2055dn route (raw PCL by name) is unchanged.
        assertEquals(RawLanguage.PCL, LanguagePicker.pick(null, "HP LaserJet P2055dn", "AUTO"))
        assertEquals(RawLanguage.ESCPR, LanguagePicker.pick(null, "EPSON L455 Series", "AUTO"))
        assertNull(LanguagePicker.pick(null, "Canon G3010 series", "AUTO"))
        assertEquals(RawLanguage.PCL, LanguagePicker.pick(null, "Printer", "AUTO", formats = listOf("application/vnd.hp-PCL", "application/pdf")))
        assertNull(LanguagePicker.pick(null, "Printer", "AUTO", formats = listOf("application/pdf")))
        assertEquals(RawLanguage.ESCPOS, LanguagePicker.pick(null, "EPSON TM-T20II", "AUTO", strict = true))
        assertEquals(RawLanguage.ESCPR, LanguagePicker.pick(null, "Anything", "ESCPR"))
    }

    private val m404 = IppProbe(
        formats = listOf("application/vnd.hp-PCL", "application/vnd.hp-PCLXL", "application/postscript", "application/pdf",
            "image/jpeg", "image/urf", "image/pwg-raster", "application/PCLm", "application/octet-stream"),
        path = "ipp/print",
        pwgResolutions = listOf(600), pwgTypes = listOf("sgray_8", "srgb_8"),
        urf = listOf("CP1", "IS1", "MT1-2-3-4-5-8-10-11-12-13-14-15-16-18", "PQ4", "RS600", "SRGB24", "V1.4", "W8", "DM1"),
        sides = listOf("one-sided", "two-sided-long-edge", "two-sided-short-edge"),
        colorModes = listOf("monochrome", "auto"), qualities = listOf(4), media = listOf("iso_a4_210x297mm", "na_letter_8.5x11in")
    )

    @Test
    fun formatsFollowWhatThePrinterAccepts() {
        val plan = IppFormatPlanner.plan(m404, "AUTO", wantGray = true, draft = false)
        assertEquals(listOf(SendKind.PWG, SendKind.URF, SendKind.PCL, SendKind.POSTSCRIPT, SendKind.PDF, SendKind.JPEG), plan.map { it.kind })
        assertEquals(600, plan[0].dpi)
        assertEquals("application/vnd.hp-PCL", plan[2].mime)

        // AirPrint-only Epson: Apple raster at its own resolution, in colour.
        val epson = IppProbe(listOf("application/octet-stream", "image/urf", "image/jpeg"), "ipp/print",
            urf = listOf("CP1", "MT1-3-8-10-11-12", "PQ3-4-5", "RS360-720", "SRGB24", "V1.4", "W8"))
        val ep = IppFormatPlanner.plan(epson, "AUTO", wantGray = false, draft = false)
        assertEquals(listOf(SendKind.URF, SendKind.JPEG), ep.map { it.kind })
        assertEquals(360, ep[0].dpi)

        // Old JetDirect IPP (e.g. LaserJet P3010): PCL rendered on the phone.
        val jet = IppProbe(listOf("application/octet-stream", "application/vnd.hp-PCL", "application/postscript", "text/plain"), "ipp/port1")
        assertEquals(listOf(SendKind.PCL, SendKind.POSTSCRIPT), IppFormatPlanner.plan(jet, "AUTO", true, false).map { it.kind })

        assertTrue(IppFormatPlanner.plan(IppProbe(listOf("image/pwg-raster"), "x", pwgTypes = listOf("black_1")), "AUTO", true, false).isEmpty())
        assertTrue(IppFormatPlanner.plan(null, "AUTO", true, false).none { it.kind == SendKind.URF || it.kind == SendKind.ESCPR })
    }

    @Test
    fun printJobOnlyAsksForOptionsThePrinterLists() {
        val client = IppPrintClient()
        val opts = IppJobOptions(
            jobName = "Doc", copies = 2, duplex = DuplexMode.TWO_SIDED_LONG_EDGE, colorMode = PrintColorMode.COLOR,
            mediaKeyword = "iso_a5_148x210mm", printScaling = null, probe = m404
        )
        val msg = IppDecoder.decode(client.buildPrintJob("ipp://p/ipp/print", "image/pwg-raster", opts))
        assertEquals("two-sided-long-edge", msg.getString("sides"))
        assertNull(msg.getString("print-color-mode"))   // mono printer: "color" not listed
        assertNull(msg.getString("media"))              // A5 not listed
        assertNull(msg.findAttribute("print-scaling"))
        assertEquals(4, msg.getInt("print-quality"))
        assertEquals(2, msg.getInt("copies"))

        val bare = IppDecoder.decode(client.buildPrintJob("ipp://p/ipp/print", "image/pwg-raster", opts.copy(minimal = true)))
        assertEquals("image/pwg-raster", bare.getString("document-format"))
        assertNull(bare.getString("sides"))
        assertNull(bare.findAttribute("print-quality"))
        assertEquals(2, bare.getInt("copies"))

        assertTrue(IppPrintClient.isFormatOrOptionRejection(0x040B))
        assertTrue(IppPrintClient.isFormatOrOptionRejection(IppPrintClient.TRANSFER_BROKEN))
        assertTrue(!IppPrintClient.isFormatOrOptionRejection(IppPrintClient.NO_CONNECTION))
    }

    @Test
    fun snmpDeviceIdRequestAndAnswer() {
        val req = SnmpDeviceId.buildGet(intArrayOf(1, 3, 6, 1, 2, 1, 1, 1, 0), "public", 1)
        assertEquals("302602010004067075626c6963a019020101020100020100300e300c06082b060102010101000500",
            req.joinToString("") { "%02x".format(it) })
        val id = "MFG:HP;CMD:PJL,PCL,POSTSCRIPT;MDL:LaserJet P3010;"
        fun tlv(t: Int, b: ByteArray) = byteArrayOf(t.toByte(), b.size.toByte()) + b
        val oid = SnmpDeviceId.encodeOid(intArrayOf(1, 3, 6, 1, 4, 1, 2699, 1, 2, 1, 2, 1, 1, 3, 1))
        val resp = tlv(0x30, tlv(0x02, byteArrayOf(0)) + tlv(0x04, "public".toByteArray()) +
            tlv(0xA2, tlv(0x02, byteArrayOf(1)) + tlv(0x02, byteArrayOf(0)) + tlv(0x02, byteArrayOf(0)) +
                tlv(0x30, tlv(0x30, tlv(0x06, oid) + tlv(0x04, byteArrayOf(0, 0x34) + id.toByteArray())))))
        assertEquals(id, SnmpDeviceId.parseResponse(resp)?.let { SnmpDeviceId.cleanDeviceId(it) })
    }

    @Test
    fun paperSizesComeFromTheMediaNames() {
        assertEquals(210 to 297, PrinterCapabilities.sizeFromMediaName("iso_a4_210x297mm"))
        assertEquals(216 to 279, PrinterCapabilities.sizeFromMediaName("na_letter_8.5x11in"))
        assertNull(PrinterCapabilities.sizeFromMediaName("custom_min_76.2x127mm"))
    }

    @Test
    fun twoSidedIsStillOfferedWhenThePrinterNamesNoTwoSidedMode() {
        // HP LaserJets often leave two-sided out of the Wi-Fi answer although they have a duplex unit.
        val silent = PrinterCapabilities(makeAndModel = "HP LaserJet P3010", state = "Idle", sidesSupported = listOf("one-sided"))
        assertTrue(!silent.twoSidedReported())
        val kept = PrintSettings().copy(duplex = DuplexMode.TWO_SIDED_LONG_EDGE).validateAgainst(silent)
        assertEquals(DuplexMode.TWO_SIDED_LONG_EDGE, kept.duplex)

        // A printer that does list modes is still respected.
        val longOnly = silent.copy(sidesSupported = listOf("one-sided", "two-sided-long-edge"))
        assertTrue(longOnly.twoSidedReported())
        val clamped = PrintSettings().copy(duplex = DuplexMode.TWO_SIDED_SHORT_EDGE).validateAgainst(longOnly)
        assertEquals(DuplexMode.TWO_SIDED_LONG_EDGE, clamped.duplex)

        // The job still carries the user's choice to such a printer.
        val jet = IppProbe(listOf("application/vnd.hp-PCL"), "ipp/port1", sides = listOf("one-sided"))
        val opts = IppJobOptions(jobName = "Doc", duplex = DuplexMode.TWO_SIDED_LONG_EDGE, probe = jet)
        val msg = IppDecoder.decode(IppPrintClient().buildPrintJob("ipp://p/ipp/print", "application/vnd.hp-PCL", opts))
        assertEquals("two-sided-long-edge", msg.getString("sides"))
    }
}
