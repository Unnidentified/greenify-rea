package com.greenify.reborn.model

import kotlinx.serialization.Serializable

enum class WorkingMode(val displayName: String, val description: String) {
    AUTO("Auto", "Prefer Shizuku if granted, then Root (su), otherwise Non-Root"),
    SHIZUKU("Shizuku", "Strictly use Shizuku ADB/system permissions"),
    ROOT("Root (su)", "Strictly execute privileged commands via su"),
    NON_ROOT("Non-Root", "Inspect apps & open App Info without elevated access"),
}

enum class ResolvedBackend(val displayName: String) {
    SHIZUKU("Shizuku"),
    ROOT("Root"),
    NON_ROOT("Non-Root"),
}

enum class ThemeMode(val displayName: String) {
    SYSTEM("Follow System"),
    LIGHT("Light"),
    DARK("Dark"),
    SAGE_LIGHT("Sage Light"),
    FOREST_DARK("Forest Dark"),
    MIST_LIGHT("Mist Light"),
    MIDNIGHT_DARK("Midnight Dark");

    fun resolveDarkTheme(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT, SAGE_LIGHT, MIST_LIGHT -> false
        DARK, FOREST_DARK, MIDNIGHT_DARK -> true
    }
}

enum class StandbyBucket(val label: String, val shellArg: String?) {
    EXEMPTED("Exempted", null),
    ACTIVE("Active", "active"),
    WORKING_SET("Working Set", "working_set"),
    FREQUENT("Frequent", "frequent"),
    RARE("Rare", "rare"),
    RESTRICTED("Restricted", "restricted"),
    UNKNOWN("Unknown", null);

    companion object {
        val assignableBuckets: List<StandbyBucket> = listOf(
            RESTRICTED,
            RARE,
            FREQUENT,
            WORKING_SET,
            ACTIVE,
        )
    }
}

enum class AppFilterChip(val label: String) {
    USER("User"),
    SYSTEM("System"),
    AWAKE("Awake"),
    NAPPING("Napping"),
    IN_RECENTS("In Recents"),
    WAKEUPS("Wake-ups"),
    CONFIGURED("Configured"),
}

@Serializable
enum class HibernationStrategy(val displayName: String, val description: String) {
    GLOBAL_DEFAULT("Global Default", "Follow global Shallow / Force-stop engine setting"),
    DEEP_FORCE_STOP("Deep Force-Stop", "Always force-stop and move to target standby bucket"),
    SHALLOW_STANDBY("Shallow Standby", "Restrict standby bucket without force-stopping"),
    EXEMPT("Exempt (Never Nap)", "Always exclude from batch & scheduled hibernation"),
}

@Serializable
enum class RecentsProtection(val displayName: String) {
    INHERIT("Follow Global"),
    ALWAYS_PROTECT("Protect in Recents"),
    ALLOW_HIBERNATION("Allow Even in Recents"),
}

@Serializable
enum class SwipeCloseMode(val displayName: String) {
    INHERIT("Follow Global"),
    ALWAYS_CLOSE("Auto-Close on Swipe"),
    NEVER_CLOSE("Never Auto-Close"),
}

@Serializable
data class PerAppPolicy(
    val includeInScheduledNaps: Boolean = false,
    val strategy: HibernationStrategy = HibernationStrategy.GLOBAL_DEFAULT,
    val recentsProtection: RecentsProtection = RecentsProtection.INHERIT,
    val autoCloseOnSwipe: SwipeCloseMode = SwipeCloseMode.INHERIT,
) {
    val isDefault: Boolean
        get() = !includeInScheduledNaps &&
            strategy == HibernationStrategy.GLOBAL_DEFAULT &&
            recentsProtection == RecentsProtection.INHERIT &&
            autoCloseOnSwipe == SwipeCloseMode.INHERIT
}

enum class AppOpRestriction(
    val opName: String,
    val label: String,
    val description: String,
) {
    WAKE_LOCK(
        opName = "WAKE_LOCK",
        label = "Block Wake Locks",
        description = "Prevent partial wake-locks from keeping the CPU awake in background",
    ),
    RUN_ANY_IN_BACKGROUND(
        opName = "RUN_ANY_IN_BACKGROUND",
        label = "Restrict Background",
        description = "Deny background execution and jobs while not in the foreground",
    ),
    START_FOREGROUND(
        opName = "START_FOREGROUND",
        label = "Block Background FGS",
        description = "Stop background triggers from starting foreground services",
    ),
    SCHEDULE_EXACT_ALARM(
        opName = "SCHEDULE_EXACT_ALARM",
        label = "Block Exact Alarms",
        description = "Prevent exact alarm wake-ups when the app is idle",
    ),
}

enum class ComponentType(val label: String) {
    SERVICE("Service"),
    RECEIVER("Receiver"),
    PROVIDER("Provider"),
}

