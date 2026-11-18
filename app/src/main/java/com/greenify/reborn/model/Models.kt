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

data class AppEntry(
    val packageName: String,
    val label: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val isStopped: Boolean,
    val isGreenified: Boolean,
    val standbyBucket: StandbyBucket,
    val lastExitReason: String? = null,
    val lastExitTimestamp: Long? = null,
    val lastHibernatedAt: Long? = null,
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
)

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
    val greenifiedPackages: List<String>,
)
