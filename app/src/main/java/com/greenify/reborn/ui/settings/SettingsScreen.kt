package com.greenify.reborn.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenify.reborn.BuildConfig
import com.greenify.reborn.model.EngineLogEntry
import com.greenify.reborn.model.StandbyBucket
import com.greenify.reborn.model.ThemeMode
import com.greenify.reborn.model.WorkingMode
import com.greenify.reborn.ui.MainUiState
import com.greenify.reborn.ui.components.GreenifySwitch

@Composable
fun SettingsScreen(
    state: MainUiState,
    logs: List<EngineLogEntry>,
    onOpenLogsSheet: () -> Unit,
    onSelectWorkingMode: (WorkingMode) -> Unit,
    onShallowChange: (Boolean) -> Unit,
    onTargetBucketChange: (StandbyBucket) -> Unit,
    onExcludeRecentsChange: (Boolean) -> Unit,
    onProtectAudioChange: (Boolean) -> Unit,
    onFinerMediaChange: (Boolean) -> Unit,
    onExcludeForegroundChange: (Boolean) -> Unit,
    onIgnoreBackgroundFreeChange: (Boolean) -> Unit,
    onIncludeSystemAppsChange: (Boolean) -> Unit,
    onWakeupTrackerChange: (Boolean) -> Unit,
    onEnhancedWakeupSourcesChange: (Boolean) -> Unit,
    onAutoHibernationChange: (Boolean) -> Unit,
    onAutoIntervalChange: (Int) -> Unit,
    onAutoScreenOffOnlyChange: (Boolean) -> Unit,
    onAutoHibernateAllUserAppsChange: (Boolean) -> Unit,
    onQuickActionNotifChange: (Boolean) -> Unit,
    onGeekExtrasChange: (Boolean) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onForceMaxRefreshRateChange: (Boolean) -> Unit,
    onLiquidGlassBottomBarChange: (Boolean) -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onRequestRoot: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showModeDialog by remember { mutableStateOf(false) }
    var showBucketDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = statusBarTop + 16.dp,
            bottom = 132.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }

        item {
            SectionTitle("Privileges & Dual Authorization")
            MiuixGroupCard {
                PrivilegeRow(
                    title = "Shizuku Service",
                    detail = when {
                        state.environment.shizukuPermissionGranted -> "Connected and authorized"
                        state.environment.shizukuRunning -> "Running — tap Grant to authorize"
                        state.environment.shizukuInstalled -> "Installed — start Shizuku via Wireless Debugging"
                        else -> "Not installed (recommended for non-root)"
                    },
                    ok = state.environment.shizukuPermissionGranted,
                    actionLabel = when {
                        state.environment.shizukuPermissionGranted -> "Re-check"
                        state.environment.shizukuRunning -> "Grant"
                        state.environment.shizukuInstalled -> "Open"
                        else -> "Check"
                    },
                    onAction = if (state.environment.shizukuRunning || !state.environment.shizukuInstalled) {
                        onRequestShizuku
                    } else {
                        onOpenShizukuApp
                    },
                )
                InsetDivider()
                PrivilegeRow(
                    title = "Root Access (su)",
                    detail = when {
                        state.environment.rootAvailable -> "Authorized (uid=0 via Magisk / KernelSU / APatch)"
                        state.environment.rootBinaryDetected -> "su binary found — tap Grant to request superuser"
                        else -> "Tap Grant to request superuser (if granted in manager)"
                    },
                    ok = state.environment.rootAvailable,
                    actionLabel = if (state.environment.rootAvailable) "Re-verify" else "Grant",
                    onAction = onRequestRoot,
                )
                InsetDivider()
                PrivilegeRow(
                    title = "Usage Access (Standby & Wake-ups)",
                    detail = if (state.environment.usageAccessGranted) "Granted" else "Grant to read Standby Buckets & wake events",
                    ok = state.environment.usageAccessGranted,
                    actionLabel = if (!state.environment.usageAccessGranted) "Open" else null,
                    onAction = onOpenUsageAccessSettings,
                )
                InsetDivider()
                PrivilegeRow(
                    title = "Notifications",
                    detail = if (state.environment.notificationsGranted) "Allowed" else "Optional for quick-action & nap alerts",
                    ok = state.environment.notificationsGranted,
                    actionLabel = if (!state.environment.notificationsGranted) "Allow" else null,
                    onAction = onRequestNotifications,
                )
            }
        }

        item {
            SectionTitle("Working Mode & Nap Engine")
            MiuixGroupCard {
                MiuixActionRow(
                    title = "Working Mode",
                    subtitle = "${state.settings.workingMode.displayName} (Active: ${state.environment.resolvedBackend.displayName})",
                    onClick = { showModeDialog = true },
                )
                InsetDivider()
                MiuixSwitchRow(
                    title = "Default to Shallow Hibernation",
                    subtitle = "Restrict standby bucket without force-stopping processes",
                    checked = state.settings.shallowHibernation,
                    onCheckedChange = onShallowChange,
                )
                InsetDivider()
                MiuixActionRow(
                    title = "Target Standby Bucket",
                    subtitle = "${state.settings.defaultTargetBucket.label} (applied during hibernation)",
                    onClick = { showBucketDialog = true },
                )
                InsetDivider()
                MiuixSwitchRow(
                    title = "Always Ignore Background-Free",
                    subtitle = "Skip apps that are already stopped / background-free during auto-naps",
                    checked = state.settings.ignoreBackgroundFree,
                    onCheckedChange = onIgnoreBackgroundFreeChange,
                )
                InsetDivider()
                MiuixSwitchRow(
                    title = "Allow Greenifying System Apps",
                    subtitle = "Expose non-critical system packages in filters & allow hibernation",
                    checked = state.settings.includeSystemApps,
                    onCheckedChange = onIncludeSystemAppsChange,
                )
            }
        }

        item {
            SectionTitle("Smart Protection (Recents & Audio)")
            MiuixGroupCard {
                MiuixSwitchRow(
                    title = "Exclude Apps in Recents",
                    subtitle = "Protect cached/background apps currently open on your Recents screen",
                    checked = state.settings.excludeRecentsApps,
                    onCheckedChange = onExcludeRecentsChange,
                )
                InsetDivider()
                MiuixSwitchRow(
                    title = "Protect Active Audio Players",
                    subtitle = "Never hibernate music/media apps while playing or active in Recents",
                    checked = state.settings.protectActiveAudioApps,
                    onCheckedChange = onProtectAudioChange,
                )
                InsetDivider()
                MiuixSwitchRow(
                    title = "Finer Media Playback Detection",
                    subtitle = "Inspect PlaybackState & AudioPlaybackConfiguration to detect active playback",
                    checked = state.settings.finerMediaPlaybackDetection,
                    onCheckedChange = onFinerMediaChange,
                )
                InsetDivider()
                MiuixSwitchRow(
                    title = "Exclude Foreground App",
                    subtitle = "Smart Hibernation: skip whichever app is currently on screen",
                    checked = state.settings.excludeForegroundApp,
                    onCheckedChange = onExcludeForegroundChange,
                )
            }
        }

        item {
            SectionTitle("Scheduled Naps & Automation")
            MiuixGroupCard {
                MiuixSwitchRow(
                    title = "Scheduled Naps",
                    subtitle = "Periodic background naps via WorkManager without AlarmManager wake-locks",
                    checked = state.settings.autoHibernationEnabled,
                    onCheckedChange = onAutoHibernationChange,
                )
                if (state.settings.autoHibernationEnabled) {
                    InsetDivider()
                    MiuixActionRow(
                        title = "Nap Interval",
                        subtitle = "Every ${state.settings.autoHibernationIntervalMinutes} minutes",
                        onClick = { showIntervalDialog = true },
                    )
                    InsetDivider()
                    MiuixSwitchRow(
                        title = "Only When Screen Is Off",
                        subtitle = "Defer scheduled hibernation if the device screen is interactive",
                        checked = state.settings.autoHibernationScreenOffOnly,
                        onCheckedChange = onAutoScreenOffOnlyChange,
                    )
                }
                InsetDivider()
                MiuixSwitchRow(
                    title = "Auto-Hibernate All User Apps",
                    subtitle = "Target all non-exempt user apps in scheduled/tile runs instead of only marked apps",
                    checked = state.settings.autoHibernateAllUserApps,
                    onCheckedChange = onAutoHibernateAllUserAppsChange,
                )
                InsetDivider()
                MiuixSwitchRow(
                    title = "Quick Action Notification",
                    subtitle = "Post a status notification with Hibernate Now action after scheduled runs",
                    checked = state.settings.quickActionNotificationEnabled,
                    onCheckedChange = onQuickActionNotifChange,
                )
            }
        }

        item {
            SectionTitle("Wake-up Diagnostics & Logs")
            MiuixGroupCard {
                MiuixActionRow(
                    title = "Raw Engine Logs",
                    subtitle = "${logs.size} recorded event(s) — view full commands, exits & copy all",
                    onClick = onOpenLogsSheet,
                )
                InsetDivider()
                MiuixSwitchRow(
                    title = "Wake-up Tracker",
                    subtitle = "Record when napped apps wake up and display wake logs per app",
                    checked = state.settings.wakeupTrackerEnabled,
                    onCheckedChange = onWakeupTrackerChange,
                )
                InsetDivider()
                MiuixSwitchRow(
                    title = "Enhanced Wake-up Sources",
                    subtitle = "Correlate UsageStats foreground/service events & exit reasons",
                    checked = state.settings.enhancedWakeupSources,
                    onCheckedChange = onEnhancedWakeupSourcesChange,
                )
                InsetDivider()
                MiuixSwitchRow(
                    title = "Extras for Geek",
                    subtitle = "Show UID, targetSdk, and technical diagnostics in app rows & sheets",
                    checked = state.settings.geekExtrasEnabled,
                    onCheckedChange = onGeekExtrasChange,
                )
            }
        }

        item {
            SectionTitle("Appearance")
            MiuixGroupCard {
                MiuixActionRow(
                    title = "Theme",
                    subtitle = state.settings.themeMode.displayName,
                    onClick = { showThemeDialog = true },
                )
                InsetDivider()
                MiuixSwitchRow(
                    title = "Dynamic Accent (Monet)",
                    subtitle = "Tint primary accents from wallpaper while keeping clean surfaces",
                    checked = state.settings.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                )
                InsetDivider()
                MiuixSwitchRow(
                    title = "Force Max Refresh Rate",
                    subtitle = "Lock the display to its highest supported Hz for smoother scrolling",
                    checked = state.settings.forceMaxRefreshRate,
                    onCheckedChange = onForceMaxRefreshRateChange,
                )
                InsetDivider()
                MiuixSwitchRow(
                    title = "Liquid Glass Bottom Bar",
                    subtitle = "Real-time backdrop blur, lens refraction, and damped droplet navigation",
                    checked = state.settings.liquidGlassBottomBar,
                    onCheckedChange = onLiquidGlassBottomBarChange,
                )
            }
        }

        item {
            SectionTitle("Backup & Quick Actions")
            MiuixGroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Export or restore your per-app rules, Recents/Audio protections, and engine settings as portable JSON. You can also add the \"Hibernate Now\" tile to Quick Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onExportJson,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = onImportJson,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import JSON")
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("About greenify-rea")
            MiuixGroupCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "greenify-rea v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Open-source rewrite • Apache License 2.0 • Zero telemetry or Firebase tracking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Working Mode") },
            text = {
                Column {
                    WorkingMode.entries.forEach { mode ->
                        val readinessText = when (mode) {
                            WorkingMode.AUTO -> when {
                                state.environment.shizukuPermissionGranted && state.environment.rootAvailable -> "Both granted (uses Shizuku first)"
                                state.environment.shizukuPermissionGranted -> "Shizuku ready"
                                state.environment.rootAvailable -> "Root ready"
                                else -> "Fallback to Non-Root"
                            }
                            WorkingMode.SHIZUKU -> if (state.environment.shizukuPermissionGranted) "Ready" else "Not authorized yet"
                            WorkingMode.ROOT -> if (state.environment.rootAvailable) "Ready (uid=0)" else "Not verified yet"
                            WorkingMode.NON_ROOT -> "Always available"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectWorkingMode(mode)
                                    showModeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.settings.workingMode == mode,
                                onClick = {
                                    onSelectWorkingMode(mode)
                                    showModeDialog = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "${mode.displayName} • $readinessText",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModeDialog = false }) { Text("Close") }
            },
        )
    }

    if (showBucketDialog) {
        val bucketOptions = listOf(StandbyBucket.RESTRICTED, StandbyBucket.RARE, StandbyBucket.FREQUENT)
        AlertDialog(
            onDismissRequest = { showBucketDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Target Standby Bucket") },
            text = {
                Column {
                    bucketOptions.forEach { bucket ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTargetBucketChange(bucket)
                                    showBucketDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.settings.defaultTargetBucket == bucket,
                                onClick = {
                                    onTargetBucketChange(bucket)
                                    showBucketDialog = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(bucket.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBucketDialog = false }) { Text("Close") }
            },
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Appearance") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.settings.themeMode == mode,
                                onClick = {
                                    onSelectThemeMode(mode)
                                    showThemeDialog = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(mode.displayName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
            },
        )
    }

    if (showIntervalDialog) {
        val options = listOf(15, 30, 60, 180, 360)
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Nap Interval") },
            text = {
                Column {
                    options.forEach { mins ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAutoIntervalChange(mins)
                                    showIntervalDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.settings.autoHibernationIntervalMinutes == mins,
                                onClick = {
                                    onAutoIntervalChange(mins)
                                    showIntervalDialog = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Every $mins minutes", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun MiuixGroupCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun InsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 10.dp, bottom = 6.dp),
    )
}

@Composable
private fun MiuixActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MiuixSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GreenifySwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PrivilegeRow(
    title: String,
    detail: String,
    ok: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(
                onClick = onAction,
                shape = RoundedCornerShape(50),
            ) {
                Text(actionLabel)
            }
        }
    }
}
