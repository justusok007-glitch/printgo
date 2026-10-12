package com.printgo.app

import com.printgo.app.data.drivers.DriverCatalog
import com.printgo.app.data.drivers.DriverEntry
import com.printgo.app.data.drivers.DriverKind
import com.printgo.app.data.drivers.DriverMatcher
import com.printgo.app.data.drivers.DriverPacks
import com.printgo.app.data.print.BlankRowSource
import com.printgo.app.data.print.DriverJobSpec
import com.printgo.app.data.print.EscprStream
import com.printgo.app.data.print.IntArrayRowSource
import com.printgo.app.data.print.NativeDrivers
import com.printgo.app.data.print.PgrWriter
import com.printgo.app.data.print.SheetFlip
import com.printgo.app.model.DuplexMode
import com.printgo.app.model.PrintQuality
import com.printgo.app.viewmodel.DriverViewModel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The built-in printer drivers: matching a printer to a driver, page order, and the data handed to the programs. */
class DriverTest {

    private val catalog = listOf(
        DriverEntry("escpr:Epson-L455_Series", DriverKind.ESCPR, "Epson", "EPSON L455 Series",
            "MFG:Epson;MDL:L455 Series;DES:EPSON L455 Series;", color = true, duplex = false),
        DriverEntry("escpr:Epson-L4150_Series", DriverKind.ESCPR, "Epson", "EPSON L4150 Series",
            "MFG:Epson;MDL:L4150 Series;", color = true, duplex = false),
        DriverEntry("hp:hp-laserjet_p3010_series-pcl3", DriverKind.HPCUPS, "HP", "HP LaserJet p3010 Series",
            "MFG:Hewlett-Packard;MDL:hp laserjet p3010 series;DES:hp laserjet p3010 series;", color = false, duplex = true,
            file = "ppd/hp-laserjet_p3010_series-pcl3.ppd.gz"),
        DriverEntry("hp:hp-laserjet_p2055-pcl3", DriverKind.HPCUPS, "HP", "HP LaserJet p2055",
            "MFG:Hewlett-Packard;MDL:hp laserjet p2055;", color = false, duplex = true, file = "ppd/hp-laserjet_p2055-pcl3.ppd.gz"),
        DriverEntry("hp:hp-laserjet_p2055dn-pcl3", DriverKind.HPCUPS, "HP", "HP LaserJet p2055dn",
            "MFG:Hewlett-Packard;MDL:hp laserjet p2055dn;", color = false, duplex = true, file = "ppd/hp-laserjet_p2055dn-pcl3.ppd.gz"),
        DriverEntry("gp:canon-pixma-ip2700", DriverKind.GUTENPRINT, "Canon", "PIXMA iP2700", "", color = true, file = "canon-pixma-ip2700"),
        DriverEntry("sx:ml1640", DriverKind.SPLIX, "Samsung", "Samsung ML-1640", "MFG:Samsung;MDL:ML-1640 Series;", color = false,
            file = "ppd/ml1640.ppd.gz")
    )

    @Test
    fun matchesTheP3010FromItsDeviceId() {
        val id = "MFG:Hewlett-Packard;CMD:PJL,PML,PCLXL,POSTSCRIPT,PCL;MDL:HP LaserJet P3010 Series;CLS:PRINTER;"
        val best = DriverMatcher.best(id, null, catalog)
        assertEquals("hp:hp-laserjet_p3010_series-pcl3", best?.id)
    }

    @Test
    fun matchesByNameAndPrefersTheExactModel() {
        assertEquals("escpr:Epson-L455_Series", DriverMatcher.best(null, "EPSON L455 Series", catalog)?.id)
        assertEquals("hp:hp-laserjet_p2055dn-pcl3", DriverMatcher.best(null, "HP LaserJet P2055dn", catalog)?.id)
        assertEquals("sx:ml1640", DriverMatcher.best("MFG:Samsung;MDL:ML-1640 Series;", null, catalog)?.id)
    }

    @Test
    fun doesNotCrossMakersOrGuessLoosely() {
        // A Canon name must not pick an HP or Epson driver.
        assertNull(DriverMatcher.best(null, "Canon LBP2900", catalog))
        // "L45" is too far from L455 to be used automatically.
        assertNull(DriverMatcher.best(null, "Epson L45", catalog))
        assertTrue(DriverMatcher.candidates(null, "HP LaserJet P3010", catalog).all { it.entry.kind == DriverKind.HPCUPS })
    }

