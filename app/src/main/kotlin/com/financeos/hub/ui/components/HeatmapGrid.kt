package com.financeos.hub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.analytics.HeatmapData
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosType

private val DAY_LABELS   = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val HOUR_LABELS  = listOf("0", "6", "12", "18", "23")
private val HOUR_INDICES = listOf(0, 6, 12, 18, 23)

/**
 * 7-column × 24-row heatmap (day of week × hour of day).
 * Cell color intensity proportional to expense amount.
 *
 * @param data       HeatmapData from BehavioralAnalyzer
 * @param cellColor  Base color for filled cells (default: Negative for expenses)
 * @param cellSize   Size of each cell
 * @param gap        Gap between cells
 */
@Composable
fun HeatmapGrid(
    data      : HeatmapData,
    modifier  : Modifier   = Modifier,
    cellColor : Color      = FosColors.Negative,
    cellSize  : Dp         = 14.dp,
    gap       : Dp         = 2.dp,
) {
    Column(modifier = modifier) {
        // Day-of-week headers
        Row(modifier = Modifier.padding(start = 24.dp)) {
            DAY_LABELS.forEachIndexed { i, label ->
                Text(
                    text     = label,
                    style    = FosType.Micro,
                    color    = FosColors.TextMuted,
                    modifier = Modifier.width(cellSize + gap),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Row {
            // Hour labels on Y-axis
            Column(modifier = Modifier.width(24.dp)) {
                (0..23).forEach { hour ->
                    val labelIdx = HOUR_INDICES.indexOf(hour)
                    Text(
                        text     = if (labelIdx >= 0) HOUR_LABELS[labelIdx] else "",
                        style    = FosType.Micro,
                        color    = FosColors.TextMuted,
                        modifier = Modifier.height(cellSize + gap),
                    )
                }
            }

            // Grid canvas
            val totalW = (cellSize + gap) * 7
            val totalH = (cellSize + gap) * 24
            Canvas(
                modifier = Modifier
                    .width(totalW)
                    .height(totalH),
            ) {
                val cs = cellSize.toPx()
                val gs = gap.toPx()
                val step = cs + gs
                val radius = CornerRadius(cs * 0.25f)

                for (day in 0..6) {
                    for (hour in 0..23) {
                        val intensity = data.intensity(day, hour)
                        // min alpha 0.06 so empty cells are slightly visible
                        val alpha = if (intensity > 0f) 0.10f + intensity * 0.90f else 0.06f
                        drawRoundRect(
                            color       = if (intensity > 0f) cellColor.copy(alpha = alpha)
                                          else FosColors.Surface2.copy(alpha = 0.6f),
                            topLeft     = Offset(day * step, hour * step),
                            size        = Size(cs, cs),
                            cornerRadius= radius,
                        )
                    }
                }
            }
        }
    }
}
