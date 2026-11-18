package com.greenify.reborn.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.greenify.reborn.data.AppPreferences
import com.greenify.reborn.data.DeviceAppRepository
import com.greenify.reborn.data.UserSettingsState
import com.greenify.reborn.engine.AutoHibernationScheduler
import com.greenify.reborn.engine.HibernationEngine
import com.greenify.reborn.model.AppEntry
import com.greenify.reborn.model.EnvironmentStatus
import com.greenify.reborn.model.ResolvedBackend
import com.greenify.reborn.model.ThemeMode
import com.greenify.reborn.model.WorkingMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

enum class AnalyzerFilter(val label: String) {
    ALL("All"),
    RUNNING("Running"),
    NON_RESTRICTED("Active Bucket"),
}

data class MainUiState(
    val settings: UserSettingsState = UserSettingsState(),
    val environment: EnvironmentStatus = EnvironmentStatus(
        shizukuInstalled = false,
        shizukuRunning = false,
        shizukuPermissionGranted = false,
        rootAvailable = false,
        usageAccessGranted = false,
        notificationsGranted = true,
        resolvedBackend = ResolvedBackend.NON_ROOT,
    ),
    val allApps: List<AppEntry> = emptyList(),
    val isLoadingApps: Boolean = true,
    val isHibernating: Boolean = false,
    val homeQuery: String = "",
    val analyzerQuery: String = "",
    val analyzerFilter: AnalyzerFilter = AnalyzerFilter.ALL,
) {
    val greenifiedApps: List<AppEntry>
        get() = allApps.filter { it.isGreenified }

    val filteredGreenifiedApps: List<AppEntry>
        get() {
            val q = homeQuery.trim().lowercase()
            return if (q.isEmpty()) {
                greenifiedApps
            } else {
                greenifiedApps.filter {
                    it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
                }
            }
        }

    val filteredAnalyzerApps: List<AppEntry>
        get() {
            val q = analyzerQuery.trim().lowercase()
            return allApps.filter { app ->
                val matchesFilter = when (analyzerFilter) {
                    AnalyzerFilter.ALL -> true
                    AnalyzerFilter.RUNNING -> !app.isStopped
                    AnalyzerFilter.NON_RESTRICTED ->
                        app.standbyBucket != com.greenify.reborn.model.StandbyBucket.RESTRICTED &&
                            app.standbyBucket != com.greenify.reborn.model.StandbyBucket.RARE
                }
                val matchesQuery = q.isEmpty() ||
                    app.label.lowercase().contains(q) ||
                    app.packageName.lowercase().contains(q)
                matchesFilter && matchesQuery
            }
        }
}

class GreenifyViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferences(application)
    private val repository = DeviceAppRepository(application)
    private val engine = HibernationEngine(application, preferences, repository)

    private val _uiState = MutableStateFlow(MainUiState(settings = preferences.state.value))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            preferences.state.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                refreshAll()
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = it.allApps.isEmpty()) }
            val settings = preferences.state.value
            val env = repository.inspectEnvironment(settings.workingMode)
            val apps = repository.loadInstalledApps(
                greenifiedPackages = settings.greenifiedPackages,
                lastHibernatedTimestamps = settings.lastHibernatedTimestamps,
                includeSystemApps = settings.includeSystemApps,
            )
            _uiState.update {
                it.copy(
                    environment = env,
                    allApps = apps,
                    isLoadingApps = false,
                )
            }
        }
    }

    fun updateHomeQuery(query: String) {
        _uiState.update { it.copy(homeQuery = query) }
    }

    fun updateAnalyzerQuery(query: String) {
        _uiState.update { it.copy(analyzerQuery = query) }
    }

    fun updateAnalyzerFilter(filter: AnalyzerFilter) {
        _uiState.update { it.copy(analyzerFilter = filter) }
    }

    fun toggleGreenified(app: AppEntry) {
        val added = preferences.toggleGreenified(app.packageName)
        _events.tryEmit(
            if (added) "Added ${app.label} to Greenify" else "Removed ${app.label}",
        )
    }

    fun hibernateAll() {
        val targets = _uiState.value.greenifiedApps.map { it.packageName }
        hibernatePackages(targets)
    }

    fun hibernateSingle(packageName: String) {
        hibernatePackages(listOf(packageName))
    }

    private fun hibernatePackages(packages: List<String>) {
        if (packages.isEmpty()) {
            _events.tryEmit("Select at least one app in Analyzer first.")
            return
        }
        if (_uiState.value.isHibernating) return

        viewModelScope.launch {
            _uiState.update { it.copy(isHibernating = true) }
            val result = engine.hibernate(packages)
            _events.tryEmit(result.message)
            _uiState.update { it.copy(isHibernating = false) }
            refreshAll()
        }
    }

    fun setWorkingMode(mode: WorkingMode) {
        preferences.setWorkingMode(mode)
    }

    fun setShallowHibernation(enabled: Boolean) {
        preferences.setShallowHibernation(enabled)
    }

    fun setAutoHibernation(enabled: Boolean) {
        preferences.setAutoHibernation(enabled)
        AutoHibernationScheduler.syncPeriodicSchedule(
            context = getApplication(),
            enabled = enabled,
            intervalMinutes = preferences.state.value.autoHibernationIntervalMinutes,
        )
    }

    fun setAutoHibernationInterval(minutes: Int) {
        preferences.setAutoHibernationIntervalMinutes(minutes)
        if (preferences.state.value.autoHibernationEnabled) {
            AutoHibernationScheduler.syncPeriodicSchedule(
                context = getApplication(),
                enabled = true,
                intervalMinutes = minutes,
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        preferences.setThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        preferences.setDynamicColor(enabled)
    }

    fun setIncludeSystemApps(include: Boolean) {
        preferences.setIncludeSystemApps(include)
    }

    fun requestShizukuPermission() {
        runCatching {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            } else {
                _events.tryEmit("Shizuku service is not running yet.")
            }
        }.onFailure {
            _events.tryEmit("Unable to request Shizuku permission: ${it.message}")
        }
    }

    fun exportBackupJson(): String = preferences.exportBackupJson()

    fun importBackupJson(rawJson: String) {
        preferences.importBackupJson(rawJson)
            .onSuccess { count ->
                _events.tryEmit("Imported $count greenified app(s) from backup.")
            }
            .onFailure { err ->
                _events.tryEmit("Backup import failed: ${err.message}")
            }
    }

    fun emitMessage(message: String) {
        _events.tryEmit(message)
    }

    companion object {
        const val SHIZUKU_REQUEST_CODE = 5110
    }
}
