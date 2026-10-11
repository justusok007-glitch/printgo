package com.printgo.app.data.converter

import com.printgo.app.model.PrintItem
import java.io.File

data class PrintableDocument(
    val sourceItem: PrintItem,
    val pdfFile: File,
    val pageCount: Int,
    val title: String
)

sealed interface ConversionResult {
    data class Loading(val sourceItem: PrintItem, val progress: Float = 0f) : ConversionResult
    data class Success(val document: PrintableDocument) : ConversionResult
    data class PasswordRequired(val sourceItem: PrintItem) : ConversionResult
    data class UnsupportedFormat(
        val sourceItem: PrintItem,
        val message: String = "This file type has limited support. Open it in its own app and use Share > PrintGo as PDF."
    ) : ConversionResult
    data class Error(
        val sourceItem: PrintItem,
        val message: String,
        val cause: Throwable? = null
    ) : ConversionResult
}
