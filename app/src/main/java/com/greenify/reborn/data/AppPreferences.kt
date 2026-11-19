package com.greenify.reborn.data

import android.content.Context
import android.content.SharedPreferences
import com.greenify.reborn.engine.WakeupRecord
import com.greenify.reborn.model.BackupSnapshot
import com.greenify.reborn.model.HibernationEvent
import com.greenify.reborn.model.HibernationStrategy
import com.greenify.reborn.model.PerAppPolicy
import com.greenify.reborn.model.RecentsProtection
import com.greenify.reborn.model.StandbyBucket
import com.greenify.reborn.model.ThemeMode
import com.greenify.reborn.model.WorkingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class UserSettingsState(
    val workingMode: WorkingMode = WorkingMode.AUTO,
    val rootVerifiedPreviously: Boolean = false,
    val shallowHibernation: Boolean = false,
    val defaultTargetBucket: StandbyBucket = StandbyBucket.RESTRICTED,
    val excludeRecentsApps: Boolean = true,
    val protectActiveAudioApps: Boolean = true,
    val finerMediaPlaybackDetection: Boolean = true,
    val excludeForegroundApp: Boolean = true,
    val ignoreBackgroundFree: Boolean = false,
    val wakeupTrackerEnabled: Boolean = true,
    val enhancedWakeupSources: Boolean = true,
    val autoHibernationEnabled: Boolean = false,
    val autoHibernationIntervalMinutes: Int = 60,
    val autoHibernationScreenOffOnly: Boolean = false,
    val autoHibernateAllUserApps: Boolean = false,
    val quickActionNotificationEnabled: Boolean = false,
    val geekExtrasEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val forceMaxRefreshRate: Boolean = true,
    val liquidGlassBottomBar: Boolean = true,
    val includeSystemApps: Boolean = false,
    val perAppPolicies: Map<String, PerAppPolicy> = emptyMap(),
    val managedPackages: Set<String> = emptySet(),
    val lastNappedTimestamps: Map<String, Long> = emptyMap(),
    val wakeupRecords: Map<String, WakeupRecord> = emptyMap(),
    val history: List<HibernationEvent> = emptyList(),
)

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<UserSettingsState> = _state.asStateFlow()

    private fun loadInitialState(): UserSettingsState {
        val workingMode = prefs.getString(KEY_WORKING_MODE, null)
            ?.let { runCatching { WorkingMode.valueOf(it) }.getOrNull() }
            ?: WorkingMode.AUTO
        val themeMode = prefs.getString(KEY_THEME_MODE, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        val defaultBucket = prefs.getString(KEY_DEFAULT_TARGET_BUCKET, null)
            ?.let { runCatching { StandbyBucket.valueOf(it) }.getOrNull() }
            ?.takeIf { it.shellArg != null }
            ?: StandbyBucket.RESTRICTED

        val legacyManaged = prefs.getStringSet(KEY_MANAGED_PACKAGES, null)?.toSet()
            ?: prefs.getStringSet(KEY_LEGACY_GREENIFIED_PACKAGES, emptySet())?.toSet()
            ?: emptySet()

        val policiesRaw = prefs.getString(KEY_PER_APP_POLICIES, null)
        val parsedPolicies = policiesRaw?.let {
            runCatching { json.decodeFromString<Map<String, PerAppPolicy>>(it) }.getOrNull()
        }
        val policies = if (parsedPolicies != null) {
            parsedPolicies
        } else {
            legacyManaged.associateWith { PerAppPolicy(includeInScheduledNaps = true) }
        }
        val scheduledPackages = policies.filterValues { it.includeInScheduledNaps }.keys

        val timestampsRaw = prefs.getString(KEY_LAST_TIMESTAMPS, null)
        val timestamps = timestampsRaw?.let {
            runCatching { json.decodeFromString<Map<String, Long>>(it) }.getOrNull()
        } ?: emptyMap()
        val wakeupsRaw = prefs.getString(KEY_WAKEUP_RECORDS, null)
        val wakeups = wakeupsRaw?.let {
            runCatching { json.decodeFromString<Map<String, WakeupRecord>>(it) }.getOrNull()
        } ?: emptyMap()
        val historyRaw = prefs.getString(KEY_HISTORY, null)
        val history = historyRaw?.let {
            runCatching { json.decodeFromString<List<HibernationEvent>>(it) }.getOrNull()
        } ?: emptyList()

        return UserSettingsState(
            workingMode = workingMode,
            rootVerifiedPreviously = prefs.getBoolean(KEY_ROOT_VERIFIED, false),
            shallowHibernation = prefs.getBoolean(KEY_SHALLOW_HIBERNATION, false),
            defaultTargetBucket = defaultBucket,
            excludeRecentsApps = prefs.getBoolean(KEY_EXCLUDE_RECENTS, true),
            protectActiveAudioApps = prefs.getBoolean(KEY_PROTECT_AUDIO, true),
            finerMediaPlaybackDetection = prefs.getBoolean(KEY_FINER_MEDIA_DETECTION, true),
            excludeForegroundApp = prefs.getBoolean(KEY_EXCLUDE_FOREGROUND, true),
            ignoreBackgroundFree = prefs.getBoolean(KEY_IGNORE_BACKGROUND_FREE, false),
            wakeupTrackerEnabled = prefs.getBoolean(KEY_WAKEUP_TRACKER_ENABLED, true),
            enhancedWakeupSources = prefs.getBoolean(KEY_ENHANCED_WAKEUP_SOURCES, true),
            autoHibernationEnabled = prefs.getBoolean(KEY_AUTO_HIBERNATION, false),
            autoHibernationIntervalMinutes = prefs.getInt(KEY_AUTO_INTERVAL_MINUTES, 60)
                .coerceAtLeast(15),
            autoHibernationScreenOffOnly = prefs.getBoolean(KEY_AUTO_SCREEN_OFF_ONLY, false),
            autoHibernateAllUserApps = prefs.getBoolean(KEY_AUTO_HIBERNATE_ALL_USER, false),
            quickActionNotificationEnabled = prefs.getBoolean(KEY_QUICK_ACTION_NOTIF, false),
            geekExtrasEnabled = prefs.getBoolean(KEY_GEEK_EXTRAS, false),
            themeMode = themeMode,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
            forceMaxRefreshRate = prefs.getBoolean(KEY_FORCE_MAX_REFRESH_RATE, true),
            liquidGlassBottomBar = prefs.getBoolean(KEY_LIQUID_GLASS_BOTTOM_BAR, true),
            includeSystemApps = prefs.getBoolean(KEY_INCLUDE_SYSTEM, false),
            perAppPolicies = policies,
            managedPackages = scheduledPackages,
            lastNappedTimestamps = timestamps,
            wakeupRecords = wakeups,
            history = history,
        )
    }

    fun setWorkingMode(mode: WorkingMode) {
        prefs.edit().putString(KEY_WORKING_MODE, mode.name).apply()
        _state.update { it.copy(workingMode = mode) }
    }

    fun setRootVerifiedPreviously(verified: Boolean) {
        prefs.edit().putBoolean(KEY_ROOT_VERIFIED, verified).apply()
        _state.update { it.copy(rootVerifiedPreviously = verified) }
    }

    fun setShallowHibernation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHALLOW_HIBERNATION, enabled).apply()
        _state.update { it.copy(shallowHibernation = enabled) }
    }

    fun setDefaultTargetBucket(bucket: StandbyBucket) {
        if (bucket.shellArg == null) return
        prefs.edit().putString(KEY_DEFAULT_TARGET_BUCKET, bucket.name).apply()
        _state.update { it.copy(defaultTargetBucket = bucket) }
    }

    fun setExcludeRecentsApps(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXCLUDE_RECENTS, enabled).apply()
        _state.update { it.copy(excludeRecentsApps = enabled) }
    }

    fun setProtectActiveAudioApps(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROTECT_AUDIO, enabled).apply()
        _state.update { it.copy(protectActiveAudioApps = enabled) }
    }

    fun setFinerMediaPlaybackDetection(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FINER_MEDIA_DETECTION, enabled).apply()
        _state.update { it.copy(finerMediaPlaybackDetection = enabled) }
    }

    fun setExcludeForegroundApp(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXCLUDE_FOREGROUND, enabled).apply()
        _state.update { it.copy(excludeForegroundApp = enabled) }
    }

    fun setIgnoreBackgroundFree(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IGNORE_BACKGROUND_FREE, enabled).apply()
        _state.update { it.copy(ignoreBackgroundFree = enabled) }
    }

    fun setWakeupTrackerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKEUP_TRACKER_ENABLED, enabled).apply()
        _state.update { it.copy(wakeupTrackerEnabled = enabled) }
    }

    fun setEnhancedWakeupSources(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENHANCED_WAKEUP_SOURCES, enabled).apply()
        _state.update { it.copy(enhancedWakeupSources = enabled) }
    }

    fun setAutoHibernation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_HIBERNATION, enabled).apply()
        _state.update { it.copy(autoHibernationEnabled = enabled) }
    }

    fun setAutoHibernationIntervalMinutes(minutes: Int) {
        val clamped = minutes.coerceAtLeast(15)
        prefs.edit().putInt(KEY_AUTO_INTERVAL_MINUTES, clamped).apply()
        _state.update { it.copy(autoHibernationIntervalMinutes = clamped) }
    }

    fun setAutoHibernationScreenOffOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SCREEN_OFF_ONLY, enabled).apply()
        _state.update { it.copy(autoHibernationScreenOffOnly = enabled) }
    }

    fun setAutoHibernateAllUserApps(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_HIBERNATE_ALL_USER, enabled).apply()
        _state.update { it.copy(autoHibernateAllUserApps = enabled) }
    }

    fun setQuickActionNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUICK_ACTION_NOTIF, enabled).apply()
        _state.update { it.copy(quickActionNotificationEnabled = enabled) }
    }

    fun setGeekExtrasEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GEEK_EXTRAS, enabled).apply()
        _state.update { it.copy(geekExtrasEnabled = enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _state.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _state.update { it.copy(dynamicColor = enabled) }
    }

    fun setForceMaxRefreshRate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FORCE_MAX_REFRESH_RATE, enabled).apply()
        _state.update { it.copy(forceMaxRefreshRate = enabled) }
    }

    fun setLiquidGlassBottomBar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIQUID_GLASS_BOTTOM_BAR, enabled).apply()
        _state.update { it.copy(liquidGlassBottomBar = enabled) }
    }

    fun setIncludeSystemApps(include: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_SYSTEM, include).apply()
        _state.update { it.copy(includeSystemApps = include) }
    }

    fun updateAppPolicy(packageName: String, transform: (PerAppPolicy) -> PerAppPolicy) {
        updateBatchAppPolicies(listOf(packageName), transform)
    }

    fun updateBatchAppPolicies(
        packages: Iterable<String>,
        transform: (PerAppPolicy) -> PerAppPolicy,
    ) {
        val pkgList = packages.distinct()
        if (pkgList.isEmpty()) return
        _state.update { current ->
            val updated = current.perAppPolicies.toMutableMap()
            for (pkg in pkgList) {
                val next = transform(updated[pkg] ?: PerAppPolicy())
                if (next.isDefault) {
                    updated.remove(pkg)
                } else {
                    updated[pkg] = next
                }
            }
            val scheduled = updated.filterValues { it.includeInScheduledNaps }.keys.toSet()
            prefs.edit()
                .putString(KEY_PER_APP_POLICIES, json.encodeToString(updated))
                .putStringSet(KEY_MANAGED_PACKAGES, scheduled)
                .apply()
            current.copy(
                perAppPolicies = updated,
                managedPackages = scheduled,
            )
        }
    }

    fun setAppStrategy(packageName: String, strategy: HibernationStrategy) {
        updateAppPolicy(packageName) { it.copy(strategy = strategy) }
    }

    fun setAppRecentsProtection(packageName: String, protection: RecentsProtection) {
        updateAppPolicy(packageName) { it.copy(recentsProtection = protection) }
    }

    fun updateWakeupRecords(updatedRecords: Map<String, WakeupRecord>) {
        if (updatedRecords == _state.value.wakeupRecords) return
        prefs.edit().putString(KEY_WAKEUP_RECORDS, json.encodeToString(updatedRecords)).apply()
        _state.update { it.copy(wakeupRecords = updatedRecords) }
    }

    fun clearWakeupRecords(packageName: String? = null) {
        _state.update { current ->
            val updated = if (packageName == null) {
                emptyMap()
            } else {
                current.wakeupRecords - packageName
            }
            prefs.edit().putString(KEY_WAKEUP_RECORDS, json.encodeToString(updated)).apply()
            current.copy(wakeupRecords = updated)
        }
    }

    fun recordHibernation(packages: List<String>, event: HibernationEvent) {
        val now = event.timestampMillis
        _state.update { current ->
            val updatedTimestamps = current.lastNappedTimestamps.toMutableMap()
            packages.forEach { pkg -> updatedTimestamps[pkg] = now }
            val updatedHistory = (listOf(event) + current.history).take(40)
            prefs.edit()
                .putString(KEY_LAST_TIMESTAMPS, json.encodeToString(updatedTimestamps))
                .putString(KEY_HISTORY, json.encodeToString(updatedHistory))
                .apply()
            current.copy(
                lastNappedTimestamps = updatedTimestamps,
                history = updatedHistory,
            )
        }
    }

    fun exportBackupJson(nowMillis: Long = System.currentTimeMillis()): String {
        val current = _state.value
        val snapshot = BackupSnapshot(
            schemaVersion = 2,
            exportedAtMillis = nowMillis,
            workingMode = current.workingMode.name,
            shallowHibernation = current.shallowHibernation,
            autoHibernationEnabled = current.autoHibernationEnabled,
            autoHibernationIntervalMinutes = current.autoHibernationIntervalMinutes,
            managedPackages = current.managedPackages.sorted(),
            perAppPolicies = current.perAppPolicies.toSortedMap(),
            excludeRecentsApps = current.excludeRecentsApps,
            protectActiveAudioApps = current.protectActiveAudioApps,
            finerMediaPlaybackDetection = current.finerMediaPlaybackDetection,
            excludeForegroundApp = current.excludeForegroundApp,
            ignoreBackgroundFree = current.ignoreBackgroundFree,
            wakeupTrackerEnabled = current.wakeupTrackerEnabled,
            enhancedWakeupSources = current.enhancedWakeupSources,
            autoHibernationScreenOffOnly = current.autoHibernationScreenOffOnly,
            autoHibernateAllUserApps = current.autoHibernateAllUserApps,
            quickActionNotificationEnabled = current.quickActionNotificationEnabled,
            includeSystemApps = current.includeSystemApps,
            geekExtrasEnabled = current.geekExtrasEnabled,
            forceMaxRefreshRate = current.forceMaxRefreshRate,
            defaultTargetBucket = current.defaultTargetBucket.name,
        )
        return json.encodeToString(snapshot)
    }

    fun importBackupJson(rawJson: String): Result<Int> = runCatching {
        val snapshot = json.decodeFromString<BackupSnapshot>(rawJson)
        val sanitizedPackages = snapshot.managedPackages
            .map { it.trim() }
            .filter { it.isNotEmpty() && PACKAGE_NAME_REGEX.matches(it) }
            .toSet()

        val sanitizedPolicies = snapshot.perAppPolicies
            .filterKeys { PACKAGE_NAME_REGEX.matches(it.trim()) }
            .mapKeys { it.key.trim() }
            .toMutableMap()

        for (pkg in sanitizedPackages) {
            val existing = sanitizedPolicies[pkg] ?: PerAppPolicy()
            if (!existing.includeInScheduledNaps) {
                sanitizedPolicies[pkg] = existing.copy(includeInScheduledNaps = true)
            }
        }
        val effectiveScheduled = sanitizedPolicies.filterValues { it.includeInScheduledNaps }.keys.toSet()
        val mode = runCatching { WorkingMode.valueOf(snapshot.workingMode) }
            .getOrDefault(WorkingMode.AUTO)
        val bucket = runCatching { StandbyBucket.valueOf(snapshot.defaultTargetBucket) }
            .getOrNull()
            ?.takeIf { it.shellArg != null }
            ?: StandbyBucket.RESTRICTED

        prefs.edit()
            .putString(KEY_WORKING_MODE, mode.name)
            .putBoolean(KEY_SHALLOW_HIBERNATION, snapshot.shallowHibernation)
            .putString(KEY_DEFAULT_TARGET_BUCKET, bucket.name)
            .putBoolean(KEY_EXCLUDE_RECENTS, snapshot.excludeRecentsApps)
            .putBoolean(KEY_PROTECT_AUDIO, snapshot.protectActiveAudioApps)
            .putBoolean(KEY_FINER_MEDIA_DETECTION, snapshot.finerMediaPlaybackDetection)
            .putBoolean(KEY_EXCLUDE_FOREGROUND, snapshot.excludeForegroundApp)
            .putBoolean(KEY_IGNORE_BACKGROUND_FREE, snapshot.ignoreBackgroundFree)
            .putBoolean(KEY_WAKEUP_TRACKER_ENABLED, snapshot.wakeupTrackerEnabled)
            .putBoolean(KEY_ENHANCED_WAKEUP_SOURCES, snapshot.enhancedWakeupSources)
            .putBoolean(KEY_AUTO_HIBERNATION, snapshot.autoHibernationEnabled)
            .putInt(KEY_AUTO_INTERVAL_MINUTES, snapshot.autoHibernationIntervalMinutes.coerceAtLeast(15))
            .putBoolean(KEY_AUTO_SCREEN_OFF_ONLY, snapshot.autoHibernationScreenOffOnly)
            .putBoolean(KEY_AUTO_HIBERNATE_ALL_USER, snapshot.autoHibernateAllUserApps)
            .putBoolean(KEY_QUICK_ACTION_NOTIF, snapshot.quickActionNotificationEnabled)
            .putBoolean(KEY_INCLUDE_SYSTEM, snapshot.includeSystemApps)
            .putBoolean(KEY_GEEK_EXTRAS, snapshot.geekExtrasEnabled)
            .putBoolean(KEY_FORCE_MAX_REFRESH_RATE, snapshot.forceMaxRefreshRate)
            .putString(KEY_PER_APP_POLICIES, json.encodeToString(sanitizedPolicies))
            .putStringSet(KEY_MANAGED_PACKAGES, effectiveScheduled)
            .apply()

        _state.update {
            it.copy(
                workingMode = mode,
                shallowHibernation = snapshot.shallowHibernation,
                defaultTargetBucket = bucket,
                excludeRecentsApps = snapshot.excludeRecentsApps,
                protectActiveAudioApps = snapshot.protectActiveAudioApps,
                finerMediaPlaybackDetection = snapshot.finerMediaPlaybackDetection,
                excludeForegroundApp = snapshot.excludeForegroundApp,
                ignoreBackgroundFree = snapshot.ignoreBackgroundFree,
                wakeupTrackerEnabled = snapshot.wakeupTrackerEnabled,
                enhancedWakeupSources = snapshot.enhancedWakeupSources,
                autoHibernationEnabled = snapshot.autoHibernationEnabled,
                autoHibernationIntervalMinutes = snapshot.autoHibernationIntervalMinutes.coerceAtLeast(15),
                autoHibernationScreenOffOnly = snapshot.autoHibernationScreenOffOnly,
                autoHibernateAllUserApps = snapshot.autoHibernateAllUserApps,
                quickActionNotificationEnabled = snapshot.quickActionNotificationEnabled,
                includeSystemApps = snapshot.includeSystemApps,
                geekExtrasEnabled = snapshot.geekExtrasEnabled,
                forceMaxRefreshRate = snapshot.forceMaxRefreshRate,
                perAppPolicies = sanitizedPolicies,
                managedPackages = effectiveScheduled,
            )
        }
        maxOf(sanitizedPolicies.size, effectiveScheduled.size)
    }

    companion object {
        const val PREFS_NAME = "greenify_reborn_prefs"
        private const val KEY_WORKING_MODE = "working_mode"
        private const val KEY_ROOT_VERIFIED = "root_verified_previously"
        private const val KEY_SHALLOW_HIBERNATION = "shallow_hibernation"
        private const val KEY_DEFAULT_TARGET_BUCKET = "default_target_bucket"
        private const val KEY_EXCLUDE_RECENTS = "exclude_recents_apps"
        private const val KEY_PROTECT_AUDIO = "protect_active_audio_apps"
        private const val KEY_FINER_MEDIA_DETECTION = "finer_media_playback_detection"
        private const val KEY_EXCLUDE_FOREGROUND = "exclude_foreground_app"
        private const val KEY_IGNORE_BACKGROUND_FREE = "ignore_background_free"
        private const val KEY_WAKEUP_TRACKER_ENABLED = "wakeup_tracker_enabled"
        private const val KEY_ENHANCED_WAKEUP_SOURCES = "enhanced_wakeup_sources"
        private const val KEY_AUTO_HIBERNATION = "auto_hibernation"
        private const val KEY_AUTO_INTERVAL_MINUTES = "auto_interval_minutes"
        private const val KEY_AUTO_SCREEN_OFF_ONLY = "auto_hibernation_screen_off_only"
        private const val KEY_AUTO_HIBERNATE_ALL_USER = "auto_hibernate_all_user_apps"
        private const val KEY_QUICK_ACTION_NOTIF = "quick_action_notification_enabled"
        private const val KEY_GEEK_EXTRAS = "geek_extras_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_FORCE_MAX_REFRESH_RATE = "force_max_refresh_rate"
        private const val KEY_LIQUID_GLASS_BOTTOM_BAR = "liquid_glass_bottom_bar"
        private const val KEY_INCLUDE_SYSTEM = "include_system"
        private const val KEY_PER_APP_POLICIES = "per_app_policies"
        private const val KEY_MANAGED_PACKAGES = "managed_packages"
        private const val KEY_LEGACY_GREENIFIED_PACKAGES = "greenified_packages"
        private const val KEY_LAST_TIMESTAMPS = "last_timestamps"
        private const val KEY_WAKEUP_RECORDS = "wakeup_records"
        private const val KEY_HISTORY = "hibernation_history"

        private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+$")
    }
}
