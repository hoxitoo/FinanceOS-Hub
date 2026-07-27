package com.financeos.hub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.financeos.hub.core.analytics.ScoreCalculator
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.LocalShimmer
import kotlin.math.roundToInt

/**
 * One pillar of the financial-health score, drawn as its own coloured arc.
 *
 * [max] is the pillar's share of the 100-point total, so its slot on the circle is
 * `max/100 * 360°`; [earned] fills that slot. This shows BOTH the total (how much of the ring is
 * lit) and exactly which pillar is short (a dim slot), which a single-colour ring can't express.
 */
data class ScoreSegment(
    val label : String,
    val earned: Int,
    val max   : Int,
    val color : Color,
)

/**
 * Segment colours are deliberately drawn from the non-alarming end of the palette.
 * [FosColors.Negative] is NEVER used: in this design system red means "expense / overrun", so a
 * red slice would read as a problem rather than as a category.
 */
fun scoreSegments(b: ScoreCalculator.ScoreBreakdown): List<ScoreSegment> = listOf(
    ScoreSegment("Сбережения",   b.savings,   30, FosColors.Positive),            // зелёный — накопления
    ScoreSegment("Стабильность", b.stability, 20, FosColors.Info),                // синий
    ScoreSegment("Обязательные", b.mandatory, 25, FosColors.Warning),             // оранжевый
    ScoreSegment("Подушка",      b.cushion,   25, FosColors.Shimmer.GlowViolet),  // фиолетовый
)

/**
 * Multi-colour financial-health donut: one arc per score pillar instead of a single solid ring.
 *
 * Each pillar owns a slice of the circle proportional to its maximum points; inside that slice the
 * earned part is drawn in full colour and the missing part stays dimmed, so the weak pillar is
 * visible at a glance. Falls back to a plain ring when [segments] is empty.
 */
@Composable
fun ScoreDonut(
    segments : List<ScoreSegment>,
    total    : Int,
    modifier : Modifier = Modifier,
    catFace  : Boolean = false,
) {
    val shimmer = LocalShimmer.current
    val totalColor = when {
        total >= 70 -> FosColors.Positive
        total >= 40 -> FosColors.Warning
        else        -> FosColors.Negative
    }

    // Count-up: a single 0..1 progress drives every arc, so the whole donut fills together.
    val enabled  = shimmer.countUp
    val progress = remember { Animatable(0f) }
    LaunchedEffect(total, segments.size, enabled) {
        if (enabled) progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        else         progress.snapTo(1f)
    }
    val p          = progress.value
    val shownScore = (total * p).roundToInt()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (shimmer.semanticGlow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            0.0f to totalColor.copy(alpha = 0.22f),
                            0.5f to totalColor.copy(alpha = 0.08f),
                            1.0f to Color.Transparent,
                        )
                    )
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke  = size.minDimension * 0.10f
            val inset   = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            if (segments.isEmpty()) {
                drawArc(
                    color      = FosColors.Surface2,
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                return@Canvas
            }

            val maxTotal = segments.sumOf { it.max }.coerceAtLeast(1)
            // Gap between slices so adjacent colours never blend into one another.
            val gap      = if (segments.size > 1) 3f else 0f
            val usable   = 360f - gap * segments.size
            var angle    = -90f

            segments.forEach { seg ->
                val slot = usable * (seg.max.toFloat() / maxTotal)
                // Dim rail: the pillar's full potential.
                drawArc(
                    color      = seg.color.copy(alpha = 0.16f),
                    startAngle = angle, sweepAngle = slot, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                // Earned part, animated.
                val filled = slot * (seg.earned.toFloat() / seg.max.coerceAtLeast(1)).coerceIn(0f, 1f) * p
                if (filled > 0.5f) {
                    drawArc(
                        color      = seg.color,
                        startAngle = angle, sweepAngle = filled, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                }
                angle += slot + gap
            }
        }

        if (catFace) {
            Image(
                painter            = painterResource(catFaceFor(total)),
                contentDescription = "Финансовое здоровье: $total из 100",
                contentScale       = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize(0.74f)
                    .clip(CircleShape),
            )
        } else {
            Text(text = "$shownScore", style = FosType.CardAmount, color = totalColor)
        }
    }
}
