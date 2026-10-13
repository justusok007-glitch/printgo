package com.printgo.app

import com.printgo.app.data.scan.BackgroundMap
import com.printgo.app.data.scan.CropShape
import com.printgo.app.data.scan.JpegInfo
import com.printgo.app.data.scan.JpegPdfWriter
import com.printgo.app.data.scan.PageDetector
import com.printgo.app.data.scan.Pt
import com.printgo.app.data.scan.Quad
import com.printgo.app.data.scan.ScanFilter
import com.printgo.app.data.scan.ScanFilterEngine
import com.printgo.app.data.scan.ScanGeometry
import com.printgo.app.data.scan.ScanPage
import com.printgo.app.data.scan.ScanPageList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ScanEngineTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(p: Int) = (p shr 16) and 0xFF
    private fun green(p: Int) = (p shr 8) and 0xFF
    private fun blue(p: Int) = p and 0xFF

    // ---------------- filters ----------------

    @Test
    fun originalAndZeroStrengthChangeNothing() {
        val px = IntArray(16) { argb(it * 10, 255 - it * 10, 77) }
        assertTrue(px.contentEquals(ScanFilterEngine.applyWhole(px, 4, 4, ScanFilter.ORIGINAL, 1f)))
        for (f in ScanFilter.entries) {
            assertTrue("$f at 0 %", px.contentEquals(ScanFilterEngine.applyWhole(px, 4, 4, f, 0f)))
        }
    }

    @Test
    fun vibrantRaisesSaturation() {
        val p = argb(180, 120, 100)
        val out = ScanFilterEngine.applyWhole(IntArray(9) { p }, 3, 3, ScanFilter.VIBRANT, 1f)[4]
        assertTrue(red(out) - blue(out) > 180 - 100)
    }

    @Test
    fun carbonBlackMakesWhitePaperAndBlackTextEvenInShadow() {
        // 40×40 page: left half lit (paper 230), right half in shadow (paper 140); text strokes at 25 % of paper.
        val w = 40; val h = 40
        val px = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val paper = if (x < w / 2) 230 else 140
            val ink = (x % 10 == 3) && y in 10..30
            val v = if (ink) paper / 4 else paper
            argb(v, v, v)
        }
        val out = ScanFilterEngine.applyWhole(px, w, h, ScanFilter.CARBON_BLACK, 1f)
        assertTrue(ScanFilterEngine.isGrey(out))
        // paper in both halves → white, ink in both halves → black
        assertEquals(255, red(out[5 * w + 6]))
        assertEquals(255, red(out[5 * w + 36]))
        assertEquals(0, red(out[20 * w + 3]))
        assertEquals(0, red(out[20 * w + 33]))
    }

    @Test
    fun cleanColourWhitensPaperButKeepsBluePen() {
        val w = 30; val h = 30
        val paper = argb(210, 200, 170) // yellowish paper
        val pen = argb(40, 60, 160)
        val px = IntArray(w * h) { i -> if ((i % w) in 14..15) pen else paper }
        val out = ScanFilterEngine.applyWhole(px, w, h, ScanFilter.CLEAN_COLOUR, 1f)
        val bg = out[2 * w + 3]
        assertTrue(red(bg) >= 250 && green(bg) >= 250 && blue(bg) >= 250)
        val ink = out[10 * w + 14]
        assertTrue("pen stays blue", blue(ink) > red(ink) + 60)
    }

    @Test
    fun softToneIsLighterAndWarmer() {
        val p = argb(100, 100, 100)
        val out = ScanFilterEngine.applyWhole(IntArray(4) { p }, 2, 2, ScanFilter.SOFT_TONE, 1f)[0]
        assertTrue(red(out) > 100)
        assertTrue(red(out) > blue(out))
    }

    @Test
    fun strengthBlendsHalfway() {
        val p = argb(100, 100, 100)
        val full = ScanFilterEngine.applyWhole(IntArray(4) { p }, 2, 2, ScanFilter.SOFT_TONE, 1f)[0]
        val half = ScanFilterEngine.applyWhole(IntArray(4) { p }, 2, 2, ScanFilter.SOFT_TONE, 0.5f)[0]
        assertEquals((100 + red(full)) / 2.0, red(half).toDouble(), 1.0)
    }

    @Test
    fun backgroundMapIgnoresThinText() {
        val w = 80; val h = 80
        val px = IntArray(w * h) { i -> if ((i % w) % 8 == 0) argb(0, 0, 0) else argb(200, 200, 200) }
        val bg = BackgroundMap.estimate(px, w, h)
        val out = FloatArray(3)
        bg.sample(0.5f, 0.5f, out)
        assertEquals(200f, out[0], 1f)
    }

    @Test
    fun bandedFilteringMatchesWholeImage() {
        val w = 20; val h = 24
        val rnd = Random(3)
        val px = IntArray(w * h) { argb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)) }
        val bg = BackgroundMap.estimate(px, w, h)
        val whole = ScanFilterEngine.apply(px, w, h, ScanFilter.CLEAN_COLOUR, 1f, bg, 0, h)
        val band = ScanFilterEngine.apply(px.copyOfRange(8 * w, 16 * w), w, 8, ScanFilter.CLEAN_COLOUR, 1f, bg, 8, h)
        assertTrue(whole.copyOfRange(8 * w, 16 * w).contentEquals(band))
    }

    // ---------------- geometry ----------------

    @Test
    fun rotatingFourTimesIsIdentity() {
        val p = Pt(0.2, 0.7)
        assertEquals(Pt(0.3, 0.2).x, ScanGeometry.rotatePoint(p, 1).x, 1e-9)
        assertEquals(0.2, ScanGeometry.rotatePoint(p, 1).y, 1e-9)
        val back = ScanGeometry.rotatePoint(p, 4)
        assertEquals(p, back)
        val q = Quad.rect(0.1, 0.2, 0.5, 0.9)
        val r = ScanGeometry.rotateQuad(q, 1)
        // A rectangle stays a rectangle, top-left first
        assertTrue(r.isRectangle)
        assertEquals(0.1, r.tl.x, 1e-9); assertEquals(0.1, r.tl.y, 1e-9)
        assertEquals(0.8, r.br.x, 1e-9); assertEquals(0.5, r.br.y, 1e-9)
    }

    @Test
    fun turnedPointsMapBackToStoredPixels() {
        // Stored bitmap 400×300. Turned 90° clockwise it shows as 300×400; its top-left is the stored bottom-left.
        val tl = ScanGeometry.toSourcePixels(Pt(0.0, 0.0), 90, 400, 300)
        assertEquals(0.0, tl.x, 1e-9); assertEquals(300.0, tl.y, 1e-9)
        val tr = ScanGeometry.toSourcePixels(Pt(1.0, 0.0), 90, 400, 300)
        assertEquals(0.0, tr.x, 1e-9); assertEquals(0.0, tr.y, 1e-9)
        val c180 = ScanGeometry.toSourcePixels(Pt(0.0, 0.0), 180, 400, 300)
        assertEquals(400.0, c180.x, 1e-9); assertEquals(300.0, c180.y, 1e-9)
        val c270 = ScanGeometry.toSourcePixels(Pt(0.0, 0.0), 270, 400, 300)
        assertEquals(400.0, c270.x, 1e-9); assertEquals(0.0, c270.y, 1e-9)
        assertEquals(300 to 400, ScanGeometry.turnedSize(400, 300, 270))
    }

    @Test
    fun orderCornersAndConvexity() {
        val q = ScanGeometry.orderCorners(listOf(Pt(9.0, 8.0), Pt(1.0, 1.0), Pt(1.0, 9.0), Pt(10.0, 0.0)))
        assertEquals(Pt(1.0, 1.0), q.tl); assertEquals(Pt(10.0, 0.0), q.tr)
        assertEquals(Pt(9.0, 8.0), q.br); assertEquals(Pt(1.0, 9.0), q.bl)
        assertTrue(ScanGeometry.isConvex(q))
        val bowTie = Quad(Pt(0.0, 0.0), Pt(1.0, 1.0), Pt(1.0, 0.0), Pt(0.0, 1.0))
        assertFalse(ScanGeometry.isConvex(bowTie))
    }

    @Test
    fun homographyFlattensTrapezoid() {
        val src = Quad(Pt(30.0, 10.0), Pt(170.0, 20.0), Pt(190.0, 280.0), Pt(5.0, 260.0))
        val dst = Quad.rect(0.0, 0.0, 210.0, 297.0)
        val h = ScanGeometry.homography(src, dst)
        for (i in 0 until 4) {
            val m = ScanGeometry.applyHomography(h, src.points[i])
            assertEquals(dst.points[i].x, m.x, 1e-6)
            assertEquals(dst.points[i].y, m.y, 1e-6)
        }
        // straight lines stay straight: the middle of the top edge maps onto the top edge
        val midTop = Pt(100.0, 15.0)
        assertEquals(0.0, ScanGeometry.applyHomography(h, midTop).y, 1e-6)
    }

    @Test
    fun flatAndOutputSize() {
        val (w, h) = ScanGeometry.flatSize(Quad.rect(0.0, 0.0, 0.5, 1.0), 4000.0, 3000.0)
        assertEquals(2000.0, w, 1e-9); assertEquals(3000.0, h, 1e-9)
        assertEquals(2000 to 3000, ScanGeometry.outputSize(w, h, 3508))
        val big = ScanGeometry.outputSize(4000.0, 6000.0, 3508)
        assertEquals(3508, big.second)
        assertEquals(2339, big.first)
    }

    @Test
    fun paperRatioCropKeepsShape() {
        val imgAspect = 4000.0 / 3000.0
        val a4 = ScanGeometry.fitRatio(Quad.FULL, CropShape.A4.ratio(false), imgAspect)
        val pxW = (a4.maxX - a4.minX) * 4000; val pxH = (a4.maxY - a4.minY) * 3000
        assertEquals(210.0 / 297.0, pxW / pxH, 1e-6)
        assertTrue(a4.minX >= 0 && a4.maxX <= 1 && a4.minY >= 0 && a4.maxY <= 1)
        val sq = ScanGeometry.fitRatio(Quad.FULL, CropShape.SQUARE.ratio(false), imgAspect)
        assertEquals(1.0, (sq.maxX - sq.minX) * 4000 / ((sq.maxY - sq.minY) * 3000), 1e-6)
        val land = ScanGeometry.fitRatio(Quad.FULL, CropShape.PHOTO_4X6.ratio(true), imgAspect)
        assertEquals(1.5, (land.maxX - land.minX) * 4000 / ((land.maxY - land.minY) * 3000), 1e-6)
    }

    @Test
    fun ratioDragStaysInsideAndKeepsRatio() {
        val imgAspect = 0.75
        val r = ScanGeometry.ratioRectFromCorner(Pt(0.2, 0.2), Pt(1.5, 0.5), 1.0, imgAspect)
        assertTrue(r.maxX <= 1.0 + 1e-9 && r.maxY <= 1.0 + 1e-9)
        assertEquals(1.0, (r.maxX - r.minX) * imgAspect / (r.maxY - r.minY), 1e-6)
        val up = ScanGeometry.ratioRectFromCorner(Pt(0.8, 0.8), Pt(0.6, 0.1), 210.0 / 297.0, imgAspect)
        assertEquals(0.8, up.maxX, 1e-9); assertEquals(0.8, up.maxY, 1e-9)
        assertEquals(210.0 / 297.0, (up.maxX - up.minX) * imgAspect / (up.maxY - up.minY), 1e-6)
    }

    // ---------------- page detection ----------------

    private fun render(w: Int, h: Int, background: (Int, Int) -> Int, quad: Quad, page: Int = 235): IntArray {
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            px[y * w + x] = if (inside(quad, x + 0.5, y + 0.5)) page else background(x, y)
        }
        return px
    }

    private fun inside(q: Quad, x: Double, y: Double): Boolean {
        val p = q.points
        for (i in 0 until 4) {
            val a = p[i]; val b = p[(i + 1) % 4]
            if ((b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x) < 0) return false
        }
        return true
    }

    private fun assertNear(expected: Quad, got: Quad, w: Int, h: Int, tolPx: Double) {
        for (i in 0 until 4) {
            val e = expected.points[i]; val g = got.points[i]
            assertTrue("corner $i expected $e got ${Pt(g.x * w, g.y * h)}",
                abs(e.x - g.x * w) <= tolPx && abs(e.y - g.y * h) <= tolPx)
        }
    }

    @Test
    fun findsWhitePageOnDarkTable() {
        val w = 600; val h = 450
        val page = Quad.rect(150.0, 60.0, 450.0, 400.0)
        val img = render(w, h, { _, _ -> 50 }, page)
        val d = PageDetector.detect(img, w, h)
        assertNotNull(d)
        assertNear(page, d!!.quad, w, h, 6.0)
    }

    @Test
    fun findsTiltedPageInPerspective() {
        val w = 640; val h = 480
        val page = Quad(Pt(180.0, 50.0), Pt(470.0, 90.0), Pt(500.0, 430.0), Pt(120.0, 400.0))
        val img = render(w, h, { x, y -> 40 + (x + y) % 20 }, page)
        val d = PageDetector.detect(img, w, h)
        assertNotNull(d)
        assertNear(page, d!!.quad, w, h, 8.0)
    }

    @Test
    fun findsRotatedPageWithText() {
        val w = 600; val h = 600
        val c = 300.0; val a = Math.toRadians(25.0)
        fun rot(x: Double, y: Double) = Pt(c + x * cos(a) - y * sin(a), c + x * sin(a) + y * cos(a))
        val page = ScanGeometry.orderCorners(listOf(rot(-150.0, -200.0), rot(150.0, -200.0), rot(150.0, 200.0), rot(-150.0, 200.0)))
        val base = render(w, h, { _, _ -> 70 }, page)
        // dark text lines inside the page
        for (y in 0 until h) for (x in 0 until w) {
            if (base[y * w + x] == 235 && y % 14 < 3 && x % 50 < 40 && inside(page.map { Pt((it.x - c) * 0.8 + c, (it.y - c) * 0.8 + c) }, x.toDouble(), y.toDouble())) {
                base[y * w + x] = 30
            }
        }
        val d = PageDetector.detect(base, w, h)
        assertNotNull(d)
        assertNear(page, d!!.quad, w, h, 8.0)
    }

    @Test
    fun findsPageOnBusyBackground() {
        val w = 600; val h = 450
        val rnd = Random(7)
        val noise = IntArray(w * h) { rnd.nextInt(30, 200) }
        val page = Quad.rect(170.0, 70.0, 430.0, 390.0)
        val img = render(w, h, { x, y -> noise[y * w + x] }, page, page = 245)
        val d = PageDetector.detect(img, w, h)
        assertNotNull(d)
        assertNear(page, d!!.quad, w, h, 8.0)
    }

    @Test
    fun findsPageWithUnevenLightOnLightTable() {
        val w = 640; val h = 480
        val page = Quad(Pt(140.0, 60.0), Pt(500.0, 75.0), Pt(520.0, 440.0), Pt(120.0, 420.0))
        val img = render(w, h, { x, _ -> 120 + x / 10 }, page)
        // shadow over the right part of the page
        for (y in 0 until h) for (x in 0 until w) if (img[y * w + x] == 235) img[y * w + x] = 235 - (x * 50 / w)
        val d = PageDetector.detect(img, w, h)
        assertNotNull(d)
        assertNear(page, d!!.quad, w, h, 8.0)
    }

    @Test
    fun noPageInPlainOrRandomImage() {
        val w = 300; val h = 200
        assertNull(PageDetector.detect(IntArray(w * h) { 128 }, w, h))
        val rnd = Random(11)
        assertNull(PageDetector.detect(IntArray(w * h) { rnd.nextInt(256) }, w, h))
    }

    @Test
    fun pageFillingWholePhotoIsNotGuessed() {
        // A page running off every edge has no edges to find → caller uses the inset fallback.
        val w = 300; val h = 400
        assertNull(PageDetector.detect(IntArray(w * h) { 240 }, w, h))
        val inset = ScanGeometry.insetQuad()
        assertTrue(inset.tl.x > 0 && inset.br.x < 1)
    }

    // ---------------- pages ----------------

    private fun pages(n: Int) = List(n) { ScanPage(id = "p$it", source = "s$it", displayName = "Page $it") }

    @Test
    fun reorderDeleteAndFilterAll() {
        val p = pages(4)
        assertEquals(listOf("p1", "p0", "p2", "p3"), ScanPageList.moveUp(p, 1).map { it.id })
        assertEquals(listOf("p0", "p1", "p3", "p2"), ScanPageList.moveDown(p, 2).map { it.id })
        assertEquals(p, ScanPageList.moveUp(p, 0))
        assertEquals(p, ScanPageList.moveDown(p, 3))
        assertEquals(listOf("p1", "p2", "p3", "p0"), ScanPageList.move(p, 0, 3).map { it.id })
        assertEquals(listOf("p0", "p2", "p3"), ScanPageList.remove(p, "p1").map { it.id })
        assertEquals(2, ScanPageList.indexAfterRemove(3, 3))
        assertEquals(0, ScanPageList.indexAfterRemove(0, 0))
        val all = ScanPageList.applyFilterToAll(p, ScanFilter.CARBON_BLACK, 0.7f)
        assertTrue(all.all { it.filter == ScanFilter.CARBON_BLACK && it.strength == 0.7f })
        val changed = p[2].copy(quarterTurns = 1)
        assertEquals(1, ScanPageList.replace(p, changed)[2].quarterTurns)
    }

    @Test
    fun renderKeyFollowsEdits() {
        val a = pages(1)[0]
        assertTrue(a.isUnchanged)
        assertFalse(a.copy(filter = ScanFilter.VIBRANT).isUnchanged)
        assertTrue(a.copy(filter = ScanFilter.VIBRANT, strength = 0f).isUnchanged)
        assertFalse(a.renderKey == a.copy(quad = ScanGeometry.insetQuad()).renderKey)
        assertEquals(a.renderKey, a.copy(quarterTurns = 4, displayName = "x").renderKey)
    }

    // ---------------- PDF ----------------

    private fun fakeJpeg(w: Int, h: Int, comps: Int = 3): ByteArray {
        // SOI, APP0 (short), SOF0 with size, then EOI. Enough for the writer and the header reader.
        val sof = byteArrayOf(
            0xFF.toByte(), 0xC0.toByte(), 0, (8 + 3 * comps).toByte(), 8,
            (h shr 8).toByte(), h.toByte(), (w shr 8).toByte(), w.toByte(), comps.toByte()
        ) + ByteArray(3 * comps)
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 4, 1, 2) + sof +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    }

    @Test
    fun readsJpegHeader() {
        assertEquals(JpegInfo(2480, 3508, 3), JpegInfo.read(fakeJpeg(2480, 3508)))
        assertEquals(1, JpegInfo.read(fakeJpeg(10, 20, 1))!!.components)
        assertNull(JpegInfo.read(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun writesOnePdfPagePerPictureWithValidXref() {
        val dir = java.nio.file.Files.createTempDirectory("pdf").toFile()
        try {
            val files = listOf(fakeJpeg(1000, 1414), fakeJpeg(1500, 1000), fakeJpeg(800, 800)).mapIndexed { i, b ->
                File(dir, "p$i.jpg").apply { writeBytes(b) }
            }
            val out = ByteArrayOutputStream()
            JpegPdfWriter.write(out, files, 210.0, 297.0, 6.0)
            val bytes = out.toByteArray()
            val text = String(bytes, Charsets.ISO_8859_1)
            assertTrue(text.startsWith("%PDF-1.4"))
            assertTrue(text.contains("/Count 3"))
            assertEquals(3, Regex("/Subtype /Image").findAll(text).count())
            assertTrue(text.contains("/MediaBox [0 0 595.28 841.89]"))
            assertTrue("landscape picture gets a landscape page", text.contains("/MediaBox [0 0 841.89 595.28]"))
            // every xref offset points at "n 0 obj"
            val start = text.substringAfterLast("startxref\n").substringBefore("\n").trim().toInt()
            assertTrue(text.startsWith("xref", start))
            val entries = text.substring(start).lines().drop(3).takeWhile { it.endsWith(" n ") }
            assertEquals(11, entries.size)
            entries.forEachIndexed { i, line ->
                val off = line.substring(0, 10).toInt()
                assertTrue("object ${i + 1}", text.startsWith("${i + 1} 0 obj", off))
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
