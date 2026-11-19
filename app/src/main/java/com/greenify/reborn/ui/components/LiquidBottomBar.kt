package com.greenify.reborn.ui.components

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

@Immutable
data class LiquidTabItem(
    val label: String,
    val icon: ImageVector,
)

private val LocalLiquidTabScale = compositionLocalOf { { 1f } }

/**
 * Backdrop source interface mirroring kyant0 / Miuix Backdrop recording.
 */
@Stable
interface GlassBackdrop {
    fun drawBackdropTo(
        drawScope: DrawScope,
        targetCoordinates: LayoutCoordinates,
        paddingPx: Float,
    )
}

@Stable
class LayerBackdrop(
    val graphicsLayer: GraphicsLayer,
) : GlassBackdrop {
    var coordinates: LayoutCoordinates? by mutableStateOf(null)

    override fun drawBackdropTo(
        drawScope: DrawScope,
        targetCoordinates: LayoutCoordinates,
        paddingPx: Float,
    ) {
        val sourceCoords = coordinates ?: return
        if (!sourceCoords.isAttached || !targetCoordinates.isAttached) return
        val offset = try {
            sourceCoords.localPositionOf(targetCoordinates, Offset.Zero)
        } catch (_: IllegalArgumentException) {
            return
        }
        drawScope.translate(left = paddingPx - offset.x, top = paddingPx - offset.y) {
            drawLayer(graphicsLayer)
        }
    }
}

@Stable
private class CombinedBackdrop(
    private val primary: GlassBackdrop,
    private val secondary: GlassBackdrop,
) : GlassBackdrop {
    override fun drawBackdropTo(
        drawScope: DrawScope,
        targetCoordinates: LayoutCoordinates,
        paddingPx: Float,
    ) {
        primary.drawBackdropTo(drawScope, targetCoordinates, paddingPx)
        secondary.drawBackdropTo(drawScope, targetCoordinates, paddingPx)
    }
}

@Composable
fun rememberLayerBackdrop(): LayerBackdrop {
    val graphicsLayer = rememberGraphicsLayer()
    return remember(graphicsLayer) { LayerBackdrop(graphicsLayer) }
}

/**
 * Records this composable's subtree into [backdrop] while still rendering normally on screen.
 */
