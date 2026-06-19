package com.financeos.hub.features.transactions

import androidx.compose.foundation.background
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
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    sheetState : SheetState,
    categories : List<CategoryEntity>,
    onDismiss  : () -> Unit,
    onSave     : (type: TransactionType, amountKopecks: Long, merchant: String, categoryId: String?, note: String?) -> Unit,
) {
    var txType     by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var merchant   by remember { mutableStateOf("") }
    var note       by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }

    val amountError = amountText.isNotBlank() && amountText.replace(",", ".").toDoubleOrNull() == null

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

            // Amount
            OutlinedTextField(
                value         = amountText,
                onValueChange = { amountText = it },
                label         = { Text("Сумма, ₽", style = FosType.Label) },
                isError       = amountError,
                singleLine    = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction    = ImeAction.Next,
                ),
                colors        = fosTextFieldColors(),
                modifier      = Modifier.fillMaxWidth(),
            )

            // Merchant / payee
            OutlinedTextField(
                value         = merchant,
                onValueChange = { merchant = it },
                label         = { Text("Получатель / магазин", style = FosType.Label) },
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

            // Category chips
            if (categories.isNotEmpty()) {
                Text("Категория", style = FosType.SectionCap, color = FosColors.TextMuted)
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(categories.size) { i ->
                        val cat      = categories[i]
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
                        onSave(txType, kopecks, merchant, categoryId, note.ifBlank { null })
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
