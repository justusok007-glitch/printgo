package com.printgo.app.data.drivers

/**
 * Finds the built-in driver for a printer from what it says about itself (the IEEE-1284 device ID it
 * reports over USB, SNMP or IPP) or, failing that, its name.
 *
 * Names are compared after removing the maker, words like "series" and all punctuation, so
 * "MFG:Hewlett-Packard;MDL:hp LaserJet P3010 Series;" matches "HP LaserJet p3010 Series".
 */
object DriverMatcher {

    data class Ieee(val make: String?, val model: String?, val commands: List<String>)
    data class Match(val entry: DriverEntry, val score: Int)

    /** A score of at least this means the driver is meant for exactly this model. */
    const val EXACT = 90
    const val MIN_SCORE = 60

    fun parseIeee(id: String?): Ieee {
        if (id.isNullOrBlank()) return Ieee(null, null, emptyList())
        val map = HashMap<String, String>()
        id.split(';').forEach { part ->
            val k = part.substringBefore(':', "").trim().uppercase()
            val v = part.substringAfter(':', "").trim()
            if (k.isNotEmpty() && v.isNotEmpty()) map.putIfAbsent(k, v)
        }
        val make = map["MFG"] ?: map["MANUFACTURER"]
        val model = map["MDL"] ?: map["MODEL"]
        val cmd = (map["CMD"] ?: map["COMMAND SET"])?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        return Ieee(make, model, cmd)
    }

    private val makeAliases = listOf(
        "hewlett-packard" to "hp", "hewlett packard" to "hp", "hp" to "hp",
        "seiko epson" to "epson", "epson" to "epson",
        "canon" to "canon", "samsung" to "samsung", "fuji xerox" to "xerox", "fujifilm" to "xerox", "xerox" to "xerox",
        "dell" to "dell", "brother" to "brother", "kyocera" to "kyocera", "lexmark" to "lexmark", "ricoh" to "ricoh",
        "konica minolta" to "konicaminolta", "minolta" to "konicaminolta", "oki" to "oki", "sharp" to "sharp",
        "toshiba" to "toshiba", "panasonic" to "panasonic", "citizen" to "citizen", "zebra" to "zebra"
    )

    /** "Hewlett-Packard" -> "hp", "EPSON" -> "epson"; unknown makers keep their first word. */
    fun normMake(make: String?): String? {
        val m = make?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        makeAliases.firstOrNull { m.startsWith(it.first) }?.let { return it.second }
        return m.split(' ', '-', '_').first()
    }

    /** Guesses the maker from the start of a model or printer name ("HP LaserJet ...", "EPSON L455"). */
    fun makeFromName(name: String?): String? {
        val n = name?.trim()?.lowercase() ?: return null
        return makeAliases.firstOrNull { n.startsWith(it.first + " ") || n.startsWith(it.first + "-") || n == it.first }?.second
    }

    private val noiseWords = listOf("series", "printer", "pixma", "imageclass", "i-sensys", "isensys")

    /** Model name reduced to letters and digits, without the maker and filler words. */
    fun normModel(model: String?): String {
        var m = model?.lowercase()?.trim() ?: return ""
        m = m.replace(Regex("\\(.*?\\)"), " ")
        for ((alias, _) in makeAliases) {
            if (m.startsWith("$alias ") || m.startsWith("$alias-")) {
                m = m.substring(alias.length + 1)
                break
            }
        }
        for (w in noiseWords) m = m.replace(Regex("\\b" + Regex.escape(w) + "\\b"), " ")
        return m.filter { it.isLetterOrDigit() }
    }

    private fun kindBonus(kind: DriverKind, make: String?): Int = when (kind) {
        DriverKind.ESCPR -> if (make == "epson") 4 else 1
        DriverKind.HPCUPS -> if (make == "hp") 4 else 1
        DriverKind.SPLIX -> 3
        DriverKind.GUTENPRINT -> 0
    }

    /**
     * Drivers that fit a printer, best first. [deviceId] is its IEEE-1284 ID when known; [name] is the
     * printer's name or make-and-model string.
     */
    fun candidates(deviceId: String?, name: String?, catalog: List<DriverEntry>, limit: Int = 6): List<Match> {
        val ieee = parseIeee(deviceId)
        val make = normMake(ieee.make) ?: makeFromName(ieee.model) ?: makeFromName(name)
        val wanted = listOfNotNull(ieee.model, name).map { normModel(it) }.filter { it.length >= 3 }.distinct()
        if (wanted.isEmpty()) return emptyList()
        val out = ArrayList<Match>()
        for (e in catalog) {
            val eIeee = parseIeee(e.deviceId)
            val eMake = normMake(eIeee.make) ?: normMake(e.make)
            if (make != null && eMake != null && make != eMake) continue
            val eModels = listOfNotNull(eIeee.model, e.model).map { normModel(it) }.filter { it.isNotEmpty() }
            var best = 0
            for (w in wanted) for ((idx, em) in eModels.withIndex()) {
                val s = when {
                    w == em -> if (idx == 0 && eIeee.model != null) 100 else 95
                    w.length >= 4 && em.length >= 4 && (em.startsWith(w) || w.startsWith(em)) ->
                        (80 - 3 * kotlin.math.abs(em.length - w.length)).coerceAtLeast(MIN_SCORE)
                    else -> 0
                }
                if (s > best) best = s
            }
            if (best >= MIN_SCORE) out += Match(e, best + kindBonus(e.kind, eMake ?: make))
        }
        return out.sortedWith(compareByDescending<Match> { it.score }.thenBy { it.entry.model.length }).take(limit)
    }

    /** The driver PrintGo uses on its own for this printer, or null when none fits exactly. */
    fun best(deviceId: String?, name: String?, catalog: List<DriverEntry>): DriverEntry? =
        candidates(deviceId, name, catalog, 1).firstOrNull()?.takeIf { it.score >= EXACT }?.entry
}
