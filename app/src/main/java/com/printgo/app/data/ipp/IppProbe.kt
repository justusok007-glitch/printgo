package com.printgo.app.data.ipp

/**
 * What a printer told us in Get-Printer-Attributes, reduced to what the print engine needs: which
 * formats it reads, the raster details for PWG and Apple raster, and which job options it accepts.
 * An empty list means "the printer did not say".
 */
data class IppProbe(
    val formats: List<String>,
    val path: String,
    val pwgResolutions: List<Int> = emptyList(),
    val pwgTypes: List<String> = emptyList(),
    val urf: List<String> = emptyList(),
    val sides: List<String> = emptyList(),
    val colorModes: List<String> = emptyList(),
    val qualities: List<Int> = emptyList(),
    val scalings: List<String> = emptyList(),
    val media: List<String> = emptyList(),
    val copiesMax: Int = 0,
    val multipleDocumentHandling: List<String> = emptyList(),
    val makeAndModel: String? = null,
    val deviceId: String? = null
) {
    fun accepts(mime: String) = formats.any { it.equals(mime, ignoreCase = true) }

    /** The printer's own spelling of [mime] (some printers are picky about "application/vnd.hp-PCL"). */
    fun spelling(mime: String) = formats.firstOrNull { it.equals(mime, ignoreCase = true) } ?: mime

    /** Resolutions from urf-supported "RS300-600". */
    val urfResolutions: List<Int>
        get() = urf.firstOrNull { it.startsWith("RS", ignoreCase = true) }
            ?.substring(2)?.split('-')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()

    val urfGray: Boolean get() = urf.any { it.equals("W8", true) || it.startsWith("W8-", true) }
    val urfRgb: Boolean get() = urf.any { it.equals("SRGB24", true) }
    val urfDuplex: Boolean get() = urf.any { it.startsWith("DM", true) }

    companion object {
        fun from(msg: IppMessage, path: String): IppProbe {
            fun dpi(v: IppValue): Int? = (v as? IppValue.ResolutionValue)?.let { r ->
                if (r.units.toInt() == 4) Math.round(r.xRes * 2.54f) else r.xRes
            }
            val res = msg.findAttribute("pwg-raster-document-resolution-supported")?.values?.mapNotNull { dpi(it) } ?: emptyList()
            val copies = (msg.findAttribute("copies-supported")?.values?.firstOrNull() as? IppValue.RangeOfIntValue)?.upper
                ?: msg.getInt("copies-supported") ?: 0
            return IppProbe(
                formats = msg.getStrings("document-format-supported"),
                path = path,
                pwgResolutions = res,
                pwgTypes = msg.getStrings("pwg-raster-document-type-supported").map { it.lowercase() },
                urf = msg.getStrings("urf-supported").flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() },
                sides = msg.getStrings("sides-supported"),
                colorModes = msg.getStrings("print-color-mode-supported"),
                qualities = msg.findAttribute("print-quality-supported")?.values?.mapNotNull { (it as? IppValue.IntValue)?.value } ?: emptyList(),
                scalings = msg.getStrings("print-scaling-supported"),
                media = msg.getStrings("media-supported"),
                copiesMax = copies,
                multipleDocumentHandling = msg.getStrings("multiple-document-handling-supported"),
                makeAndModel = msg.getString("printer-make-and-model"),
                deviceId = msg.getString("printer-device-id")
            )
        }
    }
}