fun Modifier.recordLayerBackdrop(
    backdrop: LayerBackdrop,
    drawToScreen: Boolean = true,
): Modifier = this
    .onGloballyPositioned { coords ->
        backdrop.coordinates = coords
    }
    .drawWithContent {
        val w = size.width.roundToInt().coerceAtLeast(1)
        val h = size.height.roundToInt().coerceAtLeast(1)
        backdrop.graphicsLayer.record(
            density = this,
            layoutDirection = layoutDirection,
            size = IntSize(w, h),
        ) {
            val contentCtx = this@drawWithContent.drawContext
            val prevCanvas = contentCtx.canvas
            val prevDensity = contentCtx.density
            val prevLayoutDir = contentCtx.layoutDirection
            val prevSize = contentCtx.size
            val prevLayer = contentCtx.graphicsLayer

            contentCtx.canvas = drawContext.canvas
            contentCtx.density = drawContext.density
            contentCtx.layoutDirection = drawContext.layoutDirection
            contentCtx.size = drawContext.size
            contentCtx.graphicsLayer = drawContext.graphicsLayer
            try {
                this@drawWithContent.drawContent()
            } finally {
                contentCtx.canvas = prevCanvas
                contentCtx.density = prevDensity
                contentCtx.layoutDirection = prevLayoutDir
                contentCtx.size = prevSize
                contentCtx.graphicsLayer = prevLayer
            }
        }
        if (drawToScreen) {
            drawLayer(backdrop.graphicsLayer)
        }
    }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object LiquidGlassShaders {

    // Exact AGSL lens refraction shader adapted from Miuix / kyant0 AndroidLiquidGlass
    const val LIQUID_GLASS_LENS_SOURCE = """
        uniform shader content;

        uniform float2 size;
        uniform float2 offset;
        uniform float4 cornerRadii;
        uniform float refractionHeight;
        uniform float refractionAmount;
        uniform float depthEffect;

        float radiusAt(float2 coord, float4 radii) {
            if (coord.x >= 0.0) {
                if (coord.y <= 0.0) return radii.y;
                else return radii.z;
            } else {
                if (coord.y <= 0.0) return radii.x;
                else return radii.w;
            }
        }

        float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
            float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
            float outside = length(max(cornerCoord, 0.0)) - radius;
            float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
            return outside + inside;
        }

        float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
            float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
            if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
                return sign(coord) * normalize(max(cornerCoord, 0.0));
            } else {
                float gradX = step(cornerCoord.y, cornerCoord.x);
                return sign(coord) * float2(gradX, 1.0 - gradX);
            }
        }

        float circleMap(float x) {
            return 1.0 - sqrt(clamp(1.0 - x * x, 0.0, 1.0));
        }

        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 centeredCoord = (coord + offset) - halfSize;
            float radius = radiusAt(centeredCoord, cornerRadii);

            float sd = sdRoundedRect(centeredCoord, halfSize, radius);
            if (-sd >= refractionHeight) {
                return content.eval(coord);
            }
            sd = min(sd, 0.0);

            float d = circleMap(clamp(1.0 - (-sd / refractionHeight), 0.0, 1.0)) * refractionAmount;
            float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float2 radial = length(centeredCoord) > 0.0001 ? normalize(centeredCoord) : float2(0.0);
            float2 gRaw = gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * radial;
            float2 grad = length(gRaw) > 0.0001 ? normalize(gRaw) : float2(0.0);

            float2 refractedCoord = coord + d * grad;
            return content.eval(refractedCoord);
        }
    """

    // Exact AGSL lens + 7-band chromatic dispersion shader for pressed/dragged droplet
    const val LIQUID_GLASS_LENS_DISPERSION_SOURCE = """
        uniform shader content;

        uniform float2 size;
        uniform float2 offset;
        uniform float4 cornerRadii;
        uniform float refractionHeight;
        uniform float refractionAmount;
        uniform float depthEffect;
        uniform float chromaticAberration;

        float radiusAt(float2 coord, float4 radii) {
            if (coord.x >= 0.0) {
                if (coord.y <= 0.0) return radii.y;
                else return radii.z;
            } else {
                if (coord.y <= 0.0) return radii.x;
                else return radii.w;
            }
        }

        float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
            float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
            float outside = length(max(cornerCoord, 0.0)) - radius;
            float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
            return outside + inside;
        }

        float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
            float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
            if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
                return sign(coord) * normalize(max(cornerCoord, 0.0));
            } else {
                float gradX = step(cornerCoord.y, cornerCoord.x);
                return sign(coord) * float2(gradX, 1.0 - gradX);
            }
        }

        float circleMap(float x) {
            return 1.0 - sqrt(clamp(1.0 - x * x, 0.0, 1.0));
        }

        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 centeredCoord = (coord + offset) - halfSize;
            float radius = radiusAt(centeredCoord, cornerRadii);

            float sd = sdRoundedRect(centeredCoord, halfSize, radius);
            if (-sd >= refractionHeight) {
                return content.eval(coord);
            }
            sd = min(sd, 0.0);

            float d = circleMap(clamp(1.0 - (-sd / refractionHeight), 0.0, 1.0)) * refractionAmount;
            float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float2 radial = length(centeredCoord) > 0.0001 ? normalize(centeredCoord) : float2(0.0);
            float2 gRaw = gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * radial;
            float2 grad = length(gRaw) > 0.0001 ? normalize(gRaw) : float2(0.0);

            float2 refractedCoord = coord + d * grad;
            float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / max(halfSize.x * halfSize.y, 1.0));
            float2 dispersedCoord = d * grad * dispersionIntensity;

            half4 color = half4(0.0);

            half4 red = content.eval(refractedCoord + dispersedCoord);
            color.r += red.r / 3.5;
            color.a += red.a / 7.0;

            half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
            color.r += orange.r / 3.5;
            color.g += orange.g / 7.0;
            color.a += orange.a / 7.0;

            half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
            color.r += yellow.r / 3.5;
            color.g += yellow.g / 3.5;
            color.a += yellow.a / 7.0;

            half4 green = content.eval(refractedCoord);
            color.g += green.g / 3.5;
            color.a += green.a / 7.0;

            half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
            color.g += cyan.g / 3.5;
            color.b += cyan.b / 3.0;
            color.a += cyan.a / 7.0;

            half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
            color.b += blue.b / 3.0;
            color.a += blue.a / 7.0;

            half4 purple = content.eval(refractedCoord - dispersedCoord);
            color.r += purple.r / 7.0;
            color.b += purple.b / 3.0;
            color.a += purple.a / 7.0;

            return color;
        }
    """

    // Exact AGSL specular rim highlight shader (BloomStrokeDual from Miuix)
    const val BLOOM_STROKE_DUAL_SOURCE = """
        uniform float2 halfView;
        uniform float2 halfViewFloor;
        uniform float4 cornerRadii;
        uniform float strokeWidth;
        uniform float innerBlurRadius;
        uniform float innerBlurRadiusSq;
        uniform float highlightAlpha;

        layout(color) uniform half4 strokeColor;
        uniform float strokeAlphaMul;

        uniform float3 lightDir1;
        layout(color) uniform half4 lightColor1;
        uniform float lightIntensity1;

        uniform float3 lightDir2;
        layout(color) uniform half4 lightColor2;
        uniform float lightIntensity2;

        float pickRadius(float2 fragCoord, float4 radii) {
            float2 up = fragCoord.y < halfView.y ? radii.xy : radii.zw;
            return fragCoord.x < halfView.x ? up.x : up.y;
        }

        float roundedBoxSDF(float2 pos, float2 halfSize, float radius) {
            radius = min(radius, min(halfSize.x, halfSize.y));
            float2 d = pos - halfSize + radius;
            return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
        }

        float3 getNormal(float2 fragCoord, float sdf, float R) {
            float2 xy = fragCoord - halfViewFloor;
            float2 xy_a = abs(xy);
            float t = smoothstep(-innerBlurRadius, 0.0, sdf);
            float z = sqrt(max(innerBlurRadiusSq - t * t, 0.0));
            float3 coord = float3(xy_a, -z);

            float2 corner = halfView - R;
            corner.x = min(corner.x, xy_a.x);
            corner.y = min(corner.y, xy_a.y);

            float2 diff = coord.xy - corner.xy;
            float2 dir = length(diff) > 0.0001 ? normalize(diff) : float2(0.0);
            corner += dir * (R - innerBlurRadius);

            if (any(lessThan(xy_a, corner))) {
                return float3(0.0, 0.0, -1.0);
            }

            float2 signal = sign(xy);
            float3 nVec = coord - float3(corner, 0.0);
            float3 n = length(nVec) > 0.0001 ? normalize(nVec) : float3(0.0, 0.0, -1.0);
            n.xy *= signal;
            return n;
        }

        half4 main(float2 fragCoord) {
            float2 xy = abs(fragCoord - halfView);
            float originRadius = pickRadius(fragCoord, cornerRadii);
            float R = max(originRadius, innerBlurRadius);

            if (all(lessThan(xy, halfView - R))) {
                return half4(0.0);
            }

            float sdf = roundedBoxSDF(xy, halfView, originRadius);
            half outMask = half(smoothstep(0.0, -1.0, sdf));
            float strokeAlpha = smoothstep(-strokeWidth, -strokeWidth + 1.0, sdf);

            half3 rgb = strokeColor.rgb * half(strokeAlphaMul * strokeAlpha * strokeAlpha);

            float3 n = getNormal(fragCoord, sdf, R);
            float l1 = dot(n.xy, lightDir1.xy);
            rgb += half(l1 * l1 * lightIntensity1) * lightColor1.rgb;
            float l2 = dot(n.xy, lightDir2.xy);
            rgb += half(l2 * l2 * lightIntensity2) * lightColor2.rgb;

            return half4(rgb * half(highlightAlpha), 1.0) * outMask;
        }
    """

    // Exact AGSL interactive radial glow shader (explicit premultiplied white output)
    const val INTERACTIVE_HIGHLIGHT_SOURCE = """
        uniform float radius;
        uniform float2 position;
        uniform float intensityAlpha;

        half4 main(float2 coord) {
            float dist = distance(coord, position);
            float intensity = smoothstep(radius, radius * 0.5, dist);
            half a = half(clamp(intensityAlpha * intensity, 0.0, 1.0));
            return half4(a, a, a, a);
        }
    """
}

