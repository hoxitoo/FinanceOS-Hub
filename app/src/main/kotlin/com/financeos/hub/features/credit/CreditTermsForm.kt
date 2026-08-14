package com.financeos.hub.features.credit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.features.dashboard.accountSheetFieldColors
import com.financeos.hub.ui.theme.AmountVisualTransformation
import com.financeos.hub.ui.components.FosSectionHeader
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

/**
 * Everything a credit card's tariff says, in the order the bank's own «Тариф» screen says it.
 *
 * The first version of this form asked for a statement day and a number of days to pay, which
 * describes a classic grace card — and does not describe Сбер's 120-day СберКарта at all. There the
 * obligatory payment is monthly while the interest-free period runs from each purchase, so the two
 * fields read as unanswerable trivia: the tariff screen does not print a statement day anywhere.
 *
 * The form now mirrors the tariff, every field says where to find it, and the two schedule fields
 * are explicitly optional — because the bank's own reminder push already supplies the real amount
 * and date, and those fields only stand in until it arrives.
 */
@Stable
class CreditTermsState(account: AccountEntity?) {
    var limit           by mutableStateOf(account?.creditLimitKopecks.toAmountText())
    var apr             by mutableStateOf(account?.aprBp.toPercentText())
    var penaltyApr      by mutableStateOf(account?.penaltyAprBp.toPercentText())
    var minPaymentPct   by mutableStateOf(account?.minPaymentBp.toPercentText())
    var minPaymentFloor by mutableStateOf(account?.minPaymentFloorKopecks.toAmountText())
    var interestFreeDays by mutableStateOf(account?.interestFreeDays?.toString().orEmpty())
    var statementDay    by mutableStateOf(account?.statementDay?.toString().orEmpty())
    var dueDays         by mutableStateOf(account?.dueDays?.toString().orEmpty())
    var cashFeePct      by mutableStateOf(account?.cashFeeBp.toPercentText())
    var cashFeeFixed    by mutableStateOf(account?.cashFeeFixedKopecks.toAmountText())

    /**
     * Prefills what the bank's tariff screen shows for the Кредитная СберКарта, so the common case
     * is a glance rather than ten trips between two apps. Only fills blanks — never overwrites a
     * figure the user already typed — and every number stays editable, because tariffs differ per
     * customer and change over time.
     */
    fun prefillSberCard() {
        if (apr.isBlank())              apr              = "59,8"
        if (penaltyApr.isBlank())       penaltyApr       = "36"
        if (minPaymentPct.isBlank())    minPaymentPct    = "10"
        if (minPaymentFloor.isBlank())  minPaymentFloor  = "150"
        if (interestFreeDays.isBlank()) interestFreeDays = "120"
        if (cashFeePct.isBlank())       cashFeePct       = "5,9"
        if (cashFeeFixed.isBlank())     cashFeeFixed     = "590"
    }

    // parseAmountInput scales by 100 and rounds, so a percentage lands straight in basis points:
    // "59,8" → 5980. Same helper as money, which is why both round identically.
    val limitKopecks     : Long? get() = FosFormatter.parseAmountInput(limit)
    val aprBpValue       : Int?  get() = FosFormatter.parseAmountInput(apr)?.toInt()
    val penaltyAprBpValue: Int?  get() = FosFormatter.parseAmountInput(penaltyApr)?.toInt()
    val minPaymentBpValue: Int?  get() = FosFormatter.parseAmountInput(minPaymentPct)?.toInt()
    val minPaymentFloorKopecks: Long? get() = FosFormatter.parseAmountInput(minPaymentFloor)
    val interestFreeDaysValue : Int? get() = interestFreeDays.toIntOrNull()?.takeIf { it in 1..365 }
    val statementDayValue     : Int? get() = statementDay.toIntOrNull()?.takeIf { it in 1..31 }
    val dueDaysValue          : Int? get() = dueDays.toIntOrNull()?.takeIf { it in 1..90 }
    val cashFeeBpValue        : Int? get() = FosFormatter.parseAmountInput(cashFeePct)?.toInt()
    val cashFeeFixedKopecks   : Long? get() = FosFormatter.parseAmountInput(cashFeeFixed)
}

private fun Long?.toAmountText(): String = this?.let { FosFormatter.amountInput(it) } ?: ""
private fun Int?.toPercentText(): String = this?.let { FosFormatter.amountInput(it.toLong()) } ?: ""

/** Keyed on the card so reopening the sheet for a second one never shows the first one's terms. */
@Composable
fun rememberCreditTermsState(account: AccountEntity?): CreditTermsState =
    remember(account?.id) { CreditTermsState(account) }

/** Digits only, capped at [max]. Leading zeros dropped, so "05" cannot become an invalid 5. */
fun sanitizeIntInput(raw: String, max: Int): String {
    val digits = raw.filter(Char::isDigit).take(max.toString().length)
    if (digits.isEmpty()) return ""
    val n = digits.toIntOrNull() ?: return ""
    return if (n in 1..max) n.toString() else digits.dropLast(1)
}

