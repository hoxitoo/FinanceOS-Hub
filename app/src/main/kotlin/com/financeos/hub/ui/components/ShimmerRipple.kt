package com.financeos.hub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.LocalShimmer
import kotlinx.coroutines.launch
import kotlin.math.hypot

private class RippleSpec(val center: Offset, val color: Color) {
    val progress = Animatable(0f)
}

/**
 * «Биолюминесцентная рябь» — a soft decorative ripple that blooms from the touch point.
 *
 * Custom implementation (no Material `ripple()` — that needs Compose BOM 2024.09+, we're on
 * 2024.06): an *observe-only* pointer reader records each press position WITHOUT consuming the
 * event, so the host's `clickable {}` still fires normally; a translucent radial bloom is then
 * drawn on top via [drawWithContent].
 *
 * Colour is from the decorative palette (GlowViolet by default) — never the semantic
 * Positive/Negative — and peak alpha is low (≈0.28) so any number underneath stays readable.
 * Gated by `LocalShimmer.touchRipple`; returns the receiver unchanged when off (no pointer
 * node, no draw overhead).
 */
fun Modifier.shimmerRipple(
    color: Color = FosColors.Shimmer.GlowViolet,
): Modifier = composed {
    val enabled = LocalShimmer.current.touchRipple
    if (!enabled) return@composed this

    val scope   = rememberCoroutineScope()
    val ripples = remember { mutableStateListOf<RippleSpec>() }

    this
        .pointerInput(Unit) {
            awaitEachGesture {
                // requireUnconsumed = false → we still see the down even if a child reacts;
                // we never call consume(), so the underlying click is preserved.
                val down = awaitFirstDown(requireUnconsumed = false)
                val spec = RippleSpec(down.position, color)
                ripples.add(spec)
                scope.launch {
                    spec.progress.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
                    ripples.remove(spec)
                }
            }
        }
        .drawWithContent {
            drawContent()
            val maxR = hypot(size.width, size.height) * 0.6f
            ripples.forEach { r ->
                val p      = r.progress.value
                val radius = maxR * p
                val alpha  = (1f - p) * 0.28f
                if (radius > 0f && alpha > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f   to r.color.copy(alpha = alpha),
                            0.7f to r.color.copy(alpha = alpha * 0.5f),
                            1f   to Color.Transparent,
                            center = r.center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = r.center,
                    )
                }
            }
        }
}
