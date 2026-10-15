package com.financeos.hub.features.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.parser.MerchantNames
import com.financeos.hub.ui.components.FosFormSheet
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.fosCardSurface
import kotlin.math.abs

/**
 * «Чем оплачено» (#56): вместо безымянной отметки «оплачено» человек показывает, КАКИМИ операциями
 * закрыт период. Приложение запоминает их продавцов и со следующего периода закрывает обязательство
 * само — кнопку больше нажимать не нужно. Наличные и чужая карта по-прежнему закрываются кнопкой
 * «Без операции».
 */
@Composable
internal fun SettleSheet(
    request  : CalendarViewModel.SettleRequest,
    onDismiss: () -> Unit,
    onSettle : (List<TransactionEntity>) -> Unit,
) {
    var chosen by remember(request.payment.id, request.dueDate) { mutableStateOf(emptySet<String>()) }
    val symbol = FosFormatter.currencySymbol(request.payment.currency)
    val sum = request.candidates.filter { it.id in chosen }.sumOf { abs(it.amountKopecks) }

    FosFormSheet(onDismiss = onDismiss, hasChanges = { chosen.isNotEmpty() }) {
        Text("Чем оплачено?", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
        Text(
            "${request.payment.title} · ${FosFormatter.compact(request.payment.amountKopecks, symbol)} · " +
                "срок ${FosFormatter.date(request.dueDate)}",
            style = FosType.Body,
            color = FosColors.TextSecondary,
        )
        Text(
            "Отметьте операции, которыми заплатили, — можно несколько. Приложение запомнит, кому идёт " +
                "этот платёж, и в следующий раз закроет его само.",
            style = FosType.Micro,
            color = FosColors.TextMuted,
        )

        if (request.candidates.isEmpty()) {
            Text(
                "Рядом со сроком подходящих операций нет. Если платили наличными или с чужой карты — " +
                    "отметьте «Без операции».",
                style = FosType.Body,
                color = FosColors.TextSecondary,
            )
        }

        request.candidates.forEach { tx ->
            val on = tx.id in chosen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fosCardSurface(
                        if (on) FosCardStyle.Rail else FosCardStyle.Plain,
                        if (on) FosTone.Info else FosTone.Neutral,
                        FosDimens.RadiusCardSmall,
                    )
                    .clickable { chosen = if (on) chosen - tx.id else chosen + tx.id }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked         = on,
                    onCheckedChange = { chosen = if (on) chosen - tx.id else chosen + tx.id },
                    colors          = CheckboxDefaults.colors(checkedColor = FosColors.Info),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        MerchantNames.display(tx.merchant ?: tx.description) ?: "Без названия",
                        style = FosType.BodySemi,
                        color = FosColors.TextPrimary,
                        maxLines = 1,
                    )
                    Text(FosFormatter.dayLabelYear(tx.timestamp), style = FosType.Micro, color = FosColors.TextMuted)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "−" + FosFormatter.compact(abs(tx.amountKopecks), FosFormatter.currencySymbol(tx.currency)),
                    style = FosType.SmallBold,
                    color = FosColors.TextPrimary,
                )
            }
        }

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onSettle(emptyList()) }) {
                Text("Без операции", style = FosType.Label, color = FosColors.TextSecondary)
            }
            TextButton(
                onClick = { onSettle(request.candidates.filter { it.id in chosen }) },
                enabled = chosen.isNotEmpty(),
            ) {
                Text(
                    if (chosen.isEmpty()) "Оплачено" else "Оплачено · ${FosFormatter.compact(sum, symbol)}",
                    style     = FosType.Label,
                    color     = if (chosen.isEmpty()) FosColors.TextMuted else FosColors.Positive,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
