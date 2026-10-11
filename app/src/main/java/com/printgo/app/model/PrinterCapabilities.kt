package com.printgo.app.model

import com.printgo.app.data.ipp.IppMessage
import com.printgo.app.data.ipp.IppValue

data class PaperSize(
    val name: String,
    val widthMm: Int,
    val heightMm: Int
)

data class Marker(
    val name: String,
    val colorHex: String,
    val levelPercent: Int, // 0..100, or -1 for unknown
    val type: String
)

data class PrinterCapabilities(
    val makeAndModel: String,
    val state: String, // Idle, Processing, Stopped, Unknown
    val stateReasons: List<String> = emptyList(),
    val isAcceptingJobs: Boolean = true,
    val mediaSupported: List<PaperSize> = emptyList(),
    val mediaDefault: String? = null,
    val mediaReady: List<String> = emptyList(),
    val sidesSupported: List<String> = listOf("one-sided"), // one-sided, two-sided-long-edge, two-sided-short-edge
    val colorSupported: Boolean = true,
    val colorModesSupported: List<String> = listOf("color", "monochrome"),
    val qualitiesSupported: List<String> = listOf("normal"), // draft, normal, high
    val resolutionsSupported: List<Int> = listOf(300, 600),
    val copiesMax: Int = 99,
    val orientationsSupported: List<String> = listOf("portrait", "landscape"),
    val documentFormatsSupported: List<String> = listOf("application/pdf"),
    val pageRangesSupported: Boolean = true,
    val numberUpSupported: List<Int> = listOf(1, 2, 4),
    val printScalingSupported: List<String> = listOf("auto", "fit", "fill"),
    val markerLevels: List<Marker> = emptyList(),
    val location: String? = null,
    val infoUrl: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        fun friendlyMediaName(keyword: String): String {
            val lower = keyword.lowercase().trim()
            return when {
                lower.startsWith("iso_a4") || lower == "a4" -> "A4"
                lower.startsWith("na_letter") || lower == "letter" -> "Letter"
                lower.startsWith("na_legal") || lower == "legal" -> "Legal"
                lower.startsWith("iso_a3") || lower == "a3" -> "A3"
                lower.startsWith("iso_a5") || lower == "a5" -> "A5"
                lower.startsWith("na_index-4x6") || lower.contains("4x6") -> "4x6 Photo"
                lower.startsWith("oe_photo-l") || lower.contains("3.5x5") -> "Photo L (3.5x5)"
                lower.startsWith("na_5x7") || lower.contains("5x7") -> "5x7 Photo"
                lower.startsWith("iso_b5") || lower == "b5" -> "B5"
                lower.startsWith("na_executive") || lower == "executive" -> "Executive"
                lower.startsWith("na_number-10") || lower.contains("envelope") -> "Envelope #10"
                else -> {
                    // Clean up prefixes like na_ or iso_ and suffixes with dimensions
                    keyword.substringBefore("_")
                        .replace("iso_", "")
                        .replace("na_", "")
                        .replace("oe_", "")
                        .replace("-", " ")
                        .trim()
                        .ifBlank { keyword }
                }
            }
        }

        fun standardDimensionsFor(name: String): Pair<Int, Int> {
            val lower = name.lowercase()
            return when {
                lower.contains("a4") -> 210 to 297
                lower.contains("letter") -> 216 to 279
                lower.contains("legal") -> 216 to 356
                lower.contains("a3") -> 297 to 420
                lower.contains("a5") -> 148 to 210
                lower.contains("4x6") -> 102 to 152
                lower.contains("3.5x5") || lower.contains("photo l") -> 89 to 127
                lower.contains("5x7") -> 127 to 178
                lower.contains("b5") -> 176 to 250
                else -> 210 to 297
            }
        }

        fun defaultFor(printer: Printer): PrinterCapabilities {
            val a4 = PaperSize("A4", 210, 297)
            val letter = PaperSize("Letter", 216, 279)
            val photo4x6 = PaperSize("4x6 Photo", 102, 152)

            return PrinterCapabilities(
                makeAndModel = printer.model ?: printer.make ?: printer.name,
                state = if (printer.isOnline) "Idle" else "Offline",
                stateReasons = if (printer.isOnline) emptyList() else listOf("offline"),
                isAcceptingJobs = printer.isOnline,
                mediaSupported = listOf(a4, letter, photo4x6),
                mediaDefault = "A4",
                mediaReady = listOf("A4"),
                sidesSupported = if (printer.supportsDuplex) listOf("one-sided", "two-sided-long-edge") else listOf("one-sided"),
                colorSupported = printer.supportsColor,
                colorModesSupported = if (printer.supportsColor) listOf("color", "monochrome") else listOf("monochrome"),
                qualitiesSupported = listOf("normal", "high"),
                resolutionsSupported = listOf(300, 600),
                copiesMax = 99,
                orientationsSupported = listOf("portrait", "landscape"),
                documentFormatsSupported = printer.supportedFormats.ifEmpty { listOf("application/pdf") },
                pageRangesSupported = true,
                numberUpSupported = listOf(1, 2, 4),
                printScalingSupported = listOf("auto", "fit", "fill"),
                markerLevels = if (printer.connectionType == ConnectionType.PDF) emptyList() else listOf(
                    Marker("Black Toner", "#000000", 85, "toner"),
                    Marker("Cyan Toner", "#00BCD4", 70, "toner"),
                    Marker("Magenta Toner", "#E91E63", 65, "toner"),
                    Marker("Yellow Toner", "#FFEB3B", 90, "toner")
                ),
                location = null,
                infoUrl = null
            )
        }

        fun fromIppResponse(response: IppMessage, fallbackPrinter: Printer): PrinterCapabilities {
            val makeAndModel = response.getString("printer-make-and-model")
                ?: fallbackPrinter.model
                ?: fallbackPrinter.name

            val stateCode = response.getInt("printer-state") ?: 3
            val state = when (stateCode) {
                3 -> "Idle"
                4 -> "Processing"
                5 -> "Stopped"
                else -> "Ready"
            }

            val rawReasons = response.getStrings("printer-state-reasons")
            val stateReasons = rawReasons.filter { it != "none" }

            val isAccepting = response.getBoolean("printer-is-accepting-jobs") ?: true

            // Media parsing: from media-size-supported collections or media-supported strings
            val mediaKeywords = response.getStrings("media-supported")
            val mediaSizeCollections = response.getCollections("media-size-supported")

            val mediaSupported = if (mediaSizeCollections.isNotEmpty()) {
                mediaSizeCollections.mapIndexed { index, col ->
                    val x = (col["x-dimension"]?.firstOrNull() as? IppValue.IntValue)?.value ?: 21000
                    val y = (col["y-dimension"]?.firstOrNull() as? IppValue.IntValue)?.value ?: 29700
                    val widthMm = x / 100
                    val heightMm = y / 100
                    val name = if (index < mediaKeywords.size) friendlyMediaName(mediaKeywords[index]) else "${widthMm}x${heightMm}mm"
                    PaperSize(name, widthMm, heightMm)
                }
            } else if (mediaKeywords.isNotEmpty()) {
                mediaKeywords.map { keyword ->
                    val friendly = friendlyMediaName(keyword)
                    val (w, h) = standardDimensionsFor(friendly)
                    PaperSize(friendly, w, h)
                }.distinctBy { it.name }
            } else {
                listOf(PaperSize("A4", 210, 297), PaperSize("Letter", 216, 279))
            }

            val defaultMediaRaw = response.getString("media-default")
            val mediaDefault = defaultMediaRaw?.let { friendlyMediaName(it) } ?: "A4"
            val mediaReady = response.getStrings("media-ready").map { friendlyMediaName(it) }

            val sidesSupported = response.getStrings("sides-supported").ifEmpty { listOf("one-sided") }
            val colorSupported = response.getBoolean("color-supported") ?: true
            val colorModesSupported = response.getStrings("print-color-mode-supported").ifEmpty {
                if (colorSupported) listOf("color", "monochrome") else listOf("monochrome")
            }

            val qualitiesSupported = response.getStrings("print-quality-supported").ifEmpty {
                listOf("draft", "normal", "high")
            }

            val copiesSupported = response.findAttribute("copies-supported")
            val copiesMax = (copiesSupported?.values?.firstOrNull() as? IppValue.RangeOfIntValue)?.upper ?: 99

            val documentFormats = response.getStrings("document-format-supported").ifEmpty {
                fallbackPrinter.supportedFormats.ifEmpty { listOf("application/pdf") }
            }

            // Marker supplies parsing
            val markerNames = response.getStrings("marker-names")
            val markerColors = response.getStrings("marker-colors")
            val markerLevels = response.findAttribute("marker-levels")?.values?.mapNotNull { (it as? IppValue.IntValue)?.value } ?: emptyList()
            val markerTypes = response.getStrings("marker-types")

            val markers = mutableListOf<Marker>()
            val markerCount = minOf(markerNames.size, markerLevels.size)
            for (i in 0 until markerCount) {
                val rawColor = markerColors.getOrNull(i)?.lowercase() ?: "#000000"
                val hexColor = when (rawColor) {
                    "black", "#000000" -> "#1E293B"
                    "cyan", "#00ffff" -> "#0EA5E9"
                    "magenta", "#ff00ff" -> "#EC4899"
                    "yellow", "#ffff00" -> "#EAB308"
                    else -> if (rawColor.startsWith("#") && rawColor.length in 4..7) rawColor else "#64748B"
                }
                val type = markerTypes.getOrNull(i) ?: "toner"
                markers.add(Marker(markerNames[i], hexColor, markerLevels[i], type))
            }

            val location = response.getString("printer-location")
            val infoUrl = response.getString("printer-more-info")

            return PrinterCapabilities(
                makeAndModel = makeAndModel,
                state = state,
                stateReasons = stateReasons,
                isAcceptingJobs = isAccepting,
                mediaSupported = mediaSupported,
                mediaDefault = mediaDefault,
                mediaReady = mediaReady,
                sidesSupported = sidesSupported,
                colorSupported = colorSupported,
                colorModesSupported = colorModesSupported,
                qualitiesSupported = qualitiesSupported,
                resolutionsSupported = listOf(300, 600, 1200),
                copiesMax = copiesMax,
                orientationsSupported = listOf("portrait", "landscape"),
                documentFormatsSupported = documentFormats,
                pageRangesSupported = true,
                numberUpSupported = listOf(1, 2, 4),
                printScalingSupported = listOf("auto", "fit", "fill"),
                markerLevels = markers,
                location = location,
                infoUrl = infoUrl,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}
