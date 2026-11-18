package com.greenify.reborn.data

import android.content.Context
import android.content.SharedPreferences
import com.greenify.reborn.model.BackupSnapshot
import com.greenify.reborn.model.HibernationEvent
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
    val shallowHibernation: Boolean = false,
    val autoHibernationEnabled: Boolean = false,
    val autoHibernationIntervalMinutes: Int = 60,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val includeSystemApps: Boolean = false,
    val greenifiedPackages: Set<String> = emptySet(),
    val lastHibernatedTimestamps: Map<String, Long> = emptyMap(),
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
        val greenified = prefs.getStringSet(KEY_GREENIFIED_PACKAGES, emptySet())?.toSet() ?: emptySet()
        val timestampsRaw = prefs.getString(KEY_LAST_TIMESTAMPS, null)
        val timestamps = timestampsRaw?.let {
            runCatching { json.decodeFromString<Map<String, Long>>(it) }.getOrNull()
        } ?: emptyMap()
        val historyRaw = prefs.getString(KEY_HISTORY, null)
        val history = historyRaw?.let {
            runCatching { json.decodeFromString<List<HibernationEvent>>(it) }.getOrNull()
        } ?: emptyList()

        return UserSettingsState(
            workingMode = workingMode,
            shallowHibernation = prefs.getBoolean(KEY_SHALLOW_HIBERNATION, false),
            autoHibernationEnabled = prefs.getBoolean(KEY_AUTO_HIBERNATION, false),
            autoHibernationIntervalMinutes = prefs.getInt(KEY_AUTO_INTERVAL_MINUTES, 60)
                .coerceAtLeast(15),
            themeMode = themeMode,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
            includeSystemApps = prefs.getBoolean(KEY_INCLUDE_SYSTEM, false),
            greenifiedPackages = greenified,
            lastHibernatedTimestamps = timestamps,
            history = history,
        )
    }

    fun setWorkingMode(mode: WorkingMode) {
        prefs.edit().putString(KEY_WORKING_MODE, mode.name).apply()
        _state.update { it.copy(workingMode = mode) }
    }

    fun setShallowHibernation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHALLOW_HIBERNATION, enabled).apply()
        _state.update { it.copy(shallowHibernation = enabled) }
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

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _state.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _state.update { it.copy(dynamicColor = enabled) }
    }

    fun setIncludeSystemApps(include: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_SYSTEM, include).apply()
        _state.update { it.copy(includeSystemApps = include) }
    }

    fun toggleGreenified(packageName: String): Boolean {
        var nowGreenified = false
        _state.update { current ->
            val updated = current.greenifiedPackages.toMutableSet()
            nowGreenified = if (updated.contains(packageName)) {
                updated.remove(packageName)
                false
            } else {
                updated.add(packageName)
                true
            }
            prefs.edit().putStringSet(KEY_GREENIFIED_PACKAGES, updated).apply()
            current.copy(greenifiedPackages = updated)
        }
        return nowGreenified
    }

    fun recordHibernation(packages: List<String>, event: HibernationEvent) {
        val now = event.timestampMillis
        _state.update { current ->
            val updatedTimestamps = current.lastHibernatedTimestamps.toMutableMap()
            packages.forEach { pkg -> updatedTimestamps[pkg] = now }
            val updatedHistory = (listOf(event) + current.history).take(25)
            prefs.edit()
                .putString(KEY_LAST_TIMESTAMPS, json.encodeToString(updatedTimestamps))
                .putString(KEY_HISTORY, json.encodeToString(updatedHistory))
                .apply()
            current.copy(
                lastHibernatedTimestamps = updatedTimestamps,
                history = updatedHistory,
            )
        }
    }

    fun exportBackupJson(nowMillis: Long = System.currentTimeMillis()): String {
        val current = _state.value
        val snapshot = BackupSnapshot(
            exportedAtMillis = nowMillis,
            workingMode = current.workingMode.name,
            shallowHibernation = current.shallowHibernation,
            autoHibernationEnabled = current.autoHibernationEnabled,
            autoHibernationIntervalMinutes = current.autoHibernationIntervalMinutes,
            greenifiedPackages = current.greenifiedPackages.sorted(),
        )
        return json.encodeToString(snapshot)
    }

    fun importBackupJson(rawJson: String): Result<Int> = runCatching {
        val snapshot = json.decodeFromString<BackupSnapshot>(rawJson)
        val sanitizedPackages = snapshot.greenifiedPackages
            .map { it.trim() }
            .filter { it.isNotEmpty() && PACKAGE_NAME_REGEX.matches(it) }
            .toSet()
        val mode = runCatching { WorkingMode.valueOf(snapshot.workingMode) }
            .getOrDefault(WorkingMode.AUTO)

        prefs.edit()
            .putString(KEY_WORKING_MODE, mode.name)
            .putBoolean(KEY_SHALLOW_HIBERNATION, snapshot.shallowHibernation)
            .putBoolean(KEY_AUTO_HIBERNATION, snapshot.autoHibernationEnabled)
            .putInt(KEY_AUTO_INTERVAL_MINUTES, snapshot.autoHibernationIntervalMinutes.coerceAtLeast(15))
            .putStringSet(KEY_GREENIFIED_PACKAGES, sanitizedPackages)
            .apply()

        _state.update {
            it.copy(
                workingMode = mode,
                shallowHibernation = snapshot.shallowHibernation,
                autoHibernationEnabled = snapshot.autoHibernationEnabled,
                autoHibernationIntervalMinutes = snapshot.autoHibernationIntervalMinutes.coerceAtLeast(15),
                greenifiedPackages = sanitizedPackages,
            )
        }
        sanitizedPackages.size
    }

    companion object {
        const val PREFS_NAME = "greenify_reborn_prefs"
        private const val KEY_WORKING_MODE = "working_mode"
        private const val KEY_SHALLOW_HIBERNATION = "shallow_hibernation"
        private const val KEY_AUTO_HIBERNATION = "auto_hibernation"
        private const val KEY_AUTO_INTERVAL_MINUTES = "auto_interval_minutes"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_INCLUDE_SYSTEM = "include_system"
        private const val KEY_GREENIFIED_PACKAGES = "greenified_packages"
        private const val KEY_LAST_TIMESTAMPS = "last_timestamps"
        private const val KEY_HISTORY = "hibernation_history"

        private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+$")
    }
}
