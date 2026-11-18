package com.greenify.reborn.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Headless automation receiver replacing legacy BroadcastApi.startActivity() calls
 * that are blocked by Android 12+ Background Activity Launch restrictions.
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_HIBERNATE_NOW) {
            AutoHibernationScheduler.enqueueImmediate(context.applicationContext)
        }
    }

    companion object {
        const val ACTION_HIBERNATE_NOW = "com.greenify.reborn.action.HIBERNATE_NOW"
    }
}
