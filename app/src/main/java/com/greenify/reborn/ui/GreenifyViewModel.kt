package com.greenify.reborn.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.greenify.reborn.data.AppLogStore
import com.greenify.reborn.data.AppPreferences
import com.greenify.reborn.data.DeviceAppRepository
import com.greenify.reborn.data.UserSettingsState
import com.greenify.reborn.engine.AutoHibernationScheduler
import com.greenify.reborn.engine.HibernationEngine
import com.greenify.reborn.model.AppComponentInfo
import com.greenify.reborn.model.AppEntry
import com.greenify.reborn.model.AppFilterChip
import com.greenify.reborn.model.AppOpRestriction
import com.greenify.reborn.model.EngineLogEntry
import com.greenify.reborn.model.EnvironmentStatus
import com.greenify.reborn.model.HibernationStrategy
import com.greenify.reborn.model.PerAppPolicy
import com.greenify.reborn.model.RecentsProtection
import com.greenify.reborn.model.ResolvedBackend
import com.greenify.reborn.model.StandbyBucket
import com.greenify.reborn.model.ThemeMode
import com.greenify.reborn.model.WakeupCategory
import com.greenify.reborn.model.WorkingMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

data class MainUiState(
    val settings: UserSettingsState = UserSettingsState(),
    val environment: EnvironmentStatus = EnvironmentStatus(
        shizukuInstalled = false,
        shizukuRunning = false,
        shizukuPermissionGranted = false,
        rootAvailable = false,
        rootBinaryDetected = false,
        usageAccessGranted = false,
        notificationsGranted = true,
        resolvedBackend = ResolvedBackend.NON_ROOT,
    ),
    val allApps: List<AppEntry> = emptyList(),
    val isLoadingApps: Boolean = true,
    val isRefreshing: Boolean = false,
    val isHibernating: Boolean = false,
    val homeQuery: String = "",
    val activeFilters: Set<AppFilterChip> = setOf(AppFilterChip.USER),
    val isBatchMode: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    val inspectedComponents: List<AppComponentInfo> = emptyList(),
    val inspectedAppOps: Map<AppOpRestriction, Boolean> = emptyMap(),
    val isLoadingAppDetails: Boolean = false,
) {
    val awakeCount: Int
        get() = filteredApps.count { !it.isStopped }

    val nappingCount: Int
        get() = filteredApps.count { it.isStopped }

    val filteredApps: List<AppEntry>
        get() = filterApps(allApps, activeFilters, homeQuery)

    companion object {
        fun filterApps(
            apps: List<AppEntry>,
            activeFilters: Set<AppFilterChip>,
            query: String,
        ): List<AppEntry> {
            val q = query.trim()
            val hasUser = AppFilterChip.USER in activeFilters
            val hasSystem = AppFilterChip.SYSTEM in activeFilters
            val hasAwake = AppFilterChip.AWAKE in activeFilters
            val hasNapping = AppFilterChip.NAPPING in activeFilters
            val requireRecents = AppFilterChip.IN_RECENTS in activeFilters
            val requireWakeups = AppFilterChip.WAKEUPS in activeFilters
            val requireConfigured = AppFilterChip.CONFIGURED in activeFilters

            return apps.filter { app ->
                val matchesOrigin = if (hasUser || hasSystem) {
                    (hasUser && !app.isSystemApp) || (hasSystem && app.isSystemApp)
                } else {
                    true
                }
                val matchesState = if (hasAwake || hasNapping) {
                    (hasAwake && !app.isStopped) || (hasNapping && app.isStopped)
                } else {
                    true
                }
                val matchesRecents = !requireRecents || (app.inRecents || app.isPlayingAudio)
                val matchesWakeups = !requireWakeups || app.wakeupSummary.totalCount > 0
                val matchesConfigured = !requireConfigured || !app.policy.isDefault
                val matchesQuery = q.isEmpty() ||
                    app.label.contains(q, ignoreCase = true) ||
                    app.packageName.contains(q, ignoreCase = true)

                matchesOrigin &&
                    matchesState &&
                    matchesRecents &&
                    matchesWakeups &&
                    matchesConfigured &&
                    matchesQuery
            }
        }
    }
}

class GreenifyViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferences(application)
    private val repository = DeviceAppRepository(application)
    private val engine = HibernationEngine(application, preferences, repository)
    private val logStore = AppLogStore.getInstance(application)

    private val _uiState = MutableStateFlow(MainUiState(settings = preferences.state.value))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val logs: StateFlow<List<EngineLogEntry>> = logStore.logs

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private data class RefreshTrigger(
        val workingMode: WorkingMode,
        val includeSystemApps: Boolean,
        val excludeRecentsApps: Boolean,
        val protectActiveAudioApps: Boolean,
        val finerMediaPlaybackDetection: Boolean,
        val excludeForegroundApp: Boolean,
        val wakeupTrackerEnabled: Boolean,
        val enhancedWakeupSources: Boolean,
        val lastNappedTimestamps: Map<String, Long>,
        val perAppPolicies: Map<String, PerAppPolicy>,
        val rootVerifiedPreviously: Boolean,
    )

    private var refreshJob: Job? = null
    private var lastRefreshRequestAtMs: Long = 0L

    init {
        logStore.info("SYSTEM", "Greenify session initialized (mode=${preferences.state.value.workingMode.name}).")
        viewModelScope.launch {
            preferences.state.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            preferences.state
                .map { s ->
                    RefreshTrigger(
                        workingMode = s.workingMode,
                        includeSystemApps = s.includeSystemApps,
                        excludeRecentsApps = s.excludeRecentsApps,
                        protectActiveAudioApps = s.protectActiveAudioApps,
                        finerMediaPlaybackDetection = s.finerMediaPlaybackDetection,
                        excludeForegroundApp = s.excludeForegroundApp,
                        wakeupTrackerEnabled = s.wakeupTrackerEnabled,
                        enhancedWakeupSources = s.enhancedWakeupSources,
                        lastNappedTimestamps = s.lastNappedTimestamps,
                        perAppPolicies = s.perAppPolicies,
                        rootVerifiedPreviously = s.rootVerifiedPreviously,
                    )
                }
                .distinctUntilChanged()
                .collect {
                    refreshAll(force = true)
                }
        }
    }

    fun refreshAll(probeRoot: Boolean = false, userTriggered: Boolean = false, force: Boolean = true) {
        val now = System.currentTimeMillis()
        if (!force && !probeRoot && !userTriggered &&
            (refreshJob?.isActive == true || now - lastRefreshRequestAtMs < 1_000L)
        ) {
            return
        }
        lastRefreshRequestAtMs = now
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    isLoadingApps = it.allApps.isEmpty(),
                    isRefreshing = userTriggered || it.isRefreshing,
                )
            }
            val settings = preferences.state.value
            val env = repository.inspectEnvironment(
                preferredMode = settings.workingMode,
                probeRoot = probeRoot,
                previousRootVerified = settings.rootVerifiedPreviously,
            )
            if (env.rootAvailable && !settings.rootVerifiedPreviously) {
                preferences.setRootVerifiedPreviously(true)
            }
            val runtimeSignals = engine.queryLiveRuntimeSignals(env.resolvedBackend)
            val includeSystem = settings.includeSystemApps ||
                AppFilterChip.SYSTEM in _uiState.value.activeFilters

            val snapshot = repository.loadInstalledApps(
                perAppPolicies = settings.perAppPolicies,
                lastNappedTimestamps = settings.lastNappedTimestamps,
                wakeupRecords = settings.wakeupRecords,
                includeSystemApps = includeSystem,
                wakeupTrackerEnabled = settings.wakeupTrackerEnabled,
                runtimeSignals = runtimeSignals,
            )
            preferences.updateWakeupRecords(snapshot.updatedWakeupRecords)

            if (userTriggered) {
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed < 380L) delay(380L - elapsed)
            }

            _uiState.update { current ->
                val validSelected = current.selectedPackages.intersect(snapshot.apps.map { it.packageName }.toSet())
                current.copy(
                    environment = env,
                    allApps = snapshot.apps,
                    isLoadingApps = false,
                    isRefreshing = false,
                    selectedPackages = validSelected,
                )
            }
        }
    }

    fun updateHomeQuery(query: String) {
        _uiState.update { it.copy(homeQuery = query) }
    }

    fun toggleFilter(chip: AppFilterChip) {
        var shouldReloadSystem = false
        var preventedLast = false
        _uiState.update { current ->
            val next = current.activeFilters.toMutableSet()
            if (chip in next) {
                if (next.size <= 1) {
                    preventedLast = true
                    return@update current
                }
                next.remove(chip)
            } else {
                next.add(chip)
                if (chip == AppFilterChip.SYSTEM) {
                    shouldReloadSystem = true
                }
            }
            current.copy(activeFilters = next)
        }
        if (preventedLast) {
            _events.tryEmit("At least one filter must stay active.")
        } else if (shouldReloadSystem) {
            refreshAll()
        }
    }

    // --- Batch selector mode ---

    fun enterBatchMode(initialPackage: String? = null) {
        _uiState.update { current ->
            val selected = if (initialPackage != null) {
                current.selectedPackages + initialPackage
            } else {
                current.selectedPackages
            }
            current.copy(isBatchMode = true, selectedPackages = selected)
        }
    }

    fun exitBatchMode() {
        _uiState.update { it.copy(isBatchMode = false, selectedPackages = emptySet()) }
    }

    fun toggleBatchSelection(packageName: String) {
        _uiState.update { current ->
            val next = current.selectedPackages.toMutableSet()
            if (!next.add(packageName)) next.remove(packageName)
            current.copy(selectedPackages = next)
        }
    }

    fun toggleSelectAllFiltered() {
        _uiState.update { current ->
            if (!current.isBatchMode) return@update current
            val filteredPkgs = current.filteredApps.map { it.packageName }.toSet()
            val allSelected = filteredPkgs.isNotEmpty() && filteredPkgs.all { it in current.selectedPackages }
            val next = if (allSelected) {
                current.selectedPackages - filteredPkgs
            } else {
                current.selectedPackages + filteredPkgs
            }
            current.copy(selectedPackages = next)
        }
    }

    // --- Per-app & batch hibernation + adjustments ---

    private var loadDetailsJob: Job? = null

    fun loadAppDetails(packageName: String) {
        loadDetailsJob?.cancel()
        loadDetailsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingAppDetails = true,
                    inspectedComponents = emptyList(),
                    inspectedAppOps = emptyMap(),
                )
            }
            coroutineScope {
                val componentsDeferred = async { repository.loadAppComponents(packageName) }
                val appOpsDeferred = async { engine.readAppOps(packageName) }
                val components = componentsDeferred.await()
                val appOps = appOpsDeferred.await()
                _uiState.update {
                    it.copy(
                        inspectedComponents = components,
                        inspectedAppOps = appOps,
                        isLoadingAppDetails = false,
                    )
                }
            }
        }
    }

    fun hibernatePackages(packages: List<String>, ignoreProtection: Boolean = false) {
        if (packages.isEmpty() || _uiState.value.isHibernating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isHibernating = true) }
            val result = engine.hibernate(packages, ignoreRecentsAndAudioProtection = ignoreProtection)
            _events.tryEmit(result.message)
            _uiState.update { it.copy(isHibernating = false) }
            refreshAll()
        }
    }

    fun updateAppPolicy(packageName: String, transform: (PerAppPolicy) -> PerAppPolicy) {
        preferences.updateAppPolicy(packageName, transform)
    }

    fun updateBatchPolicy(packages: List<String>, transform: (PerAppPolicy) -> PerAppPolicy) {
        preferences.updateBatchAppPolicies(packages, transform)
        _events.tryEmit("Updated policy for ${packages.size} app(s).")
    }

    fun applyStandbyBucket(packages: List<String>, bucket: StandbyBucket) {
        if (packages.isEmpty()) return
        viewModelScope.launch {
            engine.setStandbyBucket(packages, bucket)
                .onSuccess {
                    _events.tryEmit("Set standby bucket to ${bucket.label} for ${packages.size} app(s).")
                    refreshAll()
                }
                .onFailure { _events.tryEmit("Failed: ${it.message}") }
        }
    }

    fun applyAppOp(
        packages: List<String>,
        restriction: AppOpRestriction,
        blocked: Boolean,
        inspectedPackage: String? = null,
    ) {
        if (packages.isEmpty()) return
        viewModelScope.launch {
            engine.setAppOp(packages, restriction, blocked)
                .onSuccess {
                    val verb = if (blocked) "Blocked" else "Allowed"
                    _events.tryEmit("$verb ${restriction.label} for ${packages.size} app(s).")
                    if (inspectedPackage != null) {
                        val refreshed = engine.readAppOps(inspectedPackage)
                        _uiState.update { it.copy(inspectedAppOps = refreshed) }
                    }
                }
                .onFailure { _events.tryEmit("Failed: ${it.message}") }
        }
    }

    fun toggleComponent(packageName: String, className: String, enable: Boolean) {
        viewModelScope.launch {
            engine.setComponentEnabled(packageName, className, enable)
                .onSuccess {
                    val components = repository.loadAppComponents(packageName)
                    _uiState.update { it.copy(inspectedComponents = components) }
                }
                .onFailure { _events.tryEmit("Component cut-off failed: ${it.message}") }
        }
    }

    fun bulkToggleInspectedComponents(
        packageName: String,
        components: List<AppComponentInfo>,
        enable: Boolean,
    ) {
        if (components.isEmpty()) return
        viewModelScope.launch {
            val pairs = components.map { packageName to it.className }
            engine.setComponentsBatchEnabled(pairs, enable)
                .onSuccess { count ->
                    val verb = if (enable) "Restored" else "Cut off"
                    _events.tryEmit("$verb $count wake-up component(s).")
                    val updated = repository.loadAppComponents(packageName)
                    _uiState.update { it.copy(inspectedComponents = updated) }
                }
                .onFailure { _events.tryEmit("Batch cut-off failed: ${it.message}") }
        }
    }

    fun batchCutOffWakeupCategories(
        packages: List<String>,
        categories: Set<WakeupCategory>,
        enable: Boolean,
    ) {
        if (packages.isEmpty() || categories.isEmpty()) return
        viewModelScope.launch {
            val pairs = mutableListOf<Pair<String, String>>()
            for (pkg in packages) {
                val comps = repository.loadAppComponents(pkg)
                comps.filter { it.category in categories && it.isEnabled != enable }
                    .forEach { pairs += pkg to it.className }
            }
            if (pairs.isEmpty()) {
                _events.tryEmit("All matching wake-up services are already ${if (enable) "enabled" else "cut off"}.")
                return@launch
            }
            engine.setComponentsBatchEnabled(pairs, enable)
                .onSuccess { count ->
                    val verb = if (enable) "Restored" else "Cut off"
                    _events.tryEmit("$verb $count wake-up service(s) across ${packages.size} app(s).")
                }
                .onFailure { _events.tryEmit("Batch cut-off failed: ${it.message}") }
        }
    }

    // --- Settings mutators ---

    fun setWorkingMode(mode: WorkingMode) {
        logStore.info("SETTINGS", "Working mode changed to ${mode.displayName}.")
        preferences.setWorkingMode(mode)
        refreshAll(probeRoot = mode == WorkingMode.ROOT)
    }

    fun setShallowHibernation(enabled: Boolean) = preferences.setShallowHibernation(enabled)
    fun setDefaultTargetBucket(bucket: StandbyBucket) = preferences.setDefaultTargetBucket(bucket)
    fun setExcludeRecentsApps(enabled: Boolean) = preferences.setExcludeRecentsApps(enabled)
    fun setProtectActiveAudioApps(enabled: Boolean) = preferences.setProtectActiveAudioApps(enabled)
    fun setFinerMediaPlaybackDetection(enabled: Boolean) = preferences.setFinerMediaPlaybackDetection(enabled)
    fun setExcludeForegroundApp(enabled: Boolean) = preferences.setExcludeForegroundApp(enabled)
    fun setIgnoreBackgroundFree(enabled: Boolean) = preferences.setIgnoreBackgroundFree(enabled)
    fun setWakeupTrackerEnabled(enabled: Boolean) = preferences.setWakeupTrackerEnabled(enabled)
    fun setEnhancedWakeupSources(enabled: Boolean) = preferences.setEnhancedWakeupSources(enabled)

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

    fun setAutoHibernationScreenOffOnly(enabled: Boolean) = preferences.setAutoHibernationScreenOffOnly(enabled)
    fun setAutoHibernateAllUserApps(enabled: Boolean) = preferences.setAutoHibernateAllUserApps(enabled)
    fun setQuickActionNotificationEnabled(enabled: Boolean) = preferences.setQuickActionNotificationEnabled(enabled)
    fun setGeekExtrasEnabled(enabled: Boolean) = preferences.setGeekExtrasEnabled(enabled)
    fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode)
    fun setDynamicColor(enabled: Boolean) = preferences.setDynamicColor(enabled)
    fun setForceMaxRefreshRate(enabled: Boolean) = preferences.setForceMaxRefreshRate(enabled)
    fun setLiquidGlassBottomBar(enabled: Boolean) = preferences.setLiquidGlassBottomBar(enabled)
    fun setIncludeSystemApps(include: Boolean) = preferences.setIncludeSystemApps(include)

    fun requestShizukuPermission() {
        runCatching {
            when {
                !Shizuku.pingBinder() -> {
                    logStore.warn("SHIZUKU", "Shizuku binder not reachable.")
                    _events.tryEmit("Shizuku service is not running yet.")
                }
                Shizuku.isPreV11() -> {
                    _events.tryEmit("Shizuku v11 or newer is required.")
                }
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                    logStore.info("SHIZUKU", "Shizuku permission confirmed granted.")
                    _events.tryEmit("Shizuku permission already granted.")
                    refreshAll()
                }
                else -> {
                    logStore.info("SHIZUKU", "Requesting Shizuku permission dialog.")
                    Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                }
            }
        }.onFailure {
            logStore.error("SHIZUKU", "Error requesting Shizuku permission: ${it.message}")
            _events.tryEmit("Unable to request Shizuku permission: ${it.message}")
        }
    }

    fun requestRootPermission() {
        viewModelScope.launch {
            val settings = preferences.state.value
            val env = repository.inspectEnvironment(
                preferredMode = settings.workingMode,
                probeRoot = true,
                previousRootVerified = settings.rootVerifiedPreviously,
            )
            if (env.rootAvailable) {
                preferences.setRootVerifiedPreviously(true)
                _events.tryEmit("Root access verified (uid=0).")
            } else {
                preferences.setRootVerifiedPreviously(false)
                _events.tryEmit("Root access denied or unavailable.")
            }
            _uiState.update { it.copy(environment = env) }
        }
    }

    fun clearLogs() = logStore.clear()

    fun formatAllRawLogs(): String = AppLogStore.formatAllRawLogs(logs.value)

    fun clearWakeupLogsForPackage(packageName: String) {
        preferences.clearWakeupRecords(packageName)
        _events.tryEmit("Cleared wake-up history for $packageName.")
    }

    fun exportBackupJson(): String = preferences.exportBackupJson()

    fun importBackupJson(rawJson: String) {
        preferences.importBackupJson(rawJson)
            .onSuccess { count ->
                logStore.info("BACKUP", "Imported backup snapshot ($count configured app rule(s)).")
                _events.tryEmit("Imported $count app rule(s) & settings.")
            }
            .onFailure { err ->
                logStore.error("BACKUP", "Backup import failed: ${err.message}")
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
