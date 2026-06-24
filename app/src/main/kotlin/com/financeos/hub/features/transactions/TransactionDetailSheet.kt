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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    transaction : TransactionEntity,
    categories  : List<CategoryEntity>,
    categoryName: String,
    sheetState  : SheetState,
    onDismiss   : () -> Unit,
    onSave      : (merchant: String, categoryId: String?, note: String?) -> Unit,
    onDelete    : () -> Unit,
) {
    // Key on transaction.id: if a different tx is shown while this composable is still in
    // the composition (e.g. rapid tap during sheet dismiss animation), remember returns
    // fresh state for the new transaction instead of the previous one's stale values.
    var merchant    by remember(transaction.id) { mutableStateOf(transaction.merchant ?: "") }
    var note        by remember(transaction.id) { mutableStateOf(transaction.description ?: "") }
    var categoryId  by remember(transaction.id) { mutableStateOf(transaction.categoryId) }
    var showConfirm by remember(transaction.id) { mutableStateOf(false) }

    // Expense red, income green; a TRANSFER is neither → neutral (mirrors TransactionRow).
    val isTransfer = transaction.type == TransactionType.TRANSFER
    val amtColor   = when (transaction.type) {
        TransactionType.EXPENSE  -> FosColors.Negative
        TransactionType.INCOME   -> FosColors.Positive
        TransactionType.TRANSFER -> FosColors.TextPrimary
    }
    val amtText = if (isTransfer) {
        "↔ ${FosFormatter.amount(kotlin.math.abs(transaction.amountKopecks))}"
    } else {
        FosFormatter.signedAmount(transaction.amountKopecks)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = FosColors.Surface,
        contentColor     = FosColors.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FosDimens.ScreenPadding)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
        ) {
            // Amount header
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text  = amtText,
                        style = FosType.HeroAmount,
                        color = amtColor,
                    )
                    Text(
                        text  = FosFormatter.dayLabelYear(transaction.timestamp),
                        style = FosType.Micro,
                        color = FosColors.TextMuted,
                    )
                }
                if (transaction.source == com.financeos.hub.core.database.entities.TransactionSource.MANUAL) {
                    Text("вручную", style = FosType.Micro, color = FosColors.TextMuted)
                } else {
                    Text("SMS", style = FosType.Micro, color = FosColors.Info)
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

            // Account requisites — which account the money left / arrived at, parsed from the
            // bank SMS/push. Only meaningful for ingested transactions; manual entries skip it.
            val src = transaction.source
            if (src == com.financeos.hub.core.database.entities.TransactionSource.SMS ||
                src == com.financeos.hub.core.database.entities.TransactionSource.PUSH
            ) {
                AccountMaskRow("Счёт списания", transaction.sourceMask)
                AccountMaskRow("Счёт зачисления", transaction.counterpartyMask)
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
                    onSave(merchant, categoryId, note.ifBlank { null })
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

            // Delete
            TextButton(
                onClick  = { showConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Удалить операцию", style = FosType.Label, color = FosColors.Negative)
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor   = FosColors.Surface,
            title            = { Text("Удалить операцию?", style = FosType.BodySemi, color = FosColors.TextPrimary) },
            text             = { Text("Это действие нельзя отменить.", style = FosType.Body, color = FosColors.TextSecondary) },
            confirmButton    = {
                TextButton(onClick = { onDelete(); onDismiss() }) {
                    Text("Удалить", color = FosColors.Negative)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Отмена", color = FosColors.TextSecondary)
                }
            },
        )
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
