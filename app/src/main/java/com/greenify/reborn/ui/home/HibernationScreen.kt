package com.greenify.reborn.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenify.reborn.model.AppEntry
import com.greenify.reborn.model.HibernationEvent
import com.greenify.reborn.ui.MainUiState
import com.greenify.reborn.ui.components.AppIconImage
import com.greenify.reborn.ui.components.ModeStatusCard
import com.greenify.reborn.ui.components.SearchField
import com.greenify.reborn.ui.components.StandbyBucketChip
import com.greenify.reborn.ui.components.StatPill

@Composable
fun HibernationScreen(
    state: MainUiState,
    onQueryChange: (String) -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAnalyzer: () -> Unit,
    onHibernateSingle: (AppEntry) -> Unit,
    onRemoveApp: (AppEntry) -> Unit,
    onOpenAppInfo: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val greenified = state.greenifiedApps
    val runningCount = greenified.count { !it.isStopped }
    val stoppedCount = greenified.count { it.isStopped }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ModeStatusCard(
                environment = state.environment,
                preferredMode = state.settings.workingMode,
                onRequestShizuku = onRequestShizuku,
                onOpenSettings = onOpenSettings,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatPill(
                    label = "Greenified",
                    value = greenified.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatPill(
                    label = "Running",
                    value = runningCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatPill(
                    label = "Hibernated",
                    value = stoppedCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.isLoadingApps) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (greenified.isEmpty()) {
            item {
                EmptyHibernationCard(onOpenAnalyzer = onOpenAnalyzer)
            }
        } else {
            item {
                SearchField(
                    query = state.homeQuery,
                    onQueryChange = onQueryChange,
                    placeholder = "Filter greenified apps...",
                )
            }

            item {
                Text(
                    text = "Managed Apps (${state.filteredGreenifiedApps.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            items(
                items = state.filteredGreenifiedApps,
                key = { it.packageName },
            ) { app ->
                GreenifiedAppCard(
                    app = app,
                    onHibernate = { onHibernateSingle(app) },
                    onRemove = { onRemoveApp(app) },
                    onOpenAppInfo = { onOpenAppInfo(app) },
                )
            }

            if (state.settings.history.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Hibernation Runs",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(state.settings.history.take(5)) { event ->
                    HistoryEventRow(event = event)
                }
            }
        }
    }
}

@Composable
private fun EmptyHibernationCard(onOpenAnalyzer: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Eco,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = "No apps in your hibernation list yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Scan installed apps in Analyzer to see background status, standby buckets, and recent exit reasons, then add apps you want to tame.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onOpenAnalyzer) {
                Icon(
                    imageVector = Icons.Outlined.Science,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Open App Analyzer")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GreenifiedAppCard(
    app: AppEntry,
    onHibernate: () -> Unit,
    onRemove: () -> Unit,
    onOpenAppInfo: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIconImage(packageName = app.packageName, contentDescription = app.label)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (app.isStopped) "Stopped" else "Active / Background",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (app.isStopped) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            fontWeight = FontWeight.Medium,
                        )
                        StandbyBucketChip(bucket = app.standbyBucket)
                    }
                    app.lastHibernatedAt?.let { ts ->
                        val rel = DateUtils.getRelativeTimeSpanString(ts)
                        Text(
                            text = "Last hibernated $rel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onHibernate) {
                    Icon(
                        imageVector = if (app.isStopped) Icons.Outlined.Bedtime else Icons.Outlined.PlayArrow,
                        contentDescription = "Hibernate ${app.label}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onOpenAppInfo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = "App info for ${app.label}",
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Remove ${app.label}",
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryEventRow(event: HibernationEvent) {
    val rel = DateUtils.getRelativeTimeSpanString(event.timestampMillis)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Hibernated ${event.succeededCount}/${event.packageCount} app(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${event.backendUsed} • ${if (event.shallowMode) "Shallow" else "Force-stop"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = rel.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
