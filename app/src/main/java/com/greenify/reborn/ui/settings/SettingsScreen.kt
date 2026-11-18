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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
    onRequestShizuku: () -> Unit,
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionTitle("Privileges & Onboarding")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrivilegeRow(
                        title = "Shizuku Service",
                        detail = when {
                            state.environment.shizukuPermissionGranted -> "Connected and authorized"
                            state.environment.shizukuRunning -> "Running — tap Grant to authorize"
                            state.environment.shizukuInstalled -> "Installed — start Shizuku via Wireless Debugging"
                            else -> "Not installed (recommended for non-root)"
                        },
                        ok = state.environment.shizukuPermissionGranted,
                        actionLabel = if (state.environment.shizukuRunning && !state.environment.shizukuPermissionGranted) "Grant" else null,
                        onAction = onRequestShizuku,
                    )
                    HorizontalDivider()
                    PrivilegeRow(
                        title = "Root Access (su)",
                        detail = if (state.environment.rootAvailable) "su binary detected (Magisk / KernelSU / APatch)" else "Not detected",
                        ok = state.environment.rootAvailable,
                    )
                    HorizontalDivider()
                    PrivilegeRow(
                        title = "Usage Access (Standby Buckets)",
                        detail = if (state.environment.usageAccessGranted) "Granted" else "Grant to read Standby Buckets accurately",
                        ok = state.environment.usageAccessGranted,
                        actionLabel = if (!state.environment.usageAccessGranted) "Open" else null,
                        onAction = onOpenUsageAccessSettings,
                    )
                    HorizontalDivider()
                    PrivilegeRow(
                        title = "Notifications",
                        detail = if (state.environment.notificationsGranted) "Allowed" else "Optional for background status updates",
                        ok = state.environment.notificationsGranted,
                        actionLabel = if (!state.environment.notificationsGranted) "Allow" else null,
                        onAction = onRequestNotifications,
                    )
                }
            }
        }

        item {
            SectionTitle("Hibernation Engine")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Working Mode") },
                        supportingContent = {
                            Text("${state.settings.workingMode.displayName} (Active: ${state.environment.resolvedBackend.displayName})")
                        },
                        modifier = Modifier.clickable { showModeDialog = true },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Shallow Hibernation") },
                        supportingContent = {
                            Text("Place apps into the Android Restricted standby bucket instead of full force-stop")
                        },
                        trailingContent = {
                            Switch(
                                checked = state.settings.shallowHibernation,
                                onCheckedChange = onShallowChange,
                            )
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("WorkManager Auto-Hibernation") },
                        supportingContent = {
                            Text("Periodic background hibernation without AlarmManager wake-locks")
                        },
                        trailingContent = {
                            Switch(
                                checked = state.settings.autoHibernationEnabled,
                                onCheckedChange = onAutoHibernationChange,
                            )
                        },
                    )
                    if (state.settings.autoHibernationEnabled) {
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("Auto-Hibernation Interval") },
                            supportingContent = { Text("Every ${state.settings.autoHibernationIntervalMinutes} minutes") },
                            modifier = Modifier.clickable { showIntervalDialog = true },
                        )
                    }
                }
            }
        }

        item {
            SectionTitle("Appearance (Material 3)")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Theme") },
                        supportingContent = { Text(state.settings.themeMode.displayName) },
                        modifier = Modifier.clickable { showThemeDialog = true },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Dynamic Color (Material You)") },
                        supportingContent = { Text("Match system wallpaper colors on Android 12+") },
                        trailingContent = {
                            Switch(
                                checked = state.settings.dynamicColor,
                                onCheckedChange = onDynamicColorChange,
                            )
                        },
                    )
                }
            }
        }

        item {
            SectionTitle("Backup & Quick Actions")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Export or restore your greenified package list and engine settings as a portable local JSON file. You can also add the \"Hibernate Now\" tile to Quick Settings.",
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
                        ) {
                            Icon(Icons.Outlined.Upload, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = onImportJson,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Import JSON")
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("About Greenify Reborn")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Greenify Reborn v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Open-source Material 3 rewrite • Apache License 2.0 • Zero telemetry or Firebase tracking",
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
            title = { Text("Appearance") },
            text = {
                Column {
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
            title = { Text("Auto-Hibernation Interval") },
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
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
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
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