private data class LightVec(
    val x: Float,
    val y: Float,
    val z: Float,
)

private fun lightVecFromBase(
    baseAngleRad: Double,
    rotationDegrees: Float,
    baseZ: Float = -0.05f,
): LightVec {
    val totalAngle = baseAngleRad + Math.toRadians(rotationDegrees.toDouble())
    return LightVec(
        x = cos(totalAngle).toFloat() + 0.5f,
        y = sin(totalAngle).toFloat() + 0.7f,
        z = baseZ,
    )
}

@Composable
private fun rememberTiltedBaseAngleRad(enabled: Boolean): Double {
    val context = LocalContext.current
    var quantizedAngleRad by remember { mutableDoubleStateOf(-Math.PI / 2.0) }

    DisposableEffect(context, enabled) {
        if (!enabled) {
            onDispose {}
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            if (sensorManager == null || gravitySensor == null) {
                onDispose {}
            } else {
                var gx = 0f
                var gy = -9.81f
                var lastStep = Int.MIN_VALUE
                val stepRad = Math.toRadians(3.0)
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        gx = gx * 0.85f + event.values[0] * 0.15f
                        gy = gy * 0.85f + event.values[1] * 0.15f
                        if (gx * gx + gy * gy > 0.01f) {
                            val angle = Math.atan2(gy.toDouble(), gx.toDouble())
                            val step = (angle / stepRad).roundToInt()
                            if (step != lastStep) {
                                val diffDeg = Math.abs(Math.toDegrees(angle - lastStep * stepRad))
                                if (lastStep == Int.MIN_VALUE || diffDeg >= 2.2) {
                                    lastStep = step
                                    quantizedAngleRad = step * stepRad
                                }
                            }
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                sensorManager.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL)
                onDispose {
                    sensorManager.unregisterListener(listener)
                }
            }
        }
    }

    return quantizedAngleRad
}

@Stable
private class InteractiveHighlightState(
    private val scope: CoroutineScope,
    private val positionProvider: (size: Size, offset: Offset) -> Offset,
) {
    val alphaAnim = Animatable(0f, 0.001f)
    val positionAnim = Animatable(Offset.Zero, Offset.VectorConverter)

    private val alphaSpring = SpringSpec<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
    private val positionSpring = SpringSpec<Offset>(dampingRatio = 0.5f, stiffness = 300f)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuntimeShader(LiquidGlassShaders.INTERACTIVE_HIGHLIGHT_SOURCE)
    } else {
        null
    }

    private val nativePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var alphaJob: Job? = null
    private var posJob: Job? = null

    fun start(offset: Offset) {
        alphaJob?.cancel()
        posJob?.cancel()
        alphaJob = scope.launch { alphaAnim.animateTo(1f, alphaSpring) }
        posJob = scope.launch { positionAnim.snapTo(offset) }
    }

    fun move(offset: Offset) {
        posJob?.cancel()
        posJob = scope.launch { positionAnim.animateTo(offset, positionSpring) }
    }

    fun end() {
        alphaJob?.cancel()
        alphaJob = scope.launch { alphaAnim.animateTo(0f, alphaSpring) }
    }

    fun drawOverlay(drawScope: DrawScope) {
        val alpha = alphaAnim.value.coerceIn(0f, 1.25f)
        if (alpha <= 0.001f) return
        with(drawScope) {
            drawRect(color = Color.White.copy(alpha = (0.06f * alpha).coerceIn(0f, 1f)), blendMode = BlendMode.Plus)
            val pos = positionProvider(size, positionAnim.value)
            val clampedX = pos.x.coerceIn(0f, size.width)
            val clampedY = pos.y.coerceIn(0f, size.height)
            val radius = (size.minDimension * 1.2f).coerceAtLeast(1f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                shader.setFloatUniform("radius", radius)
                shader.setFloatUniform("position", clampedX, clampedY)
                shader.setFloatUniform("intensityAlpha", (0.12f * alpha).coerceIn(0f, 1f))
                nativePaint.shader = shader
                nativePaint.blendMode = android.graphics.BlendMode.PLUS
                drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, nativePaint)
            } else {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = (0.12f * alpha).coerceIn(0f, 1f)), Color.Transparent),
                        center = Offset(clampedX, clampedY),
                        radius = radius,
                    ),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}

/**
 * Port of SukiSU-Nova's `DampedDragAnimation` + `inspectDragGestures`
 * (manager/app/.../component/miuix/animation/DampedDragAnimation.kt).
 *
 * Behaviour mirrored from the original:
 *  - the press (lens pop) starts on the very first touch-down anywhere inside the bar, no touch slop;
 *  - touch-down immediately retargets the pill to the touched tab;
 *  - dragging adds `dragAmount / tabWidth` to the *target* value (spring-chased);
 *  - velocity is snapped from a VelocityTracker fed by the value animation (not animated);
 *  - the press is only released once the value is within 2.5% of its target;
 *  - rubber-band offset is `4.dp * EaseOut(offset / totalWidth)` and returns with spring(1, 300, 0.5).
 */
