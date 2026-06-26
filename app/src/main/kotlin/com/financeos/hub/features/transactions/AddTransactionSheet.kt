package com.financeos.hub.features.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.CardEntity
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Preset income sources — emoji + label. Selected value is stored into the description. */
private val INCOME_SOURCES = listOf(
    "💼" to "Зарплата",
    "↔" to "Перевод",
    "🎰" to "Букмекер",
    "🎁" to "Подарок",
    "💸" to "Кэшбэк",
    "📈" to "Инвестиции",
    "💰" to "Другое",
)

/**
 * One selectable money-source in the picker: a specific card (or the account itself when it has
 * no card). Several options can share the same [accountId] — a second card on an account is the
 * same balance, so picking either attributes the operation to that one account; [mask] only records
 * which physical card it left from.
 */
private data class SourceOption(
    val accountId: String,
    val accountName: String,
    val mask: String?,
) {
    val key: String get() = "${accountId}_${mask ?: "_"}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    sheetState : SheetState,
    categories : List<CategoryEntity>,
    accounts   : List<AccountEntity>,
    cards      : List<CardEntity>,
    onDismiss  : () -> Unit,
    onSave     : (type: TransactionType, amountKopecks: Long, merchant: String, categoryId: String?, note: String?, accountId: String?, sourceMask: String?, timestamp: Long) -> Unit,
) {
    var txType       by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountText   by remember { mutableStateOf("") }
    var merchant     by remember { mutableStateOf("") }
    var note         by remember { mutableStateOf("") }
    var categoryId   by remember { mutableStateOf<String?>(null) }
    var incomeSource by remember { mutableStateOf<String?>(null) }
    // Selected money-source key (account + card mask). Null = no source chosen.
    var sourceKey    by remember { mutableStateOf<String?>(null) }
    // Selected date for the operation (defaults to today). Stored as the local day; the
    // actual save-time clock is folded in so back-dated rows sort correctly within their day.
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val amountError = amountText.isNotBlank() && amountText.replace(",", ".").toDoubleOrNull() == null
    // One chip per card: the account's primary card plus every secondary card from the `cards`
    // table (deduped), so a second card like ••2548 on the «текущий» account is selectable too.
    // An account with no card at all still gets a single chip.
    val sourceOptions = remember(accounts, cards) {
        accounts.flatMap { acc ->
            val masks = (listOfNotNull(acc.cardMask) +
                cards.filter { it.accountId == acc.id }.map { it.cardMask })
                .distinct()
            if (masks.isEmpty()) listOf(SourceOption(acc.id, acc.name, null))
            else masks.map { m -> SourceOption(acc.id, acc.name, m) }
        }
    }
    val selectedOption  = sourceOptions.firstOrNull { it.key == sourceKey }
    val selectedAccount = accounts.firstOrNull { it.id == selectedOption?.accountId }
    val currencySymbol  = FosFormatter.currencySymbol(selectedAccount?.currency ?: "RUB")

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = sheetState,
        containerColor    = FosColors.Surface,
        contentColor      = FosColors.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FosDimens.ScreenPadding)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
        ) {
            Text("Добавить операцию", style = FosType.ScreenTitle, color = FosColors.TextPrimary)

            // Expense / Income toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TransactionType.EXPENSE to "Расход", TransactionType.INCOME to "Доход")
                    .forEach { (type, label) ->
                        val selected = txType == type
                        val selColor = if (type == TransactionType.EXPENSE) FosColors.Negative else FosColors.Positive
                        FilterChip(
                            selected = selected,
                            onClick  = { txType = type },
                            label    = { Text(label, style = FosType.Label) },
                            shape    = RoundedCornerShape(FosDimens.RadiusChip),
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = selColor.copy(alpha = 0.15f),
                                selectedLabelColor     = selColor,
                                containerColor         = FosColors.Surface2,
                                labelColor             = FosColors.TextSecondary,
                            ),
                        )
                    }
            }

            // Amount — currency symbol follows the selected account
            OutlinedTextField(
                value         = amountText,
                onValueChange = { amountText = it },
                label         = { Text("Сумма, $currencySymbol", style = FosType.Label) },
                isError       = amountError,
                singleLine    = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction    = ImeAction.Next,
                ),
                colors        = fosTextFieldColors(),
                modifier      = Modifier.fillMaxWidth(),
            )

            // Date of the operation — for manual entries that were missed by SMS/push
            Text("Дата операции", style = FosType.SectionCap, color = FosColors.TextMuted)
            val selectedDateMillis = selectedDate
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            FilterChip(
                selected = true,
                onClick  = { showDatePicker = true },
                label    = { Text("📅  ${FosFormatter.dayLabelYear(selectedDateMillis)}", style = FosType.Micro) },
                shape    = RoundedCornerShape(FosDimens.RadiusChip),
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = FosColors.Info.copy(alpha = 0.15f),
                    selectedLabelColor     = FosColors.Info,
                ),
            )

            // Account / card picker — where the money landed / left from. One chip per card
            // (primary + secondary), so any card on a multi-card account can be chosen.
            if (sourceOptions.isNotEmpty()) {
                Text(
                    if (txType == TransactionType.INCOME) "Куда зачислено" else "С какого счёта / карты",
                    style = FosType.SectionCap,
                    color = FosColors.TextMuted,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sourceOptions, key = { it.key }) { opt ->
                        val selected = sourceKey == opt.key
                        val mask     = opt.mask?.let { " ••$it" } ?: ""
                        FilterChip(
                            selected = selected,
                            onClick  = { sourceKey = if (selected) null else opt.key },
                            label    = { Text("${opt.accountName}$mask", style = FosType.Micro) },
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
            }

            // Merchant / payee
            OutlinedTextField(
                value         = merchant,
                onValueChange = { merchant = it },
                label         = {
                    Text(
                        if (txType == TransactionType.INCOME) "Отправитель / источник" else "Получатель / магазин",
                        style = FosType.Label,
                    )
                },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors        = fosTextFieldColors(),
                modifier      = Modifier.fillMaxWidth(),
            )

            // Note
            OutlinedTextField(
                value         = note,
                onValueChange = { note = it },
                label         = { Text("Заметка (необязательно)", style = FosType.Label) },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors        = fosTextFieldColors(),
                modifier      = Modifier.fillMaxWidth(),
            )

            // INCOME → source presets; EXPENSE → category chips
            if (txType == TransactionType.INCOME) {
                Text("Тип дохода", style = FosType.SectionCap, color = FosColors.TextMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(INCOME_SOURCES) { (emoji, label) ->
                        val selected = incomeSource == label
                        FilterChip(
                            selected = selected,
                            onClick  = { incomeSource = if (selected) null else label },
                            label    = { Text("$emoji $label", style = FosType.Micro) },
                            shape    = RoundedCornerShape(FosDimens.RadiusChip),
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FosColors.Positive.copy(alpha = 0.15f),
                                selectedLabelColor     = FosColors.Positive,
                                containerColor         = FosColors.Surface2,
                                labelColor             = FosColors.TextSecondary,
                            ),
                        )
                    }
                }
            } else if (categories.isNotEmpty()) {
                Text("Категория", style = FosType.SectionCap, color = FosColors.TextMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories, key = { it.id }) { cat ->
                        val selected = categoryId == cat.id
                        FilterChip(
                            selected = selected,
                            onClick  = { categoryId = if (selected) null else cat.id },
                            label    = { Text("${cat.emoji} ${cat.name}", style = FosType.Micro) },
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
            }

            Spacer(Modifier.height(4.dp))

            // Save button
            val kopecks = amountText.replace(",", ".").toDoubleOrNull()?.let { (it * 100).toLong() } ?: 0L
            Button(
                onClick  = {
                    if (kopecks > 0) {
                        // For income, fold the chosen source into the description (with the note if any).
                        val finalNote = if (txType == TransactionType.INCOME) {
                            listOfNotNull(incomeSource, note.ifBlank { null }).joinToString(" · ").ifBlank { null }
                        } else {
                            note.ifBlank { null }
                        }
                        // Today keeps the live clock; a back-dated day uses the current time-of-day
                        // on that date so it sorts naturally within the day's group.
                        val today = LocalDate.now()
                        val timestamp = if (selectedDate == today) {
                            System.currentTimeMillis()
                        } else {
                            selectedDate.atTime(LocalTime.now())
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }
                        onSave(txType, kopecks, merchant, categoryId, finalNote,
                            selectedOption?.accountId, selectedOption?.mask, timestamp)
                        onDismiss()
                    }
                },
                enabled  = kopecks > 0 && !amountError,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(FosDimens.RadiusCard),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = FosColors.Positive,
                    contentColor   = FosColors.Background,
                ),
            ) {
                Text("Сохранить", style = FosType.BodySemi)
            }
        }
    }

    if (showDatePicker) {
        // The picker works in UTC; we read back the picked day-of-month directly to avoid
        // any timezone drift when converting to a LocalDate.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = NoFutureDates,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
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

/** Disallows selecting a date in the future — you cannot spend money tomorrow. */
@OptIn(ExperimentalMaterial3Api::class)
private val NoFutureDates = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val todayUtc = LocalDate.now()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return utcTimeMillis <= todayUtc
    }
    override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
}

@Composable
private fun fosTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = FosColors.Info,
    unfocusedBorderColor = FosColors.BorderStrong,
    focusedLabelColor    = FosColors.Info,
    unfocusedLabelColor  = FosColors.TextMuted,
    cursorColor          = FosColors.Info,
    focusedTextColor     = FosColors.TextPrimary,
    unfocusedTextColor   = FosColors.TextPrimary,
    errorBorderColor     = FosColors.Negative,
    errorLabelColor      = FosColors.Negative,
)
