package com.greenify.reborn.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.greenify.reborn.MainActivity
import com.greenify.reborn.R
import com.greenify.reborn.data.AppLogStore
import com.greenify.reborn.data.AppPreferences
import com.greenify.reborn.data.DeviceAppRepository
import com.greenify.reborn.model.HibernationStrategy
import java.util.concurrent.TimeUnit

class HibernationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val preferences = AppPreferences(applicationContext)
        val settings = preferences.state.value
        val logStore = AppLogStore.getInstance(applicationContext)

        if (settings.autoHibernationScreenOffOnly) {
            val powerManager = applicationContext.getSystemService(PowerManager::class.java)
            if (powerManager?.isInteractive == true) {
                logStore.info("SCHEDULER", "Skipped scheduled nap: screen is currently interactive.")
                return Result.success()
            }
        }

        val repository = DeviceAppRepository(applicationContext)
        val engine = HibernationEngine(applicationContext, preferences, repository)
        val runtimeSignals = engine.queryLiveRuntimeSignals()
        val snapshot = repository.loadInstalledApps(
            perAppPolicies = settings.perAppPolicies,
            lastNappedTimestamps = settings.lastNappedTimestamps,
            wakeupRecords = settings.wakeupRecords,
            includeSystemApps = settings.includeSystemApps,
            wakeupTrackerEnabled = settings.wakeupTrackerEnabled,
            runtimeSignals = runtimeSignals,
        )
        preferences.updateWakeupRecords(snapshot.updatedWakeupRecords)

        val targetApps = snapshot.apps.filter { app ->
            if (app.policy.strategy == HibernationStrategy.EXEMPT) return@filter false
            if (settings.ignoreBackgroundFree && app.isStopped) return@filter false
            if (settings.autoHibernateAllUserApps) {
                !app.isSystemApp || app.policy.includeInScheduledNaps
            } else {
                app.policy.includeInScheduledNaps
            }
        }

        val packages = targetApps.map { it.packageName }
        if (packages.isEmpty()) {
            logStore.info("SCHEDULER", "Scheduled nap run had 0 eligible packages.")
            return Result.success()
        }

        val batchResult = engine.hibernate(packages)
        if (settings.quickActionNotificationEnabled && batchResult.succeeded.isNotEmpty()) {
            postSummaryNotification(applicationContext, batchResult.message)
        }
        return if (batchResult.failed.isEmpty() || batchResult.succeeded.isNotEmpty()) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    private fun postSummaryNotification(context: Context, summaryText: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channelId = context.getString(R.string.channel_hibernation_id)
        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.channel_hibernation_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_hibernation_desc)
        }
        manager.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val hibernateNowIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(AutomationReceiver.ACTION_HIBERNATE_NOW).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_tile_leaf)
            .setContentTitle("Greenify Scheduled Nap")
            .setContentText(summaryText)
            .setContentIntent(openIntent)
            .addAction(0, "Hibernate Now", hibernateNowIntent)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(SUMMARY_NOTIFICATION_ID, notif) }
    }

    companion object {
        private const val SUMMARY_NOTIFICATION_ID = 4102
    }
}

object AutoHibernationScheduler {

    private const val PERIODIC_WORK_NAME = "greenify_reborn_auto_hibernation"
    private const val ONE_TIME_WORK_NAME = "greenify_reborn_manual_hibernation"

    fun enqueueImmediate(context: Context) {
        val request = OneTimeWorkRequestBuilder<HibernationWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun syncPeriodicSchedule(context: Context, enabled: Boolean, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }
        val clampedMinutes = intervalMinutes.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<HibernationWorker>(
            clampedMinutes,
            TimeUnit.MINUTES,
        ).build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
