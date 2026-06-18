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
    var merchant    by remember { mutableStateOf(transaction.merchant ?: "") }
    var note        by remember { mutableStateOf(transaction.description ?: "") }
    var categoryId  by remember { mutableStateOf(transaction.categoryId) }
    var showConfirm by remember { mutableStateOf(false) }

    val isExpense = transaction.type == TransactionType.EXPENSE
    val amtColor  = if (isExpense) FosColors.Negative else FosColors.Positive

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
                        text  = FosFormatter.signedAmount(transaction.amountKopecks),
                        style = FosType.HeroAmount,
                        color = amtColor,
                    )
                    Text(
                        text  = FosFormatter.dayLabel(transaction.timestamp),
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
    unfocusedBorderColor = FosColors.Border,
    focusedLabelColor    = FosColors.Info,
    unfocusedLabelColor  = FosColors.TextMuted,
    cursorColor          = FosColors.Info,
    focusedTextColor     = FosColors.TextPrimary,
    unfocusedTextColor   = FosColors.TextPrimary,
)
