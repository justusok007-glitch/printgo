package com.printgo.app.data.drivers

import android.content.Context

/**
 * Which driver each printer uses. Nothing stored = automatic: PrintGo talks to the printer directly and
 * uses a built-in driver only when the printer cannot take the formats PrintGo makes itself.
 */
class DriverPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("printgo_drivers", Context.MODE_PRIVATE)

    /** The driver id the user picked for [printerId], or null for automatic. */
    fun choice(printerId: String): String? = sp.getString("p_$printerId", null)

    fun setChoice(printerId: String, driverId: String?) {
        sp.edit().apply { if (driverId == null) remove("p_$printerId") else putString("p_$printerId", driverId) }.apply()
    }

    /** The printer's own IEEE-1284 device ID, remembered the last time it was seen (for suggestions). */
    fun deviceId(printerId: String): String? = sp.getString("d_$printerId", null)

    fun rememberDeviceId(printerId: String, deviceId: String?) {
        if (!deviceId.isNullOrBlank() && deviceId != deviceId(printerId)) sp.edit().putString("d_$printerId", deviceId).apply()
    }
}
