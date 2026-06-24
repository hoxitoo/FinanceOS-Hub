package com.financeos.hub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.LocalShimmer
import kotlin.math.roundToInt

@Composable
fun ScoreRing(
    score    : Int,         // 0–100
    modifier : Modifier = Modifier,
    catFace  : Boolean = false,   // «Кот-режим»: show the mood cat face in the centre, ring fills around it
) {
    // Colour is keyed to the target score so it never sweeps through red→yellow→green while counting.
    val shimmer = LocalShimmer.current
    val color = when {
        score >= 70 -> FosColors.Positive
        score >= 40 -> FosColors.Warning
        else        -> FosColors.Negative
    }

    // Count up from 0 on first appearance when the «Анимации» layer is on.
    val enabled  = shimmer.countUp
    val animated = remember { Animatable(0f) }
    LaunchedEffect(score, enabled) {
        if (enabled) animated.animateTo(score.toFloat(), tween(900, easing = FastOutSlowInEasing))
        else         animated.snapTo(score.toFloat())
    }
    val shown      = animated.value
    val shownScore = shown.roundToInt()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // «Атмосфера» semantic glow: ambient light whose colour tracks financial health.
        // Drawn inside the ring bounds only — never bleeds into net worth text.
        if (shimmer.semanticGlow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            0.0f to color.copy(alpha = 0.22f),
                            0.5f to color.copy(alpha = 0.08f),
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

        if (catFace) {
            // «Кот-режим»: the mood-matched cat face fills the inner disc; the arc above fills
            // around it. 0.74 leaves room for the ring stroke (10 % per side) + a small gap.
            Image(
                painter            = painterResource(catFaceFor(score)),
                contentDescription = "Финансовое здоровье: $shownScore из 100",
                contentScale       = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize(0.74f)
                    .clip(CircleShape),
            )
        } else {
            Text(
                text  = "$shownScore",
                style = FosType.CardAmount,
                color = color,
            )
        }
    }
}
