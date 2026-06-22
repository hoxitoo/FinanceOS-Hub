package com.financeos.hub.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.financeos.hub.ui.theme.FosColors
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

private data class Firefly(
    val x0       : Float,   // base position, fraction of size
    val y0       : Float,
    val ax       : Float,   // drift amplitude, fraction of size
    val ay       : Float,
    val speed    : Float,
    val phase    : Float,
    val radiusPx : Float,
    val baseAlpha: Float,
    val color    : Color,
)

/**
 * «Светлячки» — a handful of soft glowing dots drifting behind hero / insights content.
 *
 * Draw it BEHIND opaque content (cards, text) so a particle never crosses a number — the rule that
 * keeps the money readable. Pure [Canvas]; positions are computed analytically from one accumulated
 * time value (no per-particle Animatable), and the frame loop only runs while [animated] is true, so
 * under reduce-motion / power-save the dots are static (and cost nothing per frame).
 */
@Composable
fun ParticleLayer(
    count   : Int = 16,
    animated: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val particles = remember(count) {
        val palette = listOf(
            FosColors.Shimmer.GlowIndigo,
            FosColors.Shimmer.GlowViolet,
            FosColors.Shimmer.GlowPink,
            FosColors.Positive,   // the single warm-green dot ties back to the semantic accent
        )
        val rnd = Random(count.toLong() * 1009L + 7L)
        List(count) {
            Firefly(
                x0        = rnd.nextFloat(),
                y0        = rnd.nextFloat(),
                ax        = 0.02f + rnd.nextFloat() * 0.05f,
                ay        = 0.02f + rnd.nextFloat() * 0.05f,
                speed     = 0.15f + rnd.nextFloat() * 0.35f,
                phase     = rnd.nextFloat() * 6.2832f,
                radiusPx  = with(density) { (1.2f + rnd.nextFloat() * 1.6f).dp.toPx() },
                baseAlpha = 0.25f + rnd.nextFloat() * 0.45f,
                color     = palette[rnd.nextInt(palette.size)],
            )
        }
    }

    // Continuously accumulated seconds. Capped per-frame delta avoids a jump after the app pauses;
    // accumulation (vs. raw frame millis) keeps the value small so sin/cos stay precise for hours.
    // Keyed on [animated] so toggling reduce-motion / power-save cancels (or restarts) the loop
    // without ever changing the composable's call count.
    var time by remember { mutableStateOf(0f) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        var last = 0L
        withInfiniteAnimationFrameMillis { last = it }
        while (true) {
            withInfiniteAnimationFrameMillis { ms ->
                time += (ms - last).coerceIn(0L, 64L) / 1000f
                last = ms
            }
        }
    }

    Canvas(modifier = modifier) {
        val t = time
        particles.forEach { p ->
            val x = (p.x0 + p.ax * sin(t * p.speed + p.phase)).coerceIn(0f, 1f) * size.width
            val y = (p.y0 + p.ay * cos(t * p.speed * 0.8f + p.phase)).coerceIn(0f, 1f) * size.height
            val pulse = if (animated) 0.55f + 0.45f * sin(t * p.speed * 1.7f + p.phase) else 1f
            val a = (p.baseAlpha * pulse).coerceIn(0f, 1f)
            // Soft halo + bright core = firefly glow.
            drawCircle(color = p.color.copy(alpha = a * 0.22f), radius = p.radiusPx * 3.2f, center = Offset(x, y))
            drawCircle(color = p.color.copy(alpha = a),          radius = p.radiusPx,         center = Offset(x, y))
        }
    }
}

/**
 * Very subtle "breathing" scale (±0.3 %, 3 s, reverse) for the hero card.
 *
 * Returns a constant 1f (and starts NO animation) when [active] is false — important, because an
 * always-running infinite transition would recompose every frame and burn battery in the default
 * Standard theme where breathing is off. The early return is safe: Compose tracks the branch, and
 * callers should also skip the `graphicsLayer` when inactive so no render layer is created either.
 */
@Composable
fun rememberBreathingScale(active: Boolean): Float {
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue  = if (active) 0.997f else 1f,
        targetValue   = if (active) 1.003f else 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathScale",
    )
    return scale
}
