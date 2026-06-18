package com.financeos.hub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.financeos.hub.ui.theme.FosColors

@Composable
fun GoalRing(
    progress : Float,   // 0f..1f
    modifier : Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val inset  = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)

        // Track
        drawArc(
            color       = FosColors.Surface2,
            startAngle  = -90f,
            sweepAngle  = 360f,
            useCenter   = false,
            topLeft     = topLeft,
            size        = arcSize,
            style       = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        // Progress arc
        if (progress > 0f) {
            drawArc(
                color       = FosColors.Positive,
                startAngle  = -90f,
                sweepAngle  = 360f * progress.coerceIn(0f, 1f),
                useCenter   = false,
                topLeft     = topLeft,
                size        = arcSize,
                style       = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
