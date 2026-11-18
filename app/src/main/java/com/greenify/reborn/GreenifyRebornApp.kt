package com.greenify.reborn

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.greenify.reborn.data.AppPreferences
import com.greenify.reborn.engine.AutoHibernationScheduler

class GreenifyRebornApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        val settings = AppPreferences(this).state.value
        AutoHibernationScheduler.syncPeriodicSchedule(
            context = this,
            enabled = settings.autoHibernationEnabled,
            intervalMinutes = settings.autoHibernationIntervalMinutes,
        )
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            getString(R.string.channel_hibernation_id),
            getString(R.string.channel_hibernation_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_hibernation_desc)
        }
        manager.createNotificationChannel(channel)
    }
}
