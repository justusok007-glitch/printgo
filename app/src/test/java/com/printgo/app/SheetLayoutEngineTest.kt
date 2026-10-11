package com.printgo.app

import com.printgo.app.data.converter.PrintableDocument
import com.printgo.app.data.layout.PagePlacement
import com.printgo.app.data.layout.Sheet
import com.printgo.app.data.layout.SheetLayoutEngine
import com.printgo.app.model.DuplexMode
import com.printgo.app.model.PageOrder
import com.printgo.app.model.PageRangeParser
import com.printgo.app.model.PageRangeResult
import com.printgo.app.model.PageSelection
import com.printgo.app.model.PagesPerSheet
import com.printgo.app.model.PrintAlignment
import com.printgo.app.model.PrintMargins
import com.printgo.app.model.PrintScale
import com.printgo.app.model.PrintSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SheetLayoutEngineTest {

    // ==========================================
    // 1. PAGE RANGE PARSING TESTS
    // ==========================================

    @Test
    fun `parse valid page ranges returns sorted deduplicated list`() {
        val result = PageRangeParser.parse("1-3,5,8-10", totalPages = 12)
        assertTrue(result is PageRangeResult.Success)
        val pages = (result as PageRangeResult.Success).pages
        assertEquals(listOf(1, 2, 3, 5, 8, 9, 10), pages)
    }

    @Test
    fun `parse overlapping and unordered ranges resolves cleanly`() {
        val result = PageRangeParser.parse("5, 1-3, 2-4", totalPages = 10)
        assertTrue(result is PageRangeResult.Success)
        val pages = (result as PageRangeResult.Success).pages
        assertEquals(listOf(1, 2, 3, 4, 5), pages)
    }

    @Test
    fun `parse out of range page numbers returns error`() {
        val resultAbove = PageRangeParser.parse("1-5, 12", totalPages = 10)
        assertTrue("Expected error for page exceeding total pages", resultAbove is PageRangeResult.Error)

        val resultRangeAbove = PageRangeParser.parse("8-15", totalPages = 10)
        assertTrue("Expected error for range exceeding total pages", resultRangeAbove is PageRangeResult.Error)

        val resultZero = PageRangeParser.parse("0-3", totalPages = 10)
        assertTrue("Expected error for page < 1", resultZero is PageRangeResult.Error)
    }

    @Test
    fun `parse invalid syntax returns descriptive error`() {
        val emptyResult = PageRangeParser.parse("   ", totalPages = 5)
        assertTrue(emptyResult is PageRangeResult.Error)

        val invalidChars = PageRangeParser.parse("1-abc", totalPages = 5)
        assertTrue(invalidChars is PageRangeResult.Error)

        val invertedRange = PageRangeParser.parse("5-2", totalPages = 5)
        assertTrue(invertedRange is PageRangeResult.Error)

        val doubleDash = PageRangeParser.parse("1--3", totalPages = 5)
        assertTrue(doubleDash is PageRangeResult.Error)

        val trailingComma = PageRangeParser.parse("1,2,", totalPages = 5)
        assertTrue(trailingComma is PageRangeResult.Error)
    }

    // ==========================================
    // 2. GRID PLACEMENT & SCALE / ALIGNMENT TESTS
    // ==========================================

    @Test
    fun `computeDestinationRect FitToPage keeps aspect ratio within cell bounds`() {
        val cellW = 300f
        val cellH = 400f
        val srcW = 600f
        val srcH = 600f // 1:1 aspect ratio

        val rect = SheetLayoutEngine.computeDestinationRect(
            cellLeft = 20f,
            cellTop = 30f,
            cellWidth = cellW,
            cellHeight = cellH,
            srcWidth = srcW,
            srcHeight = srcH,
            scale = PrintScale.FitToPage,
            alignment = PrintAlignment.CENTER
        )

        // Fits within 300x300, centered horizontally and vertically
        assertEquals(300f, rect.width(), 0.01f)
        assertEquals(300f, rect.height(), 0.01f)
        // Center offset X = (300 - 300)/2 = 0 -> left = 20
        assertEquals(20f, rect.left, 0.01f)
        // Center offset Y = (400 - 300)/2 = 50 -> top = 30 + 50 = 80
        assertEquals(80f, rect.top, 0.01f)
    }

    @Test
    fun `computeDestinationRect alignment positions correctly`() {
        val cellW = 400f
        val cellH = 400f
        val srcW = 200f
        val srcH = 200f

        val rectTopLeft = SheetLayoutEngine.computeDestinationRect(
            cellLeft = 0f,
            cellTop = 0f,
            cellWidth = cellW,
            cellHeight = cellH,
            srcWidth = srcW,
            srcHeight = srcH,
            scale = PrintScale.ActualSize,
            alignment = PrintAlignment.TOP_LEFT
        )
        assertEquals(0f, rectTopLeft.left, 0.01f)
        assertEquals(0f, rectTopLeft.top, 0.01f)

        val rectBottomRight = SheetLayoutEngine.computeDestinationRect(
            cellLeft = 0f,
            cellTop = 0f,
            cellWidth = cellW,
            cellHeight = cellH,
            srcWidth = srcW,
            srcHeight = srcH,
            scale = PrintScale.ActualSize,
            alignment = PrintAlignment.BOTTOM_RIGHT
        )
        assertEquals(200f, rectBottomRight.left, 0.01f)
        assertEquals(200f, rectBottomRight.top, 0.01f)
    }

    @Test
    fun `computeDestinationRect CustomPercent scales dimensions accurately`() {
        val cellW = 500f
        val cellH = 500f
        val srcW = 200f
        val srcH = 300f

        val rect = SheetLayoutEngine.computeDestinationRect(
            cellLeft = 10f,
            cellTop = 10f,
            cellWidth = cellW,
            cellHeight = cellH,
            srcWidth = srcW,
            srcHeight = srcH,
            scale = PrintScale.CustomPercent(150),
            alignment = PrintAlignment.TOP_LEFT
        )

        assertEquals(300f, rect.width(), 0.01f) // 200 * 1.5
        assertEquals(450f, rect.height(), 0.01f) // 300 * 1.5
    }

    // ==========================================
    // 3. DUPLEX PADDING TESTS
    // ==========================================

    @Test
    fun `padSheetsForDuplex one sided does not add padding`() {
        val dummySheets = listOf(
            Sheet(0, emptyList(), 595f, 842f),
            Sheet(1, emptyList(), 595f, 842f),
            Sheet(2, emptyList(), 595f, 842f)
        )

        val result = SheetLayoutEngine.padSheetsForDuplex(dummySheets, DuplexMode.ONE_SIDED)
        assertEquals(3, result.size)
        assertFalse(result.any { it.isBlankPad })
    }

    @Test
    fun `padSheetsForDuplex two sided with odd sheet count adds blank backing sheet`() {
        val dummySheets = listOf(
            Sheet(0, emptyList(), 595f, 842f),
            Sheet(1, emptyList(), 595f, 842f),
            Sheet(2, emptyList(), 595f, 842f)
        )

        val result = SheetLayoutEngine.padSheetsForDuplex(dummySheets, DuplexMode.TWO_SIDED_LONG_EDGE)
        assertEquals(4, result.size)
        assertFalse(result[0].isBlankPad)
        assertFalse(result[0].isBackOfDuplex)

        assertFalse(result[1].isBlankPad)
        assertTrue(result[1].isBackOfDuplex)

        assertFalse(result[2].isBlankPad)
        assertFalse(result[2].isBackOfDuplex)

        assertTrue("Last sheet must be blank pad", result[3].isBlankPad)
        assertTrue("Last sheet must be back of duplex", result[3].isBackOfDuplex)
    }

    @Test
    fun `padSheetsForDuplex two sided with even sheet count needs no padding`() {
        val dummySheets = listOf(
            Sheet(0, emptyList(), 595f, 842f),
            Sheet(1, emptyList(), 595f, 842f)
        )

        val result = SheetLayoutEngine.padSheetsForDuplex(dummySheets, DuplexMode.TWO_SIDED_SHORT_EDGE)
        assertEquals(2, result.size)
        assertFalse(result.any { it.isBlankPad })
        assertTrue(result[1].isBackOfDuplex)
    }

    // ==========================================
    // 4. PAGES PER SHEET GROUPING TESTS
    // ==========================================

    @Test
    fun `computeSheets groups 7 pages into 2 sheets for 4-up`() {
        val fakeDoc = PrintableDocument(
            sourceItem = com.printgo.app.model.PrintItem(
                uri = android.net.Uri.parse("file:///tmp/doc1.pdf"),
                displayName = "Sample Document",
                mimeType = "application/pdf",
                sizeBytes = 1024L,
                type = com.printgo.app.model.PrintItemType.PDF
            ),
            pdfFile = File("/tmp/nonexistent.pdf"),
            pageCount = 7,
            title = "Sample Document"
        )

        val settings = PrintSettings(
            pagesPerSheet = PagesPerSheet.FOUR,
            margins = PrintMargins.Narrow
        )

        val sheets = SheetLayoutEngine.computeSheets(listOf(fakeDoc), settings, includeExcludedForPreview = false)
        assertEquals(2, sheets.size)
        assertEquals(4, sheets[0].placements.size)
        assertEquals(3, sheets[1].placements.size)
    }

    @Test
    fun `computeSheets respects PageOrder TOP_TO_BOTTOM`() {
        val fakeDoc = PrintableDocument(
            sourceItem = com.printgo.app.model.PrintItem(
                uri = android.net.Uri.parse("file:///tmp/doc2.pdf"),
                displayName = "Grid Test",
                mimeType = "application/pdf",
                sizeBytes = 1024L,
                type = com.printgo.app.model.PrintItemType.PDF
            ),
            pdfFile = File("/tmp/nonexistent.pdf"),
            pageCount = 4,
            title = "Grid Test"
        )

        val settings = PrintSettings(
            pagesPerSheet = PagesPerSheet.FOUR,
            pageOrder = PageOrder.TOP_TO_BOTTOM
        )

        val sheets = SheetLayoutEngine.computeSheets(listOf(fakeDoc), settings, includeExcludedForPreview = false)
        assertEquals(1, sheets.size)
        val p = sheets[0].placements
        assertEquals(4, p.size)

        // For 2x2 TOP_TO_BOTTOM:
        // index 0: col 0, row 0
        // index 1: col 0, row 1
        // index 2: col 1, row 0
        // index 3: col 1, row 1
        assertTrue(p[0].destRectPt.left == p[1].destRectPt.left)
        assertTrue(p[0].destRectPt.top < p[1].destRectPt.top)
        assertTrue(p[2].destRectPt.left > p[0].destRectPt.left)
    }

    private fun doc(pages: Int) = PrintableDocument(
        sourceItem = com.printgo.app.model.PrintItem(
            uri = android.net.Uri.parse("file:///tmp/doc_sel.pdf"),
            displayName = "Sel",
            mimeType = "application/pdf",
            sizeBytes = 1L,
            type = com.printgo.app.model.PrintItemType.PDF
        ),
        pdfFile = File("/tmp/nonexistent_sel.pdf"),
        pageCount = pages,
        title = "Sel"
    )

    @Test
    fun `odd pages in reverse order`() {
        val sheets = SheetLayoutEngine.computeSheets(
            listOf(doc(7)), PrintSettings(pageSelection = PageSelection.Odd, reverseOrder = true), includeExcludedForPreview = false
        )
        assertEquals(listOf(6, 4, 2, 0), sheets.map { it.placements.single().globalPageIndex })
    }

    @Test
    fun `range selection keeps only listed pages`() {
        val sheets = SheetLayoutEngine.computeSheets(
            listOf(doc(10)), PrintSettings(pageSelection = PageSelection.Range("2-3, 9")), includeExcludedForPreview = false
        )
        assertEquals(listOf(1, 2, 8), sheets.map { it.placements.single().globalPageIndex })
    }

    @Test
    fun `landscape orientation gives wide sheets and auto keeps portrait pages portrait`() {
        val land = SheetLayoutEngine.computeSheets(listOf(doc(1)), PrintSettings(orientation = com.printgo.app.model.PrintOrientation.LANDSCAPE), false)
        assertTrue(land[0].paperWidthPt > land[0].paperHeightPt)
        val auto = SheetLayoutEngine.computeSheets(listOf(doc(1)), PrintSettings(), false)
        assertTrue(auto[0].paperWidthPt < auto[0].paperHeightPt)
    }

    @Test
    fun `legal paper gives legal sized sheets`() {
        val sheets = SheetLayoutEngine.computeSheets(
            listOf(doc(1)), PrintSettings(paperSize = com.printgo.app.model.PaperSize("Legal", 216, 356)), false
        )
        assertEquals(216 * SheetLayoutEngine.POINTS_PER_MM, sheets[0].paperWidthPt, 0.5f)
        assertEquals(356 * SheetLayoutEngine.POINTS_PER_MM, sheets[0].paperHeightPt, 0.5f)
    }

    @Test
    fun `large custom scale is clipped to the printable cell`() {
        val sheets = SheetLayoutEngine.computeSheets(
            listOf(doc(1)), PrintSettings(scale = PrintScale.CustomPercent(300), alignment = PrintAlignment.TOP_LEFT), false
        )
        val p = sheets[0].placements.single()
        assertTrue(p.destRectPt.width() > sheets[0].paperWidthPt)
        val visible = SheetLayoutEngine.visibleRect(p)!!
        val cell = p.cellRectPt!!
        assertEquals(cell.left, visible.left, 0.01f)
        assertEquals(cell.right, visible.right, 0.01f)
        assertEquals(cell.bottom, visible.bottom, 0.01f)
    }
}
