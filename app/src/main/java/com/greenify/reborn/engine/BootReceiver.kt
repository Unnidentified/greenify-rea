package com.greenify.reborn.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.greenify.reborn.data.AppPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = AppPreferences(context.applicationContext).state.value
            AutoHibernationScheduler.syncPeriodicSchedule(
                context = context.applicationContext,
                enabled = prefs.autoHibernationEnabled,
                intervalMinutes = prefs.autoHibernationIntervalMinutes,
            )
        }
    }
}
