package com.ztmanager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.ztmanager.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ZtApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_TASKS,
            getString(R.string.notif_channel_tasks),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_TASKS = "tasks"
        lateinit var instance: ZtApp
            private set
    }
}
