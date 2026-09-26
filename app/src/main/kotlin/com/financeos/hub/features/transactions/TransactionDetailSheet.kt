package com.financeos.hub.features.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.CardEntity
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.edit.TransactionEditor
import com.financeos.hub.ui.components.AccountPicker
import com.financeos.hub.ui.components.FosFormSheet
import com.financeos.hub.ui.components.SourceOption
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Карточка операции — просмотр и правка, включая счёт и дату.
 *
 * Счёт правится здесь, а не удалением и повторным вводом, потому что у операции из пуша есть то,
 * чего у ручной копии не будет: исходный текст сообщения, остаток банка, связь с обязательством и
 * с целью. Пуш без реквизитов приходит без счёта — сумма есть, а откуда ушли деньги, приложение
 * не знает. Раньше единственным выходом было удалить операцию и занести её руками заново.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    transaction : TransactionEntity,
    categories  : List<CategoryEntity>,
    accounts    : List<AccountEntity>,
    cards       : List<CardEntity>,
    /** Вторая сторона перевода: чья она и можно ли её править. Считается по базе, отсюда suspend. */
    loadCounterSide: suspend (TransactionEntity) -> TransactionEditor.CounterSide,
    onDismiss   : () -> Unit,
    onSave      : (TransactionEditor.Edit) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }

    // Key on transaction.id: if a different tx is shown while this composable is still in
    // the composition (e.g. rapid tap during sheet dismiss animation), remember returns
    // fresh state for the new transaction instead of the previous one's stale values.
    var merchant     by remember(transaction.id) { mutableStateOf(transaction.merchant ?: "") }
    var note         by remember(transaction.id) { mutableStateOf(transaction.description ?: "") }
    var categoryId   by remember(transaction.id) { mutableStateOf(transaction.categoryId) }
    var selectedType by remember(transaction.id) { mutableStateOf(transaction.type) }

    // Счёт хранится как id, а не как ключ пункта списка. Если текущего счёта в списке нет (он в
    // другой валюте или уже удалён), пустой выбор в пикере НЕ должен означать «отвязать»: иначе
    // простое «Сохранить» после правки заметки молча снимало бы счёт с операции.
    var ownAccountId by remember(transaction.id) { mutableStateOf(transaction.accountId) }

    val originalDate = remember(transaction.id) {
        Instant.ofEpochMilli(transaction.timestamp).atZone(zone).toLocalDate()
    }
    var date           by remember(transaction.id) { mutableStateOf(originalDate) }
    var showDatePicker by remember(transaction.id) { mutableStateOf(false) }

    val counterSide by produceState<TransactionEditor.CounterSide?>(null, transaction.id) {
        value = loadCounterSide(transaction)
    }
    var counterTouched   by remember(transaction.id) { mutableStateOf(false) }
    var counterAccountId by remember(transaction.id) { mutableStateOf<String?>(null) }
    val effectiveCounter = if (counterTouched) counterAccountId else counterSide?.accountId

    // Только счета в валюте операции: сумма пуша записана в ЕГО валюте, и привязка рублёвой
    // операции к долларовому счёту сдвинула бы баланс на 1 500 долларов вместо 1 500 рублей.
    val options = remember(accounts, cards, transaction.currency) {
        accounts.filter { it.currency == transaction.currency }.flatMap { acc ->
            val masks = (listOfNotNull(acc.cardMask) +
                cards.filter { it.accountId == acc.id }.map { it.cardMask })
                .distinct()
            val mk = { m: String? ->
                SourceOption(acc.id, acc.name, acc.bank, m, acc.balanceKopecks, acc.currency)
            }
            if (masks.isEmpty()) listOf(mk(null)) else masks.map(mk)
        }
    }
    fun keyOf(accountId: String?, preferMask: String? = null): String? = accountId?.let { id ->
        options.firstOrNull { it.accountId == id && it.mask == preferMask }?.key
            ?: options.firstOrNull { it.accountId == id }?.key
    }
    fun accountOfKey(key: String?): String? = options.firstOrNull { it.key == key }?.accountId

    // Направление денег задаёт подписи. Перевод сохраняет знак строки, поэтому его направление —
    // знак суммы, а не выбранный тип.
    val outgoing = when (selectedType) {
        TransactionType.EXPENSE  -> true
        TransactionType.INCOME   -> false
        TransactionType.TRANSFER -> transaction.amountKopecks < 0
    }
    val ownLabel     = if (outgoing) "Счёт списания" else "Счёт зачисления"
    val counterLabel = if (outgoing) "Счёт зачисления" else "Счёт списания"
    val isTransfer   = selectedType == TransactionType.TRANSFER

    // The header colour/sign follow the CURRENTLY-SELECTED type so reclassifying a transfer to a
    // расход immediately reflects red/− before the user even saves. (mirrors TransactionRow rules)
    val amtColor   = when (selectedType) {
        TransactionType.EXPENSE  -> FosColors.Negative
        TransactionType.INCOME   -> FosColors.Positive
        TransactionType.TRANSFER -> FosColors.TextPrimary
    }
    val mag = kotlin.math.abs(transaction.amountKopecks)
    val sym = FosFormatter.currencySymbol(transaction.currency)
    val amtText = when (selectedType) {
        TransactionType.TRANSFER -> "↔ ${FosFormatter.amount(mag, sym)}"
        TransactionType.INCOME   -> FosFormatter.signedAmount(mag, sym)
        TransactionType.EXPENSE  -> FosFormatter.signedAmount(-mag, sym)
    }

    val counterChanged = isTransfer && counterTouched && counterSide?.editable == true &&
        counterAccountId != counterSide?.accountId

    // Правка: изменением считается отличие от самой операции, а не любой ввод — карточку часто
    // открывают просто посмотреть, и вопрос на выходе из неё был бы шумом.
    val dirty = {
        merchant != (transaction.merchant ?: "") ||
            note != (transaction.description ?: "") ||
            categoryId != transaction.categoryId ||
            selectedType != transaction.type ||
            ownAccountId != transaction.accountId ||
            date != originalDate ||
            counterChanged
    }

    FosFormSheet(
        onDismiss  = onDismiss,
        hasChanges = dirty,
    ) {
        // Amount header
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text  = amtText,
                style = FosType.HeroAmount,
                color = amtColor,
            )
            when (transaction.source) {
                TransactionSource.MANUAL -> Text("вручную", style = FosType.Micro, color = FosColors.TextMuted)
                TransactionSource.PUSH   -> Text("push", style = FosType.Micro, color = FosColors.Info)
                TransactionSource.PDF    -> Text("PDF", style = FosType.Micro, color = FosColors.TextMuted)
                else                     -> Text("SMS", style = FosType.Micro, color = FosColors.Info)
            }
        }

        // Merchant field
        OutlinedTextField(
            value           = merchant,
            onValueChange   = { merchant = it },
            label           = { Text("Получатель / магазин", style = FosType.Label) },
            singleLine      = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors          = addSheetTextFieldColors(),
            modifier        = Modifier.fillMaxWidth(),
        )

        // Type selector — lets the user reclassify, e.g. an outgoing «перевод другу» that the
        // app booked as a neutral TRANSFER but is really a расход (money left for good).
        Text("Тип операции", style = FosType.SectionCap, color = FosColors.TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeChip("Расход",  selectedType == TransactionType.EXPENSE,  FosColors.Negative,  Modifier.weight(1f)) { selectedType = TransactionType.EXPENSE }
            TypeChip("Доход",   selectedType == TransactionType.INCOME,   FosColors.Positive,  Modifier.weight(1f)) { selectedType = TransactionType.INCOME }
            TypeChip("Перевод", selectedType == TransactionType.TRANSFER, FosColors.TextPrimary, Modifier.weight(1f)) { selectedType = TransactionType.TRANSFER }
        }

        // ── Дата ─────────────────────────────────────────────────────────────────
        Text("Дата операции", style = FosType.SectionCap, color = FosColors.TextMuted)
        FilterChip(
            selected = true,
            onClick  = { showDatePicker = true },
            label    = {
                Text(
                    "📅  ${FosFormatter.dayLabelYear(date.atStartOfDay(zone).toInstant().toEpochMilli())}",
                    style = FosType.Micro,
                )
            },
            shape    = RoundedCornerShape(FosDimens.RadiusChip),
            colors   = FilterChipDefaults.filterChipColors(
                selectedContainerColor = FosColors.Info.copy(alpha = 0.15f),
                selectedLabelColor     = FosColors.Info,
            ),
        )

        // ── Счета ────────────────────────────────────────────────────────────────
        if (options.isNotEmpty()) {
            AccountPicker(
                title       = ownLabel,
                // У перевода обе стороны на одном счёте — не перевод: второй стороны среди
                // вариантов нет.
                options     = if (isTransfer) options.filter { it.accountId != effectiveCounter } else options,
                selectedKey = keyOf(ownAccountId, transaction.sourceMask),
                accent      = FosColors.Info,
                onSelect    = { ownAccountId = accountOfKey(it) },
            )
        } else {
            Text(
                "$ownLabel: нет счетов в валюте операции (${transaction.currency})",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
        }
        // Что будет с балансом — до нажатия, а не после. Молча сдвинутый баланс — ровно тот
        // сюрприз, из-за которого человек перестаёт верить цифре на главной.
        if (ownAccountId != transaction.accountId) {
            Text(
                when {
                    ownAccountId == null ->
                        "Операция останется в истории, но без счёта. Сумма вернётся на прежний счёт, " +
                            "если банк не прислал по нему остаток позже."
                    transaction.balanceKopecks != null ->
                        "В сообщении был остаток банка — он и станет балансом счёта, если он свежее."
                    else ->
                        "Баланс счёта сдвинется на сумму операции. Если банк позже уже прислал остаток " +
                            "по этому счёту, баланс не тронется: цифра банка его уже учла."
                },
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
        }

        if (isTransfer) {
            val side = counterSide
            when {
                side == null -> Unit   // ещё считается
                side.editable && options.isNotEmpty() -> AccountPicker(
                    title       = counterLabel,
                    options     = options.filter { it.accountId != ownAccountId },
                    selectedKey = keyOf(effectiveCounter, transaction.counterpartyMask),
                    accent      = FosColors.Positive,
                    onSelect    = { key ->
                        counterTouched   = true
                        counterAccountId = accountOfKey(key)
                    },
                )
                else -> {
                    val name = accounts.firstOrNull { it.id == side.accountId }?.name
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(counterLabel, style = FosType.Label, color = FosColors.TextMuted)
                        Text(name ?: "—", style = FosType.SmallBold, color = FosColors.TextPrimary)
                    }
                    side.lockedReason?.let {
                        Text(it, style = FosType.Micro, color = FosColors.TextMuted)
                    }
                }
            }
            if (counterChanged) {
                Text(
                    if (counterAccountId == null)
                        "Вторая сторона перевода будет удалена вместе с её суммой на счёте."
                    else
                        "На счёте появится вторая сторона перевода — своя строка, которую можно удалить.",
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                )
            }
        }

        // Реквизиты из самого сообщения банка — справка, а не поле: по ним видно, почему операция
        // пришла без счёта (маски нет) или не привязалась (маска есть, но карта не заведена).
        if (transaction.source == TransactionSource.SMS || transaction.source == TransactionSource.PUSH) {
            AccountMaskRow("В сообщении: списание", transaction.sourceMask)
            AccountMaskRow("В сообщении: зачисление", transaction.counterpartyMask)
            DiagRow(
                "Остаток в сообщении",
                transaction.balanceKopecks
                    ?.let { FosFormatter.amount(it, FosFormatter.currencySymbol(transaction.currency)) }
                    ?: "не пойман",
                ok = transaction.balanceKopecks != null,
            )
        }

        // Note field
        OutlinedTextField(
            value           = note,
            onValueChange   = { note = it },
            label           = { Text("Заметка", style = FosType.Label) },
            singleLine      = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors          = addSheetTextFieldColors(),
            modifier        = Modifier.fillMaxWidth(),
        )

        // Category grid
        Text("Категория", style = FosType.SectionCap, color = FosColors.TextMuted)
        LazyVerticalGrid(
            columns                  = GridCells.Fixed(4),
            horizontalArrangement    = Arrangement.spacedBy(8.dp),
            verticalArrangement      = Arrangement.spacedBy(8.dp),
            modifier                 = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            items(categories) { cat ->
                val selected = categoryId == cat.id
                CategoryCell(
                    cat      = cat,
                    selected = selected,
                    onClick  = { categoryId = if (selected) null else cat.id },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Save
        Button(
            onClick  = {
                onSave(
                    TransactionEditor.Edit(
                        type       = selectedType,
                        merchant   = merchant,
                        categoryId = categoryId,
                        note       = note.ifBlank { null },
                        accountId  = ownAccountId,
                        date       = date,
                        counter    = if (counterChanged) TransactionEditor.CounterChange(counterAccountId) else null,
                    )
                )
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(FosDimens.RadiusCard),
            colors   = ButtonDefaults.buttonColors(
                containerColor = FosColors.Positive,
                contentColor   = FosColors.Background,
            ),
        ) {
            Text("Сохранить", style = FosType.BodySemi)
        }

        // (Deletion is handled by swipe-left-to-reveal-trash on the row, so no delete button here.)

        // Diagnostic: the exact SMS/push text the app captured. Lets a mis-parse (e.g. an Alfa
        // push whose "Остаток"/card line wasn't delivered to the listener) be inspected/reported.
        transaction.rawText?.takeIf { it.isNotBlank() }?.let { raw ->
            Spacer(Modifier.height(8.dp))
            Text("Исходный текст", style = FosType.SectionCap, color = FosColors.TextMuted)
            Spacer(Modifier.height(4.dp))
            Text(
                text  = raw,
                style = FosType.Micro,
                color = FosColors.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
                    .background(FosColors.Surface2)
                    .padding(10.dp),
            )
        }
    }

    if (showDatePicker) {
        // The picker works in UTC; we read back the picked day-of-month directly to avoid
        // any timezone drift when converting to a LocalDate.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates           = NoFutureDates,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Готово", color = FosColors.Info) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Отмена", color = FosColors.TextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = FosColors.Surface),
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

/** Read-only label → account/card mask row. Shows "неизвестно" when the bank message omitted it. */
@Composable
private fun AccountMaskRow(label: String, mask: String?) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, style = FosType.Label, color = FosColors.TextMuted)
        Text(
            text  = mask?.takeIf { it.isNotBlank() }?.let { "••$it" } ?: "неизвестно",
            style = if (mask.isNullOrBlank()) FosType.Body else FosType.SmallBold,
            color = if (mask.isNullOrBlank()) FosColors.TextMuted else FosColors.TextPrimary,
        )
    }
}

/** Read-only diagnostic row: label → value, value coloured green (ok) / red (problem). */
@Composable
private fun DiagRow(label: String, value: String, ok: Boolean) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, style = FosType.Label, color = FosColors.TextMuted)
        Text(
            text  = value,
            style = FosType.SmallBold,
            color = if (ok) FosColors.Positive else FosColors.Negative,
        )
    }
}

