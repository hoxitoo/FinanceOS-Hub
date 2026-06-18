package com.financeos.hub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosType

@Composable
fun ScoreRing(
    score    : Int,         // 0–100
    modifier : Modifier = Modifier,
) {
    val color = when {
        score >= 70 -> FosColors.Positive
        score >= 40 -> FosColors.Warning
        else        -> FosColors.Negative
    }

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
            if (score > 0) {
                drawArc(
                    color      = color,
                    startAngle = -90f,
                    sweepAngle = 360f * (score / 100f),
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        Text(
            text  = "$score",
            style = FosType.CardAmount,
            color = color,
        )
    }
}
