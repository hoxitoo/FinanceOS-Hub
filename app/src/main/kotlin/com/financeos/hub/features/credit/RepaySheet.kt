package com.financeos.hub.features.credit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.features.dashboard.accountSheetFieldColors
import com.financeos.hub.ui.components.FosFormSheet
import com.financeos.hub.ui.theme.AmountVisualTransformation
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

/**
 * Records a repayment: how much, and from which of your accounts.
 *
 * The amount is prefilled with whatever the card is actually asking for — the bank's demand when
 * it sent one, otherwise the computed statement debt — because "pay what is due" is the case
 * nearly every time, and retyping a figure the app already knows invites a typo.
 *
 * Nothing here talks to a bank. It records a payment the user is making (or has just made)
 * elsewhere, so the app's numbers match reality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepaySheet(
    card       : CreditCardState,
    payFrom    : List<AccountEntity>,
    onDismiss  : () -> Unit,
    onConfirm  : (sourceAccountId: String, amountKopecks: Long) -> Unit,
) {
    val suggested = card.duePayment?.amountKopecks?.takeIf { it > 0L } ?: card.debt
    // Keyed on the card: opening this for a second card must not keep the first one's amount.
    var amountText by remember(card.account.id) {
        mutableStateOf(if (suggested > 0L) FosFormatter.amountInput(suggested) else "")
    }
    var sourceId by remember(card.account.id) { mutableStateOf(payFrom.firstOrNull()?.id) }

    val amount   = FosFormatter.parseAmountInput(amountText) ?: 0L
    val source   = payFrom.firstOrNull { it.id == sourceId }
    val canPay   = amount > 0L && source != null
    // Not a hard block — an account can hold money the app has not seen yet, and refusing to
    // record a payment the user really made would leave the numbers wrong on purpose.
    val overdraws = source != null && amount > source.balanceKopecks

    // Сумма и счёт подставлены за человека, поэтому изменением считается именно отличие от
    // подставленного: закрывать вопросом лист, который открыли и сразу передумали, незачем.
    val prefilled = if (suggested > 0L) FosFormatter.amountInput(suggested) else ""
    val dirty = { amountText != prefilled || sourceId != payFrom.firstOrNull()?.id }

    FosFormSheet(
        onDismiss  = onDismiss,
        hasChanges = dirty,
    ) {
        Text("Погашение", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
        Text(
            "${card.account.name} · долг ${FosFormatter.amount(card.debt)}",
            style = FosType.Micro,
            color = FosColors.TextMuted,
        )

        OutlinedTextField(
            value                = amountText,
            onValueChange        = { amountText = FosFormatter.sanitizeAmountInput(it, allowNegative = false) },
            visualTransformation = AmountVisualTransformation,
            label                = {
                Text("Сумма, ${FosFormatter.currencySymbol(card.account.currency)}", style = FosType.Label)
            },
            supportingText       = {
                Text(
                    card.duePayment?.let { "Банк просит ${FosFormatter.amount(it.amountKopecks)}" }
                        ?: "Подставлен весь текущий долг",
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                )
            },
            singleLine           = true,
            keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            colors               = accountSheetFieldColors(),
            modifier             = Modifier.fillMaxWidth(),
        )

        Text("Откуда", style = FosType.SectionCap, color = FosColors.TextMuted)
        if (payFrom.isEmpty()) {
            Text(
                "Нет счёта, с которого можно списать. Добавьте обычный счёт на главной.",
                style = FosType.Body,
                color = FosColors.TextMuted,
            )
        } else {
            payFrom.forEach { account ->
                SourceRow(
                    account  = account,
                    selected = account.id == sourceId,
                    onClick  = { sourceId = account.id },
                )
            }
        }

        if (overdraws) {
            Text(
                "На счёте меньше этой суммы — платёж всё равно запишется, баланс уйдёт в минус.",
                style = FosType.Micro,
                color = FosColors.Warning,
            )
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick  = {
                val id = sourceId ?: return@Button
                onConfirm(id, amount)
                onDismiss()
            },
            enabled  = canPay,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(FosDimens.RadiusCard),
            colors   = ButtonDefaults.buttonColors(
                containerColor = FosColors.Positive,
                contentColor   = FosColors.Background,
            ),
        ) {
            Text("Записать погашение", style = FosType.BodySemi)
        }

        Text(
            "Приложение не переводит деньги — оно записывает платёж, который вы делаете " +
                "в банке, чтобы цифры сходились.",
            style = FosType.Micro,
            color = FosColors.TextMuted,
        )
    }
}

@Composable
private fun SourceRow(account: AccountEntity, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
            .background(if (selected) FosColors.Info.copy(alpha = 0.12f) else FosColors.Surface2)
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                account.name,
                style = FosType.BodySemi,
                color = if (selected) FosColors.Info else FosColors.TextPrimary,
            )
            Text(account.bank, style = FosType.Micro, color = FosColors.TextMuted)
        }
        Text(
            FosFormatter.amount(account.balanceKopecks, FosFormatter.currencySymbol(account.currency)),
            style = FosType.SmallBold,
            color = if (account.balanceKopecks >= 0) FosColors.TextSecondary else FosColors.Negative,
        )
    }
}
