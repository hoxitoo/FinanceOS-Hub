package com.financeos.hub.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * «Биолюминесцентная рябь» as a global [Indication].
 *
 * Provided app-wide via `LocalIndication` (see [ProvideShimmer]) ONLY while the «Анимации»
 * toggle is on, so every default `clickable` / Material component blooms from the touch point
 * instead of the standard grey Material ripple. When the toggle is off, `ProvideShimmer` leaves
 * `LocalIndication` untouched and the platform ripple is used as before.
 *
 * The bloom is purely decorative: a low-alpha radial of [color] (GlowViolet from the Shimmer
 * palette — never the semantic Positive/Negative) that fades as it expands. Drawn inside a
 * [clipRect] so it never bleeds past the component bounds (e.g. into adjacent list rows).
 *
 * Implementation note: uses the classic [Indication]/[IndicationInstance] API rather than the
 * newer `IndicationNodeFactory` — the latter needs Compose foundation 1.7+, and this project is
 * on BOM 2024.06 (foundation 1.6.x). Press positions come from [PressInteraction.Press], which
 * carries the touch offset in the host component's own coordinate space.
 */
class BioluminescentIndication(private val color: Color) : Indication {

    private class Bloom(val center: Offset) {
        val progress = Animatable(0f)
    }

    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance {
        val scope  = rememberCoroutineScope()
        val blooms = remember { mutableStateListOf<Bloom>() }

        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                if (interaction is PressInteraction.Press) {
                    val bloom = Bloom(interaction.pressPosition)
                    blooms.add(bloom)
                    scope.launch {
                        bloom.progress.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
                        blooms.remove(bloom)
                    }
                }
            }
        }

        return remember(blooms, color) {
            object : IndicationInstance {
                override fun ContentDrawScope.drawIndication() {
                    drawContent()
                    if (blooms.isEmpty()) return
                    val maxR = hypot(size.width, size.height) * 0.58f
                    clipRect {
                        blooms.forEach { b ->
                            val p      = b.progress.value
                            val radius = maxR * p
                            val alpha  = (1f - p) * 0.40f
                            if (radius > 0f && alpha > 0f) {
                                // Three concentric circles approximate a soft radial gradient
                                // without allocating a Brush per frame (GC churn at 60fps).
                                drawCircle(color.copy(alpha = alpha * 0.30f), radius,         b.center)
                                drawCircle(color.copy(alpha = alpha * 0.55f), radius * 0.52f, b.center)
                                drawCircle(color.copy(alpha = alpha),         radius * 0.20f, b.center)
                            }
                        }
                    }
                }
            }
        }
    }
}
