package com.greenify.reborn.ui.analyzer

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenify.reborn.model.AppEntry
import com.greenify.reborn.ui.AnalyzerFilter
import com.greenify.reborn.ui.MainUiState
import com.greenify.reborn.ui.components.AppIconImage
import com.greenify.reborn.ui.components.SearchField
import com.greenify.reborn.ui.components.StandbyBucketChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzerScreen(
    state: MainUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (AnalyzerFilter) -> Unit,
    onIncludeSystemToggle: (Boolean) -> Unit,
    onToggleGreenified: (AppEntry) -> Unit,
    onOpenAppInfo: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var inspectedApp by remember { mutableStateOf<AppEntry?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchField(
                query = state.analyzerQuery,
                onQueryChange = onQueryChange,
                placeholder = "Search installed apps or packages...",
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnalyzerFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.analyzerFilter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(filter.label) },
                    )
                }
                FilterChip(
                    selected = state.settings.includeSystemApps,
                    onClick = { onIncludeSystemToggle(!state.settings.includeSystemApps) },
                    label = { Text("All System") },
                )
            }
        }

        if (state.isLoadingApps) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val apps = state.filteredAnalyzerApps
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = "${apps.size} app(s) discovered",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                items(items = apps, key = { it.packageName }) { app ->
                    AnalyzerAppRow(
                        app = app,
                        onClick = { inspectedApp = app },
                        onToggleGreenify = { onToggleGreenified(app) },
                    )
                }
            }
        }
    }

    inspectedApp?.let { selected ->
        val currentApp = state.allApps.find { it.packageName == selected.packageName } ?: selected
        ModalBottomSheet(onDismissRequest = { inspectedApp = null }) {
            AppDiagnosticSheet(
                app = currentApp,
                onToggleGreenify = { onToggleGreenified(currentApp) },
                onOpenAppInfo = { onOpenAppInfo(currentApp) },
            )
        }
    }
}

@Composable
private fun AnalyzerAppRow(
    app: AppEntry,
    onClick: () -> Unit,
    onToggleGreenify: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIconImage(packageName = app.packageName, contentDescription = app.label)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (app.isStopped) "Stopped" else "Running",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (app.isStopped) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    StandbyBucketChip(bucket = app.standbyBucket)
                }
                app.lastExitReason?.let { reason ->
                    Text(
                        text = "Last exit: $reason",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (app.isGreenified) {
                FilledTonalButton(onClick = onToggleGreenify) {
                    Icon(imageVector = Icons.Outlined.Check, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Added")
                }
            } else {
                OutlinedButton(onClick = onToggleGreenify) {
                    Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Greenify")
                }
            }
        }
    }
}

@Composable
private fun AppDiagnosticSheet(
    app: AppEntry,
    onToggleGreenify: () -> Unit,
    onOpenAppInfo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIconImage(packageName = app.packageName, contentDescription = app.label)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${app.packageName} • v${app.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DiagnosticLine(label = "Process state", value = if (app.isStopped) "Force-stopped" else "Active / Allowed")
        DiagnosticLine(label = "App type", value = if (app.isSystemApp) "System package" else "User package")
        DiagnosticLine(label = "Standby bucket", value = app.standbyBucket.label)
        app.lastExitReason?.let { reason ->
            val whenText = app.lastExitTimestamp?.let {
                " (${DateUtils.getRelativeTimeSpanString(it)})"
            }.orEmpty()
            DiagnosticLine(label = "Latest exit reason", value = "$reason$whenText")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onOpenAppInfo,
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("App Info")
            }
            Button(
                onClick = onToggleGreenify,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (app.isGreenified) "Remove" else "Add to Greenify")
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
