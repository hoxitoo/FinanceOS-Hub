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
 * Решение «показывать ли плитку» принимает ВЫЗЫВАЮЩИЙ, а не она сама. Ранний `return` внутри
 * композиции не убирает `item {}` из списка — остаётся пустая ячейка, а `spacedBy` добавляет к ней
 * отступ, и на главной появляется необъяснимая дыра. Плитка не показывается, пока календарь пуст:
 * без обязательств «Свободно» вырождается в «остаток минус резерв», то есть в нетто-капитал
 * строчкой выше под другим именем.
 *
 */
@Composable
fun FreeMoneyTile(
    free   : FreeMoney.FreeMoneyBreakdown,
    onClick: () -> Unit,
) {
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
