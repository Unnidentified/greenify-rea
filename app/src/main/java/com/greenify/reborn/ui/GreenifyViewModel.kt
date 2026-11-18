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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

enum class HomeFilter(val label: String) {
    MANAGED("Managed"),
    ALL_APPS("All apps"),
    AWAKE("Awake"),
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
    val homeFilter: HomeFilter = HomeFilter.MANAGED,
) {
    val managedApps: List<AppEntry>
        get() = allApps.filter { it.isManaged }

    val awakeManagedCount: Int
        get() = managedApps.count { !it.isStopped }

    val filteredApps: List<AppEntry>
        get() {
            val q = homeQuery.trim().lowercase()
            return allApps.filter { app ->
                val matchesFilter = when (homeFilter) {
                    HomeFilter.MANAGED -> app.isManaged
                    HomeFilter.ALL_APPS -> true
                    HomeFilter.AWAKE -> !app.isStopped
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
            val snapshot = repository.loadInstalledApps(
                managedPackages = settings.managedPackages,
                lastNappedTimestamps = settings.lastNappedTimestamps,
                wakeupRecords = settings.wakeupRecords,
                includeSystemApps = settings.includeSystemApps,
            )
            preferences.updateWakeupRecords(snapshot.updatedWakeupRecords)
            _uiState.update {
                it.copy(
                    environment = env,
                    allApps = snapshot.apps,
                    isLoadingApps = false,
                )
            }
        }
    }

    fun updateHomeQuery(query: String) {
        _uiState.update { it.copy(homeQuery = query) }
    }

    fun updateHomeFilter(filter: HomeFilter) {
        _uiState.update { it.copy(homeFilter = filter) }
    }

    fun toggleManaged(app: AppEntry) {
        val added = preferences.toggleManaged(app.packageName)
        _events.tryEmit(
            if (added) "Added ${app.label} to nap list" else "Removed ${app.label} from nap list",
        )
    }

    fun hibernateAll() {
        val targets = _uiState.value.managedApps.map { it.packageName }
        if (targets.isEmpty()) {
            _events.tryEmit("Add apps from All apps to put them down for a nap.")
            return
        }
        hibernatePackages(targets)
    }

    fun hibernateSingle(app: AppEntry) {
        hibernatePackages(listOf(app.packageName))
    }

    private fun hibernatePackages(packages: List<String>) {
        if (packages.isEmpty() || _uiState.value.isHibernating) return

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
                _events.tryEmit("Imported $count app(s) into your nap list.")
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
