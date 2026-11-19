package com.greenify.reborn.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenify.reborn.model.AppComponentInfo
import com.greenify.reborn.model.AppEntry
import com.greenify.reborn.model.AppOpRestriction
import com.greenify.reborn.model.HibernationStrategy
import com.greenify.reborn.model.PerAppPolicy
import com.greenify.reborn.model.RecentsProtection
import com.greenify.reborn.model.StandbyBucket
import com.greenify.reborn.model.SwipeCloseMode
import com.greenify.reborn.model.WakeupCategory
import com.greenify.reborn.ui.components.AppIconImage
import com.greenify.reborn.ui.components.GreenifySwitch
import com.greenify.reborn.ui.components.MiuixSearchBar
import com.greenify.reborn.ui.components.StandbyBucketChip
import com.greenify.reborn.ui.components.fadingHorizontalEdges
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SingleAppTab(val label: String) {
    ADJUSTMENTS("Adjustments"),
    SERVICES("Wake Services"),
    WAKE_LOGS("Wake Logs"),
}

private enum class BatchTab(val label: String) {
    ADJUSTMENTS("Adjustments"),
    CUTOFFS("Wake Cut-offs"),
    WAKE_LOGS("Wake Logs"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppDetailSheet(
    app: AppEntry,
    components: List<AppComponentInfo>,
    appOpsState: Map<AppOpRestriction, Boolean>,
    isLoadingDetails: Boolean,
    hasPrivilegedBackend: Boolean,
    geekExtrasEnabled: Boolean,
    onHibernate: (ignoreProtection: Boolean) -> Unit,
    onUpdatePolicy: ((PerAppPolicy) -> PerAppPolicy) -> Unit,
    onSetStandbyBucket: (StandbyBucket) -> Unit,
    onSetAppOp: (AppOpRestriction, Boolean) -> Unit,
    onToggleComponent: (AppComponentInfo, Boolean) -> Unit,
    onBulkToggleComponents: (List<AppComponentInfo>, Boolean) -> Unit,
    onClearWakeupLogs: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onEmitMessage: (String) -> Unit,
) {
    var currentTab by remember { mutableStateOf(SingleAppTab.ADJUSTMENTS) }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIconImage(packageName = app.packageName, contentDescription = app.label)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${app.packageName} • v${app.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onOpenAppInfo,
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = "Open App Info",
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Segmented tab row fading on screen borders
        val tabScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fadingHorizontalEdges(tabScrollState, fadeWidth = 24.dp)
                .horizontalScroll(tabScrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.width(12.dp))
            SingleAppTab.entries.forEach { tab ->
                val countSuffix = when (tab) {
                    SingleAppTab.SERVICES -> if (components.isNotEmpty()) " (${components.size})" else ""
                    SingleAppTab.WAKE_LOGS -> if (app.wakeupSummary.totalCount > 0) " (${app.wakeupSummary.totalCount})" else ""
                    SingleAppTab.ADJUSTMENTS -> ""
                }
                SheetTabPill(
                    label = "${tab.label}$countSuffix",
                    selected = currentTab == tab,
                    onClick = { currentTab = tab },
                )
            }
            Spacer(Modifier.width(12.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(horizontal = 20.dp),
        ) {
        when (currentTab) {
            SingleAppTab.ADJUSTMENTS -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Status & runtime protection summary
                    SheetCard {
                        DiagnosticLine("Runtime State", if (app.isStopped) "Napping (stopped)" else "Awake (running)")
                        InsetLine()
                        DiagnosticLine(
                            "Live Protection",
                            when {
                                app.isForeground -> "Foreground active"
                                app.isPlayingAudio -> "Playing audio"
                                app.inRecents -> "Open in Recents"
                                else -> "Not protected"
                            },
                        )
                        if (app.standbyBucket != StandbyBucket.UNKNOWN) {
                            InsetLine()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Standby Bucket",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                StandbyBucketChip(bucket = app.standbyBucket)
                            }
                        }
                        if (geekExtrasEnabled) {
                            InsetLine()
                            DiagnosticLine(
                                "Geek Info",
                                "uid=${app.uid} • sdk=${app.targetSdk} • audio=${app.isAudioApp} • sys=${app.isSystemApp}",
                            )
                        }
                    }

                    // Scheduled Naps & Policy
                    SheetCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdatePolicy { it.copy(includeInScheduledNaps = !it.includeInScheduledNaps) }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Include in Scheduled Naps",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "Hibernate automatically during WorkManager / Quick Tile runs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            GreenifySwitch(
                                checked = app.policy.includeInScheduledNaps,
                                onCheckedChange = { checked ->
                                    onUpdatePolicy { it.copy(includeInScheduledNaps = checked) }
                                },
                            )
                        }

                        InsetLine()
                        Text(
                            text = "Hibernation Strategy",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HibernationStrategy.entries.forEach { strategy ->
                                ChoiceChip(
                                    label = strategy.displayName,
                                    selected = app.policy.strategy == strategy,
                                    onClick = { onUpdatePolicy { it.copy(strategy = strategy) } },
                                )
                            }
                        }

                        InsetLine()
                        Text(
                            text = "Recents & Audio Protection",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RecentsProtection.entries.forEach { mode ->
                                ChoiceChip(
                                    label = mode.displayName,
                                    selected = app.policy.recentsProtection == mode,
                                    onClick = { onUpdatePolicy { it.copy(recentsProtection = mode) } },
                                )
                            }
                        }

                        InsetLine()
                        Text(
                            text = "Auto-Close on Recents Swipe",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SwipeCloseMode.entries.forEach { mode ->
                                ChoiceChip(
                                    label = mode.displayName,
                                    selected = app.policy.autoCloseOnSwipe == mode,
                                    onClick = { onUpdatePolicy { it.copy(autoCloseOnSwipe = mode) } },
                                )
                            }
                        }

                        InsetLine()
                        Text(
                            text = "Set Standby Bucket Now",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StandbyBucket.assignableBuckets.forEach { bucket ->
                                ChoiceChip(
                                    label = bucket.label,
                                    selected = app.standbyBucket == bucket,
                                    onClick = { onSetStandbyBucket(bucket) },
                                )
                            }
                        }
                    }

                    // AppOps Wake-up Cut-offs (render rows immediately so fast scrolling never jumps/flashes)
                    SheetCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "AppOps Wake-up Cut-offs",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (hasPrivilegedBackend && isLoadingDetails) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 1.8.dp,
                                )
                            }
                        }
                        if (!hasPrivilegedBackend) {
                            Text(
                                text = "Grant Shizuku or Root to inspect and toggle AppOps restrictions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val rowEnabled = !isLoadingDetails
                            AppOpRestriction.entries.forEachIndexed { idx, op ->
                                val isBlocked = appOpsState[op] == true
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = rowEnabled) { onSetAppOp(op, !isBlocked) },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            text = op.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = op.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    GreenifySwitch(
                                        checked = isBlocked,
                                        enabled = rowEnabled,
                                        onCheckedChange = { checked -> onSetAppOp(op, checked) },
                                    )
                                }
                                if (idx < AppOpRestriction.entries.lastIndex) {
                                    InsetLine()
                                }
                            }
                        }
                    }

                    // Action buttons
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onHibernate(false) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Outlined.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (app.isProtectedNow) {
                                    "Put down for a nap (respecting protection)"
                                } else {
                                    "Put down for a nap"
                                },
                            )
                        }
                        if (app.isProtectedNow) {
                            OutlinedButton(
                                onClick = { onHibernate(true) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text("Force nap anyway (bypass Recents/Audio guard)")
                            }
                        }
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }
            }

            SingleAppTab.SERVICES -> {
                var componentQuery by remember { mutableStateOf("") }
                val filteredComponents = remember(components, componentQuery) {
                    val q = componentQuery.trim().lowercase()
                    if (q.isEmpty()) components
                    else components.filter {
                        it.shortName.lowercase().contains(q) ||
                            it.className.lowercase().contains(q) ||
                            it.category.label.lowercase().contains(q)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MiuixSearchBar(
                        query = componentQuery,
                        onQueryChange = { componentQuery = it },
                        placeholder = "Filter services or receivers...",
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val bootComps = components.filter { it.category == WakeupCategory.BOOT && it.isEnabled }
                        val wakeupComps = components.filter {
                            it.category != WakeupCategory.OTHER && it.isEnabled
                        }
                        val disabledComps = components.filter { !it.isEnabled }

                        FilledTonalButton(
                            onClick = { onBulkToggleComponents(bootComps, false) },
                            enabled = hasPrivilegedBackend && bootComps.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cut Boot (${bootComps.size})")
                        }
                        FilledTonalButton(
                            onClick = { onBulkToggleComponents(wakeupComps, false) },
                            enabled = hasPrivilegedBackend && wakeupComps.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Cut Wake-up (${wakeupComps.size})")
                        }
                        OutlinedButton(
                            onClick = { onBulkToggleComponents(disabledComps, true) },
                            enabled = hasPrivilegedBackend && disabledComps.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restore (${disabledComps.size})")
                        }
                    }

                    if (!hasPrivilegedBackend) {
                        Text(
                            text = "Shizuku or Root access is required to cut off individual wake-up services and receivers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (isLoadingDetails) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (filteredComponents.isEmpty()) {
                        Text(
                            text = "No matching services or receivers declared.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filteredComponents, key = { "${it.type.name}:${it.className}" }) { comp ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = hasPrivilegedBackend) {
                                                onToggleComponent(comp, !comp.isEnabled)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = comp.shortName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${comp.type.label} • ${comp.category.label}" +
                                                    if (!comp.isEnabled) " • CUT OFF" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (!comp.isEnabled) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                            )
                                        }
                                        GreenifySwitch(
                                            checked = comp.isEnabled,
                                            enabled = hasPrivilegedBackend,
                                            onCheckedChange = { enable -> onToggleComponent(comp, enable) },
                                        )
                                    }
                                }
                            }
                            item {
                                Spacer(Modifier.height(14.dp).navigationBarsPadding())
                            }
                        }
                    }
                }
            }

            SingleAppTab.WAKE_LOGS -> {
                val formatter = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Total wake-ups: ${app.wakeupSummary.totalCount} (${app.wakeupSummary.last24hCount} in 24h)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = {
                                    val dump = buildString {
                                        appendLine("${app.label} (${app.packageName}) — Wake Logs")
                                        appendLine("Total: ${app.wakeupSummary.totalCount}, Last 24h: ${app.wakeupSummary.last24hCount}")
                                        app.lastExitReason?.let {
                                            val ts = app.lastExitTimestamp?.let { t -> formatter.format(Date(t)) } ?: ""
                                            appendLine("Last exit: $it $ts")
                                        }
                                        app.wakeupSummary.recentEvents.forEach { ev ->
                                            appendLine("[${formatter.format(Date(ev.timestampMillis))}] ${ev.cause}")
                                        }
                                    }
                                    clipboard.setText(AnnotatedString(dump))
                                    onEmitMessage("Copied wake logs for ${app.label}.")
                                },
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy")
                            }
                            if (app.wakeupSummary.totalCount > 0) {
                                TextButton(onClick = onClearWakeupLogs) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Clear")
                                }
                            }
                        }
                    }

                    SheetCard {
                        Text(
                            text = "Exit Diagnostics",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (app.lastExitReason != null) {
                            val whenText = app.lastExitTimestamp?.let {
                                "${formatter.format(Date(it))} (${DateUtils.getRelativeTimeSpanString(it)})"
                            }.orEmpty()
                            DiagnosticLine(app.lastExitReason, whenText)
                        } else {
                            Text(
                                text = "No process exit record captured yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    SheetCard {
                        Text(
                            text = "Recorded Wake-up Timeline",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (app.wakeupSummary.recentEvents.isEmpty()) {
                            Text(
                                text = "No wake-ups recorded since last hibernation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            app.wakeupSummary.recentEvents.forEachIndexed { idx, event ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = event.cause,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = "${formatter.format(Date(event.timestampMillis))} • ${DateUtils.getRelativeTimeSpanString(event.timestampMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (idx < app.wakeupSummary.recentEvents.lastIndex) InsetLine()
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp).navigationBarsPadding())
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchAdjustmentsSheet(
    selectedApps: List<AppEntry>,
    hasPrivilegedBackend: Boolean,
    onHibernateSelected: (ignoreProtection: Boolean) -> Unit,
    onUpdateBatchPolicy: ((PerAppPolicy) -> PerAppPolicy) -> Unit,
    onSetBatchStandbyBucket: (StandbyBucket) -> Unit,
    onSetBatchAppOp: (AppOpRestriction, Boolean) -> Unit,
    onBatchCutOffCategories: (Set<WakeupCategory>, Boolean) -> Unit,
    onEmitMessage: (String) -> Unit,
) {
    var currentTab by remember { mutableStateOf(BatchTab.ADJUSTMENTS) }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Batch Adjustments (${selectedApps.size} apps)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            text = selectedApps.take(4).joinToString(", ") { it.label } +
                if (selectedApps.size > 4) " +${selectedApps.size - 4} more" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        val tabScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fadingHorizontalEdges(tabScrollState, fadeWidth = 24.dp)
                .horizontalScroll(tabScrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.width(12.dp))
            BatchTab.entries.forEach { tab ->
                SheetTabPill(
                    label = tab.label,
                    selected = currentTab == tab,
                    onClick = { currentTab = tab },
                )
            }
            Spacer(Modifier.width(12.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(horizontal = 20.dp),
        ) {
        when (currentTab) {
            BatchTab.ADJUSTMENTS -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { onHibernateSelected(false) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Outlined.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Hibernate ${selectedApps.size} Selected")
                    }

                    SheetCard {
                        Text(
                            text = "Scheduled Naps Membership",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { onUpdateBatchPolicy { it.copy(includeInScheduledNaps = true) } },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Include All")
                            }
                            OutlinedButton(
                                onClick = { onUpdateBatchPolicy { it.copy(includeInScheduledNaps = false) } },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Exclude All")
                            }
                        }

                        InsetLine()
                        Text(
                            text = "Batch Hibernation Strategy",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HibernationStrategy.entries.forEach { strategy ->
                                ChoiceChip(
                                    label = strategy.displayName,
                                    selected = false,
                                    onClick = { onUpdateBatchPolicy { it.copy(strategy = strategy) } },
                                )
                            }
                        }

                        InsetLine()
                        Text(
                            text = "Batch Recents & Audio Protection",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RecentsProtection.entries.forEach { mode ->
                                ChoiceChip(
                                    label = mode.displayName,
                                    selected = false,
                                    onClick = { onUpdateBatchPolicy { it.copy(recentsProtection = mode) } },
                                )
                            }
                        }

                        InsetLine()
                        Text(
                            text = "Batch Auto-Close on Recents Swipe",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SwipeCloseMode.entries.forEach { mode ->
                                ChoiceChip(
                                    label = mode.displayName,
                                    selected = false,
                                    onClick = { onUpdateBatchPolicy { it.copy(autoCloseOnSwipe = mode) } },
                                )
                            }
                        }

                        InsetLine()
                        Text(
                            text = "Apply Standby Bucket to Selected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StandbyBucket.assignableBuckets.forEach { bucket ->
                                ChoiceChip(
                                    label = bucket.label,
                                    selected = false,
                                    onClick = { onSetBatchStandbyBucket(bucket) },
                                )
                            }
                        }
                    }

                    SheetCard {
                        Text(
                            text = "Batch AppOps Cut-offs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        AppOpRestriction.entries.forEachIndexed { idx, op ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = op.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilledTonalButton(
                                        onClick = { onSetBatchAppOp(op, true) },
                                        enabled = hasPrivilegedBackend,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text("Block All")
                                    }
                                    OutlinedButton(
                                        onClick = { onSetBatchAppOp(op, false) },
                                        enabled = hasPrivilegedBackend,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text("Allow All")
                                    }
                                }
                            }
                            if (idx < AppOpRestriction.entries.lastIndex) InsetLine()
                        }
                    }
                    Spacer(Modifier.height(14.dp).navigationBarsPadding())
                }
            }

            BatchTab.CUTOFFS -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!hasPrivilegedBackend) {
                        Text(
                            text = "Shizuku or Root access is required to cut off wake-up services across selected apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    SheetCard {
                        Text(
                            text = "Cut Off Wake-up Services by Category",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        WakeupCategory.entries.forEachIndexed { idx, category ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = category.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = { onBatchCutOffCategories(setOf(category), false) },
                                        enabled = hasPrivilegedBackend,
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Text("Cut off")
                                    }
                                    OutlinedButton(
                                        onClick = { onBatchCutOffCategories(setOf(category), true) },
                                        enabled = hasPrivilegedBackend,
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Text("Restore")
                                    }
                                }
                            }
                            if (idx < WakeupCategory.entries.lastIndex) InsetLine()
                        }
                        InsetLine()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    onBatchCutOffCategories(
                                        setOf(
                                            WakeupCategory.BOOT,
                                            WakeupCategory.PUSH_SYNC,
                                            WakeupCategory.ALARM_JOB,
                                            WakeupCategory.CONNECTIVITY,
                                        ),
                                        false,
                                    )
                                },
                                enabled = hasPrivilegedBackend,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Cut All Wake-ups")
                            }
                            OutlinedButton(
                                onClick = {
                                    onBatchCutOffCategories(WakeupCategory.entries.toSet(), true)
                                },
                                enabled = hasPrivilegedBackend,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Restore All")
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp).navigationBarsPadding())
                }
            }

            BatchTab.WAKE_LOGS -> {
                val formatter = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }
                val combined = remember(selectedApps) {
                    selectedApps.flatMap { app ->
                        app.wakeupSummary.recentEvents.map { ev -> Triple(app, ev.timestampMillis, ev.cause) }
                    }.sortedByDescending { it.second }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${combined.size} wake event(s) across selection",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(
                            onClick = {
                                val text = combined.joinToString("\n") { (app, ts, cause) ->
                                    "[${formatter.format(Date(ts))}] ${app.label} (${app.packageName}): $cause"
                                }.ifBlank { "No wake-up events recorded for selected apps." }
                                clipboard.setText(AnnotatedString(text))
                                onEmitMessage("Copied batch wake logs.")
                            },
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy Logs")
                        }
                    }

                    if (combined.isEmpty()) {
                        SheetCard {
                            Text(
                                text = "No wake-up events recorded for the selected apps yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(combined) { (app, ts, cause) ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = "${app.label} • $cause",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = "${formatter.format(Date(ts))} (${DateUtils.getRelativeTimeSpanString(ts)})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            item {
                                Spacer(Modifier.height(14.dp).navigationBarsPadding())
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SheetTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun SheetCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

@Composable
private fun InsetLine() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
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