/** Pill toggle used by the type selector (Расход / Доход / Перевод). */
@Composable
private fun TypeChip(
    label    : String,
    selected : Boolean,
    accent   : Color,
    modifier : Modifier = Modifier,
    onClick  : () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(FosDimens.RadiusChip))
            .background(if (selected) accent.copy(alpha = 0.14f) else FosColors.Surface2)
            .border(1.dp, if (selected) accent else FosColors.Border, RoundedCornerShape(FosDimens.RadiusChip))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = label,
            style = FosType.Label,
            color = if (selected) accent else FosColors.TextSecondary,
        )
    }
}

@Composable
private fun CategoryCell(cat: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) FosColors.Positive else FosColors.Border
    Column(
        modifier          = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(FosDimens.RadiusIcon))
            .background(if (selected) FosColors.Positive.copy(alpha = 0.10f) else FosColors.Surface2)
            .border(1.dp, borderColor, RoundedCornerShape(FosDimens.RadiusIcon))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(cat.emoji, style = FosType.CardAmount)
        Text(
            text     = cat.name,
            style    = FosType.Micro,
            color    = if (selected) FosColors.Positive else FosColors.TextSecondary,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun addSheetTextFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = FosColors.Info,
    unfocusedBorderColor = FosColors.BorderStrong,
    focusedLabelColor    = FosColors.Info,
    unfocusedLabelColor  = FosColors.TextMuted,
    cursorColor          = FosColors.Info,
    focusedTextColor     = FosColors.TextPrimary,
    unfocusedTextColor   = FosColors.TextPrimary,
)
