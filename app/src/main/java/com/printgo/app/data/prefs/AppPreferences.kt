package com.printgo.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "printgo_app_prefs")

data class AppSettings(
    val onboardingDone: Boolean = false,
    val keepHistoryDays: Int = 30,        // 0 = never keep history
    val notificationsEnabled: Boolean = true,
    val confirmBeforePrint: Boolean = false,
    val autoCleanCache: Boolean = true,
    val defaultColorMode: String = "COLOR",
    val defaultPaper: String = "A4",
    val bluetoothLanguage: String = "AUTO",   // AUTO, ESCPOS, PCL, POSTSCRIPT, PDF (Bluetooth and raw port 9100)
    val dataFormat: String = "AUTO"          // AUTO = raster first (safest), PDF = send PDF first
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val HISTORY_DAYS = intPreferencesKey("history_days")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val CONFIRM = booleanPreferencesKey("confirm_before_print")
        val AUTO_CLEAN = booleanPreferencesKey("auto_clean_cache")
        val COLOR = stringPreferencesKey("default_color")
        val PAPER = stringPreferencesKey("default_paper")
        val BT_LANG = stringPreferencesKey("bt_language")
        val DATA_FORMAT = stringPreferencesKey("data_format")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            onboardingDone = p[Keys.ONBOARDING] ?: false,
            keepHistoryDays = p[Keys.HISTORY_DAYS] ?: 30,
            notificationsEnabled = p[Keys.NOTIFICATIONS] ?: true,
            confirmBeforePrint = p[Keys.CONFIRM] ?: false,
            autoCleanCache = p[Keys.AUTO_CLEAN] ?: true,
            defaultColorMode = p[Keys.COLOR] ?: "COLOR",
            defaultPaper = p[Keys.PAPER] ?: "A4",
            bluetoothLanguage = p[Keys.BT_LANG] ?: "AUTO",
            dataFormat = p[Keys.DATA_FORMAT] ?: "AUTO"
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[Keys.ONBOARDING] = v }
    suspend fun setHistoryDays(v: Int) = context.dataStore.edit { it[Keys.HISTORY_DAYS] = v }
    suspend fun setNotifications(v: Boolean) = context.dataStore.edit { it[Keys.NOTIFICATIONS] = v }
    suspend fun setConfirmBeforePrint(v: Boolean) = context.dataStore.edit { it[Keys.CONFIRM] = v }
    suspend fun setBluetoothLanguage(v: String) = context.dataStore.edit { it[Keys.BT_LANG] = v }
    suspend fun setDataFormat(v: String) = context.dataStore.edit { it[Keys.DATA_FORMAT] = v }
    suspend fun setAutoCleanCache(v: Boolean) = context.dataStore.edit { it[Keys.AUTO_CLEAN] = v }
}
