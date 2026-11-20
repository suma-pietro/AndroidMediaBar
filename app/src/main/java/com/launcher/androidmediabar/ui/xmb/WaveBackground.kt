package com.launcher.androidmediabar.ui.xmb

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.launcher.androidmediabar.model.BackgroundType
import com.launcher.androidmediabar.model.ThemeColor
import com.launcher.androidmediabar.model.Wallpaper
import com.launcher.androidmediabar.model.WaveAnimation
import com.launcher.androidmediabar.model.XmbThemeSettings
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.sin

private const val TWO_PI = 2f * PI.toFloat()

/** Theme tint for [now]: the month color for [ThemeColor.ORIGINAL], optionally darkened at night. */
internal fun backgroundColorFor(now: LocalDateTime, settings: XmbThemeSettings): Color {
    val base = (settings.color.color ?: ThemeColor.Monthly[now.monthValue - 1].color)!!
    if (!settings.dayNight) return base
    val hour = now.hour + now.minute / 60f
    // 0 = full day brightness, 1 = deep night.
    val night = when {
        hour < 5f -> 1f
        hour < 8f -> 1f - (hour - 5f) / 3f
        hour < 18f -> 0f
        hour < 22f -> (hour - 18f) / 4f
        else -> 1f
    }
    return lerp(base, Color.Black, 0.45f * night)
}

/**
 * Full-screen launcher background: theme gradient or custom wallpaper (with blur), a brightness
 * scrim for legibility, and the selected animation on top.
 */
@Composable
fun XmbBackground(
    settings: XmbThemeSettings,
    wallpaper: Wallpaper?,
    baseColor: Color,
    modifier: Modifier = Modifier,
) {
    // While previewing another background type, the loaded picture is not the right one.
    val image = wallpaper?.takeIf { it.type == settings.background }?.image
    Box(modifier) {
        if (image != null) {
            val blurRadius = settings.blur.radiusDp
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (blurRadius > 0) {
                            Modifier.blur(blurRadius.dp, BlurredEdgeTreatment.Rectangle)
                        } else {
                            Modifier
                        },
                    ),
            )
        } else {
            Canvas(Modifier.fillMaxSize()) { drawThemeGradient(baseColor) }
        }

        val scrim = 1f - settings.brightness / 100f
        if (scrim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
        }

        if (settings.animation != WaveAnimation.NONE) {
            AnimationLayer(settings.animation, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun AnimationLayer(animation: WaveAnimation, modifier: Modifier) {
    // A frozen frame that still looks good for the "still wave" option.
    val time = remember { mutableFloatStateOf(STILL_TIME) }
    val path = remember { Path() }
    val lifecycleOwner = LocalLifecycleOwner.current

    if (animation.animated) {
        // Animate only while visible: a home screen stays alive for days.
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val offset = time.floatValue
                val start = withFrameNanos { it }
                while (true) {
                    withFrameNanos { now -> time.floatValue = offset + (now - start) / 1_000_000_000f }
                }
            }
        }
    }

    Canvas(modifier) {
        // Reading the state here only invalidates the draw phase, never recomposition.
        val t = time.floatValue
        when (animation) {
            WaveAnimation.CLASSIC, WaveAnimation.STATIC -> drawWave(t, path)
            WaveAnimation.SPARKLE -> {
                drawWave(t, path)
                drawSparkles(t)
            }
            WaveAnimation.BOKEH -> drawBokeh(t)
            WaveAnimation.NONE -> Unit
        }
    }
}

private const val STILL_TIME = 12f

private fun DrawScope.drawThemeGradient(base: Color) {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.verticalGradient(
            0f to lerp(base, Color.Black, 0.10f),
            0.55f to lerp(base, Color.Black, 0.50f),
            1f to lerp(base, Color.Black, 0.85f),
        ),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
            center = Offset(w * 0.28f, h * 0.30f),
            radius = w * 0.65f,
        ),
        radius = w * 0.65f,
        center = Offset(w * 0.28f, h * 0.30f),
    )
}

private const val WAVE_LINES = 18
private const val WAVE_SEGMENTS = 64

/** Y of the wave line identified by [f] (0..1 across the sheet) at horizontal position [nx] (0..1). */
private fun waveY(nx: Float, f: Float, t: Float, h: Float): Float {
    val centerY = h * (0.60f + 0.10f * f)
    val amplitude = h * (0.05f + 0.035f * sin(f * PI.toFloat()))
    val phase = t * 0.35f + f * 0.9f
    return centerY +
        amplitude * sin(TWO_PI * (nx * 1.1f + phase * 0.25f)) +
        amplitude * 0.45f * sin(TWO_PI * (nx * 2.3f - t * 0.06f + f * 0.3f))
}

