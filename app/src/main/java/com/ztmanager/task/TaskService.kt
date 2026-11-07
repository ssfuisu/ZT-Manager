package com.ztmanager.task

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.ztmanager.MainActivity
import com.ztmanager.R
import com.ztmanager.ZtApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps the process alive while file operations are running and shows progress. */
class TaskService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            ServiceCompat.startForeground(this, NOTIF_ID, build(), type)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            scope.launch {
                delay(300)
                while (TaskManager.tasks.isNotEmpty()) {
                    try {
                        NotificationManagerCompat.from(this@TaskService).notify(NOTIF_ID, build())
                    } catch (_: SecurityException) {
                    }
                    delay(1000)
                }
                ServiceCompat.stopForeground(this@TaskService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun build(): Notification {
        val task = TaskManager.tasks.firstOrNull()
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(this, ZtApp.CHANNEL_TASKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task?.title ?: getString(R.string.app_name))
            .setContentText(task?.message ?: "")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (task != null) {
            if (task.progress < 0) b.setProgress(0, 0, true)
            else b.setProgress(1000, (task.progress * 1000).toInt(), false)
            if (TaskManager.tasks.size > 1) b.setSubText("+" + (TaskManager.tasks.size - 1))
        }
        return b.build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 1001
    }
}
