package com.greenify.reborn.data

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.ApplicationExitInfo
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import com.greenify.reborn.engine.WakeupReconcileInput
import com.greenify.reborn.engine.WakeupRecord
import com.greenify.reborn.engine.WakeupTracker
import com.greenify.reborn.model.AppComponentInfo
import com.greenify.reborn.model.AppEntry
import com.greenify.reborn.model.ComponentType
import com.greenify.reborn.model.EnvironmentStatus
import com.greenify.reborn.model.LiveRuntimeSignals
import com.greenify.reborn.model.PerAppPolicy
import com.greenify.reborn.model.ResolvedBackend
import com.greenify.reborn.model.StandbyBucket
import com.greenify.reborn.model.WakeupCategory
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

    private val logStore = AppLogStore.getInstance(context)

    @Volatile
    private var rootProbeGranted: Boolean = false

    suspend fun inspectEnvironment(
        preferredMode: WorkingMode,
        probeRoot: Boolean = false,
        previousRootVerified: Boolean = false,
    ): EnvironmentStatus =
        withContext(Dispatchers.IO) {
            val shizukuInstalled = isPackageInstalled("moe.shizuku.privileged.api")
            val shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            val shizukuGranted = shizukuRunning && runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)

            val suBinary = hasSuBinary()
            if (probeRoot || preferredMode == WorkingMode.ROOT || (previousRootVerified && !rootProbeGranted)) {
                val (verified, detail) = verifySuExecution()
                rootProbeGranted = verified
                if (probeRoot) {
                    if (verified) {
                        logStore.info("ROOT", "Root access verified via su.", detail)
                    } else {
                        logStore.warn("ROOT", "Root verification did not obtain uid=0.", detail)
                    }
                }
            }

            val rootAvailable = rootProbeGranted
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
                rootBinaryDetected = suBinary || rootAvailable,
                usageAccessGranted = usageAccessGranted,
                notificationsGranted = notificationsGranted,
                resolvedBackend = resolved,
            )
        }

    suspend fun loadInstalledApps(
        perAppPolicies: Map<String, PerAppPolicy>,
        lastNappedTimestamps: Map<String, Long>,
        wakeupRecords: Map<String, WakeupRecord>,
        includeSystemApps: Boolean,
        wakeupTrackerEnabled: Boolean = true,
        runtimeSignals: LiveRuntimeSignals = LiveRuntimeSignals(),
        nowMillis: Long = System.currentTimeMillis(),
    ): LoadedAppsSnapshot = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val audioManager = context.getSystemService(AudioManager::class.java)
        val ownPackage = context.packageName

        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }

        val ownExitMap = readOwnOrPermittedExitReasons(activityManager)
        val earliestNap = lastNappedTimestamps.values.minOrNull()
        val usageSignals = if (hasUsageStatsPermission()) {
            queryRecentUsageSignals(
                usageStatsManager = usageStatsManager,
                sinceMillis = earliestNap ?: (nowMillis - 30L * 60L * 1000L),
                nowMillis = nowMillis,
            )
        } else {
            UsageSignalsSnapshot(emptyMap(), null)
        }

        // Batch-query all launchable packages in at most 2 IPC calls instead of 1 IPC per installed package
        val launchablePackages = queryLaunchablePackages(pm)

        val effectiveForeground = runtimeSignals.foregroundPackage ?: usageSignals.foregroundPackage
        val anyAudioPlayingSystemWide = runCatching { audioManager?.isMusicActive == true }.getOrDefault(false)

        val observations = mutableListOf<WakeupReconcileInput>()
        val rawRows = ArrayList<AppEntry>(installed.size)
        for (pkgInfo in installed) {
            val pkg = pkgInfo.packageName
            if (pkg == ownPackage || pkg == "moe.shizuku.privileged.api") continue
            val info = pkgInfo.applicationInfo ?: continue
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val policy = perAppPolicies[pkg] ?: PerAppPolicy()
            val isLaunchable = pkg in launchablePackages
            if (!includeSystemApps && isSystem && !isLaunchable && policy.isDefault) {
                continue
            }

            val isStopped = (info.flags and ApplicationInfo.FLAG_STOPPED) != 0
            val isAudioApp = info.category == ApplicationInfo.CATEGORY_AUDIO
            val inRecents = pkg in runtimeSignals.recentsPackages
            val isPlayingAudio = pkg in runtimeSignals.activeAudioPackages ||
                (anyAudioPlayingSystemWide && isAudioApp && inRecents && !isStopped)
            val lastNap = lastNappedTimestamps[pkg]
            val usageWake = usageSignals.latestWakeups[pkg]

            if (wakeupTrackerEnabled && lastNap != null) {
                observations += WakeupReconcileInput(
                    packageName = pkg,
                    isStopped = isStopped,
                    lastNappedAt = lastNap,
                    usageWakeupTimestamp = usageWake?.first,
                    usageWakeupCause = usageWake?.second,
                )
            }

            val versionName = pkgInfo.versionName.orEmpty().ifBlank { "—" }

            val bucket = resolveStandbyBucket(usageStatsManager, pkg)
            val exitInfo = ownExitMap[pkg]

            rawRows += AppEntry(
                packageName = pkg,
                label = pm.getApplicationLabel(info).toString(),
                versionName = versionName,
                uid = info.uid,
                targetSdk = info.targetSdkVersion,
                isSystemApp = isSystem,
                isStopped = isStopped,
                isAudioApp = isAudioApp,
                inRecents = inRecents,
                isPlayingAudio = isPlayingAudio,
                isForeground = pkg == effectiveForeground,
                standbyBucket = bucket,
                lastExitReason = exitInfo?.let(::formatExitReason),
                lastExitTimestamp = exitInfo?.timestamp,
                lastNappedAt = lastNap,
                policy = policy,
            )
        }

        val reconciledRecords = if (wakeupTrackerEnabled) {
            WakeupTracker.reconcile(
                existing = wakeupRecords,
                observations = observations,
                nowMillis = nowMillis,
            )
        } else {
            wakeupRecords
        }

        val entries = rawRows.map { entry ->
            entry.copy(
                wakeupSummary = WakeupTracker.summarize(reconciledRecords[entry.packageName], nowMillis),
            )
        }.sortedWith(
            compareByDescending<AppEntry> { it.inRecents }
                .thenBy { it.isStopped }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
        )

        LoadedAppsSnapshot(
            apps = entries,
            updatedWakeupRecords = reconciledRecords,
        )
    }

    suspend fun loadAppComponents(packageName: String): List<AppComponentInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val flags = (
                PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.MATCH_DISABLED_COMPONENTS
                ).toLong()

            val pkgInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, flags.toInt())
                }
            }.getOrNull() ?: return@withContext emptyList()

            val out = mutableListOf<AppComponentInfo>()
            pkgInfo.receivers?.forEach { out += it.toAppComponentInfo(pm, packageName, ComponentType.RECEIVER) }
            pkgInfo.services?.forEach { out += it.toAppComponentInfo(pm, packageName, ComponentType.SERVICE) }
            pkgInfo.providers?.forEach { out += it.toAppComponentInfo(pm, packageName, ComponentType.PROVIDER) }

        out.sortedWith(
            compareBy<AppComponentInfo> { it.category.ordinal }
                .thenBy { it.type.ordinal }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.shortName },
        )
    }

    private fun queryLaunchablePackages(pm: PackageManager): Set<String> {
        val result = HashSet<String>(128)
        for (category in listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_INFO)) {
            runCatching {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
                val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(intent, 0)
                }
                for (info in resolved) {
                    info.activityInfo?.packageName?.let { result.add(it) }
                }
            }
        }
        return result
    }

    private fun ComponentInfo.toAppComponentInfo(
        pm: PackageManager,
        pkg: String,
        type: ComponentType,
    ): AppComponentInfo {
        val state = runCatching {
            pm.getComponentEnabledSetting(ComponentName(pkg, name))
        }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        val currentlyEnabled = when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> enabled
        }
        val short = name.removePrefix(pkg).ifBlank { name }
        return AppComponentInfo(
            packageName = pkg,
            className = name,
            shortName = short,
            type = type,
            category = classifyComponent(name),
            isEnabled = currentlyEnabled,
            exported = exported,
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

    private fun verifySuExecution(): Pair<Boolean, String> = runCatching {
        val process = ProcessBuilder("su", "-c", "id")
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(4_000L, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            false to "su -c id timed out"
        } else {
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val ok = process.exitValue() == 0 && output.contains("uid=0")
            ok to "exit=${process.exitValue()} output=$output"
        }
    }.getOrElse { err ->
        false to "Exception: ${err.message ?: err::class.simpleName}"
    }

    private fun resolveStandbyBucket(
        usageStatsManager: UsageStatsManager?,
        packageName: String,
    ): StandbyBucket {
        if (usageStatsManager == null) return StandbyBucket.UNKNOWN
        val method = STANDBY_BUCKET_METHOD ?: return StandbyBucket.UNKNOWN
        return runCatching {
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

    private data class UsageSignalsSnapshot(
        val latestWakeups: Map<String, Pair<Long, String>>,
        val foregroundPackage: String?,
    )

    private fun queryRecentUsageSignals(
        usageStatsManager: UsageStatsManager?,
        sinceMillis: Long,
        nowMillis: Long,
    ): UsageSignalsSnapshot {
        if (usageStatsManager == null || sinceMillis >= nowMillis) {
            return UsageSignalsSnapshot(emptyMap(), null)
        }
        return runCatching {
            val events = usageStatsManager.queryEvents(sinceMillis, nowMillis)
                ?: return UsageSignalsSnapshot(emptyMap(), null)
            val event = UsageEvents.Event()
            val latestByPackage = HashMap<String, Pair<Long, String>>()
            var latestForegroundPkg: String? = null
            var latestForegroundTs = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp >= latestForegroundTs) {
                    latestForegroundTs = event.timeStamp
                    latestForegroundPkg = pkg
                }
                val cause = when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> "Foreground launch"
                    UsageEvents.Event.FOREGROUND_SERVICE_START -> "Foreground service started"
                    UsageEvents.Event.USER_INTERACTION -> "User interaction"
                    UsageEvents.Event.STANDBY_BUCKET_CHANGED -> "Standby bucket changed"
                    else -> null
                } ?: continue
                val prev = latestByPackage[pkg]
                if (prev == null || event.timeStamp > prev.first) {
                    latestByPackage[pkg] = event.timeStamp to cause
                }
            }
            UsageSignalsSnapshot(latestByPackage, latestForegroundPkg)
        }.getOrDefault(UsageSignalsSnapshot(emptyMap(), null))
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
        private val STANDBY_BUCKET_METHOD by lazy {
            runCatching {
                UsageStatsManager::class.java.getMethod("getAppStandbyBucket", String::class.java)
            }.getOrNull()
        }

        fun classifyComponent(className: String): WakeupCategory {
            return when {
                className.contains("boot", ignoreCase = true) ||
                    className.contains("package", ignoreCase = true) ||
                    className.contains("startup", ignoreCase = true) ||
                    className.contains("update", ignoreCase = true) ||
                    className.contains("install", ignoreCase = true) -> WakeupCategory.BOOT
                className.contains("fcm", ignoreCase = true) ||
                    className.contains("gcm", ignoreCase = true) ||
                    className.contains("firebase", ignoreCase = true) ||
                    className.contains("push", ignoreCase = true) ||
                    className.contains("messaging", ignoreCase = true) ||
                    className.contains("sync", ignoreCase = true) -> WakeupCategory.PUSH_SYNC
                className.contains("alarm", ignoreCase = true) ||
                    className.contains("job", ignoreCase = true) ||
                    className.contains("work", ignoreCase = true) ||
                    className.contains("scheduler", ignoreCase = true) ||
                    className.contains("timer", ignoreCase = true) -> WakeupCategory.ALARM_JOB
                className.contains("network", ignoreCase = true) ||
                    className.contains("connectivity", ignoreCase = true) ||
                    className.contains("wifi", ignoreCase = true) ||
                    className.contains("power", ignoreCase = true) ||
                    className.contains("battery", ignoreCase = true) ||
                    className.contains("screen", ignoreCase = true) -> WakeupCategory.CONNECTIVITY
                else -> WakeupCategory.OTHER
            }
        }

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