@Stable
private class DampedDragAnimationState(
    private val scope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedFloatingPointRange<Float>,
    private val maxTabOffsetPx: Float,
    private val totalWidthPxProvider: () -> Float,
    private val tabWidthPxProvider: () -> Float,
    private val onDragStoppedCommit: (snappedIndex: Int) -> Unit,
) {
    private val dragMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val valueAnim = Animatable(initialValue, 0.001f)
    val velocityAnim = Animatable(0f, 5.0f)
    val pressProgressAnim = Animatable(0f, 0.001f)
    val scaleXAnim = Animatable(1f, 0.001f)
    val scaleYAnim = Animatable(1f, 0.001f)
    val offsetAnim = Animatable(0f)

    private val valueSpring = SpringSpec<Float>(dampingRatio = 1.0f, stiffness = 1000f, visibilityThreshold = 0.001f)
    private val velocitySpring = SpringSpec<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.01f)
    private val pressSpring = SpringSpec<Float>(dampingRatio = 1.0f, stiffness = 1000f, visibilityThreshold = 0.001f)
    private val scaleXSpring = SpringSpec<Float>(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f)
    private val scaleYSpring = SpringSpec<Float>(dampingRatio = 0.7f, stiffness = 250f, visibilityThreshold = 0.001f)
    private val offsetReturnSpring = SpringSpec<Float>(dampingRatio = 1.0f, stiffness = 300f, visibilityThreshold = 0.5f)

    private val pressedScale = 78f / 56f

    private var pressJob: Job? = null
    private var releaseJob: Job? = null

    val value: Float get() = valueAnim.value
    /** Logical target, updated synchronously (Animatable.targetValue lags when events arrive faster than frames). */
    private var logicalTarget: Float = initialValue
    val targetValue: Float get() = logicalTarget
    val pressProgress: Float get() = pressProgressAnim.value

    val dampedDragOffset: Float by derivedStateOf {
        val total = totalWidthPxProvider()
        if (total <= 0f) {
            0f
        } else {
            val fraction = (offsetAnim.value / total).coerceIn(-1f, 1f)
            maxTabOffsetPx * sign(fraction) * EaseOut.transform(abs(fraction))
        }
    }

    fun press() {
        releaseJob?.cancel()
        pressJob?.cancel()
        velocityTracker.resetTracking()
        pressJob = scope.launch {
            launch { pressProgressAnim.animateTo(1f, pressSpring) }
            launch { scaleXAnim.animateTo(pressedScale, scaleXSpring) }
            launch { scaleYAnim.animateTo(pressedScale, scaleYSpring) }
        }
    }

    fun release() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            awaitFrame()
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnim.value }.first { abs(it - logicalTarget) < threshold }
            }
            launch { pressProgressAnim.animateTo(0f, pressSpring) }
            launch { scaleXAnim.animateTo(1f, scaleXSpring) }
            launch { scaleYAnim.animateTo(1f, scaleYSpring) }
        }
    }

    fun updateValue(newValue: Float) {
        val clamped = newValue.coerceIn(valueRange.start, valueRange.endInclusive)
        logicalTarget = clamped
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnim.animateTo(clamped, valueSpring) { updateVelocity() }
            if (velocityAnim.value != 0f) {
                velocityAnim.animateTo(0f, velocitySpring)
            }
        }
    }

    fun animateToValue(newValue: Float) {
        logicalTarget = newValue.coerceIn(valueRange.start, valueRange.endInclusive)
        scope.launch {
            dragMutex.mutate {
                press()
                val clamped = newValue.coerceIn(valueRange.start, valueRange.endInclusive)
                this@launch.launch { valueAnim.animateTo(clamped, valueSpring) }
                if (velocityAnim.value != 0f) {
                    this@launch.launch { velocityAnim.animateTo(0f, velocitySpring) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(System.nanoTime() / 1_000_000L, Offset(value, 0f))
        // Normalize against at least a 3-tab span so 2-tab layouts match SukiSU's 4-tab jelly damping
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(3.0f)
        val rawVelocity = (velocityTracker.calculateVelocity().x / span).coerceIn(-12f, 12f)
        val smoothedVelocity = lerp(velocityAnim.value, rawVelocity, 0.35f)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            velocityAnim.snapTo(smoothedVelocity)
        }
    }

    private fun indexAt(positionX: Float, isRtl: Boolean, tabCount: Int): Int {
        val tabWidth = tabWidthPxProvider()
        if (tabWidth <= 0f) return targetValue.roundToInt().coerceIn(0, tabCount - 1)
        val padding = maxTabOffsetPx
        val logicalX = if (!isRtl) positionX else totalWidthPxProvider() - positionX
        return ((logicalX - padding) / tabWidth).toInt().coerceIn(0, tabCount - 1)
    }

    private fun finishGesture(tabCount: Int) {
        val targetIndex = targetValue.roundToInt().coerceIn(0, (tabCount - 1).coerceAtLeast(0))
        updateValue(targetIndex.toFloat())
        onDragStoppedCommit(targetIndex)
        scope.launch { offsetAnim.animateTo(0f, offsetReturnSpring) }
        release()
    }

    fun pointerModifier(
        isRtl: Boolean,
        tabCount: Int,
        highlight: InteractiveHighlightState,
    ): Modifier = Modifier.pointerInput(isRtl, tabCount) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            updateValue(indexAt(down.position.x, isRtl, tabCount).toFloat())
            press()
            highlight.start(down.position)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUpIgnoreConsumed() || !change.pressed) break
                    val total = totalWidthPxProvider()
                    val tabWidth = tabWidthPxProvider()
                    val dx = change.position.x - change.previousPosition.x
                    val inside = change.position.x in 0f..total && change.previousPosition.x in 0f..total
                    if (dx != 0f && inside && tabWidth > 0f) {
                        val dir = if (isRtl) -1f else 1f
                        updateValue(targetValue + dx / tabWidth * dir)
                        scope.launch { offsetAnim.snapTo(offsetAnim.value + dx) }
                    }
                    highlight.move(change.position)
                }
            } finally {
                finishGesture(tabCount)
                highlight.end()
            }
        }
    }
}

