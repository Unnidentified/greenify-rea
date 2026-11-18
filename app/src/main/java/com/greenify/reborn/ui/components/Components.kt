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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.unit.dp
import com.greenify.reborn.model.EnvironmentStatus
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

/**
 * Shown on Home ONLY when the active working mode's backend is down or degraded.
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
            subtitle = "Shizuku is running, but Siesta needs permission to put apps to sleep."
            actionLabel = "Grant"
            onFix = onRequestShizuku
        }

        environment.shizukuInstalled && !environment.shizukuRunning &&
            (preferredMode == WorkingMode.AUTO || preferredMode == WorkingMode.SHIZUKU) -> {
            title = "Shizuku service is stopped"
            subtitle = "Start Shizuku via Wireless Debugging to restore one-tap naps."
            actionLabel = "Open Shizuku"
            onFix = onOpenShizukuApp
        }

        preferredMode == WorkingMode.ROOT && !environment.rootAvailable -> {
            title = "Root access unavailable"
            subtitle = "No su binary detected. Switch working mode or grant root."
            actionLabel = "Settings"
            onFix = onOpenSettings
        }

        else -> {
            title = "Nap engine degraded"
            subtitle = "Neither Shizuku nor Root is active for ${preferredMode.displayName} mode."
            actionLabel = "Fix in Settings"
            onFix = onOpenSettings
        }
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.88f),
                )
            }
            Button(
                onClick = onFix,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(actionLabel)
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
