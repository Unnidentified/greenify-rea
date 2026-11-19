package com.greenify.reborn

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenify.reborn.model.ResolvedBackend
import com.greenify.reborn.ui.GreenifyViewModel
import com.greenify.reborn.ui.components.AppIconCache
import com.greenify.reborn.ui.components.FlowyBottomSheet
import com.greenify.reborn.ui.components.LiquidBottomTabs
import com.greenify.reborn.ui.components.LiquidTabItem
import com.greenify.reborn.ui.components.ProgressiveAcrylicStatusBar
import com.greenify.reborn.ui.components.recordLayerBackdrop
import com.greenify.reborn.ui.components.rememberLayerBackdrop
import com.greenify.reborn.ui.home.AppDetailSheet
import com.greenify.reborn.ui.home.BatchAdjustmentsSheet
import com.greenify.reborn.ui.home.HibernationScreen
import com.greenify.reborn.ui.settings.RawLogsSheet
import com.greenify.reborn.ui.settings.SettingsScreen
import com.greenify.reborn.ui.theme.GreenifyRebornTheme
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

private enum class TopTab(val label: String) {
    HOME("Home"),
    SETTINGS("Settings"),
}

class MainActivity : ComponentActivity() {

    private val viewModel: GreenifyViewModel by viewModels()

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        viewModel.refreshAll()
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        viewModel.refreshAll()
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == GreenifyViewModel.SHIZUKU_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                viewModel.emitMessage("Shizuku permission granted.")
            } else {
                viewModel.emitMessage("Shizuku permission denied.")
            }
            viewModel.refreshAll()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        runCatching { Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener) }
        runCatching { Shizuku.addBinderDeadListener(shizukuBinderDeadListener) }
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = state.settings.themeMode.resolveDarkTheme(isSystemInDarkTheme())
            LaunchedEffect(state.settings.forceMaxRefreshRate) {
                applyDisplayRefreshRate(state.settings.forceMaxRefreshRate)
            }
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                )
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
                onDispose {}
            }
            GreenifyRebornTheme(
                themeMode = state.settings.themeMode,
                dynamicColor = state.settings.dynamicColor,
            ) {
                GreenifyReaShell(
                    viewModel = viewModel,
                    darkTheme = darkTheme,
                    onOpenAppInfo = { pkg -> openSystemAppInfo(pkg) },
                    onOpenUsageAccess = { openUsageAccessSettings() },
                    onOpenShizukuApp = { openShizukuApp() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyDisplayRefreshRate(viewModel.uiState.value.settings.forceMaxRefreshRate)
        viewModel.refreshAll(force = false)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppIconCache.trim(level)
    }

    private fun applyDisplayRefreshRate(forceMax: Boolean) {
        runCatching {
            val currentDisplay = display ?: return
            val currentMode = currentDisplay.mode
            val attrs = window.attributes
            if (forceMax) {
                val bestMode = currentDisplay.supportedModes
                    .filter {
                        it.physicalWidth == currentMode.physicalWidth &&
                            it.physicalHeight == currentMode.physicalHeight
                    }
                    .maxByOrNull { it.refreshRate }
                    ?: currentDisplay.supportedModes.maxByOrNull { it.refreshRate }
                if (bestMode != null) {
                    attrs.preferredDisplayModeId = bestMode.modeId
                    attrs.preferredRefreshRate = bestMode.refreshRate
                }
            } else {
                attrs.preferredDisplayModeId = 0
                attrs.preferredRefreshRate = 0f
            }
            window.attributes = attrs
        }
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener) }
        runCatching { Shizuku.removeBinderDeadListener(shizukuBinderDeadListener) }
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        super.onDestroy()
    }

    private fun openSystemAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun openUsageAccessSettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                },
            )
        }.recoverCatching {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }.onFailure {
            viewModel.emitMessage("Unable to open Usage Access settings.")
        }
    }

    private fun openShizukuApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            viewModel.emitMessage("Shizuku app is not installed.")
        }
    }
}

