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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenify.reborn.model.ThemeMode
import com.greenify.reborn.ui.GreenifyViewModel
import com.greenify.reborn.ui.analyzer.AnalyzerScreen
import com.greenify.reborn.ui.home.HibernationScreen
import com.greenify.reborn.ui.settings.SettingsScreen
import com.greenify.reborn.ui.theme.GreenifyRebornTheme
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

private enum class TopTab(val label: String, val icon: ImageVector) {
    HIBERNATION("Hibernation", Icons.Outlined.Eco),
    ANALYZER("Analyzer", Icons.Outlined.Science),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

class MainActivity : ComponentActivity() {

    private val viewModel: GreenifyViewModel by viewModels()

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
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = when (state.settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF),
                        android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b),
                    ) { darkTheme },
                )
                onDispose {}
            }
            GreenifyRebornTheme(
                themeMode = state.settings.themeMode,
                dynamicColor = state.settings.dynamicColor,
            ) {
                GreenifyRebornShell(
                    viewModel = viewModel,
                    onOpenAppInfo = { pkg -> openSystemAppInfo(pkg) },
                    onOpenUsageAccess = { openUsageAccessSettings() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAll()
    }

    override fun onDestroy() {
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
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }.onFailure {
            viewModel.emitMessage("Unable to open Usage Access settings.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GreenifyRebornShell(
    viewModel: GreenifyViewModel,
    onOpenAppInfo: (String) -> Unit,
    onOpenUsageAccess: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by remember { mutableStateOf(TopTab.HIBERNATION) }
    val context = androidx.compose.ui.platform.LocalContext.current

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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentTab) {
                            TopTab.HIBERNATION -> "Siesta"
                            TopTab.ANALYZER -> "App Analyzer"
                            TopTab.SETTINGS -> "Settings"
                        },
                    )
                },
                actions = {
                    if (currentTab != TopTab.SETTINGS) {
                        IconButton(onClick = { viewModel.refreshAll() }) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh app list",
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibility(visible = currentTab == TopTab.HIBERNATION && state.greenifiedApps.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.hibernateAll() },
                    icon = {
                        if (state.isHibernating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Outlined.Bedtime, contentDescription = null)
                        }
                    },
                    text = {
                        Text(if (state.isHibernating) "Hibernating..." else "Hibernate All")
                    },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                TopTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (currentTab) {
            TopTab.HIBERNATION -> HibernationScreen(
                state = state,
                onQueryChange = viewModel::updateHomeQuery,
                onRequestShizuku = viewModel::requestShizukuPermission,
                onOpenSettings = { currentTab = TopTab.SETTINGS },
                onOpenAnalyzer = { currentTab = TopTab.ANALYZER },
                onHibernateSingle = { viewModel.hibernateSingle(it.packageName) },
                onRemoveApp = { viewModel.toggleGreenified(it) },
                onOpenAppInfo = { onOpenAppInfo(it.packageName) },
                modifier = contentModifier,
            )

            TopTab.ANALYZER -> AnalyzerScreen(
                state = state,
                onQueryChange = viewModel::updateAnalyzerQuery,
                onFilterChange = viewModel::updateAnalyzerFilter,
                onIncludeSystemToggle = viewModel::setIncludeSystemApps,
                onToggleGreenified = viewModel::toggleGreenified,
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
                onRequestShizuku = viewModel::requestShizukuPermission,
                onOpenUsageAccessSettings = onOpenUsageAccess,
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onExportJson = { exportLauncher.launch("siesta-backup.json") },
                onImportJson = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                modifier = contentModifier,
            )
        }
    }
}
