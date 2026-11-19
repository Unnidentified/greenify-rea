package com.greenify.reborn.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.greenify.reborn.model.EnvironmentStatus
import com.greenify.reborn.model.ResolvedBackend
import com.greenify.reborn.model.StandbyBucket
import com.greenify.reborn.model.WorkingMode

internal object AppIconCache {
    // Byte-budgeted (KB) cache capped at 6 MiB so downsampled 44dp icons never bloat native/EGL memory
    private val cache = object : LruCache<String, ImageBitmap>(6 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return ((value.width.toLong() * value.height.toLong() * 4L) / 1024L)
                .toInt()
                .coerceAtLeast(1)
        }
    }

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    fun put(packageName: String, bitmap: ImageBitmap) {
        cache.put(packageName, bitmap)
    }

    fun trim(level: Int) {
        @Suppress("DEPRECATION")
        when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> cache.evictAll()
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> cache.trimToSize(cache.maxSize() / 4)
        }
    }
}

@Composable
fun AppIconImage(
    packageName: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val targetPx = with(LocalDensity.current) { 44.dp.roundToPx() }.coerceIn(72, 160)
    var cached by remember(packageName) { mutableStateOf(AppIconCache.get(packageName)) }
    val bitmap by produceState<ImageBitmap?>(initialValue = cached, packageName, targetPx) {
        if (value == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val drawable = context.packageManager.getApplicationIcon(packageName)
                    drawableToBitmap(drawable, targetPx).asImageBitmap()
                }.getOrNull()
            }
            if (loaded != null) {
                AppIconCache.put(packageName, loaded)
                cached = loaded
                value = loaded
            }
        }
    }

    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            filterQuality = FilterQuality.Medium,
            modifier = modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    }
}

private fun drawableToBitmap(drawable: Drawable, targetPx: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        val src = drawable.bitmap
        if (src.width <= targetPx && src.height <= targetPx) {
            return src
        }
        return Bitmap.createScaledBitmap(src, targetPx, targetPx, true)
    }
    val bitmap = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

/**
 * Miuix-style flat squircle card shown ONLY when the active mode's backend is degraded.
 */
