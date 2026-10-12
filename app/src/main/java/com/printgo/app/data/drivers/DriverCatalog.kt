package com.printgo.app.data.drivers

import android.content.Context
import org.json.JSONObject

/**
 * The printer drivers built into PrintGo (see native/ and THIRD_PARTY_NOTICES.md). Each driver program is
 * in the APK; Gutenprint, HPLIP and SpliX also need a data pack (printer descriptions) downloaded once.
 */
enum class DriverKind(val pack: String?, val program: String, val label: String) {
    ESCPR(null, "pg_escpr", "Epson ESC/P-R"),
    GUTENPRINT("gutenprint", "pg_gutenprint", "Gutenprint"),
    HPCUPS("hplip", "pg_hpcups", "HP (HPLIP)"),
    SPLIX("splix", "pg_splix", "Samsung / Xerox (SpliX)");

    companion object {
        fun fromCode(code: String): DriverKind? = when (code) {
            "escpr" -> ESCPR
            "gutenprint" -> GUTENPRINT
            "hpcups" -> HPCUPS
            "splix" -> SPLIX
            else -> null
        }
    }
}

/** One printer model a driver supports. [file] is the PPD inside the pack, or the Gutenprint driver name. */
data class DriverEntry(
    val id: String,
    val kind: DriverKind,
    val make: String,
    val model: String,
    val deviceId: String = "",
    val color: Boolean = true,
    val duplex: Boolean = false,
    val file: String? = null,
    val family: String? = null
) {
    /** What the user sees: "Epson L455 Series" etc., without repeating the maker twice. */
    val displayName: String
        get() = if (model.startsWith(make, ignoreCase = true)) model else "$make $model"
}

object DriverCatalog {
    @Volatile private var cached: List<DriverEntry>? = null

    /** Every model of every built-in driver. Empty when the app was built without the drivers. */
    fun load(context: Context): List<DriverEntry> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val list = try {
                context.assets.open("drivers/catalog.json").use { parse(it.readBytes().decodeToString()) }
            } catch (_: Exception) {
                emptyList()
            }
            cached = list
            return list
        }
    }

    fun find(context: Context, id: String): DriverEntry? = load(context).firstOrNull { it.id == id }

    fun parse(json: String): List<DriverEntry> {
        val arr = JSONObject(json).getJSONArray("drivers")
        val out = ArrayList<DriverEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val kind = DriverKind.fromCode(o.optString("k")) ?: continue
            out += DriverEntry(
                id = o.getString("i"),
                kind = kind,
                make = o.optString("m"),
                model = o.optString("n"),
                deviceId = o.optString("d"),
                color = o.optInt("c", 1) == 1,
                duplex = o.optInt("x", 0) == 1,
                file = o.optString("f").takeIf { it.isNotEmpty() },
                family = o.optString("g").takeIf { it.isNotEmpty() }
            )
        }
        return out
    }
}
