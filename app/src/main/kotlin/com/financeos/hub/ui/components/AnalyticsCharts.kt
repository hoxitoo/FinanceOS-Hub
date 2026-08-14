package com.financeos.hub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.analytics.WaterfallBar
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Small "ⓘ" affordance that opens a plain-language explanation of how a metric is computed.
 * Every behavioural card gets one — a number the user can't verify is a number they won't trust.
 */
@Composable
fun InfoBadge(
    title: String,
    body : String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(FosColors.info(0.16f))
            .clickable { open = true },
        contentAlignment = Alignment.Center,
    ) {
        Text("?", style = FosType.Micro, color = FosColors.Info)
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor   = FosColors.Surface,
            title   = { Text(title, style = FosType.BodySemi, color = FosColors.TextPrimary) },
            text    = { Text(body, style = FosType.Body, color = FosColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text("Понятно", color = FosColors.Info)
                }
            },
        )
    }
}

/**
 * Card header: caption, the hairline rule that separates it from the block above, and an
 * [InfoBadge] pinned to the right.
 *
 * Shares [FosSectionHeader] with the rest of the app rather than drawing its own row, so a header
 * that happens to carry an explanation is still visually the same object as one that doesn't.
 */
@Composable
fun SectionHeader(
    title    : String,
    infoTitle: String,
    infoBody : String,
    tone     : FosTone = FosTone.Neutral,
) {
    FosSectionHeader(
        title    = title,
        tone     = tone,
        trailing = { InfoBadge(title = infoTitle, body = infoBody) },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Месяц к месяцу
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Month-over-month comparison as diverging bars with the raw "было → стало" figures.
 *
 * Replaces the waterfall chart, which nobody could read: it showed only a difference, and a rise in
 * SPENDING was drawn green-with-a-plus (because internally `delta = prev − current`), which reads as
 * "good/income". Here the semantics are explicit — a bar to the RIGHT in red means you spent more,
 * to the LEFT in green means you spent less.
 */
@Composable
fun MoMComparison(bars: List<WaterfallBar>) {
    val rows = bars.filter { !it.isTotal }
    val maxAbs = (rows.maxOfOrNull { abs(it.currentKopecks - it.prevKopecks) } ?: 1L).coerceAtLeast(1L)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { bar ->
            // Work from the RAW change, so the sign the user sees matches the numbers below it.
            val rawChange = bar.currentKopecks - bar.prevKopecks
            // Growing income is good; growing spending is bad.
            val better = if (bar.isIncome) rawChange > 0 else rawChange < 0
            val color  = if (better) FosColors.Positive else FosColors.Negative
            val share  = (abs(rawChange).toFloat() / maxAbs).coerceIn(0f, 1f)

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(bar.label, style = FosType.Body, color = FosColors.TextPrimary, maxLines = 1)
                    Text(
                        (if (rawChange >= 0) "+" else "−") + FosFormatter.compact(abs(rawChange)),
                        style = FosType.SmallBold,
                        color = color,
                    )
                }

                // Diverging bar around a centre line: left = spent less, right = spent more.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(FosColors.Surface2),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(share * 0.5f)
                            .align(if (better) Alignment.CenterStart else Alignment.CenterEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                    )
                    // Centre tick
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .align(Alignment.Center)
                            .background(FosColors.Border)
                    )
                }

                Text(
                    "было ${FosFormatter.compact(bar.prevKopecks)} → стало ${FosFormatter.compact(bar.currentKopecks)}",
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Усталость бюджета
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Average spend per day-of-month as bars, with an average reference line and the peak day labelled.
 * A bare line chart gave no way to tell WHICH day a spike belonged to.
 */
@Composable
fun FatigueBars(
    dailyAverages: List<Pair<Int, Long>>,
    modifier     : Modifier = Modifier,
) {
    if (dailyAverages.isEmpty()) return
    val maxVal  = (dailyAverages.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val avg     = dailyAverages.map { it.second }.average().toFloat()
    val peakDay = dailyAverages.maxByOrNull { it.second }?.first

    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val n     = dailyAverages.size
                val gap   = 2f
                val barW  = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)

                // Average reference line — instantly shows which days are above the norm.
                val avgY = size.height - (avg / maxVal) * size.height
                drawLine(
                    color = FosColors.TextMuted.copy(alpha = 0.5f),
                    start = Offset(0f, avgY),
                    end   = Offset(size.width, avgY),
                    strokeWidth = 1f,
                )

                dailyAverages.forEachIndexed { i, (day, value) ->
                    val h = (value.toFloat() / maxVal) * size.height
                    val x = i * (barW + gap)
                    // Peak day highlighted; above-average days warm; the rest muted.
                    val color = when {
                        day == peakDay            -> FosColors.Negative
                        value.toFloat() > avg     -> FosColors.Warning
                        else                      -> FosColors.Info.copy(alpha = 0.55f)
                    }
                    drawRect(
                        color   = color,
                        topLeft = Offset(x, size.height - h),
                        size    = Size(barW, h),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("1-е", style = FosType.Micro, color = FosColors.TextMuted)
            Text("15-е", style = FosType.Micro, color = FosColors.TextMuted)
            Text("31-е", style = FosType.Micro, color = FosColors.TextMuted)
        }
        peakDay?.let { d ->
            val peakValue = dailyAverages.first { it.first == d }.second
            Spacer(Modifier.height(6.dp))
            Text(
                "Пик — $d-е число: в среднем ${FosFormatter.compact(peakValue)} в день",
                style = FosType.Micro,
                color = FosColors.Negative,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Когда ты тратишь — кликабельные кольцевые диаграммы
// ─────────────────────────────────────────────────────────────────────────────

/** One slice of [SegmentedDonut]. */
data class DonutSlice(
    val label   : String,
    val kopecks : Long,
    val color   : Color,
)

/**
 * Clickable multi-colour donut. Tapping a slice selects it and reports the index; the centre shows
 * the selected slice's share, or the total when nothing is picked.
 *
 * Used instead of the 7×24 heat grid, which was tall, dense and unreadable on a phone.
 */
@Composable
fun SegmentedDonut(
    slices     : List<DonutSlice>,
    selected   : Int?,
    onSelect   : (Int?) -> Unit,
    modifier   : Modifier = Modifier,
    centreTitle: String,
) {
    val total = slices.sumOf { it.kopecks }.coerceAtLeast(1L)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(slices, selected) {
                    detectTapGestures { tap ->
                        val c  = Offset(size.width / 2f, size.height / 2f)
                        val dx = tap.x - c.x
                        val dy = tap.y - c.y
                        val r  = hypot(dx, dy)
                        val outer = min(size.width, size.height) / 2f
                        // Ignore taps outside the ring or in the hole.
                        if (r > outer || r < outer * 0.45f) { onSelect(null); return@detectTapGestures }
                        // Angle measured clockwise from 12 o'clock, matching the drawing order.
                        var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                        if (deg < 0f) deg += 360f
                        var acc = 0f
                        slices.forEachIndexed { i, s ->
                            val sweep = 360f * (s.kopecks.toFloat() / total)
                            if (deg >= acc && deg < acc + sweep) {
                                onSelect(if (selected == i) null else i)
                                return@detectTapGestures
                            }
                            acc += sweep
                        }
                    }
                }
        ) {
            val stroke  = this.size.minDimension * 0.22f
            val inset   = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            var angle   = -90f

            slices.forEachIndexed { i, s ->
                val sweep = 360f * (s.kopecks.toFloat() / total)
                if (sweep > 0f) {
                    val dim = selected != null && selected != i
                    drawArc(
                        color      = if (dim) s.color.copy(alpha = 0.22f) else s.color,
                        startAngle = angle,
                        sweepAngle = maxOf(sweep - 1.5f, 0.5f),   // hairline gap between slices
                        useCenter  = false,
                        topLeft    = topLeft,
                        size       = arcSize,
                        style      = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                }
                angle += sweep
            }
        }

        // Centre readout
        val sel = selected?.let { slices.getOrNull(it) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (sel != null) {
                Text(sel.label, style = FosType.Micro, color = FosColors.TextSecondary, maxLines = 1)
                Text(
                    "${((sel.kopecks.toFloat() / total) * 100).toInt()}%",
                    style = FosType.BodySemi,
                    color = sel.color,
                )
                Text(FosFormatter.compact(sel.kopecks), style = FosType.Micro, color = FosColors.TextMuted)
            } else {
                Text(centreTitle, style = FosType.Micro, color = FosColors.TextMuted, maxLines = 1)
                Text(FosFormatter.compact(total), style = FosType.SmallBold, color = FosColors.TextPrimary)
            }
        }
    }
}