@Composable
private fun GreenifyReaShell(
    viewModel: GreenifyViewModel,
    darkTheme: Boolean,
    onOpenAppInfo: (String) -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenShizukuApp: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by remember { mutableStateOf(TopTab.HOME) }
    val pagerState = rememberPagerState(initialPage = currentTab.ordinal, pageCount = { TopTab.entries.size })
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(currentTab) {
        if (pagerState.currentPage != currentTab.ordinal) {
            pagerState.animateScrollToPage(currentTab.ordinal)
        }
    }
    val screenBackdrop = rememberLayerBackdrop()
    val homeLeafIcon = ImageVector.vectorResource(R.drawable.ic_tile_leaf)
    val liquidTabs = remember(homeLeafIcon) {
        listOf(
            LiquidTabItem(label = TopTab.HOME.label, icon = homeLeafIcon),
            LiquidTabItem(label = TopTab.SETTINGS.label, icon = Icons.Outlined.Settings),
        )
    }

    var inspectedPackage by remember { mutableStateOf<String?>(null) }
    var showBatchSheet by remember { mutableStateOf(false) }
    var showLogsSheet by remember { mutableStateOf(false) }
    val hasPrivilegedBackend = state.environment.resolvedBackend != ResolvedBackend.NON_ROOT
    val selectedApps = remember(state.allApps, state.selectedPackages) {
        state.allApps.filter { it.packageName in state.selectedPackages }
    }

    LaunchedEffect(inspectedPackage) {
        inspectedPackage?.let { viewModel.loadAppDetails(it) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.emitMessage(
            if (granted) "Notification permission granted." else "Notification permission denied.",
        )
        viewModel.refreshAll()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(viewModel.exportBackupJson().toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                viewModel.emitMessage("Backup exported.")
            }.onFailure {
                viewModel.emitMessage("Export failed: ${it.message}")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                }.orEmpty()
            }.onSuccess { raw ->
                viewModel.importBackupJson(raw)
            }.onFailure {
                viewModel.emitMessage("Unable to read backup: ${it.message}")
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            launch {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    // No FloatingActionButton anywhere per UX requirement
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .recordLayerBackdrop(screenBackdrop)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                val contentModifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding() * 0f)
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (TopTab.entries[page]) {
                        TopTab.HOME -> HibernationScreen(
                            state = state,
                            onRefresh = { userTriggered -> viewModel.refreshAll(userTriggered = userTriggered) },
                            onQueryChange = viewModel::updateHomeQuery,
                            onToggleFilter = viewModel::toggleFilter,
                            onEnterBatchMode = viewModel::enterBatchMode,
                            onExitBatchMode = viewModel::exitBatchMode,
                            onToggleBatchSelection = viewModel::toggleBatchSelection,
                            onToggleSelectAllFiltered = viewModel::toggleSelectAllFiltered,
                            onHibernatePackages = viewModel::hibernatePackages,
                            onInspectPackage = { pkg -> inspectedPackage = pkg },
                            onOpenBatchSheet = { showBatchSheet = true },
                            onRequestShizuku = viewModel::requestShizukuPermission,
                            onOpenShizukuApp = onOpenShizukuApp,
                            onOpenSettings = { currentTab = TopTab.SETTINGS },
                            onEmitMessage = viewModel::emitMessage,
                            modifier = contentModifier,
                        )

                        TopTab.SETTINGS -> SettingsScreen(
                            state = state,
                            logs = logs,
                            onOpenLogsSheet = { showLogsSheet = true },
                            onSelectWorkingMode = viewModel::setWorkingMode,
                            onShallowChange = viewModel::setShallowHibernation,
                            onTargetBucketChange = viewModel::setDefaultTargetBucket,
                            onExcludeRecentsChange = viewModel::setExcludeRecentsApps,
                            onProtectAudioChange = viewModel::setProtectActiveAudioApps,
                            onFinerMediaChange = viewModel::setFinerMediaPlaybackDetection,
                            onExcludeForegroundChange = viewModel::setExcludeForegroundApp,
                            onIgnoreBackgroundFreeChange = viewModel::setIgnoreBackgroundFree,
                            onIncludeSystemAppsChange = viewModel::setIncludeSystemApps,
                            onWakeupTrackerChange = viewModel::setWakeupTrackerEnabled,
                            onEnhancedWakeupSourcesChange = viewModel::setEnhancedWakeupSources,
                            onAutoHibernationChange = viewModel::setAutoHibernation,
                            onAutoIntervalChange = viewModel::setAutoHibernationInterval,
                            onAutoScreenOffOnlyChange = viewModel::setAutoHibernationScreenOffOnly,
                            onAutoHibernateAllUserAppsChange = viewModel::setAutoHibernateAllUserApps,
                            onQuickActionNotifChange = viewModel::setQuickActionNotificationEnabled,
                            onGeekExtrasChange = viewModel::setGeekExtrasEnabled,
                            onSelectThemeMode = viewModel::setThemeMode,
                            onDynamicColorChange = viewModel::setDynamicColor,
                            onForceMaxRefreshRateChange = viewModel::setForceMaxRefreshRate,
                            onLiquidGlassBottomBarChange = viewModel::setLiquidGlassBottomBar,
                            onRequestShizuku = viewModel::requestShizukuPermission,
                            onOpenShizukuApp = onOpenShizukuApp,
                            onRequestRoot = viewModel::requestRootPermission,
                            onOpenUsageAccessSettings = onOpenUsageAccess,
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onExportJson = { exportLauncher.launch("greenify-rea-backup.json") },
                            onImportJson = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = contentModifier,
                        )
                    }
                }
            }

            // Progressive Acrylic blur below the status bar so scrolling content blurs away cleanly
            ProgressiveAcrylicStatusBar(
                backdrop = screenBackdrop,
                isDark = darkTheme,
                enableBlur = state.settings.liquidGlassBottomBar,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // Liquid Glass floating bottom bar with AGSL lens refraction, chromatic dispersion & damped drag
            LiquidBottomTabs(
                selectedIndex = currentTab.ordinal,
                onSelectIndex = { currentTab = TopTab.entries[it] },
                backdrop = screenBackdrop,
                tabs = liquidTabs,
                isDark = darkTheme,
                enableBlur = state.settings.liquidGlassBottomBar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )

            // In-window flowy bottom sheets running at native refresh rate with transparent nav bar
            val inspectedAppTarget = remember(inspectedPackage, state.allApps) {
                inspectedPackage?.let { pkg -> state.allApps.find { it.packageName == pkg } }
            }
            FlowyBottomSheet(
                targetState = inspectedAppTarget,
                onDismissRequest = { inspectedPackage = null },
            ) { currentApp ->
                AppDetailSheet(
                    app = currentApp,
                    components = state.inspectedComponents,
                    appOpsState = state.inspectedAppOps,
                    isLoadingDetails = state.isLoadingAppDetails,
                    hasPrivilegedBackend = hasPrivilegedBackend,
                    geekExtrasEnabled = state.settings.geekExtrasEnabled,
                    onHibernate = { ignoreProtection ->
                        viewModel.hibernatePackages(listOf(currentApp.packageName), ignoreProtection)
                        inspectedPackage = null
                    },
                    onUpdatePolicy = { transform -> viewModel.updateAppPolicy(currentApp.packageName, transform) },
                    onSetStandbyBucket = { bucket ->
                        viewModel.applyStandbyBucket(listOf(currentApp.packageName), bucket)
                    },
                    onSetAppOp = { op, blocked ->
                        viewModel.applyAppOp(listOf(currentApp.packageName), op, blocked, currentApp.packageName)
                    },
                    onToggleComponent = { comp, enable ->
                        viewModel.toggleComponent(currentApp.packageName, comp.className, enable)
                    },
                    onBulkToggleComponents = { comps, enable ->
                        viewModel.bulkToggleInspectedComponents(currentApp.packageName, comps, enable)
                    },
                    onClearWakeupLogs = { viewModel.clearWakeupLogsForPackage(currentApp.packageName) },
                    onOpenAppInfo = { onOpenAppInfo(currentApp.packageName) },
                    onEmitMessage = viewModel::emitMessage,
                )
            }

            FlowyBottomSheet(
                targetState = if (showBatchSheet) selectedApps.takeIf { it.isNotEmpty() } else null,
                onDismissRequest = { showBatchSheet = false },
            ) { batchApps ->
                val selectedPkgList = batchApps.map { it.packageName }
                BatchAdjustmentsSheet(
                    selectedApps = batchApps,
                    hasPrivilegedBackend = hasPrivilegedBackend,
                    onHibernateSelected = { ignoreProtection ->
                        viewModel.hibernatePackages(selectedPkgList, ignoreProtection)
                        showBatchSheet = false
                    },
                    onUpdateBatchPolicy = { transform -> viewModel.updateBatchPolicy(selectedPkgList, transform) },
                    onSetBatchStandbyBucket = { bucket -> viewModel.applyStandbyBucket(selectedPkgList, bucket) },
                    onSetBatchAppOp = { op, blocked -> viewModel.applyAppOp(selectedPkgList, op, blocked, null) },
                    onBatchCutOffCategories = { categories, enable ->
                        viewModel.batchCutOffWakeupCategories(selectedPkgList, categories, enable)
                    },
                    onEmitMessage = viewModel::emitMessage,
                )
            }

            FlowyBottomSheet(
                targetState = if (showLogsSheet) Unit else null,
                onDismissRequest = { showLogsSheet = false },
            ) {
                RawLogsSheet(
                    logs = logs,
                    formatRawDump = viewModel::formatAllRawLogs,
                    onClearLogs = viewModel::clearLogs,
                    onCopied = viewModel::emitMessage,
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 86.dp),
            )
        }
    }
}
