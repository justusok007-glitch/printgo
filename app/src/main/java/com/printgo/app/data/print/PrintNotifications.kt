package com.printgo.app.data.print

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.printgo.app.MainActivity
import com.printgo.app.data.local.JobStatus
import com.printgo.app.data.local.PrintJobEntity

object PrintNotifications {
    const val CHANNEL_PROGRESS = "printgo_progress"
    const val CHANNEL_RESULT = "printgo_result"
    const val ID_FOREGROUND = 4101

    /** Mirrors the "Job notifications" setting. */
    @Volatile var resultsEnabled: Boolean = true

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Print progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while PrintGo is sending a document to a printer."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "Print results", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when a print job finished or failed."
            }
        )
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_JOBS, true)
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun foreground(context: Context, active: List<PrintJobEntity>): Notification {
        val first = active.firstOrNull()
        val text = when {
            first == null -> "Finishing up…"
            active.size == 1 -> "${first.title} • ${first.statusMessage.ifBlank { first.status.lowercase() }}"
            else -> "${active.size} jobs in progress"
        }
        val b = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("PrintGo is printing")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (first != null && first.progress > 0f) b.setProgress(100, (first.progress * 100).toInt(), false)
        else b.setProgress(0, 0, true)
        return b.build()
    }

    @SuppressLint("MissingPermission")
    fun result(context: Context, job: PrintJobEntity) {
        if (!resultsEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val (title, icon) = when (job.status) {
            JobStatus.COMPLETED -> "Printed: ${job.title}" to android.R.drawable.stat_sys_upload_done
            JobStatus.CANCELLED -> "Cancelled: ${job.title}" to android.R.drawable.ic_menu_close_clear_cancel
            else -> "Print failed: ${job.title}" to android.R.drawable.stat_notify_error
        }
        val n = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(job.statusMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(job.statusMessage))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        NotificationManagerCompat.from(context).notify(job.id.hashCode(), n)
    }
}
