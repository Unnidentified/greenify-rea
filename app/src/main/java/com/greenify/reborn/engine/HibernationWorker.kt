package com.greenify.reborn.engine

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.greenify.reborn.data.AppPreferences
import com.greenify.reborn.data.DeviceAppRepository
import java.util.concurrent.TimeUnit

class HibernationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val preferences = AppPreferences(applicationContext)
        val settings = preferences.state.value
        val packages = settings.managedPackages.toList()
        if (packages.isEmpty()) return Result.success()

        val repository = DeviceAppRepository(applicationContext)
        val snapshot = repository.loadInstalledApps(
            managedPackages = settings.managedPackages,
            lastNappedTimestamps = settings.lastNappedTimestamps,
            wakeupRecords = settings.wakeupRecords,
            includeSystemApps = settings.includeSystemApps,
        )
        preferences.updateWakeupRecords(snapshot.updatedWakeupRecords)

        val engine = HibernationEngine(applicationContext, preferences, repository)
        val batchResult = engine.hibernate(packages)
        return if (batchResult.failed.isEmpty() || batchResult.succeeded.isNotEmpty()) {
            Result.success()
        } else {
            Result.retry()
        }
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
