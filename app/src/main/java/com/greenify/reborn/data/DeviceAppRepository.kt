package com.greenify.reborn.data

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.ApplicationExitInfo
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import com.greenify.reborn.engine.WakeupReconcileInput
import com.greenify.reborn.engine.WakeupRecord
import com.greenify.reborn.engine.WakeupTracker
import com.greenify.reborn.model.AppEntry
import com.greenify.reborn.model.EnvironmentStatus
import com.greenify.reborn.model.ResolvedBackend
import com.greenify.reborn.model.StandbyBucket
import com.greenify.reborn.model.WorkingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.TimeUnit

data class LoadedAppsSnapshot(
    val apps: List<AppEntry>,
    val updatedWakeupRecords: Map<String, WakeupRecord>,
)

class DeviceAppRepository(private val context: Context) {

    @Volatile
    private var rootProbeGranted: Boolean = false

    suspend fun inspectEnvironment(
        preferredMode: WorkingMode,
        probeRoot: Boolean = false,
    ): EnvironmentStatus =
        withContext(Dispatchers.IO) {
            val shizukuInstalled = isPackageInstalled("moe.shizuku.privileged.api")
            val shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            val shizukuGranted = shizukuRunning && runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            if (probeRoot || preferredMode == WorkingMode.ROOT) {
                rootProbeGranted = verifySuExecution()
            }
            val rootAvailable = rootProbeGranted || hasSuBinary()
            val usageAccessGranted = hasUsageStatsPermission()
            val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val resolved = resolveBackend(
                preferredMode = preferredMode,
                shizukuReady = shizukuGranted,
                rootAvailable = rootAvailable,
            )

            EnvironmentStatus(
                shizukuInstalled = shizukuInstalled,
                shizukuRunning = shizukuRunning,
                shizukuPermissionGranted = shizukuGranted,
                rootAvailable = rootAvailable,
                usageAccessGranted = usageAccessGranted,
                notificationsGranted = notificationsGranted,
                resolvedBackend = resolved,
            )
        }

    suspend fun loadInstalledApps(
        managedPackages: Set<String>,
        lastNappedTimestamps: Map<String, Long>,
        wakeupRecords: Map<String, WakeupRecord>,
        includeSystemApps: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): LoadedAppsSnapshot = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val ownPackage = context.packageName

        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        val ownExitMap = readOwnOrPermittedExitReasons(activityManager)
        val earliestNap = lastNappedTimestamps.values.minOrNull()
        val usageWakeups = if (earliestNap != null && hasUsageStatsPermission()) {
            queryRecentUsageWakeups(usageStatsManager, earliestNap, nowMillis)
        } else {
            emptyMap()
        }

