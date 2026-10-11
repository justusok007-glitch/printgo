package com.printgo.app

import android.app.Application
import com.printgo.app.data.prefs.AppPreferences
import com.printgo.app.data.print.PrintJobManager
import com.printgo.app.data.print.PrintNotifications
import com.printgo.app.screens.clearTempFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PrintGoApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PrintNotifications.createChannels(this)
        PrintJobManager.init(this)
        val prefs = AppPreferences(this)
        scope.launch {
            val s = prefs.current()
            PrintNotifications.resultsEnabled = s.notificationsEnabled
            // History retention (0 = keep nothing older than a day)
            PrintJobManager.cleanupOlderThan(if (s.keepHistoryDays == 0) 1 else s.keepHistoryDays)
            if (s.autoCleanCache) clearTempFiles(cacheDir, olderThanMs = 24L * 60 * 60 * 1000)
            prefs.settings.collect { PrintNotifications.resultsEnabled = it.notificationsEnabled }
        }
    }
}
