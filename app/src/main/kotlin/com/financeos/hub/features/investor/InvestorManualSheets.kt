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
import com.financeos.hub.core.invest.SecurityGroups
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

/**
 * Операция: пополнение / вывод / перевод / покупка / продажа. С [initial] — карточка ПРАВКИ (#51):
 * поля заполнены записью, сохранение переписывает её, а не добавляет новую, и внизу есть «Удалить».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrokerOperationSheet(
    contracts: List<Portfolio.Contract>,
    onSave   : (List<BrokerEvent>) -> Unit,
    onDismiss: () -> Unit,
    initial  : ManualEntry.Draft? = null,
    /**
     * Правится сделка из ПУША: у неё меняются только цена, счёт и валюта. Бумага, сторона, лоты и
     * время связывают её с прежними пушами той же заявки (см. [ManualEntry.keepPushed]).
     */
    pushedOrder: Boolean = false,
    onDelete : (() -> Unit)? = null,
) {
    val start = remember(initial) { initial?.let { FormValues(it) } ?: FormValues.EMPTY }
    var kind      by remember(initial) { mutableStateOf(initial?.kind ?: ManualEntry.Kind.DEPOSIT) }
    var amount    by remember(initial) { mutableStateOf(start.amount) }
    var ticker    by remember(initial) { mutableStateOf(start.ticker) }
    var quantity  by remember(initial) { mutableStateOf(start.quantity) }
    var price     by remember(initial) { mutableStateOf(start.price) }
    var currency  by remember(initial) { mutableStateOf(initial?.currency ?: "RUB") }
    var from      by remember(initial) { mutableStateOf(if (initial != null) initial.contract else contracts.firstOrNull()?.contract) }
    var to        by remember(initial) { mutableStateOf(initial?.toContract) }
    var date      by remember(initial) { mutableStateOf(initial?.let { dayOf(it.timestamp) } ?: LocalDate.now()) }

    val trade = kind == ManualEntry.Kind.BUY || kind == ManualEntry.Kind.SELL
    val broker = contracts.firstOrNull { it.contract == from }?.broker ?: contracts.firstOrNull()?.broker ?: BrokerPushParser.BKS
    val sym = FosFormatter.currencySymbol(currency)
    val events = ManualEntry.operation(
        kind          = kind,
        broker        = broker,
        timestamp     = initial?.let { ManualEntry.editedTimestamp(it.timestamp, date) } ?: timestampOf(date),
        amountKopecks = FosFormatter.parseAmountInput(amount),
        ticker        = ticker,
        quantity      = quantity.toLongOrNull(),
        priceMicros   = ManualEntry.parsePrice(price),
        contract      = from,
        toContract    = to,
        currency      = currency,
    )
    val changed = initial == null ||
        kind != initial.kind || currency != initial.currency || from != initial.contract || to != initial.toContract ||
        date != dayOf(initial.timestamp) || FormValues(amount, ticker, quantity, price) != start

    FosFormSheet(
        onDismiss  = onDismiss,
        hasChanges = {
            if (initial == null) amount.isNotBlank() || ticker.isNotBlank() || quantity.isNotBlank() || price.isNotBlank()
            else changed
        },
    ) {
        Text(if (initial == null) "Операция у брокера" else "Операция", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
        if (pushedOrder) {
            // Сделка из пуша: что купили и сколько — факт брокера; правится цена (рыночная заявка
            // приходит без неё, #52), счёт и валюта.
            Text(
                "${kind.title} · $ticker · $quantity лот. · ${FosFormatter.dayLabel(initial?.timestamp ?: 0L)}",
                style = FosType.BodySemi,
                color = FosColors.TextPrimary,
            )
            Text(
                "Пришло пушем: бумага, количество и время — как у брокера. Цену исполнения возьмите в " +
                    "приложении брокера, если она не пришла.",
                style = FosType.Micro,
                color = FosColors.TextSecondary,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ManualEntry.Kind.values().forEach { k -> InvestChip(k.title, k == kind) { kind = k } }
            }
        }
        CurrencyChips(currency) { currency = it }
        if (trade) {
            if (!pushedOrder) {
                InvestField(ticker, { ticker = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(16) },
                    "Тикер (SBER, LQDT…)", KeyboardType.Ascii)
                InvestField(quantity, { quantity = it.filter(Char::isDigit).take(12) }, "Количество, шт.", KeyboardType.Number)
            }
            InvestField(price, { price = ManualEntry.sanitizePrice(it) }, "Цена одной бумаги, $sym", KeyboardType.Decimal)
            val total = events?.filterIsInstance<com.financeos.hub.core.invest.BrokerOrder>()?.firstOrNull()?.let {
                com.financeos.hub.core.invest.microsToKopecks(it.priceMicros * it.lots)
            }
            total?.let { Text("Сумма сделки: ${FosFormatter.amount(it, sym)}", style = FosType.MicroNum, color = FosColors.TextSecondary) }
        } else {
            // Дробная сумма законна в любой валюте: 0,41 $, 41,60 ¥ (#51).
            MoneyField(amount, { amount = it }, "Сумма, $sym")
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
        if (!pushedOrder) DateChip(date) { date = it }
        SaveButton(enabled = events != null && changed) {
            events?.let(onSave)
            onDismiss()
        }
        onDelete?.let { DeleteButton("Удалить операцию", it) }
    }
}

/**
 * Актив, купленный раньше: тикер, количество, цена покупки, по желанию — текущая цена. Валютный
 * тикер («USD000SMALL», «CNY000SMALL») — это деньги в своей валюте: вместо штук и цены — сумма (#51).
 * С [initial] — карточка правки записи «Актив» целиком.
 */
@Composable
fun BrokerAssetSheet(
    contracts: List<Portfolio.Contract>,
    onSave   : (List<BrokerEvent>) -> Unit,
    onDismiss: () -> Unit,
    initial  : ManualEntry.Draft? = null,
    onDelete : (() -> Unit)? = null,
) {
    val start = remember(initial) { initial?.let { FormValues(it) } ?: FormValues.EMPTY }
    val startCurrent = remember(initial) { initial?.currentPriceMicros?.let(ManualEntry::priceInput) ?: "" }
    var ticker   by remember(initial) { mutableStateOf(start.ticker) }
    var quantity by remember(initial) { mutableStateOf(start.quantity) }
    var price    by remember(initial) { mutableStateOf(start.price) }
    var current  by remember(initial) { mutableStateOf(startCurrent) }
    var amount   by remember(initial) { mutableStateOf("") }
    var currency by remember(initial) { mutableStateOf(initial?.currency ?: "RUB") }
    var contract by remember(initial) { mutableStateOf(if (initial != null) initial.contract else contracts.firstOrNull()?.contract) }
    var date     by remember(initial) { mutableStateOf(initial?.let { dayOf(it.timestamp) } ?: LocalDate.now()) }

    val broker = contracts.firstOrNull { it.contract == contract }?.broker ?: contracts.firstOrNull()?.broker ?: BrokerPushParser.BKS
    val ts = initial?.let { ManualEntry.editedTimestamp(it.timestamp, date) } ?: timestampOf(date)
    val cashCurrency = SecurityGroups.cashCurrency(ticker)
    val events = if (cashCurrency != null) {
        ManualEntry.currencyCash(broker, ts, ticker, FosFormatter.parseAmountInput(amount), contract)
    } else {
        val base = ManualEntry.asset(broker, ts, ticker, quantity.toLongOrNull() ?: 0L, ManualEntry.parsePrice(price) ?: 0L, contract, currency)
        // Текущая цена — отдельной отметкой «сейчас», после покупки.
        base?.let { list ->
            // Неизменённая цена сохраняет СВОЁ время: «сейчас» перебило бы цену, указанную позже, и
            // записало бы старую разницу в результат «за 24 часа».
            val keptAt = initial?.currentPriceAt?.takeIf { current == startCurrent }
            val now = maxOf(keptAt ?: System.currentTimeMillis(), ts + 1)
            val mark = ManualEntry.parsePrice(current)?.let {
                com.financeos.hub.core.invest.BrokerPriceMark(broker, now, ticker.trim().uppercase(), it, currency)
            }
            list + listOfNotNull(mark)
        }
    }
    val changed = initial == null ||
        currency != initial.currency || contract != initial.contract || date != dayOf(initial.timestamp) ||
        FormValues("", ticker, quantity, price) != start || current != startCurrent || amount.isNotBlank()
    val sym = FosFormatter.currencySymbol(cashCurrency ?: currency)

    FosFormSheet(
        onDismiss  = onDismiss,
        hasChanges = {
            if (initial == null) ticker.isNotBlank() || quantity.isNotBlank() || price.isNotBlank() ||
                current.isNotBlank() || amount.isNotBlank()
            else changed
        },
    ) {
        Text(if (initial == null) "Актив в портфеле" else "Актив", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
        Text(
            "Для бумаги, купленной раньше или без пуша. Деньги на её покупку заводятся вместе с ней — " +
                "свободные деньги не уйдут в минус. Удаляется тоже вместе. Валюту на счёте " +
                "(USD000SMALL, CNY000SMALL) вводите суммой.",
            style = FosType.Micro,
            color = FosColors.TextSecondary,
        )
        InvestField(ticker, { ticker = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(16) },
            "Тикер (SBER, LQDT, USD000SMALL…)", KeyboardType.Ascii)
        if (cashCurrency != null) {
            Text(
                "${SecurityGroups.currencyName(cashCurrency)} — это деньги на счёте, а не бумага: " +
                    "запишется пополнением в этой валюте.",
                style = FosType.Micro,
                color = FosColors.TextSecondary,
            )
            MoneyField(amount, { amount = it }, "Сумма, $sym")
        } else {
            CurrencyChips(currency) { currency = it }
            InvestField(quantity, { quantity = it.filter(Char::isDigit).take(12) }, "Количество, шт.", KeyboardType.Number)
            InvestField(price, { price = ManualEntry.sanitizePrice(it) }, "Средняя цена покупки, $sym", KeyboardType.Decimal)
            InvestField(current, { current = ManualEntry.sanitizePrice(it) }, "Текущая цена, $sym (необязательно)", KeyboardType.Decimal)
        }
        if (contracts.isNotEmpty()) ContractChips("Счёт", contracts, contract, allowNone = true) { contract = it }
        DateChip(date, label = "Дата покупки") { date = it }
        SaveButton(enabled = events != null && changed) {
            events?.let(onSave)
            onDismiss()
        }
        onDelete?.let { DeleteButton("Удалить актив", it) }
    }
}

/** Поля формы строками — так их держат поля ввода (инвариант #7); для сравнения «изменилось ли». */
private data class FormValues(val amount: String, val ticker: String, val quantity: String, val price: String) {
    constructor(d: ManualEntry.Draft) : this(
        amount   = d.amountKopecks?.let(FosFormatter::plainAmountInput) ?: "",
        ticker   = d.ticker,
        quantity = d.quantity?.toString() ?: "",
        price    = d.priceMicros?.let(ManualEntry::priceInput) ?: "",
    )
    companion object { val EMPTY = FormValues("", "", "", "") }
}

private fun dayOf(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrencyChips(selected: String, onSelect: (String) -> Unit) {
    Text("Валюта", style = FosType.SectionCap, color = FosColors.TextMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ManualEntry.CURRENCIES.forEach { c ->
            InvestChip("${FosFormatter.currencySymbol(c)} $c", c == selected) { onSelect(c) }
        }
    }
}

@Composable
private fun DeleteButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = FosType.Label, color = FosColors.Negative)
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
 * Карточка бумаги: текущая цена, СДЕЛКИ, из которых сложилась позиция, и удаление целиком.
 * Позиция — сумма сделок, поэтому исправляется не она, а сделка: нажатие открывает её карточку
 * правки (#51) — количество, цену, дату, счёт. Цену без котировок знает только человек — без неё
 * стоимость стоит на цене последней своей сделки.
 */
@Composable
fun BrokerPositionSheet(
    position : Portfolio.Position,
    onPrice  : (Long) -> Unit,
    onDelete : () -> Unit,
    onDismiss: () -> Unit,
    /** Исполненные и отменённые сделки по этой бумаге — новые сверху. */
    trades   : List<com.financeos.hub.core.invest.BrokerOrder> = emptyList(),
    estimates: Map<com.financeos.hub.core.invest.BrokerOrder, Long> = emptyMap(),
    onTrade  : ((com.financeos.hub.core.invest.BrokerOrder) -> Unit)? = null,
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
        if (trades.isNotEmpty()) {
            Text("Сделки", style = FosType.SectionCap, color = FosColors.TextMuted)
            Text(
                "Нажмите на сделку, чтобы исправить количество, цену, дату или счёт.",
                style = FosType.Micro,
                color = FosColors.TextSecondary,
            )
            trades.forEach { o -> OrderRow(o, estimates[o], onTrade) }
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
