package com.printgo.app.data.print

/** Printer languages PrintGo can create itself for printers that are fed raw data (USB, port 9100, Bluetooth). */
enum class RawLanguage(val label: String) {
    PDF("PDF"), POSTSCRIPT("PostScript"), PCL("PCL"), ESCPOS("ESC/POS"), ESCPR("ESC/P-R")
}

/**
 * Chooses the raw language for a printer. The printer's own IEEE-1284 device id (CMD: list, read over
 * USB or SNMP) is trusted first, then the languages it announces on the network (mDNS "pdl"), then a
 * user choice, and only then a careful guess from the name. Guessing wrong prints pages of junk text,
 * so inkjet brands are never guessed as PCL and raw PDF is never guessed.
 */
object LanguagePicker {

    private val thermalCmd = listOf("ESC/POS", "ESCPOS", "STAR", "CPCL", "ZPL", "TSPL", "EPL2", "EPL")
    private val thermalNames = listOf("pos", "receipt", "thermal", "xprinter", "goojprt", "zjiang", "label", "tm-", "star ", "rongta", "sunmi")

    /** Office laser brands/series that read PCL 5. Inkjet brands (Epson, Canon PIXMA, HP DeskJet…) are not here. */
    private val pclNameHints = listOf(
        "laserjet", "kyocera", "ecosys", "taskalfa", "ricoh", "lexmark", "xerox", "samsung ml", "samsung m",
        "konica", "bizhub", "sharp mx", "oki", "brother hl-", "brother mfc-l", "brother dcp-l", "hl-l", "mfc-l", "dcp-l"
    )

    /** PCL 5 / PCL 3 raster tokens. PCLXL (PCL 6), PCL3GUI and PCLm are different languages. */
    private fun hasPcl(cmd: Set<String>) = cmd.any { it == "PCL" || it == "PCL5" || it == "PCL5E" || it == "PCL5C" || it == "PCL3" || it == "HPPCL" }
    private fun hasPostScript(cmd: Set<String>) = cmd.any { it == "POSTSCRIPT" || it == "PS" || it.startsWith("POSTSCRIPT") || it == "PS3" || it == "PS2" || it == "BR-SCRIPT" || it == "KPDL" }
    private fun hasEscpr(cmd: Set<String>) = cmd.any { it.startsWith("ESCPR") }

    fun fromCmd(cmd: Set<String>): RawLanguage? = when {
        cmd.isEmpty() -> null
        cmd.any { c -> thermalCmd.any { c.contains(it) } } && !hasEscpr(cmd) && !hasPcl(cmd) -> RawLanguage.ESCPOS
        hasEscpr(cmd) -> RawLanguage.ESCPR
        hasPcl(cmd) -> RawLanguage.PCL
        hasPostScript(cmd) -> RawLanguage.POSTSCRIPT
        "PDF" in cmd -> RawLanguage.PDF
        else -> null
    }

    /** From announced MIME types (mDNS "pdl" / IPP document-format-supported). PDF alone is not trusted. */
    fun fromFormats(formats: Collection<String>): RawLanguage? {
        val f = formats.map { it.trim().lowercase() }
        return when {
            f.any { it == "application/vnd.epson.escpr" || it.startsWith("application/vnd.epson.escpr") } -> RawLanguage.ESCPR
            f.any { it == "application/vnd.hp-pcl" || it == "application/pcl" } -> RawLanguage.PCL
            f.any { it == "application/postscript" } -> RawLanguage.POSTSCRIPT
            else -> null
        }
    }

    fun fromName(name: String): RawLanguage? {
        val n = name.lowercase()
        if (thermalNames.any { n.contains(it) }) return RawLanguage.ESCPOS
        if (n.contains("epson")) return RawLanguage.ESCPR
        if (pclNameHints.any { n.contains(it) }) return RawLanguage.PCL
        return null
    }

    /**
     * @param pref Settings choice: AUTO, ESCPOS, PCL, POSTSCRIPT, PDF, ESCPR
     * @param strict USB: only the device id or a clearly identifiable name is acceptable.
     */
    fun pick(deviceId: String?, name: String, pref: String, strict: Boolean = false, formats: Collection<String> = emptyList()): RawLanguage? {
        fromCmd(PclEncoder.parseCmd(deviceId))?.let { return it }
        if (strict) {
            val n = name.lowercase()
            if (thermalNames.any { n.contains(it) }) return RawLanguage.ESCPOS
            if (n.contains("epson")) return RawLanguage.ESCPR
            return null
        }
        when (pref) {
            "ESCPOS" -> return RawLanguage.ESCPOS
            "PCL" -> return RawLanguage.PCL
            "POSTSCRIPT" -> return RawLanguage.POSTSCRIPT
            "PDF" -> return RawLanguage.PDF
            "ESCPR" -> return RawLanguage.ESCPR
        }
        fromFormats(formats)?.let { return it }
        return fromName(name)
    }
}
