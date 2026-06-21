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
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    sheetState : SheetState,
    categories : List<CategoryEntity>,
    accounts   : List<AccountEntity>,
    onDismiss  : () -> Unit,
    onSave     : (type: TransactionType, amountKopecks: Long, merchant: String, categoryId: String?, note: String?, accountId: String?) -> Unit,
) {
    var txType       by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountText   by remember { mutableStateOf("") }
    var merchant     by remember { mutableStateOf("") }
    var note         by remember { mutableStateOf("") }
    var categoryId   by remember { mutableStateOf<String?>(null) }
    var incomeSource by remember { mutableStateOf<String?>(null) }
    var accountId    by remember { mutableStateOf<String?>(null) }

    val amountError = amountText.isNotBlank() && amountText.replace(",", ".").toDoubleOrNull() == null
    val selectedAccount = accounts.firstOrNull { it.id == accountId }
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

            // Account / card picker — where the money landed / left from
            if (accounts.isNotEmpty()) {
                Text(
                    if (txType == TransactionType.INCOME) "Куда зачислено" else "С какого счёта",
                    style = FosType.SectionCap,
                    color = FosColors.TextMuted,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(accounts, key = { it.id }) { acc ->
                        val selected = accountId == acc.id
                        val mask     = acc.cardMask?.let { " ••$it" } ?: ""
                        FilterChip(
                            selected = selected,
                            onClick  = { accountId = if (selected) null else acc.id },
                            label    = { Text("${acc.name}$mask", style = FosType.Micro) },
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
                        onSave(txType, kopecks, merchant, categoryId, finalNote, accountId)
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
