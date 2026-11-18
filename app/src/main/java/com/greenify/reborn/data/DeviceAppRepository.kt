package com.greenify.reborn.data

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.ApplicationExitInfo
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import com.greenify.reborn.model.AppEntry
import com.greenify.reborn.model.EnvironmentStatus
import com.greenify.reborn.model.ResolvedBackend
import com.greenify.reborn.model.StandbyBucket
import com.greenify.reborn.model.WorkingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File

class DeviceAppRepository(private val context: Context) {

    suspend fun inspectEnvironment(preferredMode: WorkingMode): EnvironmentStatus =
        withContext(Dispatchers.IO) {
            val shizukuInstalled = isPackageInstalled("moe.shizuku.privileged.api")
            val shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            val shizukuGranted = shizukuRunning && runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            val rootAvailable = hasSuBinary()
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
        greenifiedPackages: Set<String>,
        lastHibernatedTimestamps: Map<String, Long>,
        includeSystemApps: Boolean,
    ): List<AppEntry> = withContext(Dispatchers.IO) {
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

        installed.asSequence()
            .filter { it.packageName != ownPackage && it.packageName != "moe.shizuku.privileged.api" }
            .mapNotNull { info ->
                val pkg = info.packageName
                val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val isLaunchable = pm.getLaunchIntentForPackage(pkg) != null
                if (!includeSystemApps && isSystem && !isLaunchable) {
                    return@mapNotNull null
                }
                val isStopped = (info.flags and ApplicationInfo.FLAG_STOPPED) != 0
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
                    isGreenified = pkg in greenifiedPackages,
                    standbyBucket = bucket,
                    lastExitReason = exitInfo?.let(::formatExitReason),
                    lastExitTimestamp = exitInfo?.timestamp,
                    lastHibernatedAt = lastHibernatedTimestamps[pkg],
                )
            }
            .sortedWith(
                compareByDescending<AppEntry> { it.isGreenified }
                    .thenBy { it.isStopped }
                    .thenBy { it.label.lowercase() },
            )
            .toList()
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
        val candidates = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "/data/adb/magisk/su",
        )
        return candidates.any { File(it).exists() }
    }

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