@Composable
fun CreditTermsForm(state: CreditTermsState, currencySymbol: String) {
    Column(verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap)) {

        Group("Лимит и ставка", "В приложении банка: карта → Тариф")

        Money(state.limit, { state.limit = it }, "Кредитный лимит, $currencySymbol",
            "Сколько банк разрешил потратить")
        Percent(state.apr, { state.apr = it }, "Ставка, % годовых", "59,8",
            "Тариф → Процентная ставка")
        Percent(state.penaltyApr, { state.penaltyApr = it }, "Неустойка, % годовых", "36",
            "Начисляется, если пропустить обязательный платёж")

        Group("Обязательный платёж", "Его банк требует каждый месяц, независимо от " +
            "беспроцентного периода")

        Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.CardGap)) {
            PercentBox(state.minPaymentPct, { state.minPaymentPct = it }, "% от долга", "10",
                Modifier.weight(1f))
            MoneyBox(state.minPaymentFloor, { state.minPaymentFloor = it },
                "но не менее, $currencySymbol", Modifier.weight(1f))
        }
        Hint("Тариф → Обязательный платёж. У Кредитной СберКарты — до 10% от долга, но не менее 150 ₽")

        Group("Беспроцентный период", null)

        Days(state.interestFreeDays, { state.interestFreeDays = sanitizeIntInput(it, 365) },
            "Длительность, дней", "120",
            "Тариф → Беспроцентный период. Отсчитывается от покупки, а не от выписки")

        Spacer(Modifier.height(2.dp))
        Text("Расчёт даты платежа — необязательно", style = FosType.Label, color = FosColors.TextSecondary)
        Hint(
            "Банк сам присылает сумму и дату платежа — приложение берёт их из уведомления. " +
                "Эти два поля нужны только чтобы прикинуть срок, пока уведомление не пришло. " +
                "Не знаете — оставьте пустыми, ничего не додумается."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.CardGap)) {
            DaysBox(state.statementDay, { state.statementDay = sanitizeIntInput(it, 31) },
                "День выписки", Modifier.weight(1f))
            DaysBox(state.dueDays, { state.dueDays = sanitizeIntInput(it, 90) },
                "Дней на оплату", Modifier.weight(1f))
        }
        Hint(
            "«День выписки» — число месяца, когда банк подводит итог по карте. " +
                "«Дней на оплату» — сколько дней после этого даётся внести платёж."
        )

        Group("Снятие наличных и переводы", null)

        Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.CardGap)) {
            PercentBox(state.cashFeePct, { state.cashFeePct = it }, "Комиссия, %", "5,9",
                Modifier.weight(1f))
            MoneyBox(state.cashFeeFixed, { state.cashFeeFixed = it },
                "и ещё, $currencySymbol", Modifier.weight(1f))
        }
        Hint("Тариф → Комиссии. Снятие наличных обычно ещё и выбивает из беспроцентного периода")
    }
}

// ── Small building blocks ─────────────────────────────────────────────────────

@Composable
private fun Group(title: String, subtitle: String?) {
    Spacer(Modifier.height(4.dp))
    // Форма длинная и вся из одинаковых полей — линейка отделяет группу от предыдущей.
    FosSectionHeader(title)
    if (subtitle != null) Hint(subtitle)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = FosType.Micro, color = FosColors.TextMuted)
}

@Composable
private fun Money(value: String, onChange: (String) -> Unit, label: String, hint: String?) {
    OutlinedTextField(
        value                = value,
        onValueChange        = { onChange(FosFormatter.sanitizeAmountInput(it, allowNegative = false)) },
        visualTransformation = AmountVisualTransformation,
        label                = { Text(label, style = FosType.Label) },
        supportingText       = hint?.let { { Hint(it) } },
        singleLine           = true,
        keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        colors               = accountSheetFieldColors(),
        modifier             = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MoneyBox(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value                = value,
        onValueChange        = { onChange(FosFormatter.sanitizeAmountInput(it, allowNegative = false)) },
        visualTransformation = AmountVisualTransformation,
        label                = { Text(label, style = FosType.Label) },
        singleLine           = true,
        keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        colors               = accountSheetFieldColors(),
        modifier             = modifier,
    )
}

@Composable
private fun Percent(
    value: String, onChange: (String) -> Unit, label: String, placeholder: String, hint: String?,
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = { onChange(FosFormatter.sanitizeAmountInput(it, allowNegative = false)) },
        label           = { Text(label, style = FosType.Label) },
        placeholder     = { Text(placeholder, style = FosType.Body, color = FosColors.TextMuted) },
        supportingText  = hint?.let { { Hint(it) } },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        colors          = accountSheetFieldColors(),
        modifier        = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PercentBox(
    value: String, onChange: (String) -> Unit, label: String, placeholder: String, modifier: Modifier,
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = { onChange(FosFormatter.sanitizeAmountInput(it, allowNegative = false)) },
        label           = { Text(label, style = FosType.Label) },
        placeholder     = { Text(placeholder, style = FosType.Body, color = FosColors.TextMuted) },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        colors          = accountSheetFieldColors(),
        modifier        = modifier,
    )
}

@Composable
private fun Days(
    value: String, onChange: (String) -> Unit, label: String, placeholder: String, hint: String?,
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onChange,
        label           = { Text(label, style = FosType.Label) },
        placeholder     = { Text(placeholder, style = FosType.Body, color = FosColors.TextMuted) },
        supportingText  = hint?.let { { Hint(it) } },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
        colors          = accountSheetFieldColors(),
        modifier        = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DaysBox(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onChange,
        label           = { Text(label, style = FosType.Label) },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
        colors          = accountSheetFieldColors(),
        modifier        = modifier,
    )
}