    @Test
    fun parsesIeeeDeviceIds() {
        val ieee = DriverMatcher.parseIeee("MANUFACTURER:EPSON;COMMAND SET:ESCPL2,BDC,D4,ESCPR1;MODEL:L455 Series;")
        assertEquals("EPSON", ieee.make)
        assertEquals("L455 Series", ieee.model)
        assertTrue("ESCPR1" in ieee.commands)
        assertEquals("hp", DriverMatcher.normMake("Hewlett-Packard"))
        assertEquals("laserjetp3010", DriverMatcher.normModel("HP LaserJet P3010 Series"))
    }

    @Test
    fun suggestionsMergeEveryNameAndSearchFindsModels() {
        val s = DriverViewModel.suggest(null, listOf("HP LaserJet P3010 Series", "Office laser"), catalog)
        assertEquals("hp:hp-laserjet_p3010_series-pcl3", s.first().entry.id)
        assertTrue(s.first().exact)
        val (hits, more) = DriverViewModel.search("laserjet 2055", catalog)
        assertEquals(listOf("hp:hp-laserjet_p2055-pcl3", "hp:hp-laserjet_p2055dn-pcl3"), hits.map { it.id })
        assertFalse(more)
        assertTrue(DriverViewModel.search("  ", catalog).first.isEmpty())
        assertEquals("gp:canon-pixma-ip2700", DriverViewModel.search("ip2700", catalog).first.single().id)
    }

    @Test
    fun pageOrderForCopiesAndTwoSided() {
        assertEquals(listOf(0, 1, 2, 0, 1, 2), NativeDrivers.pageOrder(3, 2, collate = true))
        assertEquals(listOf(0, 0, 1, 1, 2, 2), NativeDrivers.pageOrder(3, 2, collate = false))
        // Two-sided copies start on a new sheet: a blank back after an odd page count (not after the last copy).
        val b = NativeDrivers.BLANK
        assertEquals(listOf(0, 1, 2, b, 0, 1, 2), NativeDrivers.pageOrder(3, 2, collate = false, twoSided = true))
        assertEquals(listOf(0, 1, 0, 1), NativeDrivers.pageOrder(2, 2, collate = true, twoSided = true))
        assertEquals(listOf(0, 1, 2), NativeDrivers.pageOrder(3, 1, collate = true, twoSided = true))
        assertTrue(NativeDrivers.pageOrder(0, 3, collate = true).isEmpty())
    }

    @Test
    fun readsTheDriverQueryAnswer() {
        val q = NativeDrivers.parseQuery(listOf("hwres=1200x600", "duplex=1", "backside=rotate"), draft = false)
        assertEquals(600, q.dpi)
        assertEquals(SheetFlip.ROTATE_180, q.backSide)
        val d = NativeDrivers.parseQuery(listOf("hwres=600x600", "backside=none"), draft = true)
        assertEquals(300, d.dpi)
        assertEquals(SheetFlip.NONE, d.backSide)
        assertEquals(300, NativeDrivers.parseQuery(emptyList(), draft = false).dpi)
        assertEquals(SheetFlip.MIRROR_Y, SheetFlip.fromQuery("flipy"))
        assertEquals(SheetFlip.MIRROR_X, SheetFlip.fromQuery("flipx"))
    }

    @Test
    fun turnedBackSidesPointAtTheRightPixels() {
        assertEquals(9 to 19, SheetFlip.sourceOf(SheetFlip.ROTATE_180, 0, 0, 10, 20))
        assertEquals(9 to 0, SheetFlip.sourceOf(SheetFlip.MIRROR_X, 0, 0, 10, 20))
        assertEquals(0 to 19, SheetFlip.sourceOf(SheetFlip.MIRROR_Y, 0, 0, 10, 20))
        assertEquals(3 to 4, SheetFlip.sourceOf(SheetFlip.NONE, 3, 4, 10, 20))
    }

    private fun spec(duplex: DuplexMode = DuplexMode.ONE_SIDED, color: Boolean = true, quality: PrintQuality = PrintQuality.NORMAL) =
        DriverJobSpec(File("x.pdf"), "Doc", 210, 297, color, duplex, quality, 1, true)

    @Test
    fun optionsHandedToTheDrivers() {
        assertEquals("PaperPt=595x842 PGColor=gray PGQuality=high Duplex=DuplexNoTumble",
            NativeDrivers.cupsOptions(spec(DuplexMode.TWO_SIDED_LONG_EDGE, color = false, quality = PrintQuality.HIGH)))
        val gp = NativeDrivers.gutenprintArgs(catalog[5], spec(DuplexMode.TWO_SIDED_SHORT_EDGE))
        assertEquals(listOf("canon-pixma-ip2700", "PaperPt=595x842", "PrintingMode=Color", "Quality=Standard", "Duplex=DuplexTumble"), gp)
        assertEquals("612x791", NativeDrivers.paperPt(DriverJobSpec(File("x"), "", 279, 216, true, DuplexMode.ONE_SIDED,
            PrintQuality.NORMAL, 1, true)))
    }

