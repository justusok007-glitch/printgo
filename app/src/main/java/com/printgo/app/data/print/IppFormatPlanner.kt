package com.printgo.app.data.print

import com.printgo.app.data.ipp.IppProbe
import kotlin.math.abs

enum class SendKind(val label: String) {
    PWG("PWG raster"), URF("AirPrint raster"), PCL("PCL"), POSTSCRIPT("PostScript"), PDF("PDF"), JPEG("JPEG"), ESCPR("ESC/P-R")
}

/** One way of sending a job: the data kind, the MIME type to announce, and raster details. */
data class SendPlan(val kind: SendKind, val mime: String, val dpi: Int = 0, val grayscale: Boolean = false)

/**
 * Decides, from what the printer says it accepts, which formats to try and in what order. Raster
 * formats come first: they are fully rendered on the phone, so every printer prints them the same way,
 * and a printer that cannot read PDF never receives PDF source.
 */
object IppFormatPlanner {
    const val MIME_PWG = "image/pwg-raster"
    const val MIME_URF = "image/urf"
    const val MIME_PCL = "application/vnd.hp-pcl"
    const val MIME_PS = "application/postscript"
    const val MIME_PDF = "application/pdf"
    const val MIME_JPEG = "image/jpeg"
    const val MIME_ESCPR = "application/vnd.epson.escpr"

    fun order(pref: String): List<SendKind> = when (pref) {
        "PDF" -> listOf(SendKind.PDF, SendKind.PWG, SendKind.URF, SendKind.JPEG, SendKind.PCL, SendKind.POSTSCRIPT)
        "JPEG" -> listOf(SendKind.JPEG, SendKind.PWG, SendKind.URF, SendKind.PDF, SendKind.PCL, SendKind.POSTSCRIPT)
        "PWG" -> listOf(SendKind.PWG, SendKind.URF, SendKind.PDF, SendKind.JPEG, SendKind.PCL, SendKind.POSTSCRIPT)
        "URF" -> listOf(SendKind.URF, SendKind.PWG, SendKind.PDF, SendKind.JPEG, SendKind.PCL, SendKind.POSTSCRIPT)
        else -> listOf(SendKind.PWG, SendKind.URF, SendKind.PCL, SendKind.POSTSCRIPT, SendKind.PDF, SendKind.JPEG, SendKind.ESCPR)
    }

    /**
     * @param p what the printer reported, or null when it could not be asked (then every common format
     *   is tried and the printer's "format not supported" answers move us along).
     */
    fun plan(p: IppProbe?, pref: String, wantGray: Boolean, draft: Boolean): List<SendPlan> {
        val target = if (draft) 150 else 300
        val out = mutableListOf<SendPlan>()
        for (k in order(pref)) {
            when (k) {
                SendKind.PWG -> {
                    if (p != null && !p.accepts(MIME_PWG)) continue
                    val types = p?.pwgTypes.orEmpty()
                    val gray = when {
                        types.isEmpty() -> wantGray
                        wantGray && "sgray_8" in types -> true
                        !wantGray && "srgb_8" in types -> false
                        "srgb_8" in types -> false
                        "sgray_8" in types -> true
                        else -> null // only 1-bit, 16-bit, CMYK or Adobe RGB: not written here
                    } ?: continue
                    out += SendPlan(SendKind.PWG, MIME_PWG, closest(p?.pwgResolutions.orEmpty(), target), gray)
                }
                SendKind.URF -> {
                    if (p == null || !p.accepts(MIME_URF)) continue
                    val gray = when {
                        !p.urfGray && !p.urfRgb -> wantGray
                        wantGray && p.urfGray -> true
                        !wantGray && p.urfRgb -> false
                        p.urfRgb -> false
                        else -> true
                    }
                    out += SendPlan(SendKind.URF, p.spelling(MIME_URF), closest(p.urfResolutions, target), gray)
                }
                SendKind.PCL -> if (p == null || p.accepts(MIME_PCL)) out += SendPlan(SendKind.PCL, p?.spelling(MIME_PCL) ?: MIME_PCL)
                SendKind.POSTSCRIPT -> if (p == null || p.accepts(MIME_PS)) out += SendPlan(SendKind.POSTSCRIPT, p?.spelling(MIME_PS) ?: MIME_PS)
                SendKind.PDF -> if (p == null || p.accepts(MIME_PDF)) out += SendPlan(SendKind.PDF, p?.spelling(MIME_PDF) ?: MIME_PDF)
                SendKind.JPEG -> if (p == null || p.accepts(MIME_JPEG)) out += SendPlan(SendKind.JPEG, p?.spelling(MIME_JPEG) ?: MIME_JPEG)
                SendKind.ESCPR -> if (p != null && p.accepts(MIME_ESCPR)) out += SendPlan(SendKind.ESCPR, p.spelling(MIME_ESCPR), 360, wantGray)
            }
        }
        return out
    }

    /** The supported resolution nearest to [target]; [target] when the printer did not say. */
    fun closest(list: List<Int>, target: Int): Int {
        val valid = list.filter { it in 72..2400 }
        if (valid.isEmpty()) return target
        return valid.minWithOrNull(compareBy<Int> { abs(it - target) }.thenBy { it }) ?: target
    }
}
