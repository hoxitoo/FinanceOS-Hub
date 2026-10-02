package com.financeos.hub.features.investor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.invest.BrokerEvent
import com.financeos.hub.core.invest.BrokerPushParser
import com.financeos.hub.core.invest.ManualEntry
import com.financeos.hub.core.invest.Portfolio
import com.financeos.hub.features.transactions.NoFutureDates
import com.financeos.hub.ui.components.FosFormSheet
import com.financeos.hub.ui.theme.AmountVisualTransformation
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/*
 * Ручной ввод в режиме инвестора (инвариант #49): пуши брокера могут не прийти, прийти не все или
 * не в том формате. Всё, что пушем приходит, человек может ввести сам — и удалить, если пришло
 * неверно. Записывается теми же событиями, что и пуши, поэтому считается одинаково.
 *
 * Все листы с вводом — через FosFormSheet (инвариант #22).
 */

/** Какой лист ручного ввода открыт. */
enum class InvestAdd { MENU, OPERATION, ASSET, ACCOUNT }

/** «Добавить» в главном блоке: что именно. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrokerAddMenuSheet(
    onOperation: () -> Unit,
    onAsset    : () -> Unit,
    onAccount  : () -> Unit,
    onDismiss  : () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = FosColors.Surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FosDimens.ScreenPadding).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Добавить", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
            MenuRow("Операцию", "Пополнение, вывод, перевод между счетами, покупка или продажа", onOperation)
            MenuRow("Актив", "Бумагу, которая уже есть в портфеле: тикер, количество, цена покупки", onAsset)
            MenuRow("Счёт", "Счёт у брокера, о котором не пришло ни одного пуша", onAccount)
        }
    }
}

@Composable
private fun MenuRow(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(title, style = FosType.BodySemi, color = FosColors.TextPrimary)
        Text(sub, style = FosType.Micro, color = FosColors.TextSecondary)
    }
}

/** Операция: пополнение / вывод / перевод / покупка / продажа. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrokerOperationSheet(
    contracts: List<Portfolio.Contract>,
    onSave   : (List<BrokerEvent>) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind      by remember { mutableStateOf(ManualEntry.Kind.DEPOSIT) }
    var amount    by remember { mutableStateOf("") }
    var ticker    by remember { mutableStateOf("") }
    var quantity  by remember { mutableStateOf("") }
    var price     by remember { mutableStateOf("") }
    var from      by remember { mutableStateOf(contracts.firstOrNull()?.contract) }
    var to        by remember { mutableStateOf<String?>(null) }
    var date      by remember { mutableStateOf(LocalDate.now()) }

    val trade = kind == ManualEntry.Kind.BUY || kind == ManualEntry.Kind.SELL
    val broker = contracts.firstOrNull { it.contract == from }?.broker ?: contracts.firstOrNull()?.broker ?: BrokerPushParser.BKS
    val events = ManualEntry.operation(
        kind          = kind,
        broker        = broker,
        timestamp     = timestampOf(date),
        amountKopecks = FosFormatter.parseAmountInput(amount),
        ticker        = ticker,
        quantity      = quantity.toLongOrNull(),
        priceMicros   = ManualEntry.parsePrice(price),
        contract      = from,
        toContract    = to,
    )

    FosFormSheet(
        onDismiss  = onDismiss,
        hasChanges = { amount.isNotBlank() || ticker.isNotBlank() || quantity.isNotBlank() || price.isNotBlank() },
    ) {
        Text("Операция у брокера", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ManualEntry.Kind.values().forEach { k -> InvestChip(k.title, k == kind) { kind = k } }
        }
        if (trade) {
            InvestField(ticker, { ticker = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(16) },
                "Тикер (SBER, LQDT…)", KeyboardType.Ascii)
            InvestField(quantity, { quantity = it.filter(Char::isDigit).take(12) }, "Количество, шт.", KeyboardType.Number)
            InvestField(price, { price = ManualEntry.sanitizePrice(it) }, "Цена одной бумаги, ₽", KeyboardType.Decimal)
            val total = events?.filterIsInstance<com.financeos.hub.core.invest.BrokerOrder>()?.firstOrNull()?.let {
                com.financeos.hub.core.invest.microsToKopecks(it.priceMicros * it.lots)
            }
            total?.let { Text("Сумма сделки: ${FosFormatter.amount(it, "₽")}", style = FosType.MicroNum, color = FosColors.TextSecondary) }
        } else {
            MoneyField(amount, { amount = it }, "Сумма, ₽")
        }
        if (contracts.isNotEmpty()) {
            ContractChips(if (kind == ManualEntry.Kind.TRANSFER) "Со счёта" else "Счёт", contracts, from, allowNone = kind != ManualEntry.Kind.TRANSFER) {
                from = it
                // «На счёт» не может совпасть с «Со счёта»: чип пропал бы из списка, а выбор остался.
                if (to == it) to = null
            }
            if (kind == ManualEntry.Kind.TRANSFER) {
                ContractChips("На счёт", contracts.filter { it.contract != from }, to, allowNone = false) { to = it }
            }
        } else if (kind == ManualEntry.Kind.TRANSFER) {
            Text("Для перевода между счетами сначала добавьте счета: «Добавить → Счёт».", style = FosType.Micro, color = FosColors.TextMuted)
        }
        DateChip(date) { date = it }
        SaveButton(enabled = events != null) {
            events?.let(onSave)
            onDismiss()
        }
    }
}

/** Актив, купленный раньше: тикер, количество, цена покупки, по желанию — текущая цена. */
@Composable
fun BrokerAssetSheet(
    contracts: List<Portfolio.Contract>,
    onSave   : (List<BrokerEvent>) -> Unit,
    onDismiss: () -> Unit,
) {
    var ticker   by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var price    by remember { mutableStateOf("") }
    var current  by remember { mutableStateOf("") }
    var contract by remember { mutableStateOf(contracts.firstOrNull()?.contract) }
    var date     by remember { mutableStateOf(LocalDate.now()) }

    val broker = contracts.firstOrNull { it.contract == contract }?.broker ?: contracts.firstOrNull()?.broker ?: BrokerPushParser.BKS
    val ts = timestampOf(date)
    val base = ManualEntry.asset(broker, ts, ticker, quantity.toLongOrNull() ?: 0L, ManualEntry.parsePrice(price) ?: 0L, contract)
    // Текущая цена — отдельной отметкой «сейчас», после покупки.
    val events = base?.let { list ->
        val now = maxOf(System.currentTimeMillis(), ts + 1)
        val mark = ManualEntry.parsePrice(current)?.let {
            com.financeos.hub.core.invest.BrokerPriceMark(broker, now, ticker.trim().uppercase(), it)
        }
        list + listOfNotNull(mark)
    }

    FosFormSheet(
        onDismiss  = onDismiss,
        hasChanges = { ticker.isNotBlank() || quantity.isNotBlank() || price.isNotBlank() || current.isNotBlank() },
    ) {
        Text("Актив в портфеле", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
        Text(
            "Для бумаги, купленной раньше или без пуша. Деньги на её покупку заводятся вместе с ней — " +
                "свободные деньги не уйдут в минус. Удаляется тоже вместе.",
            style = FosType.Micro,
            color = FosColors.TextSecondary,
        )
        InvestField(ticker, { ticker = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(16) },
            "Тикер (SBER, LQDT…)", KeyboardType.Ascii)
        InvestField(quantity, { quantity = it.filter(Char::isDigit).take(12) }, "Количество, шт.", KeyboardType.Number)
        InvestField(price, { price = ManualEntry.sanitizePrice(it) }, "Средняя цена покупки, ₽", KeyboardType.Decimal)
        InvestField(current, { current = ManualEntry.sanitizePrice(it) }, "Текущая цена, ₽ (необязательно)", KeyboardType.Decimal)
        if (contracts.isNotEmpty()) ContractChips("Счёт", contracts, contract, allowNone = true) { contract = it }
        DateChip(date, label = "Дата покупки") { date = it }
        SaveButton(enabled = events != null) {
            events?.let(onSave)
            onDismiss()
        }
    }
}

/** Новый счёт у брокера: номер и название, как в приложении брокера. */
@Composable
fun BrokerNewAccountSheet(
    onSave   : (broker: String, contract: String, label: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var number by remember { mutableStateOf("") }
    var label  by remember { mutableStateOf("") }
    var broker by remember { mutableStateOf(BrokerPushParser.BKS) }
    FosFormSheet(onDismiss = onDismiss, hasChanges = { number.isNotBlank() || label.isNotBlank() }) {
        Text("Счёт у брокера", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
        InvestField(number, { number = it.take(40) }, "Номер счёта (580922/19-м)", KeyboardType.Text)
        InvestField(label, { label = it.take(40) }, "Название (ИИС, Облигации) — необязательно", KeyboardType.Text)
        InvestField(broker, { broker = it.take(40) }, "Брокер", KeyboardType.Text)
        SaveButton(enabled = number.isNotBlank() && broker.isNotBlank()) {
            onSave(broker.trim(), number.trim(), label.trim().takeIf { it.isNotEmpty() })
            onDismiss()
        }
    }
}

/**
 * Бумага в портфеле: указать текущую цену или удалить целиком. Цену без котировок знает только
 * человек — без неё стоимость стоит на цене последней своей сделки.
 */
@Composable
fun BrokerPositionSheet(
    position : Portfolio.Position,
    onPrice  : (Long) -> Unit,
    onDelete : () -> Unit,
    onDismiss: () -> Unit,
) {
    var priceText by remember(position.ticker) { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val sym = FosFormatter.currencySymbol(position.currency)
    FosFormSheet(onDismiss = onDismiss, hasChanges = { priceText.isNotBlank() }) {
        Text(position.ticker, style = FosType.ScreenTitle, color = FosColors.TextPrimary)
        Text(
            "${grouped(position.quantity)} шт. · средняя ${price(position.avgPriceMicros)} $sym · " +
                "сейчас ${price(position.lastPriceMicros)} $sym",
            style = FosType.MicroNum,
            color = FosColors.TextSecondary,
        )
        InvestField(priceText, { priceText = ManualEntry.sanitizePrice(it) }, "Текущая цена, $sym", KeyboardType.Decimal)
        val parsed = ManualEntry.parsePrice(priceText)
        SaveButton(enabled = parsed != null, label = "Указать цену") {
            parsed?.let(onPrice)
            onDismiss()
        }
        TextButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Удалить актив", style = FosType.Label, color = FosColors.Negative)
        }
    }
    if (confirm) {
        ConfirmDelete(
            title = "Удалить ${position.ticker}?",
            text  = "Уйдут все сделки и цены по бумаге — и пришедшие пушем, и введённые вручную. Деньги, " +
                "заведённые вместе с активом, уйдут вместе с ним.",
            onConfirm = { confirm = false; onDelete(); onDismiss() },
            onDismiss = { confirm = false },
        )
    }
}

/** Подтверждение удаления — общее для операции, актива и счёта. */
@Composable
fun ConfirmDelete(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = FosColors.Surface,
        title = { Text(title, style = FosType.BodySemi, color = FosColors.TextPrimary) },
        text  = { Text(text, style = FosType.Body, color = FosColors.TextSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить", color = FosColors.Negative) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = FosColors.TextSecondary) } },
    )
}

// ── Поля ─────────────────────────────────────────────────────────────────────

@Composable
private fun InvestField(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, style = FosType.Label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = investFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Сумма: строка в состоянии — сырая, разбивка — через AmountVisualTransformation (инвариант #7). */
@Composable
private fun MoneyField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(FosFormatter.sanitizeAmountInput(it)) },
        visualTransformation = AmountVisualTransformation,
        label = { Text(label, style = FosType.Label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = investFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContractChips(
    title: String,
    contracts: List<Portfolio.Contract>,
    selected: String?,
    allowNone: Boolean,
    onSelect: (String?) -> Unit,
) {
    Text(title, style = FosType.SectionCap, color = FosColors.TextMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (allowNone) InvestChip("Не указан", selected == null) { onSelect(null) }
        contracts.forEach { c -> InvestChip(c.title, selected == c.contract) { onSelect(c.contract) } }
    }
}

@Composable
private fun InvestChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = { Text(label, style = FosType.Label, maxLines = 1) },
        shape    = RoundedCornerShape(FosDimens.RadiusChip),
        colors   = FilterChipDefaults.filterChipColors(
            selectedContainerColor = FosColors.Invest.copy(alpha = 0.18f),
            selectedLabelColor     = FosColors.Invest,
            containerColor         = FosColors.Surface2,
            labelColor             = FosColors.TextSecondary,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateChip(date: LocalDate, label: String = "Дата", onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = FosType.SectionCap, color = FosColors.TextMuted)
        InvestChip(FosFormatter.dayLabelYear(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()), true) { open = true }
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = NoFutureDates,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("Готово", color = FosColors.Invest) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Отмена", color = FosColors.TextSecondary) } },
            colors = DatePickerDefaults.colors(containerColor = FosColors.Surface),
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun SaveButton(enabled: Boolean, label: String = "Сохранить", onClick: () -> Unit) {
    Button(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(FosDimens.RadiusCard),
        colors   = ButtonDefaults.buttonColors(containerColor = FosColors.Invest, contentColor = FosColors.Background),
    ) { Text(label, style = FosType.BodySemi) }
}

@Composable
private fun investFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = FosColors.Invest,
    unfocusedBorderColor = FosColors.BorderStrong,
    focusedLabelColor    = FosColors.Invest,
    unfocusedLabelColor  = FosColors.TextMuted,
    cursorColor          = FosColors.Invest,
    focusedTextColor     = FosColors.TextPrimary,
    unfocusedTextColor   = FosColors.TextPrimary,
)

/** Сегодня — текущее время; прошедший день — то же время суток в тот день (как у ручной операции кошелька). */
private fun timestampOf(date: LocalDate): Long =
    if (date == LocalDate.now()) System.currentTimeMillis()
    else date.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun grouped(n: Long): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale("ru")).format(n)
