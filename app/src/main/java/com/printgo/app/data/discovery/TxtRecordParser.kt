package com.printgo.app.data.discovery

import com.printgo.app.model.PrinterBrandDetector

data class ParsedPrinterAttributes(
    val make: String?,
    val model: String?,
    val resourcePath: String,
    val supportedFormats: List<String>,
    val supportsColor: Boolean,
    val supportsDuplex: Boolean,
    val note: String?
)

object TxtRecordParser {

    fun parse(attributes: Map<String, ByteArray?>): ParsedPrinterAttributes {
        val stringMap = attributes.mapNotNull { (key, bytes) ->
            if (bytes != null) key.lowercase() to String(bytes, Charsets.UTF_8).trim() else null
        }.toMap()

        return parseStringMap(stringMap)
    }

    fun parseStringMap(attributes: Map<String, String>): ParsedPrinterAttributes {
        val rawModel = attributes["ty"] ?: attributes["product"] ?: attributes["usb_mdl"] ?: attributes["mdl"]
        var make = attributes["usb_mfg"] ?: attributes["mfg"]
        var model = rawModel

        // If make is missing, try to detect it from the model or product name
        if (make.isNullOrBlank() && !model.isNullOrBlank()) {
            val detectedBrand = PrinterBrandDetector.detect(null, model, null)
            if (detectedBrand.displayName != "Generic") {
                make = detectedBrand.displayName
            }
        }

        // Clean up model string if it starts with make (e.g. "HP HP LaserJet" -> "LaserJet")
        if (make != null && model != null) {
            val cleanMake = make.trim()
            if (model.startsWith(cleanMake, ignoreCase = true)) {
                model = model.substring(cleanMake.length).trim()
            }
        }

        val resourcePath = attributes["rp"]?.ifBlank { "ipp/print" } ?: "ipp/print"

        val pdlRaw = attributes["pdl"] ?: "application/pdf"
        val supportedFormats = pdlRaw.split(",", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("application/pdf") }

        val colorValue = attributes["color"]?.lowercase()
        val supportsColor = colorValue == "t" || colorValue == "true" || colorValue == "1" || colorValue == "yes"

        val duplexValue = attributes["duplex"]?.lowercase()
        val supportsDuplex = duplexValue == "t" || duplexValue == "true" || duplexValue == "1" || duplexValue == "yes"

        val note = attributes["note"]

        return ParsedPrinterAttributes(
            make = make,
            model = model?.ifBlank { null },
            resourcePath = resourcePath,
            supportedFormats = supportedFormats,
            supportsColor = supportsColor,
            supportsDuplex = supportsDuplex,
            note = note
        )
    }
}
