package com.financeos.hub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.financeos.hub.core.analytics.WaterfallBar
import com.financeos.hub.ui.theme.FosColors
import kotlin.math.abs

/**
 * Waterfall chart for month-over-month breakdown.
 * Green bars = positive delta (income up, expense down).
 * Red bars   = negative delta (expense growth).
 * Gray bars  = total/subtotal markers.
 */
@Composable
fun WaterfallChart(
    bars     : List<WaterfallBar>,
    modifier : Modifier = Modifier.fillMaxSize(),
) {
    if (bars.isEmpty()) return

    Canvas(modifier = modifier) {
        val w       = size.width
        val h       = size.height
        val n       = bars.size
        val barW    = w / (n * 1.6f)
        val gap     = (w - barW * n) / (n + 1)

        val maxAbs  = bars.maxOf { abs(it.delta) }.coerceAtLeast(1L).toFloat()
        val midY    = h * 0.5f
        val scaleY  = (h * 0.45f) / maxAbs

        var runningY = midY  // current baseline (floats up/down as bars accumulate)

        // baseline zero line
        drawLine(
            color       = FosColors.Border,
            start       = Offset(0f, midY),
            end         = Offset(w, midY),
            strokeWidth = 1.5f,
            pathEffect  = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
        )

        bars.forEachIndexed { i, bar ->
            val x       = gap + i * (barW + gap)
            val barH    = abs(bar.delta) * scaleY
            val color: Color = when {
                bar.isTotal  -> FosColors.Info
                bar.delta >= 0 -> FosColors.Positive
                else           -> FosColors.Negative
            }

            val top: Float
            val connectorY: Float

            if (bar.isTotal) {
                // Total bar always starts at midY (zero baseline)
                top        = if (bar.delta >= 0) midY - barH else midY
                connectorY = if (bar.delta >= 0) midY - barH else midY + barH
            } else if (bar.delta >= 0) {
                top        = runningY - barH
                connectorY = runningY - barH
                runningY  -= barH
            } else {
                top        = runningY
                connectorY = runningY + barH
                runningY  += barH
            }

            drawRect(
                color   = color.copy(alpha = 0.85f),
                topLeft = Offset(x, top),
                size    = Size(barW, barH.coerceAtLeast(2f)),
            )

            // Connector line to next bar
            if (!bar.isTotal && i < bars.size - 1) {
                drawLine(
                    color       = FosColors.Border,
                    start       = Offset(x + barW, connectorY),
                    end         = Offset(x + barW + gap, connectorY),
                    strokeWidth = 1f,
                    pathEffect  = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)),
                )
            }
        }
    }
}