@Composable
fun DegradedBackendCard(
    environment: EnvironmentStatus,
    preferredMode: WorkingMode,
    onRequestShizuku: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title: String
    val subtitle: String
    val actionLabel: String
    val onFix: () -> Unit

    when {
        environment.shizukuRunning && !environment.shizukuPermissionGranted &&
            (preferredMode == WorkingMode.AUTO || preferredMode == WorkingMode.SHIZUKU) -> {
            title = "Shizuku permission needed"
            subtitle = "Grant Shizuku access to enable one-tap naps"
            actionLabel = "Grant"
            onFix = onRequestShizuku
        }

        environment.shizukuInstalled && !environment.shizukuRunning &&
            (preferredMode == WorkingMode.AUTO || preferredMode == WorkingMode.SHIZUKU) -> {
            title = "Shizuku is not running"
            subtitle = "Start Shizuku via Wireless Debugging"
            actionLabel = "Open"
            onFix = onOpenShizukuApp
        }

        preferredMode == WorkingMode.ROOT && !environment.rootAvailable -> {
            title = "Root unavailable"
            subtitle = "No su binary detected for Root mode"
            actionLabel = "Settings"
            onFix = onOpenSettings
        }

        else -> {
            title = "Nap engine degraded"
            subtitle = "Neither Shizuku nor Root is active"
            actionLabel = "Fix"
            onFix = onOpenSettings
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onFix),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
fun AllSetBackendCard(
    environment: EnvironmentStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = when (environment.resolvedBackend) {
        ResolvedBackend.SHIZUKU -> "Shizuku service authorized and ready for background naps."
        ResolvedBackend.ROOT -> "Root (su) verified and ready for deep hibernation."
        ResolvedBackend.NON_ROOT -> "Standard mode active. Manage standby apps from Settings."
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val containerColor = if (isDark) Color(0xFF113322) else Color(0xFFD7F4E3)
    val contentColor = if (isDark) Color(0xFFC5F7D9) else Color(0xFF002112)
    val accentColor = if (isDark) Color(0xFF4CD985) else Color(0xFF0D8450)
    val onAccentColor = if (isDark) Color(0xFF00381E) else Color(0xFFFFFFFF)

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "All set",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.85f),
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = accentColor,
                contentColor = onAccentColor,
            ) {
                Text(
                    text = environment.resolvedBackend.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
fun StandbyBucketChip(
    bucket: StandbyBucket,
    modifier: Modifier = Modifier,
) {
    if (bucket == StandbyBucket.UNKNOWN) return

    val container = when (bucket) {
        StandbyBucket.ACTIVE -> MaterialTheme.colorScheme.errorContainer
        StandbyBucket.WORKING_SET -> MaterialTheme.colorScheme.tertiaryContainer
        StandbyBucket.FREQUENT, StandbyBucket.RARE -> MaterialTheme.colorScheme.surfaceContainerHigh
        StandbyBucket.RESTRICTED -> MaterialTheme.colorScheme.primaryContainer
        StandbyBucket.EXEMPTED, StandbyBucket.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when (bucket) {
        StandbyBucket.ACTIVE -> MaterialTheme.colorScheme.onErrorContainer
        StandbyBucket.WORKING_SET -> MaterialTheme.colorScheme.onTertiaryContainer
        StandbyBucket.FREQUENT, StandbyBucket.RARE -> MaterialTheme.colorScheme.onSurfaceVariant
        StandbyBucket.RESTRICTED -> MaterialTheme.colorScheme.onPrimaryContainer
        StandbyBucket.EXEMPTED, StandbyBucket.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container,
    ) {
        Text(
            text = bucket.label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

/**
 * Borderless Miuix-style pill search input.
 */
@Composable
fun MiuixSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier.size(20.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GreenifySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            disabledUncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        ),
    )
}

/**
 * In-window flowy bottom sheet overlay that runs inside MainActivity's edge-to-edge surface,
 * eliminating Dialog window startup/close hitches and guaranteeing transparent system bars.
 */
@Composable
fun <T : Any> FlowyBottomSheet(
    targetState: T?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    var retainedState by remember { mutableStateOf(targetState) }
    if (targetState != null) {
        retainedState = targetState
    }
    val activeState = retainedState ?: return

    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f, visibilityThreshold = 0.0005f) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }
    var pendingDismissVelocity by remember { mutableFloatStateOf(0f) }

    val openSpring = remember {
        spring<Float>(
            dampingRatio = 0.90f,
            stiffness = 300f,
            visibilityThreshold = 0.0005f,
        )
    }
    val closeSpring = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 310f,
            visibilityThreshold = 0.0005f,
        )
    }

    val isShowing = targetState != null
    LaunchedEffect(isShowing) {
        if (isShowing) {
            pendingDismissVelocity = 0f
            progress.animateTo(1f, openSpring)
        } else {
            val initVel = pendingDismissVelocity
            pendingDismissVelocity = 0f
            progress.animateTo(0f, closeSpring, initialVelocity = initVel)
            retainedState = null
        }
    }

    BackHandler(enabled = isShowing) {
        onDismissRequest()
    }

    val nestedScrollConnection = remember(onDismissRequest) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val h = sheetHeightPx.coerceAtLeast(1f)
                // Only intercept if sheet is already pulled partway down
                if (progress.value < 0.999f) {
                    val delta = -available.y / h
                    val next = (progress.value + delta).coerceIn(0f, 1f)
                    val consumedY = (progress.value - next) * h
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(next) }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    val h = sheetHeightPx.coerceAtLeast(1f)
                    val next = (progress.value - available.y / h).coerceIn(0f, 1f)
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(next) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (progress.value < 0.999f) {
                    val h = sheetHeightPx.coerceAtLeast(1f)
                    if (available.y > 750f || progress.value < 0.72f) {
                        pendingDismissVelocity = (-available.y / h).coerceAtMost(0f)
                        onDismissRequest()
                    } else {
                        progress.animateTo(1f, openSpring)
                    }
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Soft dim scrim that tracks exact sheet progress
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.46f))
                .pointerInput(isShowing) {
                    if (isShowing) {
                        detectTapGestures { onDismissRequest() }
                    }
                },
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 10.dp)
                .onSizeChanged { sheetHeightPx = it.height.toFloat() }
                .graphicsLayer {
                    val h = size.height
                    translationY = if (h > 0f) {
                        (1f - progress.value.coerceIn(0f, 1f)) * h
                    } else {
                        4000f
                    }
                }
                .nestedScroll(nestedScrollConnection)
                // Handle vertical swipe-to-dismiss from handle, header, or any non-scrollable surface area
                .pointerInput(isShowing) {
                    if (!isShowing) return@pointerInput
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val h = sheetHeightPx.coerceAtLeast(1f)
                            val next = (progress.value - dragAmount / h).coerceIn(0f, 1f)
                            scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(next) }
                        },
                        onDragEnd = {
                            val h = sheetHeightPx.coerceAtLeast(1f)
                            val vy = tracker.calculateVelocity().y
                            if (vy > 750f || progress.value < 0.72f) {
                                pendingDismissVelocity = (-vy / h).coerceAtMost(0f)
                                onDismissRequest()
                            } else {
                                scope.launch { progress.animateTo(1f, openSpring) }
                            }
                        },
                        onDragCancel = {
                            scope.launch { progress.animateTo(1f, openSpring) }
                        },
                    )
                }
                // Consume taps on empty sheet surface so they do not pass to scrim behind
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 18.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Visual drag handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f)),
                    )
                }

                content(activeState)
            }
        }
    }
}

/**
 * Fades horizontally scrollable pill rows cleanly along screen/container borders.
 */
fun Modifier.fadingHorizontalEdges(
    scrollState: ScrollState,
    fadeWidth: Dp = 24.dp,
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fadePx = fadeWidth.toPx().coerceAtMost(size.width * 0.25f)
        if (fadePx <= 0f || size.width <= 0f) return@drawWithContent

        val leftAlpha = (scrollState.value / fadePx).coerceIn(0f, 1f)
        if (leftAlpha > 0.01f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 1f - leftAlpha),
                    1f to Color.Black,
                    startX = 0f,
                    endX = fadePx,
                ),
                size = Size(fadePx, size.height),
                blendMode = BlendMode.DstIn,
            )
        }

        val remaining = (scrollState.maxValue - scrollState.value).toFloat()
        val rightAlpha = (remaining / fadePx).coerceIn(0f, 1f)
        if (rightAlpha > 0.01f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Black,
                    1f to Color.Black.copy(alpha = 1f - rightAlpha),
                    startX = size.width - fadePx,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - fadePx, 0f),
                size = Size(fadePx, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }
