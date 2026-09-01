package com.financeos.hub.features.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.calendar.FreeMoney
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.fosCardSurface

/**
 * «Свободно» на главном экране — вход в календарь.
 *
 * Данные приходят снаружи, из того же [CalendarViewModel], что питает календарь: два места,
 * считающие одно и то же число разными путями, рано или поздно расходятся, и объяснить человеку,
 * какому из них верить, будет нечем.
 *
 * **Плитка — единственный вход в календарь, поэтому она видна ВСЕГДА.** Скрывая её «пока нечего
 * показывать», мы делали функцию недостижимой: обязательство добавляется только на экране
 * календаря, а попасть туда можно было только через плитку, которая появлялась после первого
 * обязательства. Замкнутый круг, в котором календаря для человека просто не существует.
 *
 * Поэтому у плитки два вида. С обязательствами — «Свободно» и разбор. Без них — приглашение: без
 * обязательств «Свободно» вырождается в «остаток минус резерв», то есть повторяет нетто-капитал
 * строчкой выше, и показывать его как число значило бы врать о новизне.
 *
 * Решение «показывать ли что-либо» всё равно принимает ВЫЗЫВАЮЩИЙ, снаружи `item {}`: ранний
 * `return` внутри композиции оставляет в списке пустую ячейку, а `spacedBy` добавляет ей отступ.
 */
@Composable
fun FreeMoneyTile(
    free   : FreeMoney.FreeMoneyBreakdown?,
    onClick: () -> Unit,
) {
    if (free == null || free.obligationCount == 0) {
        CalendarInviteTile(onClick)
        return
    }
    val tone   = if (free.freeKopecks >= 0) FosTone.Positive else FosTone.Negative
    val accent = tone.accent ?: FosColors.TextPrimary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fosCardSurface(FosCardStyle.Rail, tone, FosDimens.RadiusCard)
            .clickable { onClick() }
            .padding(FosDimens.CardPadding),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                "Свободно до ${FosFormatter.date(free.horizon)}",
                style = FosType.SectionCap,
                color = FosColors.TextMuted,
            )
            Text("›", style = FosType.IconAction, color = FosColors.TextMuted)
        }

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(FosFormatter.amount(free.freeKopecks), style = FosType.CardAmount, color = accent)
            free.dailyAllowanceKopecks?.let { perDay ->
                Spacer(Modifier.width(8.dp))
                Text(
                    "≈ ${FosFormatter.compact(perDay)} в день",
                    style = FosType.MicroNum,
                    color = FosColors.TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            "Учтено ${pluralPayments(free.obligationCount)} на ${FosFormatter.compact(free.obligationsKopecks)}",
            style = FosType.MicroNum,
            color = FosColors.TextMuted,
        )
    }
}

/**
 * Вид плитки, когда обязательств ещё нет: вход и объяснение одной карточкой.
 *
 * `Outline` — стиль призыва к действию по правилам огранки. Числа здесь нет намеренно: пока в
 * календаре пусто, любое число дублировало бы нетто-капитал строчкой выше.
 */
@Composable
private fun CalendarInviteTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fosCardSurface(FosCardStyle.Outline, FosTone.Info, FosDimens.RadiusCard)
            .clickable { onClick() }
            .padding(FosDimens.CardPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text("Календарь платежей", style = FosType.BodySemi, color = FosColors.TextPrimary)
            Text("›", style = FosType.IconAction, color = FosColors.TextMuted)
        }
        Text(
            "Добавьте аренду и подтвердите найденные подписки — и увидите «Свободно»: " +
                "сколько можно потратить, ничего не сломав.",
            style = FosType.Micro,
            color = FosColors.TextSecondary,
        )
    }
}

private fun pluralPayments(n: Int): String {
    val mod100 = n % 100
    val mod10  = n % 10
    return when {
        mod100 in 11..14 -> "$n платежей"
        mod10 == 1       -> "$n платёж"
        mod10 in 2..4    -> "$n платежа"
        else             -> "$n платежей"
    }
}