    @Test
    fun pgrStreamLayout() {
        val h = PgrWriter.pageHeader(100, 200, 300, grayscale = true, backSide = true)
        assertArrayEquals("PAGE".toByteArray(), h.copyOfRange(0, 4))
        assertEquals(100, le32(h, 4)); assertEquals(200, le32(h, 8)); assertEquals(300, le32(h, 12)); assertEquals(300, le32(h, 16))
        assertEquals(1, le32(h, 20)); assertEquals(1, le32(h, 24))

        val out = ByteArrayOutputStream()
        PgrWriter.start(out)
        PgrWriter.writePage(out, IntArrayRowSource(2, 1, intArrayOf(0xFFFF0000.toInt(), 0xFF000000.toInt())), 300, grayscale = false)
        PgrWriter.writePage(out, BlankRowSource(2, 1), 300, grayscale = true)
        PgrWriter.end(out)
        val b = out.toByteArray()
        assertEquals(4 + (28 + 6) + (28 + 2) + 4, b.size)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0, 0, 0, 0, 0), b.copyOfRange(32, 38))
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), b.copyOfRange(66, 68))
        assertArrayEquals("DONE".toByteArray(), b.copyOfRange(b.size - 4, b.size))
    }

    @Test
    fun epsonDriverInput() {
        val args = EscprStream.args(2976, 4209, true, "A4", high = false, duplex = "None")
        assertEquals(listOf("PrintGo", "2976", "4209", "360", "COLOR", "A4", "PLAIN_NORMAL", "None", "Auto", "0", "0", "0", "Off"), args)
        assertEquals(13, args.size)
        assertEquals(2976 to 4209, EscprStream.sheetPixels(210, 297))
        assertEquals("Letter", EscprStream.pageSizeName(216, 279))
        assertEquals("4X6", EscprStream.pageSizeName(152, 102))
        assertEquals(99, EscprStream.pagesAfterByte(5))
        assertEquals(2, EscprStream.pagesAfterByte(2))
    }

    private fun le32(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or ((b[at + 2].toInt() and 0xFF) shl 16) or
            ((b[at + 3].toInt() and 0xFF) shl 24)

    @Test
    fun packUnzipRefusesPathsOutsideItsFolder() {
        val dir = Files.createTempDirectory("pack").toFile()
        try {
            DriverPacks.unzip(zip("ppd/a.ppd.gz" to "ok", "xml/b.xml" to "ok").inputStream(), dir)
            assertTrue(File(dir, "ppd/a.ppd.gz").isFile)
            try {
                DriverPacks.unzip(zip("../evil.txt" to "x").inputStream(), File(dir, "inner").apply { mkdirs() })
                fail("an entry outside the folder must be refused")
            } catch (e: DriverPacks.PackException) {
                assertFalse(File(dir, "evil.txt").exists())
            }
            assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                DriverPacks.sha256(ByteArray(0).inputStream())
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((n, d) in entries) {
                z.putNextEntry(ZipEntry(n)); z.write(d.toByteArray()); z.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}

/** The catalog JSON the CI build writes (native/tools/make_packs.py) is read the way the app expects. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DriverCatalogTest {
    @Test
    fun parsesTheCatalogFormat() {
        val json = """{"version":1,"drivers":[
            {"i":"escpr:Epson-L455_Series","k":"escpr","m":"Epson","n":"EPSON L455 Series","d":"MFG:Epson;MDL:L455 Series;","c":1,"x":0},
            {"i":"gp:pcl-hp-lj-1320","k":"gutenprint","m":"HP","n":"HP LaserJet 1320","d":"","c":0,"x":1,"f":"pcl-hp-lj-1320","g":"pcl"},
            {"i":"hp:hp-laserjet_p3010_series-pcl3","k":"hpcups","m":"HP","n":"HP LaserJet p3010 Series","d":"","c":0,"x":1,"f":"ppd/hp-laserjet_p3010_series-pcl3.ppd.gz","l":"ljmono"},
            {"i":"zz:unknown","k":"other","m":"X","n":"Y"}
        ]}"""
        val list = DriverCatalog.parse(json)
        assertEquals(3, list.size)
        assertEquals(DriverKind.ESCPR, list[0].kind)
        assertEquals("EPSON L455 Series", list[0].displayName)
        assertEquals("pcl-hp-lj-1320", list[1].file)
        assertEquals("pcl", list[1].family)
        assertFalse(list[1].color)
        assertTrue(list[2].duplex)
        assertEquals("HP LaserJet p3010 Series", list[2].displayName)
    }
}
