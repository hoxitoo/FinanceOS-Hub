package com.financeos.hub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.LocalShimmer
import kotlin.math.roundToInt

@Composable
fun ScoreRing(
    score    : Int,         // 0–100
    modifier : Modifier = Modifier,
) {
    // Colour is keyed to the target score so it never sweeps through red→yellow→green while counting.
    val color = when {
        score >= 70 -> FosColors.Positive
        score >= 40 -> FosColors.Warning
        else        -> FosColors.Negative
    }

    // Count up from 0 on first appearance when the «Анимации» layer is on.
    val enabled  = LocalShimmer.current.countUp
    val animated = remember { Animatable(0f) }
    LaunchedEffect(score, enabled) {
        if (enabled) animated.animateTo(score.toFloat(), tween(900, easing = FastOutSlowInEasing))
        else         animated.snapTo(score.toFloat())
    }
    val shown      = animated.value
    val shownScore = shown.roundToInt()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke  = size.minDimension * 0.10f
            val inset   = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            // Track
            drawArc(
                color      = FosColors.Surface2,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter  = false,
                topLeft    = topLeft,
                size       = arcSize,
                style      = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // Score arc
            if (shown > 0f) {
                drawArc(
                    color      = color,
                    startAngle = -90f,
                    sweepAngle = 360f * (shown / 100f),
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        Text(
            text  = "$shownScore",
            style = FosType.CardAmount,
            color = color,
        )
    }
}
