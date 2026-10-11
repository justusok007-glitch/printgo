package com.printgo.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.printgo.app.data.local.JobStatus
import com.printgo.app.data.print.PrintJobManager
import com.printgo.app.data.print.PrintNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the process alive (and shows a notification) while print jobs are being sent. */
class PrintJobService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PrintJobManager.init(applicationContext)
        PrintNotifications.createChannels(this)
        try {
            ServiceCompat.startForeground(
                this,
                PrintNotifications.ID_FOREGROUND,
                PrintNotifications.foreground(this, emptyList()),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (watcher == null) {
            var sawActive = false
            scope.launch {
                kotlinx.coroutines.delay(15000)
                if (!sawActive) { stopSelf() }
            }
            watcher = scope.launch {
                PrintJobManager.jobs.collect { all ->
                    val active = all.filter { JobStatus.isActive(it.status) }
                    if (active.isNotEmpty()) sawActive = true
                    val nm = androidx.core.app.NotificationManagerCompat.from(this@PrintJobService)
                    try {
                        @Suppress("MissingPermission")
                        nm.notify(PrintNotifications.ID_FOREGROUND, PrintNotifications.foreground(this@PrintJobService, active))
                    } catch (_: SecurityException) { }
                    if (active.isEmpty() && sawActive) {
                        ServiceCompat.stopForeground(this@PrintJobService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, PrintJobService::class.java))
            } catch (_: Exception) {
                // Printing still proceeds while the app is open.
            }
        }
    }
}
