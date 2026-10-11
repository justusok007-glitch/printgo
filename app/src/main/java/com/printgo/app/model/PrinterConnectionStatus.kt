package com.printgo.app.model

sealed interface PrinterConnectionStatus {
    val printerName: String
    val address: String?
    val warnings: List<String>

    data object None : PrinterConnectionStatus {
        override val printerName: String = "No printer selected"
        override val address: String? = null
        override val warnings: List<String> = emptyList()
    }

    data object PdfOnly : PrinterConnectionStatus {
        override val printerName: String = "Save as PDF"
        override val address: String? = null
        override val warnings: List<String> = emptyList()
    }

    data class Checking(
        override val printerName: String,
        override val address: String?,
        override val warnings: List<String> = emptyList()
    ) : PrinterConnectionStatus

    data class Connected(
        override val printerName: String,
        override val address: String?,
        val connectionType: ConnectionType,
        val isReady: Boolean = true,
        override val warnings: List<String> = emptyList()
    ) : PrinterConnectionStatus

    data class Busy(
        override val printerName: String,
        override val address: String?,
        val connectionType: ConnectionType,
        override val warnings: List<String> = emptyList()
    ) : PrinterConnectionStatus

    data class Unreachable(
        override val printerName: String,
        override val address: String?,
        val connectionType: ConnectionType,
        override val warnings: List<String> = emptyList()
    ) : PrinterConnectionStatus
}
