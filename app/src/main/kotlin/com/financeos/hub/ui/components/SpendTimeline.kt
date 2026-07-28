package com.financeos.hub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

private val PLOT_HEIGHT = 148.dp

/** Micro + tabular figures — axis ticks and summary values align instead of shifting. */
private val TNUM_MICRO = FosType.Micro.copy(fontFeatureSettings = "tnum")

/**
 * Daily-spending timeline.
 *
 * Replaces a smoothed line+area that carried no scale at all: it had no y-axis, no values
 * and no way to inspect a point, so a spike could have been 500 ₽ or 50 000 ₽ and the
 * reader had no way to tell. Columns are also the honest mark here — daily spending is a
 * set of independent amounts, not a continuous signal, and the cubic-bezier smoothing was
 * inventing values between days (and overshooting on either side of an empty day).
 *
 * Values are readable three ways, so nothing is gated behind the interaction: the summary
 * line under the chart, the average/peak reference lines, and tap-or-scrub for one column.
 */
@Composable
fun SpendTimeline(
    daily   : List<Pair<Long, Long>>,
    modifier: Modifier = Modifier,
) {
    // Rules of Hooks: every remember runs before the empty-data early return below, so
    // toggling a period chip that empties the list can't corrupt the slot table.
    val series = remember(daily) { buildSpendSeries(daily) }
    var selected by remember(series) { mutableStateOf<Int?>(null) }

    val buckets = series.buckets
    if (buckets.isEmpty()) {
        Text("Нет данных за выбранный период", style = FosType.Body, color = FosColors.TextMuted)
        return
    }

    val maxV    = buckets.maxOf { it.kopecks }.coerceAtLeast(1L)
    val avg     = series.average
    val avgFrac = (avg.toFloat() / maxV).coerceIn(0f, 1f)
    val peakIdx = buckets.indexOfFirst { it.kopecks == maxV }.takeIf { maxV > 0 }
    // Only ever ONE column is emphasised: the tapped one, or the peak when nothing is tapped.
    val emphasised = selected ?: peakIdx
    val current    = selected?.let(buckets::getOrNull)

    Column(modifier.fillMaxWidth()) {

        // ── Readout: the selected column, or the period summary when nothing is selected.
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                current?.rangeLabel ?: "Всего за период",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
            Text(
                FosFormatter.compact(current?.kopecks ?: series.total),
                // Project rule: every monetary Text is tabular, so scrubbing across
                // columns doesn't make the readout jitter as digit widths change.
                style = FosType.BodySemi.copy(fontFeatureSettings = "tnum"),
                color = FosColors.Negative,
            )
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PLOT_HEIGHT),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // Two detectors: the drag one only fires past touch slop, so a plain tap
                    // falls through to the tap detector. Horizontal-only, so scrubbing never
                    // fights the vertical scroll of the Trends tab.
                    .pointerInput(buckets.size) {
                        detectTapGestures { pos ->
                            val i = (pos.x / size.width * buckets.size).toInt()
                            selected = i.coerceIn(0, buckets.lastIndex)
                                .takeIf { selected != it }
                        }
                    }
                    .pointerInput(buckets.size) {
                        detectHorizontalDragGestures(
                            onDragStart = { pos ->
                                selected = (pos.x / size.width * buckets.size).toInt()
                                    .coerceIn(0, buckets.lastIndex)
                            },
                        ) { change, _ ->
                            selected = (change.position.x / size.width * buckets.size).toInt()
                                .coerceIn(0, buckets.lastIndex)
                        }
                    },
            ) {
                val n       = buckets.size
                val slot    = size.width / n
                val gapPx   = 2.dp.toPx()                       // surface gap, not a border
                val barW    = (slot - gapPx).coerceIn(1f, 24.dp.toPx())
                val radius  = 4.dp.toPx()
                val minBar  = 2.dp.toPx()

                // Reference lines: hairline, solid, one step off the surface.
                drawLine(
                    color       = FosColors.TextMuted.copy(alpha = 0.18f),
                    start       = Offset(0f, 0f),
                    end         = Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
                if (avgFrac > 0f) {
                    val y = size.height * (1f - avgFrac)
                    drawLine(
                        color       = FosColors.TextMuted.copy(alpha = 0.45f),
                        start       = Offset(0f, y),
                        end         = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                }

                buckets.forEachIndexed { i, bucket ->
                    if (bucket.kopecks <= 0L) return@forEachIndexed   // a real zero draws nothing
                    val raw = (bucket.kopecks.toFloat() / maxV) * size.height
                    val h   = raw.coerceAtLeast(minBar)              // a tiny day still shows
                    val x   = i * slot + (slot - barW) / 2f
                    val top = size.height - h
                    val r   = radius.coerceAtMost(h / 2f)
                    drawPath(
                        path = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect        = Rect(x, top, x + barW, size.height),
                                    topLeft     = CornerRadius(r, r),
                                    topRight    = CornerRadius(r, r),
                                    bottomLeft  = CornerRadius.Zero,
                                    bottomRight = CornerRadius.Zero,
                                )
                            )
                        },
                        color = FosColors.Negative.copy(alpha = if (i == emphasised) 1f else 0.5f),
                    )
                }

                // Guide on the tapped column, so the readout is unambiguously tied to a bar.
                selected?.let { i ->
                    val cx = i * slot + slot / 2f
                    drawLine(
                        color       = FosColors.TextPrimary.copy(alpha = 0.35f),
                        start       = Offset(cx, 0f),
                        end         = Offset(cx, size.height),
                        strokeWidth = 1f,
                    )
                }
            }

            // Scale labels ride the lines as real Text — the axis the old chart never had.
            // Skipped when the two would collide (avg close to max).
            if (avgFrac < 0.82f) {
                Text(
                    FosFormatter.compact(maxV),
                    style    = TNUM_MICRO,
                    color    = FosColors.TextMuted,
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 2.dp),
                )
            }
            if (avgFrac > 0.06f) {
                Text(
                    "среднее ${FosFormatter.compact(avg)}",
                    style    = TNUM_MICRO,
                    color    = FosColors.TextMuted,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = PLOT_HEIGHT * (1f - avgFrac) - 13.dp)
                        .padding(end = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // ── X axis: start / middle / end, so a spike can actually be placed in time.
        Row(modifier = Modifier.fillMaxWidth()) {
            val mid = buckets[buckets.size / 2]
            listOf(
                buckets.first().axisLabel to TextAlign.Start,
                (if (buckets.size >= 5) mid.axisLabel else "") to TextAlign.Center,
                buckets.last().axisLabel to TextAlign.End,
            ).forEach { (label, align) ->
                Text(
                    label,
                    style     = FosType.Micro,
                    color     = FosColors.TextMuted,
                    textAlign = align,
                    modifier  = Modifier.weight(1f),
                )
            }
        }

        // ── Summary: every headline value is here in text, so tapping is an enhancement
        //    and never the only way to read a number.
        series.peak?.let { peak ->
            Spacer(Modifier.height(8.dp))
            val unit = when (series.size) {
                SpendBucketSize.DAY   -> "в день"
                SpendBucketSize.WEEK  -> "в неделю"
                SpendBucketSize.MONTH -> "в месяц"
            }
            Text(
                "Пик — ${peak.rangeLabel}: ${FosFormatter.compact(peak.kopecks)}",
                style = TNUM_MICRO,
                color = FosColors.Negative,
            )
            Text(
                "В среднем ${FosFormatter.compact(avg)} $unit",
                style = TNUM_MICRO,
                color = FosColors.TextMuted,
            )
        }
    }
}
