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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenify.reborn.model.ThemeMode
import com.greenify.reborn.ui.GreenifyViewModel
import com.greenify.reborn.ui.components.LiquidBottomTabs
import com.greenify.reborn.ui.components.LiquidTabItem
import com.greenify.reborn.ui.components.recordLayerBackdrop
import com.greenify.reborn.ui.components.rememberLayerBackdrop
import com.greenify.reborn.ui.home.HibernationScreen
import com.greenify.reborn.ui.settings.SettingsScreen
import com.greenify.reborn.ui.theme.GreenifyRebornTheme
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

private enum class TopTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Bedtime),
    SETTINGS("Settings", Icons.Outlined.Settings),
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
        enableEdgeToEdge()
        runCatching { Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener) }
        runCatching { Shizuku.addBinderDeadListener(shizukuBinderDeadListener) }
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = state.settings.themeMode.resolveDarkTheme(isSystemInDarkTheme())
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                )
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
        viewModel.refreshAll()
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
    val liquidTabs = remember {
        TopTab.entries.map { LiquidTabItem(label = it.label, icon = it.icon) }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 86.dp),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = currentTab == TopTab.HOME && state.managedApps.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.hibernateAll() },
                    shape = RoundedCornerShape(50),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(bottom = 82.dp),
                    icon = {
                        if (state.isHibernating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Outlined.Bedtime, contentDescription = null)
                        }
                    },
                    text = {
                        Text(if (state.isHibernating) "Putting to sleep..." else "Hibernate All")
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .recordLayerBackdrop(screenBackdrop)
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding(),
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
                            onRefresh = viewModel::refreshAll,
                            onQueryChange = viewModel::updateHomeQuery,
                            onFilterChange = viewModel::updateHomeFilter,
                            onIncludeSystemToggle = viewModel::setIncludeSystemApps,
                            onRequestShizuku = viewModel::requestShizukuPermission,
                            onOpenShizukuApp = onOpenShizukuApp,
                            onOpenSettings = { currentTab = TopTab.SETTINGS },
                            onHibernateSingle = viewModel::hibernateSingle,
                            onToggleManaged = viewModel::toggleManaged,
                            onOpenAppInfo = { onOpenAppInfo(it.packageName) },
                            modifier = contentModifier,
                        )

                        TopTab.SETTINGS -> SettingsScreen(
                            state = state,
                            onSelectWorkingMode = viewModel::setWorkingMode,
                            onShallowChange = viewModel::setShallowHibernation,
                            onAutoHibernationChange = viewModel::setAutoHibernation,
                            onAutoIntervalChange = viewModel::setAutoHibernationInterval,
                            onSelectThemeMode = viewModel::setThemeMode,
                            onDynamicColorChange = viewModel::setDynamicColor,
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
        }
    }
}
