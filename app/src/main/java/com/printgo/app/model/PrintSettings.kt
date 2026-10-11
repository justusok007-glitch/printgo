package com.printgo.app.model

enum class PagesPerSheet(val count: Int, val cols: Int, val rows: Int) {
    ONE(1, 1, 1),
    TWO(2, 2, 1),
    FOUR(4, 2, 2),
    SIX(6, 3, 2),
    NINE(9, 3, 3);

    companion object {
        fun fromCount(count: Int): PagesPerSheet = entries.find { it.count == count } ?: ONE
    }
}

enum class PageOrder(val label: String) {
    LEFT_TO_RIGHT("Left to Right"),
    RIGHT_TO_LEFT("Right to Left"),
    TOP_TO_BOTTOM("Top to Bottom")
}

enum class DuplexMode(val label: String) {
    ONE_SIDED("One-Sided"),
    TWO_SIDED_LONG_EDGE("Two-Sided (Long Edge)"),
    TWO_SIDED_SHORT_EDGE("Two-Sided (Short Edge)")
}

enum class PrintOrientation(val label: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

sealed interface PrintScale {
    val label: String

    data object FitToPage : PrintScale {
        override val label: String = "Scale to fit"
    }

    data object FillPage : PrintScale {
        override val label: String = "Fill entire page"
    }

    data object ActualSize : PrintScale {
        override val label: String = "Actual size (100%)"
    }

    data class CustomPercent(val percent: Int) : PrintScale {
        init {
            require(percent in 25..400) { "Scale percent must be between 25 and 400" }
        }
        override val label: String = "Custom ($percent%)"
    }
}

sealed interface PrintMargins {
    val label: String
    val topMm: Float
    val bottomMm: Float
    val leftMm: Float
    val rightMm: Float

    data object None : PrintMargins {
        override val label: String = "No margins"
        override val topMm: Float = 0f
        override val bottomMm: Float = 0f
        override val leftMm: Float = 0f
        override val rightMm: Float = 0f
    }

    data object Narrow : PrintMargins {
        override val label: String = "Narrow margins (6 mm)"
        override val topMm: Float = 6f
        override val bottomMm: Float = 6f
        override val leftMm: Float = 6f
        override val rightMm: Float = 6f
    }

    data object Normal : PrintMargins {
        override val label: String = "Normal margins (12 mm)"
        override val topMm: Float = 12f
        override val bottomMm: Float = 12f
        override val leftMm: Float = 12f
        override val rightMm: Float = 12f
    }

    data object Wide : PrintMargins {
        override val label: String = "Wide margins (25 mm)"
        override val topMm: Float = 25f
        override val bottomMm: Float = 25f
        override val leftMm: Float = 25f
        override val rightMm: Float = 25f
    }

    data class Custom(
        override val topMm: Float,
        override val bottomMm: Float,
        override val leftMm: Float,
        override val rightMm: Float
    ) : PrintMargins {
        override val label: String = "Custom (${topMm.toInt()}×${leftMm.toInt()} mm)"
    }
}

enum class PrintAlignment(val label: String) {
    CENTER("Center"),
    TOP("Top"),
    TOP_LEFT("Top Left"),
    TOP_RIGHT("Top Right"),
    LEFT("Left"),
    RIGHT("Right"),
    BOTTOM("Bottom"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right")
}

enum class PrintColorMode(val label: String) {
    COLOR("Color"),
    GRAYSCALE("Grayscale")
}

enum class PrintQuality(val label: String) {
    DRAFT("Draft"),
    NORMAL("Normal"),
    HIGH("High")
}

enum class PageNumberPosition(val label: String) {
    BOTTOM_CENTER("Bottom Center"),
    BOTTOM_RIGHT("Bottom Right"),
    TOP_RIGHT("Top Right")
}

data class WatermarkSettings(
    val text: String,
    val opacity: Float = 0.20f, // 0.05f..0.50f
    val isDiagonal: Boolean = true
)

sealed interface PageSelection {
    data object All : PageSelection
    data object Odd : PageSelection
    data object Even : PageSelection
    data class Range(val text: String) : PageSelection
    data class Custom(val pages: Set<Int>) : PageSelection // 1-indexed page numbers
}

sealed interface PageRangeResult {
    data class Success(val pages: List<Int>) : PageRangeResult // 1-indexed, sorted, deduplicated
    data class Error(val message: String) : PageRangeResult
}

object PageRangeParser {
    /**
     * Parses a page range string like "1-3,5,8-10".
     * Returns a [PageRangeResult.Success] with 1-indexed page numbers sorted and deduplicated,
     * or a [PageRangeResult.Error] with a friendly message.
     */
    fun parse(text: String, totalPages: Int = 0): PageRangeResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return PageRangeResult.Error("Page selection cannot be empty")
        }

        val parts = trimmed.split(",")
        val result = mutableSetOf<Int>()

        for (rawPart in parts) {
            val part = rawPart.trim()
            if (part.isEmpty()) {
                return PageRangeResult.Error("Invalid range syntax (extra or trailing comma)")
            }

            if (part.contains("-")) {
                val tokens = part.split("-")
                if (tokens.size != 2) {
                    return PageRangeResult.Error("Invalid range format: '$part'")
                }
                val startStr = tokens[0].trim()
                val endStr = tokens[1].trim()
                val start = startStr.toIntOrNull()
                val end = endStr.toIntOrNull()

                if (start == null || end == null) {
                    return PageRangeResult.Error("Non-numeric page in range: '$part'")
                }
                if (start < 1) {
                    return PageRangeResult.Error("Page number must be at least 1 (got $start)")
                }
                if (end < start) {
                    return PageRangeResult.Error("Start page ($start) cannot exceed end page ($end)")
                }
                if (totalPages > 0 && (start > totalPages || end > totalPages)) {
                    return PageRangeResult.Error("Page range $start-$end exceeds total pages ($totalPages)")
                }
                for (p in start..end) {
                    result.add(p)
                }
            } else {
                val page = part.toIntOrNull()
                    ?: return PageRangeResult.Error("Invalid page number: '$part'")
                if (page < 1) {
                    return PageRangeResult.Error("Page number must be at least 1 (got $page)")
                }
                if (totalPages > 0 && page > totalPages) {
                    return PageRangeResult.Error("Page $page exceeds total pages ($totalPages)")
                }
                result.add(page)
            }
        }

        if (result.isEmpty()) {
            return PageRangeResult.Error("No valid pages specified")
        }

        return PageRangeResult.Success(result.sorted())
    }
}