enum class WakeupCategory(val label: String) {
    BOOT("Boot / Update"),
    PUSH_SYNC("Push / Sync"),
    ALARM_JOB("Alarm / Job"),
    CONNECTIVITY("Network / Power"),
    OTHER("Service / Receiver"),
}

data class AppComponentInfo(
    val packageName: String,
    val className: String,
    val shortName: String,
    val type: ComponentType,
    val category: WakeupCategory,
    val isEnabled: Boolean,
    val exported: Boolean,
)

@Serializable
data class WakeupEvent(
    val timestampMillis: Long,
    val cause: String = "Woke from nap",
)

data class AppWakeupSummary(
    val totalCount: Int = 0,
    val last24hCount: Int = 0,
    val lastWakeupAt: Long? = null,
    val recentEvents: List<WakeupEvent> = emptyList(),
)

data class AppEntry(
    val packageName: String,
    val label: String,
    val versionName: String,
    val uid: Int = 0,
    val targetSdk: Int = 0,
    val isSystemApp: Boolean,
    val isStopped: Boolean,
    val isAudioApp: Boolean = false,
    val inRecents: Boolean = false,
    val isPlayingAudio: Boolean = false,
    val isForeground: Boolean = false,
    val standbyBucket: StandbyBucket,
    val lastExitReason: String? = null,
    val lastExitTimestamp: Long? = null,
    val lastNappedAt: Long? = null,
    val wakeupSummary: AppWakeupSummary = AppWakeupSummary(),
    val policy: PerAppPolicy = PerAppPolicy(),
) {
    val isProtectedNow: Boolean
        get() = inRecents || isPlayingAudio || isForeground
}

@Serializable
data class HibernationEvent(
    val timestampMillis: Long,
    val packageCount: Int,
    val succeededCount: Int,
    val backendUsed: String,
    val shallowMode: Boolean,
)

@Serializable
enum class LogLevel {
    INFO,
    EXEC,
    WARN,
    ERROR,
}

@Serializable
data class EngineLogEntry(
    val id: Long,
    val timestampMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val detail: String? = null,
)

data class EnvironmentStatus(
    val shizukuInstalled: Boolean,
    val shizukuRunning: Boolean,
    val shizukuPermissionGranted: Boolean,
    val rootAvailable: Boolean,
    val rootBinaryDetected: Boolean = rootAvailable,
    val usageAccessGranted: Boolean,
    val notificationsGranted: Boolean,
    val resolvedBackend: ResolvedBackend,
) {
    fun isBackendDegraded(preferredMode: WorkingMode): Boolean = when (preferredMode) {
        WorkingMode.AUTO -> resolvedBackend == ResolvedBackend.NON_ROOT
        WorkingMode.SHIZUKU -> !shizukuPermissionGranted
        WorkingMode.ROOT -> !rootAvailable
        WorkingMode.NON_ROOT -> false
    }
}

data class HibernationBatchResult(
    val requested: Int,
    val succeeded: List<String>,
    val skippedProtected: List<String> = emptyList(),
    val failed: List<String>,
    val backend: ResolvedBackend,
    val message: String,
)

data class LiveRuntimeSignals(
    val recentsPackages: Set<String> = emptySet(),
    val activeAudioPackages: Set<String> = emptySet(),
    val foregroundPackage: String? = null,
)

@Serializable
data class BackupSnapshot(
    val schemaVersion: Int = 2,
    val exportedAtMillis: Long,
    val workingMode: String,
    val shallowHibernation: Boolean,
    val autoHibernationEnabled: Boolean,
    val autoHibernationIntervalMinutes: Int,
    val managedPackages: List<String> = emptyList(),
    val perAppPolicies: Map<String, PerAppPolicy> = emptyMap(),
    val excludeRecentsApps: Boolean = true,
    val protectActiveAudioApps: Boolean = true,
    val finerMediaPlaybackDetection: Boolean = true,
    val excludeForegroundApp: Boolean = true,
    val ignoreBackgroundFree: Boolean = false,
    val wakeupTrackerEnabled: Boolean = true,
    val enhancedWakeupSources: Boolean = true,
    val autoHibernationScreenOffOnly: Boolean = false,
    val autoHibernateAllUserApps: Boolean = false,
    val quickActionNotificationEnabled: Boolean = false,
    val includeSystemApps: Boolean = false,
    val geekExtrasEnabled: Boolean = false,
    val forceMaxRefreshRate: Boolean = true,
    val menuBarTint: Boolean = false,
    val autoCloseSwipedRecents: Boolean = false,
    val autoCloseSwipedOnlyMarked: Boolean = false,
    val defaultTargetBucket: String = StandbyBucket.RESTRICTED.name,
)