        val observations = mutableListOf<WakeupReconcileInput>()
        val rawRows = installed.asSequence()
            .filter { it.packageName != ownPackage && it.packageName != "moe.shizuku.privileged.api" }
            .mapNotNull { info ->
                val pkg = info.packageName
                val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val isLaunchable = pm.getLaunchIntentForPackage(pkg) != null
                if (!includeSystemApps && isSystem && !isLaunchable && pkg !in managedPackages) {
                    return@mapNotNull null
                }

                val isStopped = (info.flags and ApplicationInfo.FLAG_STOPPED) != 0
                val lastNap = lastNappedTimestamps[pkg]
                val usageWake = usageWakeups[pkg]
                if (lastNap != null) {
                    observations += WakeupReconcileInput(
                        packageName = pkg,
                        isStopped = isStopped,
                        lastNappedAt = lastNap,
                        usageWakeupTimestamp = usageWake?.first,
                        usageWakeupCause = usageWake?.second,
                    )
                }

                val versionName = runCatching {
                    val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkg, 0)
                    }
                    pkgInfo.versionName
                }.getOrNull().orEmpty().ifBlank { "—" }

                val bucket = resolveStandbyBucket(usageStatsManager, pkg)
                val exitInfo = ownExitMap[pkg]

                AppEntry(
                    packageName = pkg,
                    label = pm.getApplicationLabel(info).toString(),
                    versionName = versionName,
                    isSystemApp = isSystem,
                    isStopped = isStopped,
                    isManaged = pkg in managedPackages,
                    standbyBucket = bucket,
                    lastExitReason = exitInfo?.let(::formatExitReason),
                    lastExitTimestamp = exitInfo?.timestamp,
                    lastNappedAt = lastNap,
                )
            }
            .toList()

        val reconciledRecords = WakeupTracker.reconcile(
            existing = wakeupRecords,
            observations = observations,
            nowMillis = nowMillis,
        )

        val entries = rawRows.map { entry ->
            entry.copy(
                wakeupSummary = WakeupTracker.summarize(reconciledRecords[entry.packageName], nowMillis),
            )
        }.sortedWith(
            compareByDescending<AppEntry> { it.isManaged }
                .thenBy { it.isStopped }
                .thenBy { it.label.lowercase() },
        )

        LoadedAppsSnapshot(
            apps = entries,
            updatedWakeupRecords = reconciledRecords,
        )
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isPackageInstalled(packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    }.getOrDefault(false)

    private fun hasSuBinary(): Boolean {
        val pathDirs = System.getenv("PATH")
            ?.split(':')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val candidates = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/debug_ramdisk/su",
            "/vendor/bin/su",
            "/su/bin/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "/data/adb/magisk/su",
        ) + pathDirs.map { "$it/su" }
        if (candidates.any { File(it).exists() }) return true

        // Some root managers (KernelSU / SukiSU-Nova) expose su only via shell $PATH or kernel hook
        return runCatching {
            val process = ProcessBuilder("sh", "-c", "command -v su")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(1_500L, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        }.getOrDefault(false)
    }

    private fun verifySuExecution(): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", "id")
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(3_000L, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            false
        } else {
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.exitValue() == 0 && output.contains("uid=0")
        }
    }.getOrDefault(false)

    private fun resolveStandbyBucket(
        usageStatsManager: UsageStatsManager?,
        packageName: String,
    ): StandbyBucket {
        if (usageStatsManager == null) return StandbyBucket.UNKNOWN
        return runCatching {
            val method = UsageStatsManager::class.java.getMethod("getAppStandbyBucket", String::class.java)
            when (method.invoke(usageStatsManager, packageName) as? Int) {
                5 -> StandbyBucket.EXEMPTED
                UsageStatsManager.STANDBY_BUCKET_ACTIVE -> StandbyBucket.ACTIVE
                UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> StandbyBucket.WORKING_SET
                UsageStatsManager.STANDBY_BUCKET_FREQUENT -> StandbyBucket.FREQUENT
                UsageStatsManager.STANDBY_BUCKET_RARE -> StandbyBucket.RARE
                UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> StandbyBucket.RESTRICTED
                else -> StandbyBucket.UNKNOWN
            }
        }.getOrDefault(StandbyBucket.UNKNOWN)
    }

    private fun queryRecentUsageWakeups(
        usageStatsManager: UsageStatsManager?,
        sinceMillis: Long,
        nowMillis: Long,
    ): Map<String, Pair<Long, String>> {
        if (usageStatsManager == null || sinceMillis >= nowMillis) return emptyMap()
        return runCatching {
            val events = usageStatsManager.queryEvents(sinceMillis, nowMillis) ?: return emptyMap()
            val event = UsageEvents.Event()
            val latestByPackage = HashMap<String, Pair<Long, String>>()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val cause = when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> "Foreground launch"
                    UsageEvents.Event.FOREGROUND_SERVICE_START -> "Foreground service started"
                    UsageEvents.Event.USER_INTERACTION -> "User interaction"
                    else -> null
                } ?: continue
                val pkg = event.packageName ?: continue
                val prev = latestByPackage[pkg]
                if (prev == null || event.timeStamp > prev.first) {
                    latestByPackage[pkg] = event.timeStamp to cause
                }
            }
            latestByPackage
        }.getOrDefault(emptyMap())
    }

    private fun readOwnOrPermittedExitReasons(
        activityManager: ActivityManager?,
    ): Map<String, ApplicationExitInfo> {
        if (activityManager == null) return emptyMap()
        return runCatching<Map<String, ApplicationExitInfo>> {
            val reasons: List<ApplicationExitInfo> =
                activityManager.getHistoricalProcessExitReasons(null, 0, 50)
            reasons
                .groupBy { info -> info.processName.substringBefore(':') }
                .mapValues { (_, list) -> list.maxBy { info -> info.timestamp } }
        }.getOrDefault(emptyMap())
    }

    private fun formatExitReason(info: ApplicationExitInfo): String = when (info.reason) {
        ApplicationExitInfo.REASON_USER_REQUESTED -> "User stopped"
        ApplicationExitInfo.REASON_USER_STOPPED -> "Force-stopped"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "Low memory (LMK)"
        ApplicationExitInfo.REASON_CRASH -> "App crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Excessive CPU/resource"
        ApplicationExitInfo.REASON_EXIT_SELF -> "Exited self"
        else -> "Stopped (${info.reason})"
    }

    companion object {
        fun resolveBackend(
            preferredMode: WorkingMode,
            shizukuReady: Boolean,
            rootAvailable: Boolean,
        ): ResolvedBackend = when (preferredMode) {
            WorkingMode.SHIZUKU -> if (shizukuReady) ResolvedBackend.SHIZUKU else ResolvedBackend.NON_ROOT
            WorkingMode.ROOT -> if (rootAvailable) ResolvedBackend.ROOT else ResolvedBackend.NON_ROOT
            WorkingMode.NON_ROOT -> ResolvedBackend.NON_ROOT
            WorkingMode.AUTO -> when {
                shizukuReady -> ResolvedBackend.SHIZUKU
                rootAvailable -> ResolvedBackend.ROOT
                else -> ResolvedBackend.NON_ROOT
            }
        }
    }
}
