package com.financeos.hub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.financeos.hub.ui.theme.FosColors

@Composable
fun LineChart(
    data     : List<Float>,
    color    : Color      = FosColors.Positive,
    modifier : Modifier   = Modifier.fillMaxSize(),
) {
    // Sanitise: NaN/Infinity from empty-bucket or overflow calculations must not reach Canvas.
    val safe = data.map { if (it.isFinite()) it else 0f }
    if (safe.size < 2 || safe.all { it == 0f }) return

    Canvas(modifier = modifier) {
        val w     = size.width
        val h     = size.height
        val maxV  = safe.max().coerceAtLeast(1f)
        val minV  = safe.min()
        val range = (maxV - minV).coerceAtLeast(1f)

        fun xAt(i: Int) = i / (safe.size - 1).toFloat() * w
        fun yAt(v: Float) = h - ((v - minV) / range) * h * 0.9f - h * 0.05f

        // Fill path (gradient area)
        val fillPath = Path().apply {
            moveTo(xAt(0), h)
            lineTo(xAt(0), yAt(safe[0]))
            safe.forEachIndexed { i, v ->
                if (i > 0) {
                    val cx = (xAt(i - 1) + xAt(i)) / 2f
                    cubicTo(
                        cx, yAt(safe[i - 1]),
                        cx, yAt(v),
                        xAt(i), yAt(v),
                    )
                }
            }
            lineTo(xAt(safe.size - 1), h)
            close()
        }
        drawPath(fillPath, color = color.copy(alpha = 0.10f))

        // Stroke path (line)
        val linePath = Path().apply {
            moveTo(xAt(0), yAt(safe[0]))
            safe.forEachIndexed { i, v ->
                if (i > 0) {
                    val cx = (xAt(i - 1) + xAt(i)) / 2f
                    cubicTo(
                        cx, yAt(safe[i - 1]),
                        cx, yAt(v),
                        xAt(i), yAt(v),
                    )
                }
            }
        }
        drawPath(
            path  = linePath,
            color = color,
            style = Stroke(
                width      = 2.5f,
                cap        = StrokeCap.Round,
                join       = StrokeJoin.Round,
            ),
        )

        // Dot at last point
        drawCircle(
            color  = color,
            radius = 5f,
            center = Offset(xAt(safe.size - 1), yAt(safe.last())),
        )
    }
}