/** A silky sheet made of many thin, slightly de-phased sine lines. */
private fun DrawScope.drawWave(t: Float, path: Path) {
    val w = size.width
    val h = size.height
    val strokeWidth = 1.2f * density
    for (i in 0 until WAVE_LINES) {
        val f = i / (WAVE_LINES - 1f)
        path.reset()
        for (s in 0..WAVE_SEGMENTS) {
            val nx = s / WAVE_SEGMENTS.toFloat()
            val y = waveY(nx, f, t, h)
            if (s == 0) path.moveTo(w * nx, y) else path.lineTo(w * nx, y)
        }
        val alpha = 0.05f + 0.13f * sin(f * PI.toFloat())
        drawPath(path, Color.White.copy(alpha = alpha), style = Stroke(width = strokeWidth))
    }

    // One soft glowing band to give the sheet some volume.
    path.reset()
    for (s in 0..WAVE_SEGMENTS) {
        val nx = s / WAVE_SEGMENTS.toFloat()
        val y = h * 0.66f + h * 0.06f * sin(TWO_PI * (nx * 0.9f + t * 0.05f))
        if (s == 0) path.moveTo(w * nx, y) else path.lineTo(w * nx, y)
    }
    for (s in WAVE_SEGMENTS downTo 0) {
        val nx = s / WAVE_SEGMENTS.toFloat()
        val y = h * 0.72f + h * 0.05f * sin(TWO_PI * (nx * 1.3f - t * 0.04f + 0.4f))
        path.lineTo(w * nx, y)
    }
    path.close()
    drawPath(path, Color.White.copy(alpha = 0.06f))
}

private const val SPARKLE_COUNT = 56

/** Tiny twinkling lights drifting along the wave lines. */
private fun DrawScope.drawSparkles(t: Float) {
    val w = size.width
    val h = size.height
    for (i in 0 until SPARKLE_COUNT) {
        val f = hash01(i, 1)
        val speed = 0.008f + 0.02f * hash01(i, 2)
        val nx = fract(hash01(i, 3) + t * speed)
        val y = waveY(nx, f, t, h) + (hash01(i, 4) - 0.5f) * h * 0.02f
        val twinkle = 0.5f + 0.5f * sin(t * (1.2f + 2.2f * hash01(i, 5)) + TWO_PI * hash01(i, 6))
        val alpha = twinkle * twinkle * (0.45f + 0.5f * hash01(i, 7))
        // Fade at the screen edges so particles don't pop when wrapping around.
        val edge = (minOf(nx, 1f - nx) * 12f).coerceAtMost(1f)
        val a = alpha * edge
        if (a < 0.02f) continue
        val r = (1.2f + 1.6f * hash01(i, 8)) * density
        val center = Offset(w * nx, y)
        drawCircle(Color.White.copy(alpha = a * 0.18f), radius = r * 4f, center = center)
        drawCircle(Color.White.copy(alpha = a), radius = r, center = center)
    }
}

private const val BOKEH_COUNT = 18

/** Large, soft out-of-focus lights slowly rising across the screen. */
private fun DrawScope.drawBokeh(t: Float) {
    val w = size.width
    val h = size.height
    for (i in 0 until BOKEH_COUNT) {
        val speed = 0.012f + 0.02f * hash01(i, 11)
        val progress = fract(hash01(i, 12) + t * speed)
        val envelope = sin(progress * PI.toFloat())
        val alpha = envelope * (0.04f + 0.08f * hash01(i, 13))
        if (alpha < 0.005f) continue
        val radius = (24f + 70f * hash01(i, 14)) * density
        val x = w * hash01(i, 15) + sin(t * 0.15f + TWO_PI * hash01(i, 16)) * w * 0.04f
        val y = h * 1.1f - progress * h * 1.3f
        val center = Offset(x, y)
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = alpha),
                0.7f to Color.White.copy(alpha = alpha * 0.7f),
                1f to Color.Transparent,
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

private fun fract(x: Float): Float = x - kotlin.math.floor(x)

/** Deterministic pseudo-random value in [0, 1) for particle [i]: no per-frame state needed. */
private fun hash01(i: Int, salt: Int): Float {
    var x = i * 374_761_393 + salt * 668_265_263
    x = (x xor (x ushr 13)) * 1_274_126_177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16_777_216f
}
