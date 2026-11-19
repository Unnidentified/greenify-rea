package com.greenify.reborn.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Flicker-free Progressive Acrylic status-bar overlay inspired by ysfemreAlbyrk/Progressive-Acrylic:
 * - Single continuous GPU Gaussian RenderEffect (no discrete multi-tap grid or MIRROR aliasing)
 *   so slow scrolling stays completely steady without shimmer.
 * - Compact 18dp feather tail so resting titles stay below the blur with zero halo.
 * - Light-reactive frosted grain (zero on dark empty space; illuminates only when light items pass).
 * - Smooth meniscus ("suck-in") border warp as cards touch the blur boundary.
 */
@Composable
fun ProgressiveAcrylicStatusBar(
    backdrop: GlassBackdrop,
    isDark: Boolean,
    enableBlur: Boolean,
    modifier: Modifier = Modifier,
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val featherTail = 10.dp
    val totalHeight = statusBarHeight + featherTail
    if (totalHeight <= 0.dp) return

    val bgTint = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    val statusBarPx = with(density) { statusBarHeight.toPx() }
    val blurRadiusPx = with(density) { 10.dp.toPx() }
    val suckPullPx = with(density) { 4.dp.toPx() }
    val suckPinchPx = with(density) { 4.dp.toPx() }

    if (!enableBlur || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(totalHeight)
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to bgTint.copy(alpha = 0.94f),
                            0.65f to bgTint.copy(alpha = 0.72f),
                            1.0f to Color.Transparent,
                        ),
                    )
                },
        )
        return
    }

    ProgressiveAcrylicStatusBarApi33(
        backdrop = backdrop,
        isDark = isDark,
        bgTint = bgTint,
        statusBarPx = statusBarPx,
        blurRadiusPx = blurRadiusPx,
        suckPullPx = suckPullPx,
        suckPinchPx = suckPinchPx,
        totalHeight = totalHeight,
        modifier = modifier,
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ProgressiveAcrylicStatusBarApi33(
    backdrop: GlassBackdrop,
    isDark: Boolean,
    bgTint: Color,
    statusBarPx: Float,
    blurRadiusPx: Float,
    suckPullPx: Float,
    suckPinchPx: Float,
    totalHeight: Dp,
    modifier: Modifier = Modifier,
) {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val cachedRenderEffect = remember(
        boxSize,
        statusBarPx,
        blurRadiusPx,
        suckPullPx,
        suckPinchPx,
        bgTint,
        isDark,
    ) {
        if (boxSize.width <= 0 || boxSize.height <= 0) {
            null
        } else {
            val shader = RuntimeShader(PROGRESSIVE_ACRYLIC_SHADER).apply {
                setFloatUniform("size", boxSize.width.toFloat(), boxSize.height.toFloat())
                setFloatUniform("statusBarHeight", statusBarPx.coerceAtLeast(1f))
                setFloatUniform("suckPullPx", suckPullPx)
                setFloatUniform("suckPinchPx", suckPinchPx)
                setFloatUniform(
                    "tintColor",
                    bgTint.red,
                    bgTint.green,
                    bgTint.blue,
                    if (isDark) 0.50f else 0.56f,
                )
                setFloatUniform("isDark", if (isDark) 1f else 0f)
            }
            val blurEffect = RenderEffect.createBlurEffect(
                blurRadiusPx,
                blurRadiusPx,
                Shader.TileMode.DECAL,
            )
            val acrylicEffect = RenderEffect.createRuntimeShaderEffect(shader, "blurred")
            RenderEffect.createChainEffect(acrylicEffect, blurEffect).asComposeRenderEffect()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .clipToBounds()
            .onSizeChanged { boxSize = it }
            .onGloballyPositioned { coordinates = it }
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                renderEffect = cachedRenderEffect
            }
            .drawBehind {
                val coords = coordinates ?: return@drawBehind
                if (!coords.isAttached) return@drawBehind
                backdrop.drawBackdropTo(
                    drawScope = this,
                    targetCoordinates = coords,
                    paddingPx = 0f,
                )
            },
    )
}

private const val PROGRESSIVE_ACRYLIC_SHADER = """
    uniform shader blurred;
    uniform float2 size;
    uniform float statusBarHeight;
    uniform float suckPullPx;
    uniform float suckPinchPx;
    uniform float4 tintColor;
    uniform float isDark;

    float cubicEase(float x) {
        float t = clamp(x, 0.0, 1.0);
        float s = t * t * (3.0 - 2.0 * t);
        return 1.0 - pow(s, 1.6);
    }

    float hashNoise(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }

    half4 main(float2 fragCoord) {
        float startFade = statusBarHeight * 0.58;
        float denom = max(size.y - startFade, 1.0);
        float progress = clamp((fragCoord.y - startFade) / denom, 0.0, 1.0);
        float falloff = cubicEase(progress);

        if (falloff <= 0.001) {
            return half4(0.0);
        }

        // Meniscus warp pulls upward from inside the blur zone (never looks downward ahead of entry)
        float entryBell = sin(progress * 3.14159265) * (1.0 - progress * 0.45);
        float pullY = -entryBell * suckPullPx;
        float centerNorm = (fragCoord.x - size.x * 0.5) / max(size.x * 0.5, 1.0);
        float pinchX = -centerNorm * entryBell * suckPinchPx;

        // Sample safely within [safeTop, fragCoord.y] so pre-entry elements never project upward early
        float safeTop = min(statusBarHeight * 0.42, size.y * 0.5);
        float upperBound = max(min(fragCoord.y, size.y - 1.0), safeTop);
        float2 warped = float2(
            clamp(fragCoord.x + pinchX, 1.0, max(size.x - 2.0, 1.0)),
            clamp(fragCoord.y + pullY, safeTop, upperBound)
        );

        half4 sample = blurred.eval(warped);
        float sampleAlpha = clamp(float(sample.a), 0.0, 1.0);
        float3 unpremul = sampleAlpha > 0.01 ? clamp(float3(sample.rgb) / sampleAlpha, 0.0, 1.0) : tintColor.rgb;
        float3 col = mix(tintColor.rgb, unpremul, smoothstep(0.05, 0.35, sampleAlpha));

        float luma = dot(col, float3(0.2126, 0.7152, 0.0722));
        float bgLuma = dot(tintColor.rgb, float3(0.2126, 0.7152, 0.0722));

        // Gently settle the very top edge (0..statusBarHeight*0.65) into the background tint
        // so single-line item transitions at y=0 are completely imperceptible.
        float topStability = smoothstep(0.0, max(statusBarHeight * 0.65, 1.0), fragCoord.y);
        col = mix(tintColor.rgb, col, 0.22 + 0.78 * topStability);

        // Acrylic luminosity adjustment
        col = clamp(mix(float3(luma), col, isDark > 0.5 ? 1.12 : 1.08), 0.0, 1.0);

        // Theme-matched tint
        float tintStrength = tintColor.a * (0.48 + 0.52 * falloff);
        col = mix(col, tintColor.rgb, tintStrength);

        // Light-reactive frosted grain (invisible over dark canvas; shimmers only over light elements)
        float lightSignal = isDark > 0.5
            ? smoothstep(0.04, 0.26, luma - bgLuma)
            : smoothstep(0.05, 0.28, abs(luma - bgLuma));
        float fineGrain = hashNoise(fragCoord) - 0.5;
        float coarseGrain = hashNoise(floor(fragCoord * 0.55)) - 0.5;
        float grain = (fineGrain * 0.024 + coarseGrain * 0.028) * lightSignal * falloff;
        col = clamp(col + grain, 0.0, 1.0);

        return half4(half3(col) * falloff, falloff);
    }
"""
