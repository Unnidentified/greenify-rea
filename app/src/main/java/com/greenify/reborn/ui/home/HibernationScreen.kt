package com.greenify.reborn.ui.home

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.greenify.reborn.model.AppEntry
import com.greenify.reborn.model.AppFilterChip
import com.greenify.reborn.model.HibernationStrategy
import com.greenify.reborn.ui.MainUiState
import com.greenify.reborn.ui.components.AllSetBackendCard
import com.greenify.reborn.ui.components.AppIconImage
import com.greenify.reborn.ui.components.DegradedBackendCard
import com.greenify.reborn.ui.components.MiuixSearchBar
import com.greenify.reborn.ui.components.ObserveScrollCollapse
import com.greenify.reborn.ui.components.StandbyBucketChip
import com.greenify.reborn.ui.components.fadingHorizontalEdges
import com.greenify.reborn.ui.components.rememberBoundaryBounceOffset
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HibernationScreen(
    state: MainUiState,
    onRefresh: (userTriggered: Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleFilter: (AppFilterChip) -> Unit,
    onEnterBatchMode: (initialPackage: String?) -> Unit,
    onExitBatchMode: () -> Unit,
    onToggleBatchSelection: (String) -> Unit,
    onToggleSelectAllFiltered: () -> Unit,
    onHibernatePackages: (List<String>, ignoreProtection: Boolean) -> Unit,
    onInspectPackage: (String) -> Unit,
    onOpenBatchSheet: () -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onOpenSettings: () -> Unit,
    onBottomBarCollapsedChange: (Boolean) -> Unit,
    onEmitMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDegraded = state.environment.isBackendDegraded(state.settings.workingMode)
    val filteredApps = remember(state.allApps, state.activeFilters, state.homeQuery) {
        MainUiState.filterApps(state.allApps, state.activeFilters, state.homeQuery)
    }
    val awakeCount = remember(filteredApps) { filteredApps.count { !it.isStopped } }
    val nappingCount = filteredApps.size - awakeCount
    val selectedApps = remember(state.allApps, state.selectedPackages) {
        state.allApps.filter { it.packageName in state.selectedPackages }
    }

    val pullToRefreshState = remember {
        val anim = Animatable(0f, visibilityThreshold = 0.0005f)
        val settleSpec = spring<Float>(
            dampingRatio = 0.78f,
            stiffness = 300f,
            visibilityThreshold = 0.0005f,
        )
        object : PullToRefreshState {
            override val distanceFraction: Float get() = anim.value
            override val isAnimating: Boolean get() = anim.isRunning
            override suspend fun animateToThreshold() {
                anim.animateTo(1f, settleSpec)
            }
            override suspend fun animateToHidden() {
                anim.animateTo(0f, settleSpec)
            }
            override suspend fun snapTo(targetValue: Float) {
                anim.snapTo(targetValue)
            }
        }
    }
    val listState = rememberLazyListState()
    ObserveScrollCollapse(listState = listState, onCollapseChange = onBottomBarCollapsedChange)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    val pullMaxPx = with(density) { 48.dp.toPx() }
    val (bounceNestedScroll, bounceOffsetPx) = rememberBoundaryBounceOffset(
        listState = listState,
        allowTopDragBounce = false,
        isPullToRefreshActive = { pullToRefreshState.distanceFraction > 0.35f || state.isRefreshing },
    )

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onRefresh(true) },
        state = pullToRefreshState,
        modifier = modifier.fillMaxSize(),
        indicator = {},
    ) {
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(bounceNestedScroll)
                    .graphicsLayer {
                        val d = pullToRefreshState.distanceFraction.coerceIn(0f, 1.5f)
                        val pullPx = (d / (1f + d * 0.35f)) * pullMaxPx
                        translationY = pullPx + bounceOffsetPx()
                    },
                contentPadding = PaddingValues(
                    top = statusBarTop + 22.dp,
                    bottom = 128.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        if (state.isBatchMode) {
                            BatchHeaderBar(
                                selectedCount = state.selectedPackages.size,
                                totalFilteredCount = filteredApps.size,
                                allFilteredSelected = filteredApps.isNotEmpty() &&
                                    filteredApps.all { it.packageName in state.selectedPackages },
                                isHibernating = state.isHibernating,
                                onToggleSelectAll = onToggleSelectAllFiltered,
                                onOpenBatchAdjustments = {
                                    if (selectedApps.isEmpty()) {
                                        onEmitMessage("Select at least one app first.")
                                    } else {
                                        onOpenBatchSheet()
                                    }
                                },
                                onHibernateSelected = {
                                    if (selectedApps.isEmpty()) {
                                        onEmitMessage("Select at least one app first.")
                                    } else {
                                        onHibernatePackages(selectedApps.map { it.packageName }, false)
                                    }
                                },
                                onExitBatchMode = onExitBatchMode,
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = "greenify-rea",
                                        style = MaterialTheme.typography.displaySmall,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Text(
                                        text = "${filteredApps.size} = $awakeCount awake + $nappingCount napping",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ) {
                                        IconButton(onClick = { onEnterBatchMode(null) }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Checklist,
                                                contentDescription = "Batch select apps",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ) {
                                        RefreshActionButton(
                                            isRefreshing = state.isRefreshing,
                                            pullFraction = { pullToRefreshState.distanceFraction },
                                            onClick = { onRefresh(true) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        AnimatedVisibility(visible = !state.isBatchMode) {
                            when {
                                !isDegraded -> AllSetBackendCard(
                                    environment = state.environment,
                                    onClick = onOpenSettings,
                                )
                                state.hasInspectedEnvironment -> DegradedBackendCard(
                                    environment = state.environment,
                                    preferredMode = state.settings.workingMode,
                                    onRequestShizuku = onRequestShizuku,
                                    onOpenShizukuApp = onOpenShizukuApp,
                                    onOpenSettings = onOpenSettings,
                                )
                            }
                        }
                    }
                }

                item {
                    val napTargets = remember(filteredApps) {
                        filteredApps
                            .filter { !it.isStopped && it.policy.strategy != HibernationStrategy.EXEMPT }
                            .map { it.packageName }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiuixSearchBar(
                            query = state.homeQuery,
                            onQueryChange = onQueryChange,
                            placeholder = "Search apps or packages...",
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (napTargets.isNotEmpty()) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            IconButton(
                                enabled = !state.isHibernating,
                                onClick = {
                                    if (napTargets.isNotEmpty()) {
                                        onHibernatePackages(napTargets, false)
                                    } else {
                                        onEmitMessage("All matching non-exempt apps are already napping.")
                                    }
                                },
                            ) {
                                if (state.isHibernating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Bedtime,
                                        contentDescription = "Nap awake apps (${napTargets.size})",
                                        tint = if (napTargets.isNotEmpty()) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // Stackable composable filter chips fading cleanly on screen borders
                item {
                    val filterScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fadingHorizontalEdges(filterScrollState, fadeWidth = 26.dp)
                            .horizontalScroll(filterScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Spacer(Modifier.width(8.dp))
                        AppFilterChip.entries.forEach { chip ->
                            MiuixFilterPill(
                                label = chip.label,
                                selected = chip in state.activeFilters,
                                onClick = { onToggleFilter(chip) },
                            )
                        }
                        Spacer(Modifier.width(8.dp))
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
                } else if (filteredApps.isEmpty()) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            EmptyStateCard(
                                hasQuery = state.homeQuery.isNotBlank(),
                                onShowUserAndAwake = {
                                    if (AppFilterChip.USER !in state.activeFilters) {
                                        onToggleFilter(AppFilterChip.USER)
                                    }
                                },
                            )
                        }
                    }
                } else {
                    items(
                        items = filteredApps,
                        key = { it.packageName },
                        contentType = { "app_row" },
                    ) { app ->
                        val isSelected = app.packageName in state.selectedPackages
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            AppListRow(
                                app = app,
                                isBatchMode = state.isBatchMode,
                                isSelected = isSelected,
                                geekExtrasEnabled = state.settings.geekExtrasEnabled,
                                onSingleTap = {
                                    if (state.isBatchMode) {
                                        onToggleBatchSelection(app.packageName)
                                    } else {
                                        onInspectPackage(app.packageName)
                                    }
                                },
                                onLongPress = {
                                    if (!state.isBatchMode) {
                                        onEnterBatchMode(app.packageName)
                                    } else {
                                        onToggleBatchSelection(app.packageName)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchHeaderBar(
    selectedCount: Int,
    totalFilteredCount: Int,
    allFilteredSelected: Boolean,
    isHibernating: Boolean,
    onToggleSelectAll: () -> Unit,
    onOpenBatchAdjustments: () -> Unit,
    onHibernateSelected: () -> Unit,
    onExitBatchMode: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onExitBatchMode, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Exit batch selection")
                    }
                    Column {
                        Text(
                            text = "$selectedCount selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "$totalFilteredCount visible in filter",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Select All ONLY exists inside batch selector mode
                OutlinedButton(
                    onClick = onToggleSelectAll,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (allFilteredSelected) "Deselect All" else "Select All")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onOpenBatchAdjustments,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Adjust ($selectedCount)")
                }
                Button(
                    onClick = onHibernateSelected,
                    enabled = selectedCount > 0 && !isHibernating,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (isHibernating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Outlined.Bedtime, contentDescription = null, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Hibernate")
                }
            }
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
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun EmptyStateCard(
    hasQuery: Boolean,
    onShowUserAndAwake: () -> Unit,
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
                text = if (hasQuery) "No matching apps found" else "No apps match active filters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (hasQuery) {
                    "Try a different app name or package search."
                } else {
                    "Tap filter pills above to adjust which app states are shown. Tap any app once for adjustments & wake logs, or long-press for batch mode."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!hasQuery) {
                FilledTonalButton(
                    onClick = onShowUserAndAwake,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Include User Apps")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AppListRow(
    app: AppEntry,
    isBatchMode: Boolean,
    isSelected: Boolean,
    geekExtrasEnabled: Boolean,
    onSingleTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onSingleTap,
                onClickLabel = if (isBatchMode) "Select ${app.label}" else "Open ${app.label} settings",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
                onLongClickLabel = "Enter batch selection",
            ),
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isBatchMode) {
                Icon(
                    imageVector = if (isSelected) {
                        Icons.Outlined.CheckBox
                    } else {
                        Icons.Outlined.CheckBoxOutlineBlank
                    },
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
            }

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
                    text = if (geekExtrasEnabled) {
                        "${app.packageName} • uid ${app.uid} • sdk ${app.targetSdk}"
                    } else {
                        app.packageName
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    if (app.isPlayingAudio) {
                        StatusTagPill(
                            text = "Playing Audio",
                            bg = MaterialTheme.colorScheme.tertiaryContainer,
                            fg = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    } else if (app.inRecents) {
                        StatusTagPill(
                            text = "In Recents",
                            bg = MaterialTheme.colorScheme.secondaryContainer,
                            fg = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    if (app.policy.includeInScheduledNaps) {
                        StatusTagPill(
                            text = "Auto-Nap",
                            bg = MaterialTheme.colorScheme.primaryContainer,
                            fg = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    if (app.policy.strategy == HibernationStrategy.EXEMPT) {
                        StatusTagPill(
                            text = "Exempt",
                            bg = MaterialTheme.colorScheme.surfaceContainerHigh,
                            fg = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StandbyBucketChip(bucket = app.standbyBucket)
                }

                val wakeup = app.wakeupSummary
                val activityLine = remember(
                    wakeup.lastWakeupAt,
                    wakeup.totalCount,
                    app.lastNappedAt,
                    app.lastExitReason,
                ) {
                    when {
                        wakeup.lastWakeupAt != null -> {
                            val rel = formatRelativeShort(wakeup.lastWakeupAt)
                            val countSuffix = if (wakeup.totalCount > 1) " • ${wakeup.totalCount}×" else ""
                            "Woke up $rel$countSuffix"
                        }
                        app.lastNappedAt != null -> {
                            val rel = formatRelativeShort(app.lastNappedAt)
                            "Last napped $rel"
                        }
                        app.lastExitReason != null -> "Last exit: ${app.lastExitReason}"
                        else -> null
                    }
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
private fun StatusTagPill(
    text: String,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun formatRelativeShort(timestampMillis: Long): String {
    val diffSec = ((System.currentTimeMillis() - timestampMillis) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "${diffSec}s ago"
        diffSec < 3600L -> "${diffSec / 60L}m ago"
        diffSec < 86_400L -> "${diffSec / 3600L}h ago"
        else -> "${diffSec / 86_400L}d ago"
    }
}

@Composable
private fun RefreshActionButton(
    isRefreshing: Boolean,
    pullFraction: () -> Float,
    onClick: () -> Unit,
) {
    // Run the 165Hz spin loop strictly while isRefreshing is true; 0 callbacks at idle
    val spinAnim = remember { Animatable(0f) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            spinAnim.snapTo(0f)
            spinAnim.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 750, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            )
        } else {
            spinAnim.snapTo(0f)
        }
    }

    val pull = pullFraction()
    val tintColor = if (isRefreshing || pull > 0.1f) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = "Refresh app list",
            tint = tintColor,
            modifier = Modifier.graphicsLayer {
                rotationZ = if (isRefreshing) {
                    spinAnim.value
                } else {
                    (pullFraction().coerceIn(0f, 1.5f) * 180f)
                }
            },
        )
    }
}
