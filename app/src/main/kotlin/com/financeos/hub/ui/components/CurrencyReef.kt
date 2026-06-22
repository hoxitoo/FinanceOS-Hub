package com.financeos.hub.ui.components

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
import com.financeos.hub.ui.theme.FosColors
import kotlin.math.sin

private data class ReefOrganism(
    val x0    : Float,   // base center, fraction of size
    val y0    : Float,
    val radius: Float,   // fraction of minDimension
    val phase : Float,
    val speed : Float,
    val color : Color,
)

/**
 * Bioluminescent reef organisms floating behind multi-currency hero content.
 *
 * Each currency in [currencies] gets its own colour (decorative palette, never reusing
 * semantic Positive/Negative). The organisms pulse softly via sin; when [animated] is
 * false they are static soft blobs — still visible, just motionless.
 *
 * Draw this BEHIND all text so amounts stay crisp and unobscured.
 */
@Composable
fun CurrencyReef(
    currencies: List<String>,
    animated  : Boolean,
    modifier  : Modifier = Modifier,
) {
    val organisms = remember(currencies) {
        val palette = currencies.map { currencyColor(it) }
        val anchors = listOf(
            0.12f to 0.72f,
            0.88f to 0.28f,
            0.55f to 0.88f,
            0.22f to 0.18f,
            0.78f to 0.65f,
        )
        palette.zip(anchors).mapIndexed { i, (color, pos) ->
            ReefOrganism(
                x0     = pos.first,
                y0     = pos.second,
                radius = 0.20f + i * 0.03f,
                phase  = i * 1.13f,
                speed  = 0.28f + i * 0.07f,
                color  = color,
            )
        }
    }

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
        organisms.forEach { o ->
            val pulse  = if (animated) 0.72f + 0.28f * sin(t * o.speed + o.phase).toFloat() else 1f
            val r      = size.minDimension * o.radius * pulse
            val center = Offset(o.x0 * size.width, o.y0 * size.height)
            drawCircle(color = o.color.copy(alpha = 0.05f * pulse), radius = r * 2.6f, center = center)
            drawCircle(color = o.color.copy(alpha = 0.09f * pulse), radius = r * 1.5f, center = center)
            drawCircle(color = o.color.copy(alpha = 0.14f * pulse), radius = r,         center = center)
        }
    }
}

private fun currencyColor(currency: String): Color = when (currency.uppercase()) {
    "RUB" -> FosColors.Shimmer.GlowIndigo
    "USD" -> FosColors.Shimmer.GlowViolet
    "EUR" -> FosColors.Shimmer.GlowPink
    "KGS" -> FosColors.Shimmer.GlowViolet
    else  -> FosColors.Shimmer.GlowIndigo
}
