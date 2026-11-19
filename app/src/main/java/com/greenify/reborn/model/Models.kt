package com.greenify.reborn.model

import kotlinx.serialization.Serializable

enum class WorkingMode(val displayName: String, val description: String) {
    AUTO("Auto", "Prefer Shizuku, then Root, otherwise Non-Root"),
    SHIZUKU("Shizuku", "Use Shizuku ADB/system permissions without root"),
    ROOT("Root (su)", "Execute batched force-stop & standby commands via su"),
    NON_ROOT("Non-Root", "Open App Info or use standby hints without elevated access"),
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

enum class StandbyBucket(val label: String) {
    EXEMPTED("Exempted"),
    ACTIVE("Active"),
    WORKING_SET("Working Set"),
    FREQUENT("Frequent"),
    RARE("Rare"),
    RESTRICTED("Restricted"),
    UNKNOWN("Unknown"),
}

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
    val isSystemApp: Boolean,
    val isStopped: Boolean,
    val isManaged: Boolean,
    val standbyBucket: StandbyBucket,
    val lastExitReason: String? = null,
    val lastExitTimestamp: Long? = null,
    val lastNappedAt: Long? = null,
    val wakeupSummary: AppWakeupSummary = AppWakeupSummary(),
)

@Serializable
data class HibernationEvent(
    val timestampMillis: Long,
    val packageCount: Int,
    val succeededCount: Int,
    val backendUsed: String,
    val shallowMode: Boolean,
)

data class EnvironmentStatus(
    val shizukuInstalled: Boolean,
    val shizukuRunning: Boolean,
    val shizukuPermissionGranted: Boolean,
    val rootAvailable: Boolean,
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
    val failed: List<String>,
    val backend: ResolvedBackend,
    val message: String,
)

@Serializable
data class BackupSnapshot(
    val schemaVersion: Int = 1,
    val exportedAtMillis: Long,
    val workingMode: String,
    val shallowHibernation: Boolean,
    val autoHibernationEnabled: Boolean,
    val autoHibernationIntervalMinutes: Int,
    val managedPackages: List<String>,
)
