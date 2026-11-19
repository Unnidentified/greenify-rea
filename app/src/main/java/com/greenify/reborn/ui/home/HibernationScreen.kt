package com.greenify.reborn.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenify.reborn.model.AppEntry
import com.greenify.reborn.model.HibernationEvent
import com.greenify.reborn.model.StandbyBucket
import com.greenify.reborn.ui.HomeFilter
import com.greenify.reborn.ui.MainUiState
import com.greenify.reborn.ui.components.AllSetBackendCard
import com.greenify.reborn.ui.components.AppIconImage
import com.greenify.reborn.ui.components.DegradedBackendCard
import com.greenify.reborn.ui.components.MiuixSearchBar
import com.greenify.reborn.ui.components.StandbyBucketChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HibernationScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (HomeFilter) -> Unit,
    onIncludeSystemToggle: (Boolean) -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onOpenSettings: () -> Unit,
    onHibernateSingle: (AppEntry) -> Unit,
    onToggleManaged: (AppEntry) -> Unit,
    onOpenAppInfo: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var inspectedApp by remember { mutableStateOf<AppEntry?>(null) }
    val managedCount = state.managedApps.size
    val awakeCount = state.awakeManagedCount
    val isDegraded = state.environment.isBackendDegraded(state.settings.workingMode)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 148.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "greenify-rea",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    val noun = if (managedCount == 1) "app" else "apps"
                    Text(
                        text = "$managedCount $noun managed, $awakeCount awake",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh app list",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        item {
            if (isDegraded) {
                DegradedBackendCard(
                    environment = state.environment,
                    preferredMode = state.settings.workingMode,
                    onRequestShizuku = onRequestShizuku,
                    onOpenShizukuApp = onOpenShizukuApp,
                    onOpenSettings = onOpenSettings,
                )
            } else {
                AllSetBackendCard(
                    environment = state.environment,
                    onClick = onOpenSettings,
                )
            }
        }

        item {
            MiuixSearchBar(
                query = state.homeQuery,
                onQueryChange = onQueryChange,
                placeholder = "Search apps or packages...",
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeFilter.entries.forEach { filter ->
                    MiuixFilterPill(
                        label = filter.label,
                        selected = state.homeFilter == filter,
                        onClick = { onFilterChange(filter) },
                    )
                }
                if (state.homeFilter != HomeFilter.MANAGED) {
                    MiuixFilterPill(
                        label = "All System",
                        selected = state.settings.includeSystemApps,
                        onClick = { onIncludeSystemToggle(!state.settings.includeSystemApps) },
                    )
                }
            }
        }

        if (state.isLoadingApps) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.filteredApps.isEmpty()) {
            item {
                EmptyStateCard(
                    filter = state.homeFilter,
                    hasQuery = state.homeQuery.isNotBlank(),
                    onBrowseAllApps = { onFilterChange(HomeFilter.ALL_APPS) },
                )
            }
        } else {
            items(
                items = state.filteredApps,
                key = { it.packageName },
            ) { app ->
                AppListRow(
                    app = app,
                    showManagedBadge = state.homeFilter != HomeFilter.MANAGED && app.isManaged,
                    onTapHibernate = { onHibernateSingle(app) },
                    onLongPressDetails = { inspectedApp = app },
                )
            }

            if (state.homeFilter == HomeFilter.MANAGED && state.settings.history.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Naps",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                    )
                }
                item {
                    val events = state.settings.history.take(5)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column {
                            events.forEachIndexed { index, event ->
                                HistoryEventRow(event = event)
                                if (index < events.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    inspectedApp?.let { selected ->
        val currentApp = state.allApps.find { it.packageName == selected.packageName } ?: selected
        ModalBottomSheet(
            onDismissRequest = { inspectedApp = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        ) {
            AppDetailSheet(
                app = currentApp,
                onHibernate = {
                    onHibernateSingle(currentApp)
                    inspectedApp = null
                },
                onToggleManaged = { onToggleManaged(currentApp) },
                onOpenAppInfo = { onOpenAppInfo(currentApp) },
            )
        }
    }
}

@Composable
private fun MiuixFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun EmptyStateCard(
    filter: HomeFilter,
    hasQuery: Boolean,
    onBrowseAllApps: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Bedtime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = when {
                    hasQuery -> "No matching apps found"
                    filter == HomeFilter.MANAGED -> "No apps in your nap list yet"
                    filter == HomeFilter.AWAKE -> "All discovered apps are resting"
                    else -> "No apps discovered"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when {
                    hasQuery -> "Try a different app name or package search."
                    filter == HomeFilter.MANAGED ->
                        "Switch to All apps to browse installed apps, tap any row to put it down for a nap, or long-press for wake-up diagnostics."
                    else -> "Try adjusting your filters."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (filter == HomeFilter.MANAGED && !hasQuery) {
                FilledTonalButton(
                    onClick = onBrowseAllApps,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Browse All apps")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AppListRow(
    app: AppEntry,
    showManagedBadge: Boolean,
    onTapHibernate: () -> Unit,
    onLongPressDetails: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onTapHibernate,
                onClickLabel = "Put ${app.label} down for a nap",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPressDetails()
                },
                onLongClickLabel = "Inspect ${app.label} details",
            ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                FlowRow(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (app.isStopped) "Napping" else "Awake",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (app.isStopped) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (showManagedBadge) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "Managed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            )
                        }
                    }
                    StandbyBucketChip(bucket = app.standbyBucket)
                }
                val wakeup = app.wakeupSummary
                val activityLine = when {
                    wakeup.lastWakeupAt != null -> {
                        val rel = DateUtils.getRelativeTimeSpanString(wakeup.lastWakeupAt)
                        val countSuffix = if (wakeup.totalCount > 1) " • ${wakeup.totalCount}×" else ""
                        "${app.label} woke up $rel$countSuffix"
                    }
                    app.lastNappedAt != null -> {
                        val rel = DateUtils.getRelativeTimeSpanString(app.lastNappedAt)
                        "Put down for a nap $rel"
                    }
                    app.lastExitReason != null -> "Last exit: ${app.lastExitReason}"
                    else -> null
                }
                if (activityLine != null) {
                    Text(
                        text = activityLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppDetailSheet(
    app: AppEntry,
    onHibernate: () -> Unit,
    onToggleManaged: () -> Unit,
    onOpenAppInfo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
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

        // Grouped Miuix info card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DiagnosticLine(label = "State", value = if (app.isStopped) "Napping" else "Awake")
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                DiagnosticLine(
                    label = "App type",
                    value = if (app.isSystemApp) "System package" else "User package",
                )
                if (app.standbyBucket != StandbyBucket.UNKNOWN) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Standby bucket",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        StandbyBucketChip(bucket = app.standbyBucket)
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Recent Wake-ups",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                val wakeup = app.wakeupSummary
                if (wakeup.totalCount == 0) {
                    Text(
                        text = "No wake-ups recorded since last nap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DiagnosticLine(
                        label = "Total recorded",
                        value = "${wakeup.totalCount} (${wakeup.last24hCount} in last 24h)",
                    )
                    wakeup.recentEvents.take(3).forEach { event ->
                        val rel = DateUtils.getRelativeTimeSpanString(event.timestampMillis)
                        DiagnosticLine(label = event.cause, value = rel.toString())
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "Exit Diagnostics",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                if (app.lastExitReason != null) {
                    val whenText = app.lastExitTimestamp?.let {
                        DateUtils.getRelativeTimeSpanString(it).toString()
                    }.orEmpty()
                    DiagnosticLine(label = app.lastExitReason, value = whenText)
                } else {
                    Text(
                        text = "No recent exit records available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onHibernate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Put down for a nap")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onToggleManaged,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (app.isManaged) "Remove from list" else "Add to nap list")
                }
                OutlinedButton(
                    onClick = onOpenAppInfo,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("App Info")
                }
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

@Composable
private fun HistoryEventRow(event: HibernationEvent) {
    val rel = DateUtils.getRelativeTimeSpanString(event.timestampMillis)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Put ${event.succeededCount}/${event.packageCount} app(s) down for a nap",
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
