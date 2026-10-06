package com.financeos.hub.features.investor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.invest.Portfolio
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.fosCardSurface

/**
 * Плитка «У брокера» в КОШЕЛЬКЕ (#53). Перевод брокеру уводит деньги из итога кошелька, и без неё они
 * просто исчезали: десять тысяч ушли «в никуда». Плитка показывает, где они, — ИТОГОМ по валютам и
 * только им (#44: бумаг, сделок и результата кошелёк не видит). В «Всего» эти деньги не входят: их
 * стоимость — по цене своих сделок, а не по рынку, и смешать её с остатками на картах значило бы
 * выдать оценку за деньги. Нажатие открывает режим инвестора.
 */
@Composable
fun BrokerWalletTile(summaries: List<Portfolio.Summary>, onClick: () -> Unit) {
    Row(
        // Нажатие — между огранкой и отступом (FosSurface): иначе поле вокруг текста мёртвое.
        modifier = Modifier
            .fillMaxWidth()
            .fosCardSurface(FosCardStyle.Plain, FosTone.Neutral, FosDimens.RadiusCard)
            .clickable(onClick = onClick)
            .padding(FosDimens.CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("У брокера", style = FosType.BodySemi, color = FosColors.TextPrimary)
            Text(
                "не входит в «Всего» · по цене ваших сделок",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            // Валюты не складываются — строка на валюту.
            summaries.forEach { s ->
                Text(
                    FosFormatter.amount(s.totalKopecks, FosFormatter.currencySymbol(s.currency)),
                    style = FosType.SmallBold,
                    color = FosColors.TextPrimary,
                )
            }
            Text("Открыть ›", style = FosType.Micro, color = FosColors.TextSecondary)
        }
    }
}
