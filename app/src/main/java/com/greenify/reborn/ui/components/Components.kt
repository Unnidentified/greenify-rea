package com.greenify.reborn.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenify.reborn.model.EnvironmentStatus
import com.greenify.reborn.model.ResolvedBackend
import com.greenify.reborn.model.StandbyBucket
import com.greenify.reborn.model.WorkingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIconImage(
    packageName: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                drawableToBitmap(drawable).asImageBitmap()
            }.getOrNull()
        }
    }

    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            modifier = modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@Composable
fun ModeStatusCard(
    environment: EnvironmentStatus,
    preferredMode: WorkingMode,
    onRequestShizuku: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (environment.resolvedBackend) {
        ResolvedBackend.SHIZUKU, ResolvedBackend.ROOT -> MaterialTheme.colorScheme.primaryContainer
        ResolvedBackend.NON_ROOT -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (environment.resolvedBackend) {
        ResolvedBackend.SHIZUKU, ResolvedBackend.ROOT -> MaterialTheme.colorScheme.onPrimaryContainer
        ResolvedBackend.NON_ROOT -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    val subtitle = when {
        environment.resolvedBackend == ResolvedBackend.SHIZUKU ->
            "Privileged ADB mode active via Shizuku (batched force-stop & standby)"
        environment.resolvedBackend == ResolvedBackend.ROOT ->
            "Root (su) mode active (single-pass batched commands)"
        environment.shizukuRunning && !environment.shizukuPermissionGranted ->
            "Shizuku is running — grant permission to enable one-tap hibernation"
        else ->
            "Running in Non-Root mode. Start Shizuku or Root for automated force-stop"
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = contentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = contentColor,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${environment.resolvedBackend.displayName} Backend • ${preferredMode.displayName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.85f),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (environment.shizukuRunning && !environment.shizukuPermissionGranted) {
                    FilledTonalButton(onClick = onRequestShizuku) {
                        Text("Grant Shizuku")
                    }
                }
                FilledTonalButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Configure Mode")
                }
            }
        }
    }
}

@Composable
fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StandbyBucketChip(
    bucket: StandbyBucket,
    modifier: Modifier = Modifier,
) {
    val container = when (bucket) {
        StandbyBucket.ACTIVE -> MaterialTheme.colorScheme.errorContainer
        StandbyBucket.WORKING_SET -> MaterialTheme.colorScheme.tertiaryContainer
        StandbyBucket.FREQUENT, StandbyBucket.RARE -> MaterialTheme.colorScheme.secondaryContainer
        StandbyBucket.RESTRICTED -> MaterialTheme.colorScheme.primaryContainer
        StandbyBucket.EXEMPTED, StandbyBucket.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (bucket) {
        StandbyBucket.ACTIVE -> MaterialTheme.colorScheme.onErrorContainer
        StandbyBucket.WORKING_SET -> MaterialTheme.colorScheme.onTertiaryContainer
        StandbyBucket.FREQUENT, StandbyBucket.RARE -> MaterialTheme.colorScheme.onSecondaryContainer
        StandbyBucket.RESTRICTED -> MaterialTheme.colorScheme.onPrimaryContainer
        StandbyBucket.EXEMPTED, StandbyBucket.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = container,
    ) {
        Text(
            text = bucket.label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
    )
}
