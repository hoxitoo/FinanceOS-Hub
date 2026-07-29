package com.financeos.hub.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
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
import com.financeos.hub.ui.theme.AmountVisualTransformation
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

private val BANKS = listOf("Сбербанк", "Т-Банк", "ВТБ", "Альфа-Банк", "Газпромбанк", "МБанк", "МКБ", "Цифра Банк", "Другой")
private val CURRENCIES = listOf("RUB" to "₽ Рубль", "USD" to "$ Доллар", "EUR" to "€ Евро", "KGS" to "сом Сом")

/**
 * Typical days-to-pay after the statement closes, by bank. A hint only — it prefills the field so
 * the common case is one tap, and the user overwrites it from their own contract. Deliberately not
 * silently applied: a wrong grace period produces a confident wrong due date, which is worse than
 * an empty one.
 */
private fun defaultDueDays(bank: String): String = when {
    "сбер"   in bank.lowercase() -> "20"
    "т-банк" in bank.lowercase() -> "25"
    "альфа"  in bank.lowercase() -> "30"
    "втб"    in bank.lowercase() -> "20"
    else                         -> "20"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountSheet(
    sheetState  : SheetState,
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
    // CREDIT-only fields. Declared unconditionally (Rules of Hooks) and simply not rendered for a
    // cash account; switching kind therefore never rebuilds the slot table.
    var limitText        by remember { mutableStateOf("") }
    var aprText          by remember { mutableStateOf("") }
    var statementDayText by remember { mutableStateOf("") }
    var dueDaysText      by remember { mutableStateOf("") }
    var minPaymentText   by remember { mutableStateOf("") }

    val isCredit  = kind == AccountKind.CREDIT
    // The sheet asks for debt as a plain positive number ("сколько должен") and negates it once,
    // here, to match the entity convention. Asking the user to type a minus sign is a trap.
    val magnitude = FosFormatter.parseAmountInput(balanceText) ?: 0L
    val balanceKopecks = if (isCredit) -kotlin.math.abs(magnitude) else magnitude
    val canSave        = name.isNotBlank()
    val currencySymbol = FosFormatter.currencySymbol(selectedCurrency)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = FosColors.Surface,
        contentColor     = FosColors.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Sheet content can exceed the screen; without verticalScroll everything
                // below the fold — including «Сохранить» — is unreachable. imePadding is
                // required because the app is edge-to-edge, so adjustResize does not shrink
                // the window and the keyboard would cover the focused field.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FosDimens.ScreenPadding)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
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
                        // Prefill the bank's typical terms so the common case is one tap. Only
                        // fills blanks — never overwrites something the user already typed.
                        if (dueDaysText.isBlank())    dueDaysText    = defaultDueDays(selectedBank)
                        if (minPaymentText.isBlank()) minPaymentText = "5"
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
                CreditTermsFields(
                    currencySymbol   = currencySymbol,
                    limitText        = limitText,
                    onLimitChange    = { limitText = FosFormatter.sanitizeAmountInput(it, allowNegative = false) },
                    aprText          = aprText,
                    onAprChange      = { aprText = FosFormatter.sanitizeAmountInput(it, allowNegative = false) },
                    statementDayText = statementDayText,
                    onStatementDayChange = { statementDayText = sanitizeDayInput(it, max = 31) },
                    dueDaysText      = dueDaysText,
                    onDueDaysChange  = { dueDaysText = sanitizeDayInput(it, max = 90) },
                    minPaymentText   = minPaymentText,
                    onMinPaymentChange = { minPaymentText = FosFormatter.sanitizeAmountInput(it, allowNegative = false) },
                )
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
                            creditLimitKopecks = if (isCredit) FosFormatter.parseAmountInput(limitText) else null,
                            // parseAmountInput multiplies by 100 and rounds — "29,8" → 2980, which
                            // is exactly the basis points we store. Same trick for the min payment.
                            aprBp          = if (isCredit) FosFormatter.parseAmountInput(aprText)?.toInt() else null,
                            statementDay   = if (isCredit) statementDayText.toIntOrNull()?.takeIf { it in 1..31 } else null,
                            dueDays        = if (isCredit) dueDaysText.toIntOrNull()?.takeIf { it in 1..90 } else null,
                            minPaymentBp   = if (isCredit) FosFormatter.parseAmountInput(minPaymentText)?.toInt() else null,
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
}

/**
 * The five credit terms. Extracted so [AddAccountSheet] and the edit path in [AccountDetailSheet]
 * present identical fields — a limit that means one thing when created and another when edited is
 * how the numbers drift apart.
 */
@Composable
internal fun CreditTermsFields(
    currencySymbol      : String,
    limitText           : String,
    onLimitChange       : (String) -> Unit,
    aprText             : String,
    onAprChange         : (String) -> Unit,
    statementDayText    : String,
    onStatementDayChange: (String) -> Unit,
    dueDaysText         : String,
    onDueDaysChange     : (String) -> Unit,
    minPaymentText      : String,
    onMinPaymentChange  : (String) -> Unit,
) {
    Text("Условия карты", style = FosType.SectionCap, color = FosColors.TextMuted)
    Text(
        "Банк не присылает эти данные ни в SMS, ни в пуше — их нужно ввести один раз. " +
            "Пустые поля просто скрывают соответствующий блок, ничего не додумывается.",
        style = FosType.Micro,
        color = FosColors.TextMuted,
    )

    OutlinedTextField(
        value                = limitText,
        onValueChange        = onLimitChange,
        visualTransformation = AmountVisualTransformation,
        label                = { Text("Кредитный лимит, $currencySymbol", style = FosType.Label) },
        singleLine           = true,
        keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        colors               = accountSheetFieldColors(),
        modifier             = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value           = aprText,
        onValueChange   = onAprChange,
        label           = { Text("Ставка, % годовых", style = FosType.Label) },
        placeholder     = { Text("29,8", style = FosType.Body, color = FosColors.TextMuted) },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        colors          = accountSheetFieldColors(),
        modifier        = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.CardGap)) {
        OutlinedTextField(
            value           = statementDayText,
            onValueChange   = onStatementDayChange,
            label           = { Text("День выписки", style = FosType.Label) },
            placeholder     = { Text("30", style = FosType.Body, color = FosColors.TextMuted) },
            singleLine      = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
            colors          = accountSheetFieldColors(),
            modifier        = Modifier.weight(1f),
        )
        OutlinedTextField(
            value           = dueDaysText,
            onValueChange   = onDueDaysChange,
            label           = { Text("Дней на оплату", style = FosType.Label) },
            placeholder     = { Text("20", style = FosType.Body, color = FosColors.TextMuted) },
            singleLine      = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
            colors          = accountSheetFieldColors(),
            modifier        = Modifier.weight(1f),
        )
    }

    OutlinedTextField(
        value           = minPaymentText,
        onValueChange   = onMinPaymentChange,
        label           = { Text("Минимальный платёж, % от долга", style = FosType.Label) },
        placeholder     = { Text("5", style = FosType.Body, color = FosColors.TextMuted) },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        colors          = accountSheetFieldColors(),
        modifier        = Modifier.fillMaxWidth(),
    )
}

/** Digits only, capped at [max]. Leading zeros are dropped so "05" can't become day 5-but-invalid. */
internal fun sanitizeDayInput(raw: String, max: Int): String {
    val digits = raw.filter(Char::isDigit).take(2)
    if (digits.isEmpty()) return ""
    val n = digits.toIntOrNull() ?: return ""
    return if (n in 1..max) n.toString() else digits.dropLast(1)
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
