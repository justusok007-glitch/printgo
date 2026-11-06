package com.printgo.app

import android.graphics.Bitmap
import com.printgo.app.data.print.NativeDrivers
import com.printgo.app.data.print.PclEncoder
import com.printgo.app.data.print.PostScriptEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/** Copies are made by PrintGo (whole copy after whole copy), never by asking the printer to repeat sheets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CopiesTest {

    @Test
    fun threeTwoSidedCopiesOfThreePagesComeOutAsSeparateSets() {
        val b = NativeDrivers.BLANK
        // Sheet 1 = pages 1+2, sheet 2 = page 3 + blank back, then the next copy starts on a new sheet.
        assertEquals(
            listOf(0, 1, 2, b, 0, 1, 2, b, 0, 1, 2),
            NativeDrivers.pageOrder(3, 3, collate = true, twoSided = true)
        )
        // Even page count: no blank backs needed
        assertEquals(listOf(0, 1, 0, 1, 0, 1), NativeDrivers.pageOrder(2, 3, collate = false, twoSided = true))
        // One-sided, collate on (the default)
        assertEquals(listOf(0, 1, 0, 1), NativeDrivers.pageOrder(2, 2, collate = true))
    }

    @Test
    fun pclAsksThePrinterForOneCopy() {
        val out = ByteArrayOutputStream()
        PclEncoder.start(out, 1, 1, PclEncoder.paperSizeCode(210, 297), "Test")
        val text = String(out.toByteArray(), Charsets.ISO_8859_1)
        assertTrue(text.contains("\u001B&l1X"))
    }

    @Test
    fun postScriptSetsUpThePageOnlyOnce() {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val first = ByteArrayOutputStream()
        PostScriptEncoder.writePage(first, bmp, 595, 842, true, 1, 1, setup = true)
        assertTrue(String(first.toByteArray()).contains("/NumCopies 1 /Duplex true"))
        val next = ByteArrayOutputStream()
        PostScriptEncoder.writePage(next, bmp, 595, 842, true, 1, 1, setup = false)
        assertFalse(String(next.toByteArray()).contains("setpagedevice"))
    }
}