object PrintSettingsValidator {
    fun validateCustomMargins(top: Float, bottom: Float, left: Float, right: Float): Result<PrintMargins.Custom> {
        if (top !in 0f..50f) return Result.failure(IllegalArgumentException("Top margin must be 0–50 mm"))
        if (bottom !in 0f..50f) return Result.failure(IllegalArgumentException("Bottom margin must be 0–50 mm"))
        if (left !in 0f..50f) return Result.failure(IllegalArgumentException("Left margin must be 0–50 mm"))
        if (right !in 0f..50f) return Result.failure(IllegalArgumentException("Right margin must be 0–50 mm"))
        return Result.success(PrintMargins.Custom(top, bottom, left, right))
    }

    fun validateCustomPaper(widthMm: Int, heightMm: Int, name: String = "Custom"): Result<PaperSize> {
        if (widthMm !in 50..500) return Result.failure(IllegalArgumentException("Width must be 50–500 mm"))
        if (heightMm !in 50..500) return Result.failure(IllegalArgumentException("Height must be 50–500 mm"))
        val safeName = name.trim().ifEmpty { "Custom ($widthMm×$heightMm mm)" }
        return Result.success(PaperSize(safeName, widthMm, heightMm))
    }

    fun validatePresetName(name: String, existingNames: List<String>): Result<String> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Preset name cannot be empty"))
        if (trimmed.length > 30) return Result.failure(IllegalArgumentException("Preset name cannot exceed 30 characters"))
        if (existingNames.any { it.equals(trimmed, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("A preset with this name already exists"))
        }
        return Result.success(trimmed)
    }
}

