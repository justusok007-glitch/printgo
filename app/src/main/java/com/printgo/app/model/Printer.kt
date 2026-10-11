package com.printgo.app.model

import androidx.compose.ui.graphics.Color

enum class ConnectionType {
    WIFI,
    BLUETOOTH,
    USB,
    PDF
}

enum class PrinterProtocol {
    IPP,
    IPPS,
    RAW9100,
    NONE
}

data class PrinterBrand(
    val displayName: String,
    val initials: String,
    val color: Color
)

object PrinterBrandDetector {
    private val brands = listOf(
        "hp" to PrinterBrand("HP", "HP", Color(0xFF0096D6)),
        "hewlett-packard" to PrinterBrand("HP", "HP", Color(0xFF0096D6)),
        "canon" to PrinterBrand("Canon", "CN", Color(0xFFCC0000)),
        "epson" to PrinterBrand("Epson", "EP", Color(0xFF003399)),
        "brother" to PrinterBrand("Brother", "BR", Color(0xFF005CAB)),
        "samsung" to PrinterBrand("Samsung", "SM", Color(0xFF1428A0)),
        "xerox" to PrinterBrand("Xerox", "XR", Color(0xFFED1C24)),
        "lexmark" to PrinterBrand("Lexmark", "LX", Color(0xFF00A651)),
        "ricoh" to PrinterBrand("Ricoh", "RC", Color(0xFFD50000)),
        "kyocera" to PrinterBrand("Kyocera", "KC", Color(0xFFE50012)),
        "konica" to PrinterBrand("Konica Minolta", "KM", Color(0xFF005BAC)),
        "minolta" to PrinterBrand("Konica Minolta", "KM", Color(0xFF005BAC)),
        "sharp" to PrinterBrand("Sharp", "SH", Color(0xFFE60012)),
        "dell" to PrinterBrand("Dell", "DL", Color(0xFF007DB8)),
        "oki" to PrinterBrand("OKI", "OK", Color(0xFFE60012)),
        "toshiba" to PrinterBrand("Toshiba", "TS", Color(0xFFCC0000))
    )

    fun detect(make: String?, model: String?, name: String?): PrinterBrand {
        val combined = listOfNotNull(make, model, name).joinToString(" ").lowercase()
        for ((key, brand) in brands) {
            if (combined.contains(key)) return brand
        }
        val cleanName = (make ?: name ?: "P").trim().take(2).uppercase()
        return PrinterBrand("Generic", cleanName.ifBlank { "PR" }, Color(0xFF2563EB))
    }
}

data class Printer(
    val id: String,
    val name: String,
    val host: String = "",
    val port: Int = 631,
    val connectionType: ConnectionType = ConnectionType.WIFI,
    val protocol: PrinterProtocol = PrinterProtocol.IPP,
    val resourcePath: String = "ipp/print",
    val make: String? = null,
    val model: String? = null,
    val supportedFormats: List<String> = listOf("application/pdf"),
    val supportsColor: Boolean = true,
    val supportsDuplex: Boolean = false,
    val isOnline: Boolean = true,
    val isDefault: Boolean = false,
    val lastUsedAt: Long = 0L,
    val isManual: Boolean = false
) {
    val brand: PrinterBrand
        get() = if (connectionType == ConnectionType.PDF) {
            PrinterBrand("PDF", "PDF", Color(0xFFE53935))
        } else {
            PrinterBrandDetector.detect(make, model, name)
        }

    val displaySubtitle: String
        get() = when (connectionType) {
            ConnectionType.PDF -> "Export document as PDF file"
            ConnectionType.BLUETOOTH -> "Bluetooth • ${model ?: "Paired device"}"
            ConnectionType.USB -> "USB OTG • ${model ?: "Direct cable"}"
            ConnectionType.WIFI -> {
                val hostPart = if (host.isNotBlank()) host else ""
                val modelPart = model ?: make ?: "Network printer"
                if (hostPart.isNotBlank()) "$modelPart • $hostPart" else modelPart
            }
        }

    companion object {
        val SAVE_AS_PDF = Printer(
            id = "pdf_virtual_printer",
            name = "Save as PDF",
            host = "localhost",
            port = 0,
            connectionType = ConnectionType.PDF,
            protocol = PrinterProtocol.NONE,
            resourcePath = "",
            make = "Android",
            model = "PDF Converter",
            supportedFormats = listOf("application/pdf"),
            supportsColor = true,
            supportsDuplex = true,
            isOnline = true,
            isDefault = false,
            lastUsedAt = Long.MAX_VALUE,
            isManual = false
        )
    }
}
