package com.financeos.hub.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.database.entities.AccountKind
import com.financeos.hub.features.credit.CreditTermsForm
import com.financeos.hub.features.credit.rememberCreditTermsState
import com.financeos.hub.ui.components.FosFormSheet
import com.financeos.hub.ui.theme.AmountVisualTransformation
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

private val BANKS = listOf("Сбербанк", "Т-Банк", "ВТБ", "Альфа-Банк", "Газпромбанк", "МБанк", "МКБ", "Цифра Банк", "Другой")
private val CURRENCIES = listOf("RUB" to "₽ Рубль", "USD" to "$ Доллар", "EUR" to "€ Евро", "KGS" to "сом Сом")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountSheet(
    initialBank : String = BANKS[0],
    onDismiss   : () -> Unit,
    onSave      : (AccountDraft) -> Unit,
) {
    var selectedBank     by remember { mutableStateOf(initialBank) }
    var selectedCurrency by remember { mutableStateOf("RUB") }
    var kind             by remember { mutableStateOf(AccountKind.CASH) }
    var name             by remember { mutableStateOf("") }
    var cardMaskText     by remember { mutableStateOf("") }
    var balanceText      by remember { mutableStateOf("") }
    // CREDIT-only fields. Created unconditionally (Rules of Hooks) and simply not rendered for a
    // cash account; switching kind therefore never rebuilds the slot table.
    val terms = rememberCreditTermsState(account = null)

    val isCredit  = kind == AccountKind.CREDIT
    // The sheet asks for debt as a plain positive number ("сколько должен") and negates it once,
    // here, to match the entity convention. Asking the user to type a minus sign is a trap.
    val magnitude = FosFormatter.parseAmountInput(balanceText) ?: 0L
    val balanceKopecks = if (isCredit) -kotlin.math.abs(magnitude) else magnitude
    val canSave        = name.isNotBlank()
    val currencySymbol = FosFormatter.currencySymbol(selectedCurrency)

    // Банк может быть предвыбран вызывающим (тап по карточке банка на главной), поэтому сравнение
    // идёт с ним, а не с null: предвыбор — это не ввод человека.
    val dirty = {
        name.isNotBlank() || cardMaskText.isNotBlank() || balanceText.isNotBlank() ||
            selectedBank != initialBank || selectedCurrency != "RUB" || kind != AccountKind.CASH
    }

    FosFormSheet(
        onDismiss  = onDismiss,
        hasChanges = dirty,
    ) {
        Text("Новый счёт", style = FosType.ScreenTitle, color = FosColors.TextPrimary)

        // Account kind — first, because it changes what the rest of the form means
        Text("Тип счёта", style = FosType.SectionCap, color = FosColors.TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !isCredit,
                onClick  = { kind = AccountKind.CASH },
                label    = { Text("Обычный счёт", style = FosType.Label) },
                shape    = RoundedCornerShape(FosDimens.RadiusChip),
                colors   = accountKindChipColors(),
            )
            FilterChip(
                selected = isCredit,
                onClick  = {
                    kind = AccountKind.CREDIT
                    // Сбер's published Кредитная СберКарта tariff, prefilled so the common case
                    // is a glance instead of ten trips between two apps. Blanks only, and every
                    // figure stays editable — tariffs differ per customer and change over time.
                    if (selectedBank.contains("сбер", ignoreCase = true)) terms.prefillSberCard()
                },
                label    = { Text("Кредитная карта", style = FosType.Label) },
                shape    = RoundedCornerShape(FosDimens.RadiusChip),
                colors   = accountKindChipColors(),
            )
        }

        // Bank selector
        Text("Банк", style = FosType.SectionCap, color = FosColors.TextMuted)
        LazyRow(
            contentPadding        = PaddingValues(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(BANKS.size) { i ->
                val bank     = BANKS[i]
                val selected = selectedBank == bank
                FilterChip(
                    selected = selected,
                    onClick  = { selectedBank = bank },
                    label    = { Text(bank, style = FosType.Label) },
                    shape    = RoundedCornerShape(FosDimens.RadiusChip),
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FosColors.Info.copy(alpha = 0.15f),
                        selectedLabelColor     = FosColors.Info,
                        containerColor         = FosColors.Surface2,
                        labelColor             = FosColors.TextSecondary,
                    ),
                )
            }
        }

        // Account name
        OutlinedTextField(
            value           = name,
            onValueChange   = { name = it },
            label           = { Text("Название счёта", style = FosType.Label) },
            placeholder     = {
                Text(
                    if (isCredit) "Например: Кредитка Сбер" else "Например: Зарплатная",
                    style = FosType.Body,
                    color = FosColors.TextMuted,
                )
            },
            singleLine      = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors          = accountSheetFieldColors(),
            modifier        = Modifier.fillMaxWidth(),
        )

        // Card mask (last 4 digits)
        OutlinedTextField(
            value         = cardMaskText,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) cardMaskText = it },
            label         = { Text("Последние 4 цифры карты (необязательно)", style = FosType.Label) },
            singleLine    = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction    = ImeAction.Next,
            ),
            colors   = accountSheetFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        // Balance — or, on a credit card, the debt
        OutlinedTextField(
            value           = balanceText,
            onValueChange   = {
                // Debt is entered as a magnitude, so a leading minus is not offered on credit.
                balanceText = FosFormatter.sanitizeAmountInput(it, allowNegative = !isCredit)
            },
            visualTransformation = AmountVisualTransformation,
            label           = {
                Text(
                    if (isCredit) "Текущий долг, $currencySymbol" else "Текущий баланс, $currencySymbol",
                    style = FosType.Label,
                )
            },
            supportingText  = if (isCredit) {
                { Text("Сколько сейчас должны банку", style = FosType.Micro, color = FosColors.TextMuted) }
            } else null,
            singleLine      = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction    = ImeAction.Next,
            ),
            colors   = accountSheetFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        if (isCredit) {
            CreditTermsForm(state = terms, currencySymbol = currencySymbol)
        }

        // Currency picker
        Text("Валюта", style = FosType.SectionCap, color = FosColors.TextMuted)
        LazyRow(
            contentPadding        = PaddingValues(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(CURRENCIES.size) { i ->
                val (code, label) = CURRENCIES[i]
                val selected = selectedCurrency == code
                FilterChip(
                    selected = selected,
                    onClick  = { selectedCurrency = code },
                    label    = { Text(label, style = FosType.Label) },
                    shape    = RoundedCornerShape(FosDimens.RadiusChip),
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FosColors.Info.copy(alpha = 0.15f),
                        selectedLabelColor     = FosColors.Info,
                        containerColor         = FosColors.Surface2,
                        labelColor             = FosColors.TextSecondary,
                    ),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick  = {
                onSave(
                    AccountDraft(
                        name           = name.trim(),
                        bank           = selectedBank,
                        cardMask       = cardMaskText.takeIf { it.length == 4 },
                        balanceKopecks = balanceKopecks,
                        currency       = selectedCurrency,
                        kind           = kind,
                        creditLimitKopecks = if (isCredit) terms.limitKopecks else null,
                        aprBp              = if (isCredit) terms.aprBpValue else null,
                        statementDay       = if (isCredit) terms.statementDayValue else null,
                        dueDays            = if (isCredit) terms.dueDaysValue else null,
                        minPaymentBp       = if (isCredit) terms.minPaymentBpValue else null,
                        minPaymentFloorKopecks = if (isCredit) terms.minPaymentFloorKopecks else null,
                        interestFreeDays   = if (isCredit) terms.interestFreeDaysValue else null,
                        penaltyAprBp       = if (isCredit) terms.penaltyAprBpValue else null,
                        cashFeeBp          = if (isCredit) terms.cashFeeBpValue else null,
                        cashFeeFixedKopecks = if (isCredit) terms.cashFeeFixedKopecks else null,
                    )
                )
                onDismiss()
            },
            enabled  = canSave,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(FosDimens.RadiusCard),
            colors   = ButtonDefaults.buttonColors(
                containerColor = FosColors.Positive,
                contentColor   = FosColors.Background,
            ),
        ) {
            Text(if (isCredit) "Добавить карту" else "Добавить счёт", style = FosType.BodySemi)
        }
    }
}

@Composable
internal fun accountSheetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = FosColors.Info,
    unfocusedBorderColor = FosColors.BorderStrong,
    focusedLabelColor    = FosColors.Info,
    unfocusedLabelColor  = FosColors.TextMuted,
    cursorColor          = FosColors.Info,
    focusedTextColor     = FosColors.TextPrimary,
    unfocusedTextColor   = FosColors.TextPrimary,
    errorBorderColor     = FosColors.Negative,
)

@Composable
private fun accountKindChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = FosColors.Info.copy(alpha = 0.15f),
    selectedLabelColor     = FosColors.Info,
    containerColor         = FosColors.Surface2,
    labelColor             = FosColors.TextSecondary,
)
