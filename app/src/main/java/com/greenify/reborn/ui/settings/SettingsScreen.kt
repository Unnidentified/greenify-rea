package com.greenify.reborn.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Switch
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
import com.greenify.reborn.model.ThemeMode
import com.greenify.reborn.model.WorkingMode
import com.greenify.reborn.ui.MainUiState

@Composable
fun SettingsScreen(
    state: MainUiState,
    onSelectWorkingMode: (WorkingMode) -> Unit,
    onShallowChange: (Boolean) -> Unit,
    onAutoHibernationChange: (Boolean) -> Unit,
    onAutoIntervalChange: (Int) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
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
    var showThemeDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 132.dp),
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
            SectionTitle("Privileges & Onboarding")
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
                        state.environment.shizukuRunning && !state.environment.shizukuPermissionGranted -> "Grant"
                        state.environment.shizukuInstalled && !state.environment.shizukuRunning -> "Open"
                        else -> null
                    },
                    onAction = if (state.environment.shizukuRunning) onRequestShizuku else onOpenShizukuApp,
                )
                InsetDivider()
                PrivilegeRow(
                    title = "Root Access (su)",
                    detail = if (state.environment.rootAvailable) {
                        "Root verified (Magisk / KernelSU / APatch)"
                    } else {
                        "Tap Verify to check or grant superuser access"
                    },
                    ok = state.environment.rootAvailable,
                    actionLabel = if (!state.environment.rootAvailable) "Verify" else null,
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
                    detail = if (state.environment.notificationsGranted) "Allowed" else "Optional for background status updates",
                    ok = state.environment.notificationsGranted,
                    actionLabel = if (!state.environment.notificationsGranted) "Allow" else null,
                    onAction = onRequestNotifications,
                )
            }
        }

        item {
            SectionTitle("Nap Engine")
            MiuixGroupCard {
                MiuixActionRow(
                    title = "Working Mode",
                    subtitle = "${state.settings.workingMode.displayName} (Active: ${state.environment.resolvedBackend.displayName})",
                    onClick = { showModeDialog = true },
                )
                InsetDivider()
                MiuixSwitchRow(
                    title = "Shallow Naps",
                    subtitle = "Place apps into Restricted standby bucket instead of full force-stop",
                    checked = state.settings.shallowHibernation,
                    onCheckedChange = onShallowChange,
                )
                InsetDivider()
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
                }
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
                        text = "Export or restore your nap list and engine settings as a portable local JSON file. You can also add the \"Hibernate Now\" tile to Quick Settings.",
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
                                Text(mode.displayName, style = MaterialTheme.typography.bodyLarge)
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
        val options = listOf(15, 30, 60, 180)
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
