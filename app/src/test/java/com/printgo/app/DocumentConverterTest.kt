package com.printgo.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.printgo.app.data.converter.ConversionResult
import com.printgo.app.data.converter.DocumentConverter
import com.printgo.app.data.converter.OfficeDocumentParser
import com.printgo.app.model.PrintItem
import com.printgo.app.model.PrintItemType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentConverterTest {

    private lateinit var context: Context
    private lateinit var converter: DocumentConverter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        converter = DocumentConverter(context)
        converter.clearConversionCache()
    }

    @Test
    fun `convert text file returns success with correct page count`() = runBlocking {
        val testFile = File(context.cacheDir, "sample_text.txt").apply {
            writeText("Hello PrintGo!\nThis is a test of the text to PDF conversion pipeline.\nIt handles multiple lines and pages.")
        }

        val item = PrintItem(
            uri = Uri.fromFile(testFile),
            displayName = "sample_text.txt",
            mimeType = "text/plain",
            sizeBytes = testFile.length(),
            type = PrintItemType.TEXT
        )

        val result = converter.convert(item)
        if (result is ConversionResult.Error) {
            result.cause?.printStackTrace()
            System.err.println("Conversion error message: ${result.message}")
        }
        assertTrue("Expected Success result, got $result", result is ConversionResult.Success)

        val success = result as ConversionResult.Success
        assertEquals("sample_text.txt", success.document.title)
        assertTrue(success.document.pageCount >= 1)
        assertTrue(success.document.pdfFile.exists())
        assertTrue(success.document.pdfFile.length() > 0)
    }

    @Test
    fun `convert non-existent file returns error`() = runBlocking {
        val ghostFile = File(context.cacheDir, "missing_${System.currentTimeMillis()}.pdf")
        val item = PrintItem(
            uri = Uri.fromFile(ghostFile),
            displayName = "missing.pdf",
            mimeType = "application/pdf",
            sizeBytes = 0,
            type = PrintItemType.PDF
        )

        val result = converter.convert(item)
        assertTrue("Expected Error result, got $result", result is ConversionResult.Error)
    }

    @Test
    fun `convert corrupt office file returns unsupported format`() = runBlocking {
        val corruptDocx = File(context.cacheDir, "corrupt.docx").apply {
            writeText("not a valid zip or docx content")
        }

        val item = PrintItem(
            uri = Uri.fromFile(corruptDocx),
            displayName = "corrupt.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            sizeBytes = corruptDocx.length(),
            type = PrintItemType.OFFICE
        )

        val result = converter.convert(item)
        assertTrue("Expected UnsupportedFormat result, got $result", result is ConversionResult.UnsupportedFormat)
    }

    @Test
    fun `clearConversionCache cleans up temporary files`() = runBlocking {
        val cacheFolder = File(context.cacheDir, "printgo_converted").apply { mkdirs() }
        val dummyFile = File(cacheFolder, "test_file.pdf").apply { writeText("dummy content") }
        assertTrue(dummyFile.exists())

        converter.clearConversionCache()
        val remainingFiles = cacheFolder.listFiles()?.size ?: 0
        assertEquals(0, remainingFiles)
    }

    @Test
    fun `OfficeDocumentParser handles invalid office stream gracefully`() {
        val stream = "invalid zip data".byteInputStream()
        val result = OfficeDocumentParser.parseDocxToHtml(stream, "Sample")
        assertEquals(null, result)
    }
}