@Composable
fun LiquidBottomTabs(
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    backdrop: GlassBackdrop,
    tabs: List<LiquidTabItem>,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
    enableBlur: Boolean = true,
    menuBarTint: Boolean = false,
    collapsed: Boolean = false,
) {
    val tabCount = tabs.size.coerceAtLeast(1)
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()

    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainerHigh
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tintedSurface = remember(surfaceContainer, primaryContainer, menuBarTint, isDark) {
        if (menuBarTint) {
            lerpColor(surfaceContainer, primaryContainer, if (isDark) 0.56f else 0.62f)
        } else {
            surfaceContainer
        }
    }
    val containerTint = if (enableBlur) {
        tintedSurface.copy(alpha = if (menuBarTint) 0.54f else 0.40f)
    } else if (menuBarTint) {
        tintedSurface
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    val accentColor = MaterialTheme.colorScheme.primary
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)
    val latestOnSelectIndex by rememberUpdatedState(onSelectIndex)

    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    val innerPaddingPx = with(density) { 4.dp.toPx() }

    val dampedDrag = remember(tabCount, isRtl) {
        DampedDragAnimationState(
            scope = scope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(tabCount - 1).toFloat(),
            maxTabOffsetPx = innerPaddingPx,
            totalWidthPxProvider = { barWidthPx },
            tabWidthPxProvider = { tabWidthPx },
            onDragStoppedCommit = { snapped ->
                if (snapped != latestSelectedIndex) {
                    latestOnSelectIndex(snapped)
                }
            },
        )
    }

    LaunchedEffect(selectedIndex) {
        snapshotFlow { selectedIndex }.collectLatest { idx ->
            if (dampedDrag.targetValue != idx.toFloat()) {
                dampedDrag.animateToValue(idx.toFloat())
            }
        }
    }

    val panelOffsetPx by remember {
        derivedStateOf {
            dampedDrag.dampedDragOffset
        }
    }

    val highlight = remember(dampedDrag) {
        InteractiveHighlightState(scope = scope) { size, _ ->
            val cx = if (!isRtl) {
                panelOffsetPx + tabWidthPx * (dampedDrag.value + 0.5f)
            } else {
                (size.width - tabWidthPx * (dampedDrag.value + 0.5f)) + panelOffsetPx
            }
            Offset(cx, size.height / 2f)
        }
    }

    val baseAngleRad = rememberTiltedBaseAngleRad(enabled = enableBlur)
    val outerLight = remember(baseAngleRad) { lightVecFromBase(baseAngleRad, -45f, -0.05f) }
    val lensLight = remember(baseAngleRad) { lightVecFromBase(baseAngleRad, 90f, -0.05f) }
    val barHighlightPath = remember { Path() }
    var barHighlightPathSize by remember { mutableStateOf(Size.Zero) }
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = remember(backdrop, tabsBackdrop) {
        CombinedBackdrop(backdrop, tabsBackdrop)
    }

    val tabScaleProvider: () -> Float = remember(dampedDrag) {
        { lerp(1f, 1.2f, dampedDrag.pressProgress) }
    }

    val indicatorXOffsetPx: () -> Float = remember(dampedDrag, isRtl) {
        {
            val slide = dampedDrag.value * tabWidthPx
            if (!isRtl) panelOffsetPx + slide else panelOffsetPx - slide
        }
    }

    val collapseProgress by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = 380f,
        ),
        label = "bottomBarCollapse",
    )
    // Only record Layer 2 & run Layer 3 glass lens while actively pressing/dragging/switching tabs.
    // During stationary list scroll this skips 2 full offscreen blur/lens passes per frame.
    val isGlassLensActive by remember(dampedDrag, selectedIndex) {
        derivedStateOf {
            dampedDrag.pressProgress > 0.002f ||
                abs(dampedDrag.velocityAnim.value) > 0.02f ||
                abs(dampedDrag.value - selectedIndex.toFloat()) > 0.005f
        }
    }

    // Root container
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        val perTabWidthDp = lerpDp(98.dp, 60.dp, collapseProgress)
        val minBarWidthDp = lerpDp(196.dp, 120.dp, collapseProgress)
        val barWidthModifier = Modifier.width((perTabWidthDp * tabCount).coerceAtLeast(minBarWidthDp) + 8.dp)
        val barHeightDp = lerpDp(64.dp, 50.dp, collapseProgress)
        val innerHeightDp = lerpDp(56.dp, 42.dp, collapseProgress)

        // LAYER 1: Outer floating glass bar + unselected tabs
        Row(
            modifier = barWidthModifier
                .height(barHeightDp)
                .onSizeChanged { sz ->
                    barWidthPx = sz.width.toFloat()
                    tabWidthPx = ((sz.width.toFloat() - innerPaddingPx * 2f) / tabCount).coerceAtLeast(0f)
                }
                .graphicsLayer {
                    translationX = panelOffsetPx
                }
                .then(
                    if (enableBlur) {
                        Modifier.drawGlassPill(
                            backdrop = backdrop,
                            shape = CircleShape,
                            blurRadiusDp = 4.dp,
                            vibrancySaturation = 1.5f,
                            lensHeightDp = 24.dp,
                            lensAmountDp = 24.dp,
                            depthEffect = false,
                            chromaticAberration = 0f,
                            surfaceOverlay = {
                                panelOffsetPx
                                drawRect(containerTint)
                            },
                            shadowRadiusDp = 10.dp,
                            shadowAlpha = if (isDark) 0.20f else 0.10f,
                            bloomAlpha = 0.38f,
                            padDp = 40.dp,
                            primaryLight = outerLight,
                            backdropScale = {
                                val w = barWidthPx.coerceAtLeast(1f)
                                val s = lerp(1f, (with(density) { 16.dp.toPx() } / w) + 1f, dampedDrag.pressProgress)
                                s to s
                            },
                            layerTransform = {
                                val w = size.width.coerceAtLeast(1f)
                                val s = lerp(1f, (with(density) { 16.dp.toPx() } / w) + 1f, dampedDrag.pressProgress)
                                scaleX = s
                                scaleY = s
                            },
                        )
                    } else {
                        Modifier
                            .shadow(
                                elevation = 6.dp,
                                shape = CircleShape,
                                ambientColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.12f),
                                spotColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.12f),
                            )
                            .clip(CircleShape)
                            .border(
                                width = 0.75.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                                shape = CircleShape,
                            )
                            .drawBehind {
                                drawRect(containerTint)
                            }
                    },
                )
                .drawWithContent {
                    if (barHighlightPathSize != size) {
                        barHighlightPath.rewind()
                        barHighlightPath.addOutline(CircleShape.createOutline(size, layoutDirection, this@drawWithContent))
                        barHighlightPathSize = size
                    }
                    clipPath(barHighlightPath) {
                        highlight.drawOverlay(this)
                    }
                    drawContent()
                }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides onSurfaceColor,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = index == selectedIndex
                    val itemColor = if ((!enableBlur || !isGlassLensActive) && isSelected) accentColor else onSurfaceColor
                    CompositionLocalProvider(LocalContentColor provides itemColor) {
                        LiquidBottomTab(
                            item = tab,
                            selected = isSelected,
                            clickable = false,
                            collapseProgress = collapseProgress,
                            onClick = { onSelectIndex(index) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minWidth = lerpDp(76.dp, 48.dp, collapseProgress)),
                        )
                    }
                }
            }
        }

        // LAYER 2: Offscreen accent-tinted scaled tabs recorded on transparent background into tabsBackdrop
        if (enableBlur && isGlassLensActive) {
            CompositionLocalProvider(
                LocalLiquidTabScale provides tabScaleProvider,
                LocalContentColor provides accentColor,
            ) {
                Row(
                    modifier = barWidthModifier
                        .height(innerHeightDp)
                        .graphicsLayer {
                            translationX = panelOffsetPx
                        }
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .recordLayerBackdrop(tabsBackdrop, drawToScreen = true)
                        .drawGlassPill(
                            backdrop = backdrop,
                            shape = CircleShape,
                            blurRadiusDp = 4.dp,
                            vibrancySaturation = 1.5f,
                            lensHeightDp = 24.dp,
                            lensAmountDp = 24.dp,
                            depthEffect = false,
                            chromaticAberration = 0f,
                            surfaceOverlay = {
                                panelOffsetPx
                                drawRect(containerTint)
                            },
                            shadowRadiusDp = 0.dp,
                            shadowAlpha = 0f,
                            bloomAlpha = 0f,
                            padDp = 40.dp,
                        primaryLight = outerLight,
                        )
                        .drawWithContent {
                            highlight.drawOverlay(this)
                            drawContent()
                        }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        LiquidBottomTab(
                            item = tab,
                            selected = index == selectedIndex,
                            clickable = false,
                            collapseProgress = collapseProgress,
                            onClick = {},
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minWidth = lerpDp(76.dp, 48.dp, collapseProgress)),
                        )
                    }
                }
            }
        }

        // LAYER 3: Sliding magnifying liquid glass droplet indicator
        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            val indicatorModifier = Modifier
                .padding(horizontal = 4.dp)
                .width(tabWidthDp)
                .height(innerHeightDp)
                .graphicsLayer {
                    translationX = indicatorXOffsetPx()
                }

            if (enableBlur && isGlassLensActive) {
                Box(
                    modifier = indicatorModifier.drawGlassPill(
                        backdrop = combinedBackdrop,
                        shape = CircleShape,
                        blurRadiusDp = 0.dp,
                        vibrancySaturation = 1.0f,
                        lensHeightDp = with(density) { (10f * density.density * dampedDrag.pressProgress).toDp() },
                        lensAmountDp = with(density) { (14f * density.density * dampedDrag.pressProgress).toDp() },
                        depthEffect = true,
                        chromaticAberration = 0.5f,
                        surfaceOverlay = {
                            // Subscribe Layer 3 backdrop invalidation to all motion/highlight states so dragging never freezes
                            dampedDrag.value
                            panelOffsetPx
                            highlight.alphaAnim.value
                            highlight.positionAnim.value
                            val press = dampedDrag.pressProgress
                            if (menuBarTint) {
                                drawRect(primaryContainer.copy(alpha = (if (isDark) 0.28f else 0.34f) * (1f - press * 0.5f)))
                            } else {
                                val baseColor = if (!isDark) Color.Black else Color.White
                                drawRect(baseColor.copy(alpha = 0.10f * (1f - press)))
                            }
                            drawRect(Color.Black.copy(alpha = 0.03f * press))
                        },
                        shadowRadiusDp = 0.dp,
                        shadowAlpha = 0f,
                        innerShadowRadiusDp = 8.dp * dampedDrag.pressProgress,
                        innerShadowAlpha = 0.15f * dampedDrag.pressProgress,
                        bloomAlpha = dampedDrag.pressProgress,
                        padDp = 32.dp,
                        primaryLight = lensLight,
                        backdropScale = {
                            val speed = abs(dampedDrag.velocityAnim.value) / 10f
                            val stretchX = (0.50f * speed).coerceIn(0f, 0.07f)
                            val squashY = (0.20f * speed).coerceIn(0f, 0.05f)
                            val sx = dampedDrag.scaleXAnim.value / (1f - stretchX)
                            val sy = dampedDrag.scaleYAnim.value * (1f - squashY)
                            sx to sy
                        },
                        layerTransform = {
                            val speed = abs(dampedDrag.velocityAnim.value) / 10f
                            val stretchX = (0.50f * speed).coerceAtLeast(0f).coerceAtMost(0.07f)
                            val squashY = (0.20f * speed).coerceAtLeast(0f).coerceAtMost(0.05f)
                            scaleX = dampedDrag.scaleXAnim.value / (1f - stretchX)
                            scaleY = dampedDrag.scaleYAnim.value * (1f - squashY)
                        },
                    ),
                )
            } else {
                val baseColor = if (!isDark) Color.Black else Color.White
                val restingTint = if (menuBarTint) {
                    primaryContainer.copy(alpha = if (isDark) 0.30f else 0.36f)
                } else {
                    baseColor.copy(alpha = if (enableBlur) 0.10f else 0.14f)
                }
                Box(
                    modifier = indicatorModifier
                        .clip(CircleShape)
                        .drawBehind {
                            drawRect(restingTint)
                        },
                )
            }
        }

        // LAYER 4: Stationary full-bar drag/press/tap recognizer (avoids local translationX feedback)
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(dampedDrag.pointerModifier(isRtl, tabCount, highlight)),
        )
    }
}