/**
 * Immutable Print Settings reflecting all options for document layout, formatting, and printing.
 */
data class PrintSettings(
    val copies: Int = 1,
    val collate: Boolean = true,
    val pageSelection: PageSelection = PageSelection.All,
    val reverseOrder: Boolean = false,
    val pagesPerSheet: PagesPerSheet = PagesPerSheet.ONE,
    val pageOrder: PageOrder = PageOrder.LEFT_TO_RIGHT,
    val duplex: DuplexMode = DuplexMode.ONE_SIDED,
    val paperSize: PaperSize = PaperSize("A4", 210, 297),
    val orientation: PrintOrientation = PrintOrientation.AUTO,
    val scale: PrintScale = PrintScale.FitToPage,
    val margins: PrintMargins = PrintMargins.Normal,
    val alignment: PrintAlignment = PrintAlignment.CENTER,
    val colorMode: PrintColorMode = PrintColorMode.COLOR,
    val quality: PrintQuality = PrintQuality.NORMAL,
    val pageRotations: Map<Int, Int> = emptyMap(), // global 0-indexed page -> rotation (0, 90, 180, 270)
    val excludedPages: Set<Int> = emptySet(), // global 0-indexed pages excluded by user
    val borderAroundPages: Boolean = false,
    val pageNumbers: PageNumberPosition? = null,
    val dateStamp: Boolean = false,
    val fileNameHeader: Boolean = false,
    val watermark: WatermarkSettings? = null,
    val activePresetName: String? = null
) {
    /**
     * Validates and clamps settings against the selected printer's actual capabilities.
     */
    fun validateAgainst(capabilities: PrinterCapabilities?): PrintSettings {
        if (capabilities == null) return this

        val clampedCopies = copies.coerceIn(1, capabilities.copiesMax.coerceAtLeast(1))

        var validDuplex = duplex
        if (duplex != DuplexMode.ONE_SIDED) {
            val supportsLong = capabilities.sidesSupported.any { it.contains("long-edge") }
            val supportsShort = capabilities.sidesSupported.any { it.contains("short-edge") }
            if (duplex == DuplexMode.TWO_SIDED_LONG_EDGE && !supportsLong) {
                validDuplex = if (supportsShort) DuplexMode.TWO_SIDED_SHORT_EDGE else DuplexMode.ONE_SIDED
            } else if (duplex == DuplexMode.TWO_SIDED_SHORT_EDGE && !supportsShort) {
                validDuplex = if (supportsLong) DuplexMode.TWO_SIDED_LONG_EDGE else DuplexMode.ONE_SIDED
            }
        }

        val validColorMode = if (!capabilities.colorSupported) {
            PrintColorMode.GRAYSCALE
        } else {
            colorMode
        }

        var validQuality = quality
        if (capabilities.qualitiesSupported.isNotEmpty()) {
            val supportedLower = capabilities.qualitiesSupported.map { it.lowercase() }
            val currentLower = quality.name.lowercase()
            if (!supportedLower.contains(currentLower)) {
                validQuality = if (supportedLower.contains("normal")) PrintQuality.NORMAL
                else PrintQuality.entries.firstOrNull { supportedLower.contains(it.name.lowercase()) } ?: PrintQuality.NORMAL
            }
        }

        var validPaper = paperSize
        if (capabilities.mediaSupported.isNotEmpty()) {
            val matched = capabilities.mediaSupported.find {
                it.name.equals(paperSize.name, ignoreCase = true) ||
                    (Math.abs(it.widthMm - paperSize.widthMm) <= 2 && Math.abs(it.heightMm - paperSize.heightMm) <= 2)
            }
            if (matched == null) {
                val defaultMedia = capabilities.mediaSupported.find {
                    it.name.equals(capabilities.mediaDefault, ignoreCase = true)
                } ?: capabilities.mediaSupported.first()
                validPaper = defaultMedia
            }
        }

        return this.copy(
            copies = clampedCopies,
            duplex = validDuplex,
            colorMode = validColorMode,
            quality = validQuality,
            paperSize = validPaper
        )
    }

    companion object {
        val DEFAULT = PrintSettings()
    }
}
