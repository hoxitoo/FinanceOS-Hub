package com.financeos.hub.features.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.calendar.CalendarEvent
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.fosCard
import java.time.LocalDate
import java.time.YearMonth

/**
 * Сетка месяца — второй режим календаря рядом с полосой.
 *
 * Полоса и сетка отвечают на разные вопросы, поэтому одна другую не заменяет. Полоса — «что дальше»:
 * она сжата к горизонту, и ближайшая дата на ней всегда видна первой. Сетка — «как устроен месяц»:
 * где сгущение платежей, где пусто, куда можно подвинуть покупку. Ответ «что дальше» с сетки
 * читается плохо (глаз ищет сегодня среди тридцати клеток), ответ «как устроен месяц» с полосы не
 * читается вообще.
 *
 * Сетка работает как ФИЛЬТР: выбранный день оставляет в списке ниже только свои события. Поэтому у
 * неё нет собственного списка — иначе одни и те же строки жили бы на экране в двух местах и
 * разошлись бы при первой же правке.
 *
 * Данные берутся из окна построения, а не из [CalendarState.upcoming]: горизонт обычно короче месяца
 * (до следующей зарплаты), и сетка была бы пустой со своей середины.
 */
@Composable
internal fun MonthGrid(
    month       : YearMonth,
    events      : List<CalendarEvent>,
    today       : LocalDate,
    selected    : LocalDate?,
    canGoBack   : Boolean,
    canGoForward: Boolean,
    onMonth     : (YearMonth) -> Unit,
    onSelect    : (LocalDate?) -> Unit,
) {
    val byDate = events.groupBy { it.date }

    Column(
        modifier            = Modifier.fillMaxWidth().fosCard(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            MonthArrow("‹", canGoBack) { onMonth(month.minusMonths(1)) }
            Text(
                FosFormatter.monthYear(month.atDay(1)),
                style = FosType.BodySemi,
                color = FosColors.TextPrimary,
            )
            MonthArrow("›", canGoForward) { onMonth(month.plusMonths(1)) }
        }

        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEachIndexed { index, label ->
                Text(
                    label,
                    style     = FosType.Micro,
                    // Выходные приглушены: это не «плохие» дни, просто у них другой ритм платежей.
                    color     = if (index >= 5) FosColors.TextMuted else FosColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.weight(1f),
                )
            }
        }

        // Неделя начинается с понедельника: у DayOfWeek понедельник = 1, поэтому сдвиг ровно
        // «номер дня недели минус один».
        val leading = month.atDay(1).dayOfWeek.value - 1
        val length  = month.lengthOfMonth()
        // Строк ровно столько, сколько нужно этому месяцу. Фиксированные шесть оставляли бы в
        // феврале пустую полосу высотой в неделю.
        val rows    = (leading + length + 6) / 7

        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val dayNumber = row * 7 + column - leading + 1
                    if (dayNumber < 1 || dayNumber > length) {
                        // Соседний месяц: клетка остаётся пустой, но занимает место — иначе строка
                        // съезжает и число оказывается не под своим днём недели.
                        Box(Modifier.weight(1f).height(CELL_HEIGHT))
                    } else {
                        val date = month.atDay(dayNumber)
                        DayCell(
                            date       = date,
                            events     = byDate[date].orEmpty(),
                            isToday    = date == today,
                            isSelected = date == selected,
                            modifier   = Modifier.weight(1f),
                            // Повторное нажатие снимает выбор: иначе из отфильтрованного дня
                            // нельзя выйти, не переключив режим.
                            onClick    = { onSelect(if (date == selected) null else date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        glyph,
        style    = FosType.IconAction,
        // Выключенная стрелка не исчезает, а гаснет: пропадающий элемент сдвигает заголовок месяца.
        color    = if (enabled) FosColors.TextSecondary else FosColors.Border,
        modifier = Modifier
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

@Composable
private fun DayCell(
    date      : LocalDate,
    events    : List<CalendarEvent>,
    isToday   : Boolean,
    isSelected: Boolean,
    modifier  : Modifier = Modifier,
    onClick   : () -> Unit,
) {
    Box(
        modifier = modifier
            .height(CELL_HEIGHT)
            .padding(1.dp)
            .clip(RoundedCornerShape(FosDimens.RadiusInset))
            .then(
                when {
                    isSelected -> Modifier.background(FosColors.Surface2)
                    isToday    -> Modifier.border(1.dp, FosColors.TextMuted, RoundedCornerShape(FosDimens.RadiusInset))
                    else       -> Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = FosType.MicroNum,
                color = when {
                    isSelected || isToday -> FosColors.TextPrimary
                    events.isNotEmpty()   -> FosColors.TextPrimary
                    // День без событий не должен спорить за внимание с днём, где есть платёж.
                    else                  -> FosColors.TextMuted
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                events.take(MAX_DOTS).forEach { event ->
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            // Закрытое обязательство гаснет: оно уже не требует денег, и держать
                            // его в том же цвете, что и предстоящее, значит звать к действию зря.
                            .background(if (event.settled) FosColors.Border else kindColor(event))
                    )
                }
            }
        }
    }
}

/** Больше трёх точек в клетке шириной в палец не различить — дальше это просто серая полоска. */
private const val MAX_DOTS = 3

private val CELL_HEIGHT = 44.dp

private val WEEKDAYS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