@Composable
private fun LiquidBottomTab(
    item: LiquidTabItem,
    selected: Boolean,
    clickable: Boolean,
    collapseProgress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalLiquidTabScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val labelFraction = (1f - collapseProgress * 1.45f).coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .then(
                if (clickable) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            }
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(lerpDp(22.dp, 23.dp, collapseProgress)),
        )
        if (labelFraction > 0.01f) {
            Text(
                text = item.label,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier
                    .height(14.dp * labelFraction)
                    .graphicsLayer { alpha = labelFraction },
            )
        }
    }
}

@Composable
private fun Modifier.drawGlassPill(
    backdrop: GlassBackdrop,
    shape: Shape,
    blurRadiusDp: Dp,
    vibrancySaturation: Float,
    lensHeightDp: Dp,
    lensAmountDp: Dp,
    depthEffect: Boolean,
    chromaticAberration: Float,
    surfaceOverlay: DrawScope.() -> Unit,
    shadowRadiusDp: Dp,
    shadowAlpha: Float,
    bloomAlpha: Float,
    primaryLight: LightVec,
    innerShadowRadiusDp: Dp = 0.dp,
    innerShadowAlpha: Float = 0f,
    padDp: Dp = 0.dp,
    backdropScale: (() -> Pair<Float, Float>)? = null,
    layerTransform: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
    val effectLayer = rememberGraphicsLayer()
    val innerShadowLayer = rememberGraphicsLayer()
    remember(innerShadowLayer) {
        innerShadowLayer.compositingStrategy = CompositingStrategy.Offscreen
        innerShadowLayer
    }
    var ownCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val lensShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        remember(chromaticAberration) {
            RuntimeShader(
                if (chromaticAberration > 0f) {
                    LiquidGlassShaders.LIQUID_GLASS_LENS_DISPERSION_SOURCE
                } else {
                    LiquidGlassShaders.LIQUID_GLASS_LENS_SOURCE
                },
            )
        }
    } else {
        null
    }
    val bloomShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        remember { RuntimeShader(LiquidGlassShaders.BLOOM_STROKE_DUAL_SOURCE) }
    } else {
        null
    }
    val shadowPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val bloomPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val cachedColorFilter = remember(vibrancySaturation) {
        if (vibrancySaturation != 1f) {
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(vibrancySaturation) })
        } else {
            null
        }
    }
    var cachedShadowRadPx by remember { mutableFloatStateOf(-1f) }
    var cachedShadowMaskFilter by remember { mutableStateOf<BlurMaskFilter?>(null) }
    val cachedClipPath = remember { Path() }
    var cachedOutlineW by remember { mutableFloatStateOf(-1f) }
    var cachedOutlineH by remember { mutableFloatStateOf(-1f) }
    var cachedOutlineDir by remember { mutableStateOf<LayoutDirection?>(null) }
    var cachedOutline by remember { mutableStateOf<androidx.compose.ui.graphics.Outline?>(null) }

    var cachedBaseKey by remember { mutableStateOf<Any?>(null) }
    var cachedBaseRenderEffect by remember { mutableStateOf<RenderEffect?>(null) }
    var cachedEffectKey by remember { mutableStateOf<Any?>(null) }
    var cachedComposeEffect by remember { mutableStateOf<androidx.compose.ui.graphics.RenderEffect?>(null) }
    var cachedInnerShadowRadPx by remember { mutableFloatStateOf(-1f) }
    var cachedInnerShadowEffect by remember { mutableStateOf<androidx.compose.ui.graphics.RenderEffect?>(null) }

    val positioned = this.onGloballyPositioned { coords -> ownCoords = coords }
    val transformed = if (layerTransform != null) {
        positioned.graphicsLayer(layerTransform)
    } else {
        positioned
    }

    return transformed
        .drawWithContent {
            val coords = ownCoords
            val w = size.width
            val h = size.height
            if (w < 1f || h < 1f) {
                drawContent()
                return@drawWithContent
            }

            if (cachedOutline == null ||
                cachedOutlineW != w ||
                cachedOutlineH != h ||
                cachedOutlineDir != layoutDirection
            ) {
                val newOutline = shape.createOutline(size, layoutDirection, this)
                cachedOutline = newOutline
                cachedOutlineW = w
                cachedOutlineH = h
                cachedOutlineDir = layoutDirection
                cachedClipPath.rewind()
                cachedClipPath.addOutline(newOutline)
            }
            val outline = cachedOutline!!
            val clipShapePath = cachedClipPath

            // 1. Soft ambient drop shadow behind capsule
            val shadowRadPx = shadowRadiusDp.toPx()
            if (shadowRadPx > 0.5f && shadowAlpha > 0.001f) {
                if (cachedShadowMaskFilter == null || abs(cachedShadowRadPx - shadowRadPx) > 0.25f) {
                    cachedShadowMaskFilter = BlurMaskFilter(shadowRadPx, BlurMaskFilter.Blur.NORMAL)
                    cachedShadowRadPx = shadowRadPx
                }
                shadowPaint.color = Color.Black.copy(alpha = shadowAlpha).toArgb()
                shadowPaint.maskFilter = cachedShadowMaskFilter
                val cornerR = minOf(w, h) * 0.5f
                drawContext.canvas.nativeCanvas.drawRoundRect(
                    0f,
                    shadowRadPx * 0.25f,
                    w,
                    h + shadowRadPx * 0.25f,
                    cornerR,
                    cornerR,
                    shadowPaint,
                )
            }

            // 2. Backdrop capture + Blur + Vibrancy + AGSL Lens Refraction
            val blurPx = blurRadiusDp.toPx().coerceAtLeast(0f)
            val lensHeightPx = lensHeightDp.toPx().coerceAtLeast(0f)
            val lensAmountPx = lensAmountDp.toPx().coerceAtLeast(0f)
            val padPx = maxOf(blurPx * 2f, padDp.toPx(), lensHeightPx, lensAmountPx).coerceAtLeast(0f)

            if (coords != null && coords.isAttached) {
                val scalePair = backdropScale?.invoke()
                val recW = (w + padPx * 2f).roundToInt().coerceAtLeast(1)
                val recH = (h + padPx * 2f).roundToInt().coerceAtLeast(1)
                effectLayer.record(
                    density = this,
                    layoutDirection = layoutDirection,
                    size = IntSize(recW, recH),
                ) {
                    if (scalePair != null &&
                        (abs(scalePair.first - 1f) > 0.001f || abs(scalePair.second - 1f) > 0.001f)
                    ) {
                        val invX = 1f / scalePair.first.coerceAtLeast(0.01f)
                        val invY = 1f / scalePair.second.coerceAtLeast(0.01f)
                        scale(
                            scaleX = invX,
                            scaleY = invY,
                            pivot = Offset(w * 0.5f + padPx, h * 0.5f + padPx),
                        ) {
                            backdrop.drawBackdropTo(this, coords, padPx)
                        }
                    } else {
                        backdrop.drawBackdropTo(this, coords, padPx)
                    }
                }

                val hasLens = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    lensShader != null &&
                    lensHeightPx > 0.05f &&
                    lensAmountPx > 0.05f
                val effectKey = listOf(
                    w,
                    h,
                    blurPx,
                    lensHeightPx,
                    lensAmountPx,
                    padPx,
                    vibrancySaturation,
                    depthEffect,
                    chromaticAberration,
                    hasLens,
                )
                if (cachedEffectKey != effectKey) {
                    val baseKey = blurPx to vibrancySaturation
                    if (cachedBaseKey != baseKey) {
                        var baseEffect: RenderEffect? = null
                        if (cachedColorFilter != null) {
                            baseEffect = RenderEffect.createColorFilterEffect(cachedColorFilter)
                        }
                        if (blurPx > 0.5f) {
                            val blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                            baseEffect = if (baseEffect != null) {
                                RenderEffect.createChainEffect(blur, baseEffect)
                            } else {
                                blur
                            }
                        }
                        cachedBaseRenderEffect = baseEffect
                        cachedBaseKey = baseKey
                    }
                    var renderEffect: RenderEffect? = cachedBaseRenderEffect
                    if (hasLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val corner = minOf(w, h) * 0.5f
                        lensShader.setFloatUniform("size", w, h)
                        lensShader.setFloatUniform("offset", -padPx, -padPx)
                        lensShader.setFloatUniform("cornerRadii", corner, corner, corner, corner)
                        lensShader.setFloatUniform("refractionHeight", lensHeightPx)
                        lensShader.setFloatUniform("refractionAmount", -lensAmountPx)
                        lensShader.setFloatUniform("depthEffect", if (depthEffect) 1.0f else 0.0f)
                        if (chromaticAberration > 0f) {
                            lensShader.setFloatUniform("chromaticAberration", chromaticAberration)
                        }
                        val lensEffect = RenderEffect.createRuntimeShaderEffect(lensShader, "content")
                        renderEffect = if (renderEffect != null) {
                            RenderEffect.createChainEffect(lensEffect, renderEffect)
                        } else {
                            lensEffect
                        }
                    }
                    cachedComposeEffect = renderEffect?.asComposeRenderEffect()
                    cachedEffectKey = effectKey
                }

                effectLayer.renderEffect = cachedComposeEffect

                clipPath(clipShapePath) {
                    translate(left = -padPx, top = -padPx) {
                        drawLayer(effectLayer)
                    }
                    surfaceOverlay()
                }
            } else {
                clipPath(clipShapePath) {
                    surfaceOverlay()
                }
            }

            // 3. Draw sharp inner content above refracted glass
            drawContent()

            // 3.5. Soft inner shadow inside capsule (matches SukiSU InnerShadow during press)
            val innerShadowRadPx = innerShadowRadiusDp.toPx()
            if (innerShadowRadPx > 0.5f && innerShadowAlpha > 0.001f) {
                if (cachedInnerShadowEffect == null || abs(cachedInnerShadowRadPx - innerShadowRadPx) > 0.25f) {
                    cachedInnerShadowEffect = RenderEffect
                        .createBlurEffect(innerShadowRadPx, innerShadowRadPx, Shader.TileMode.DECAL)
                        .asComposeRenderEffect()
                    cachedInnerShadowRadPx = innerShadowRadPx
                }
                innerShadowLayer.alpha = innerShadowAlpha
                innerShadowLayer.renderEffect = cachedInnerShadowEffect
                innerShadowLayer.record(
                    density = this,
                    layoutDirection = layoutDirection,
                    size = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                ) {
                    clipPath(clipShapePath) {
                        drawOutline(outline, color = Color.Black)
                        translate(top = innerShadowRadPx) {
                            drawOutline(
                                outline = outline,
                                color = Color.Black,
                                blendMode = BlendMode.Clear,
                            )
                        }
                    }
                }
                clipPath(clipShapePath) {
                    drawLayer(innerShadowLayer)
                }
            }

            // 4. Specular BloomStrokeDual glass rim
            if (bloomAlpha > 0.001f) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && bloomShader != null) {
                    val halfW = w * 0.5f
                    val halfH = h * 0.5f
                    val corner = minOf(w, h) * 0.5f
                    val strokeW = 1.dp.toPx().coerceAtMost(corner)
                    val innerBlur = 2.dp.toPx()
                    bloomShader.setFloatUniform("halfView", halfW, halfH)
                    bloomShader.setFloatUniform("halfViewFloor", floor(halfW), floor(halfH))
                    bloomShader.setFloatUniform("cornerRadii", corner, corner, corner, corner)
                    bloomShader.setFloatUniform("strokeWidth", strokeW)
                    bloomShader.setFloatUniform("innerBlurRadius", innerBlur)
                    bloomShader.setFloatUniform("innerBlurRadiusSq", innerBlur * innerBlur)
                    bloomShader.setFloatUniform("highlightAlpha", bloomAlpha)
                    bloomShader.setColorUniform("strokeColor", Color.White.toArgb())
                    bloomShader.setFloatUniform("strokeAlphaMul", 0.12f)
                    bloomShader.setFloatUniform("lightDir1", primaryLight.x, primaryLight.y, primaryLight.z)
                    bloomShader.setColorUniform("lightColor1", Color.White.toArgb())
                    bloomShader.setFloatUniform("lightIntensity1", 1.0f)
                    bloomShader.setFloatUniform("lightDir2", 0.5f, 0.8f, -0.5f)
                    bloomShader.setColorUniform("lightColor2", Color.White.toArgb())
                    bloomShader.setFloatUniform("lightIntensity2", 0.4f)
                    bloomPaint.shader = bloomShader
                    bloomPaint.blendMode = android.graphics.BlendMode.PLUS
                    drawContext.canvas.nativeCanvas.drawRect(0f, 0f, w, h, bloomPaint)
                } else {
                    val cornerR = minOf(w, h) * 0.5f
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.45f * bloomAlpha),
                                Color.White.copy(alpha = 0.08f * bloomAlpha),
                                Color.White.copy(alpha = 0.25f * bloomAlpha),
                            ),
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR, cornerR),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
        }
}
