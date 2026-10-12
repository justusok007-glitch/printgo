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
    /**
     * False when the printer named no two-sided mode at all. Many printers (HP LaserJets over Wi-Fi, for
     * example) leave it out even though they have a duplex unit, so PrintGo then lets the user choose
     * two-sided and sends it anyway; a printer without the unit just prints one side.
     */
    fun twoSidedReported(): Boolean = sidesSupported.any { it.startsWith("two-sided") }

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

        /** Size in mm from a PWG self-describing media name, or null (custom ranges, odd names). */
        fun sizeFromMediaName(keyword: String): Pair<Int, Int>? {
            val k = keyword.trim().lowercase()
            if (k.startsWith("custom_")) return null
            val m = Regex("_(\\d+(?:\\.\\d+)?)x(\\d+(?:\\.\\d+)?)(mm|in)$").find(k) ?: return null
            val f = if (m.groupValues[3] == "in") 25.4f else 1f
            val w = Math.round(m.groupValues[1].toFloat() * f)
            val h = Math.round(m.groupValues[2].toFloat() * f)
            if (w <= 0 || h <= 0) return null
            return minOf(w, h) to maxOf(w, h)
        }

        private fun nameForMedia(keyword: String, w: Int, h: Int): String {
            val n = nameForSize(w, h)
            if (!n.endsWith(" mm")) return n
            val k = keyword.lowercase()
            return when {
                k.startsWith("oe_photo-l") -> "Photo L (3.5x5)"
                k.startsWith("na_executive") -> "Executive"
                k.startsWith("na_number-10") -> "Envelope #10"
                k.startsWith("iso_dl") -> "Envelope DL"
                else -> n
            }
        }

        private fun nameForSize(w: Int, h: Int): String {
            val known = listOf("A4" to (210 to 297), "Letter" to (216 to 279), "Legal" to (216 to 356), "A3" to (297 to 420),
                "A5" to (148 to 210), "4x6 Photo" to (102 to 152), "5x7 Photo" to (127 to 178), "B5" to (176 to 250))
            return known.firstOrNull { (_, s) -> kotlin.math.abs(s.first - w) <= 2 && kotlin.math.abs(s.second - h) <= 2 }?.first ?: "${w}x${h} mm"
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
            val legal = PaperSize("Legal", 216, 356)
            val a5 = PaperSize("A5", 148, 210)
            val a3 = PaperSize("A3", 297, 420)

            // Used when the printer cannot be asked (raw port 9100, Bluetooth, USB without IPP). Nothing is
            // known for sure, so allow every common option; a printer without a duplex unit simply prints
            // one-sided, and the printer itself asks for paper it does not have loaded.
            return PrinterCapabilities(
                makeAndModel = printer.model ?: printer.make ?: printer.name,
                state = if (printer.isOnline) "Idle" else "Offline",
                stateReasons = if (printer.isOnline) emptyList() else listOf("offline"),
                isAcceptingJobs = printer.isOnline,
                mediaSupported = listOf(a4, a5, a3, letter, legal, photo4x6),
                mediaDefault = "A4",
                mediaReady = listOf("A4"),
                sidesSupported = if (printer.connectionType == ConnectionType.PDF) listOf("one-sided")
                else listOf("one-sided", "two-sided-long-edge", "two-sided-short-edge"),
                colorSupported = printer.supportsColor,
                colorModesSupported = if (printer.supportsColor) listOf("color", "monochrome") else listOf("monochrome"),
                qualitiesSupported = listOf("draft", "normal", "high"),
                resolutionsSupported = listOf(300, 600),
                copiesMax = 99,
                orientationsSupported = listOf("portrait", "landscape"),
                documentFormatsSupported = printer.supportedFormats.ifEmpty { listOf("application/pdf") },
                pageRangesSupported = true,
                numberUpSupported = listOf(1, 2, 4),
                printScalingSupported = listOf("auto", "fit", "fill"),
                markerLevels = emptyList(), // unknown: never show made-up toner levels
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

            // PWG media names carry their own size ("iso_a4_210x297mm", "na_letter_8.5x11in"). Read the size
            // from the name itself: media-size-supported is a separate list in a different order, and pairing
            // the two by position labelled Letter-sized paper "A4".
            val fromNames = mediaKeywords.mapNotNull { kw -> sizeFromMediaName(kw)?.let { (w, h) -> PaperSize(nameForMedia(kw, w, h), w, h) } }
                .distinctBy { it.name }
            val mediaSupported = if (fromNames.isNotEmpty()) {
                fromNames
            } else if (mediaSizeCollections.isNotEmpty()) {
                mediaSizeCollections.mapNotNull { col ->
                    val x = (col["x-dimension"]?.firstOrNull() as? IppValue.IntValue)?.value ?: return@mapNotNull null
                    val y = (col["y-dimension"]?.firstOrNull() as? IppValue.IntValue)?.value ?: return@mapNotNull null
                    val widthMm = Math.round(x / 100f)
                    val heightMm = Math.round(y / 100f)
                    PaperSize(nameForSize(widthMm, heightMm), widthMm, heightMm)
                }.distinctBy { it.name }.ifEmpty { listOf(PaperSize("A4", 210, 297), PaperSize("Letter", 216, 279)) }
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
