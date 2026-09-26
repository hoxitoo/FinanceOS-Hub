package com.financeos.hub.features.analytics.lifetime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.analytics.LifetimeStats
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val MONTH_YEAR = DateTimeFormatter.ofPattern("LLL yyyy", Locale("ru"))

/** Подпись корзины под шаг: у года месяц не нужен, у полугодия нужен. */
fun periodLabel(p: LifetimeStats.CurvePoint, step: LifetimeStats.Step): String = when (step) {
    LifetimeStats.Step.MONTH     -> p.periodStart.format(MONTH_YEAR)
    LifetimeStats.Step.HALF_YEAR -> "${if (p.periodStart.monthValue <= 6) "I" else "II"} пол. ${p.periodStart.year}"
    LifetimeStats.Step.YEAR      -> "${p.periodStart.year}"
    LifetimeStats.Step.TWO_YEARS -> "${p.periodStart.year}–${p.periodStart.year + 1}"
}

/**
 * Две «ползущие» кривые на одном графике: всего заработано (зелёная) и всего потрачено (красная).
 *
 * Нарастающий итог, а не сумма за период: такой график отвечает на вопрос «сколько за всё время», и
 * расстояние между кривыми — это то, что осталось. Кривая зелёная выше красной — вы в плюсе за весь
 * срок; красная обгоняет — траты съели больше, чем пришло.
 *
 * Обе линии в ОДНОМ масштабе — от нуля до общего максимума. Свой масштаб у каждой красиво растянул
 * бы обе на всю высоту и сделал бы их сравнение бессмысленным.
 *
 * Касание выбирает ближайшую точку; её цифры — над графиком. Подписи на осях — только края: на
 * телефоне двадцать подписей по горизонтали не читаются.
 */
@Composable
fun CumulativeDualChart(
    points  : List<LifetimeStats.CurvePoint>,
    step    : LifetimeStats.Step,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val earnedColor = FosColors.Positive
    val spentColor  = FosColors.Negative
    val gridColor   = FosColors.Border
    val maxV = points.maxOf { maxOf(it.spent, it.earned) }.coerceAtLeast(1L)

    val shown = selected?.let { points.getOrNull(it) } ?: points.last()
    Column(modifier) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (selected == null) "на сегодня" else "к концу: ${periodLabel(shown, step)}",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
            Text(
                "разница ${FosFormatter.signedAmount(shown.earned - shown.spent)}",
                style = FosType.MicroNum,
                color = if (shown.earned >= shown.spent) FosColors.Positive else FosColors.Negative,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(earnedColor, "заработано", FosFormatter.amount(shown.earned))
            LegendDot(spentColor, "потрачено", FosFormatter.amount(shown.spent))
        }
        Spacer(Modifier.height(8.dp))

        // Точка ноль слева: иначе первая корзина начиналась бы в воздухе, и единственная корзина
        // вообще не дала бы линии.
        val n = points.size + 1
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        val i = ((tap.x / size.width) * (n - 1)).roundToInt() - 1
                        onSelect(if (i < 0) null else i.coerceAtMost(points.lastIndex))
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = if (n <= 1) 0f else i / (n - 1).toFloat() * w
            fun y(v: Long) = h - (v.toFloat() / maxV) * h * 0.92f

            // Сетка: четверти высоты — опора для глаза, не для чтения цифр.
            for (k in 1..3) {
                val gy = h - h * 0.92f * k / 4f
                drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
            }

            fun series(value: (LifetimeStats.CurvePoint) -> Long, color: Color) {
                val path = Path().apply {
                    moveTo(x(0), y(0))
                    points.forEachIndexed { i, p -> lineTo(x(i + 1), y(value(p))) }
                }
                drawPath(
                    path,
                    color,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            series({ it.earned }, earnedColor)
            series({ it.spent }, spentColor)

            selected?.let { i ->
                val sx = x(i + 1)
                drawLine(FosColors.TextMuted, Offset(sx, 0f), Offset(sx, h), strokeWidth = 1.5f)
                drawCircle(earnedColor, 5.dp.toPx(), Offset(sx, y(points[i].earned)))
                drawCircle(spentColor, 5.dp.toPx(), Offset(sx, y(points[i].spent)))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(periodLabel(points.first(), step), style = FosType.Micro, color = FosColors.TextMuted)
            Text("макс. ${FosFormatter.compact(maxV)}", style = FosType.MicroNum, color = FosColors.TextMuted)
            Text(periodLabel(points.last(), step), style = FosType.Micro, color = FosColors.TextMuted)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, style = FosType.Micro, color = FosColors.TextMuted)
            Text(value, style = FosType.SmallBold, color = FosColors.TextPrimary)
        }
    }
}

/**
 * Бары по годам, внутри — категории.
 *
 * Слои окрашены цветом категории, поэтому уже по бару видно, из чего состоял год: вырос ли он за
 * счёт одной категории или всех понемногу. Касание выбирает год — его разбивка открывается ниже.
 * Высота — от общего максимума по годам: иначе каждый бар был бы в полный рост, и сравнивать годы
 * стало бы нечем.
 *
 * Горизонтальная прокрутка — сознательно: двадцать лет в ширину телефона не помещаются, а сжать
 * бары до волоска значило бы потерять и цвета внутри, и попадание пальцем.
 */
@Composable
fun YearStackedBars(
    bars       : List<LifetimeStats.YearBar>,
    colorOf    : (String?) -> Color,
    selected   : Int?,
    accent     : Color,
    onSelect   : (Int?) -> Unit,
) {
    if (bars.isEmpty()) return
    val maxTotal = bars.maxOf { it.total }.coerceAtLeast(1L)
    val barMax   = 140.dp

    Row(
        modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.Bottom,
    ) {
        bars.forEach { bar ->
            val isSel = selected == bar.year
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(FosDimens.RadiusChip))
                    .clickable { onSelect(if (isSel) null else bar.year) }
                    .padding(4.dp),
            ) {
                Text(
                    FosFormatter.compact(bar.total),
                    style = FosType.MicroNum,
                    color = if (isSel) accent else FosColors.TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                val barHeight = barMax * (bar.total.toFloat() / maxTotal)
                Column(
                    modifier = Modifier
                        .width(40.dp)
                        .height(barHeight.coerceAtLeast(4.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .border(
                            width = if (isSel) 2.dp else 0.dp,
                            color = if (isSel) accent else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                        ),
                ) {
                    // Сверху — самые мелкие категории, снизу — крупные: основание бара держит главное.
                    bar.segments.asReversed().forEach { seg ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(seg.kopecks.toFloat().coerceAtLeast(1f))
                                .background(colorOf(seg.categoryId)),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${bar.year}",
                    style = FosType.MicroNum,
                    color = if (isSel) accent else FosColors.TextMuted,
                )
            }
        }
    }
}

/** Строка «категория — сумма — доля» под выбранным годом и под круговой диаграммой. */
@Composable
fun ShareRow(color: Color, name: String, kopecks: Long, total: Long) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(name, style = FosType.Body, color = FosColors.TextPrimary, modifier = Modifier.weight(1f))
        Text(
            "${(kopecks * 100.0 / total.coerceAtLeast(1L)).roundToInt()}%",
            style = FosType.MicroNum,
            color = FosColors.TextMuted,
        )
        Spacer(Modifier.width(10.dp))
        Text(FosFormatter.amount(abs(kopecks)), style = FosType.SmallBold, color = FosColors.TextPrimary)
    }
}
