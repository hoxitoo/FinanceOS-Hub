package com.financeos.hub.features.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

private val GOAL_EMOJIS = listOf(
    "🏠", "🚗", "✈", "📱", "💻", "📚", "🎓", "💍",
    "🏖", "🎸", "🏋", "💊", "🛋", "🎁", "💰", "⭐",
)

/**
 * Bottom sheet for creating OR editing a goal.
 * Pass [existing] to pre-fill the fields and switch to edit mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGoalSheet(
    sheetState: SheetState,
    existing  : GoalEntity? = null,
    accounts  : List<AccountEntity> = emptyList(),
    onDismiss : () -> Unit,
    onSave    : (name: String, emoji: String, targetKopecks: Long, deadlineAt: Long?, linkedAccountId: String?) -> Unit,
) {
    val editing = existing != null

    var name             by remember { mutableStateOf(existing?.name ?: "") }
    var targetDigits     by remember { mutableStateOf(existing?.let { (it.targetKopecks / 100).toString() } ?: "") }
    var selectedEmoji    by remember { mutableStateOf(existing?.emoji ?: GOAL_EMOJIS[0]) }
    var deadline         by remember { mutableStateOf(existing?.deadlineAt) }
    var linkedAccountId  by remember { mutableStateOf<String?>(null) }
    var showDatePicker   by remember { mutableStateOf(false) }

    val targetKopecks = (targetDigits.toLongOrNull() ?: 0L) * 100L
    val canSave = name.isNotBlank() && targetKopecks > 0

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
            Text(
                if (editing) "Редактировать цель" else "Новая цель",
                style = FosType.ScreenTitle,
                color = FosColors.TextPrimary,
            )

            // Emoji picker
            Text("Иконка", style = FosType.SectionCap, color = FosColors.TextMuted)
            LazyRow(
                contentPadding        = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(GOAL_EMOJIS.size) { i ->
                    val emoji    = GOAL_EMOJIS[i]
                    val selected = emoji == selectedEmoji
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(FosDimens.RadiusIcon))
                            .background(
                                if (selected) FosColors.Positive.copy(alpha = 0.15f)
                                else FosColors.Surface2
                            )
                            .border(
                                1.dp,
                                if (selected) FosColors.Positive else FosColors.BorderStrong,
                                RoundedCornerShape(FosDimens.RadiusIcon),
                            )
                            .clickable { selectedEmoji = emoji },
                    ) {
                        Text(emoji, style = FosType.BodySemi)
                    }
                }
            }

            // Name
            OutlinedTextField(
                value           = name,
                onValueChange   = { name = it },
                label           = { Text("Название цели", style = FosType.Label) },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors          = sheetFieldColors(),
                modifier        = Modifier.fillMaxWidth(),
            )

            // Target amount — digits grouped live with spaces
            OutlinedTextField(
                value           = FosFormatter.groupDigits(targetDigits),
                onValueChange   = { input -> targetDigits = input.filter { it.isDigit() }.take(12) },
                label           = { Text("Целевая сумма, ₽", style = FosType.Label) },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction    = ImeAction.Done,
                ),
                colors          = sheetFieldColors(),
                modifier        = Modifier.fillMaxWidth(),
            )

            // Deadline — optional date picker
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FosDimens.RadiusButton))
                    .border(
                        1.dp,
                        FosColors.BorderStrong,
                        RoundedCornerShape(FosDimens.RadiusButton),
                    )
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        deadline?.let { "Срок: ${FosFormatter.date(it)}" } ?: "Срок выполнения (необязательно)",
                        style = FosType.Body,
                        color = if (deadline != null) FosColors.TextPrimary else FosColors.TextMuted,
                    )
                    if (deadline != null) {
                        Text(
                            "× Убрать",
                            style    = FosType.Label,
                            color    = FosColors.Negative,
                            modifier = Modifier.clickable { deadline = null },
                        )
                    } else {
                        Text("📅", style = FosType.Body)
                    }
                }
            }

            // Account picker — optional; links goal to a bank account for auto-routing
            if (accounts.isNotEmpty()) {
                Text("ПРИВЯЗАТЬ СЧЁТ (АВТО)", style = FosType.SectionCap, color = FosColors.TextMuted)
                Text(
                    "Переводы на этот счёт будут автоматически добавлять к цели, переводы с него — вычитать.",
                    style = FosType.Micro,
                    color = FosColors.TextSecondary,
                )
                LazyRow(
                    contentPadding        = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // "No link" chip
                    item {
                        val selected = linkedAccountId == null
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(FosDimens.RadiusButton))
                                .background(if (selected) FosColors.Surface2 else FosColors.Surface2)
                                .border(
                                    1.dp,
                                    if (selected) FosColors.TextSecondary else FosColors.BorderStrong,
                                    RoundedCornerShape(FosDimens.RadiusButton),
                                )
                                .clickable { linkedAccountId = null }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "— Не привязывать",
                                style = FosType.Label,
                                color = if (selected) FosColors.TextPrimary else FosColors.TextMuted,
                            )
                        }
                    }
                    items(accounts) { acc ->
                        val selected = linkedAccountId == acc.id
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(FosDimens.RadiusButton))
                                .background(
                                    if (selected) FosColors.Positive.copy(alpha = 0.12f) else FosColors.Surface2
                                )
                                .border(
                                    1.dp,
                                    if (selected) FosColors.Positive else FosColors.BorderStrong,
                                    RoundedCornerShape(FosDimens.RadiusButton),
                                )
                                .clickable { linkedAccountId = acc.id }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    acc.name,
                                    style = FosType.Label,
                                    color = if (selected) FosColors.Positive else FosColors.TextPrimary,
                                )
                                if (acc.cardMask != null) {
                                    Text(
                                        "•• ${acc.cardMask}",
                                        style = FosType.Micro,
                                        color = if (selected) FosColors.Positive.copy(alpha = 0.7f) else FosColors.TextMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick  = {
                    onSave(name.trim(), selectedEmoji, targetKopecks, deadline, linkedAccountId)
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
                Text(if (editing) "Сохранить" else "Создать цель", style = FosType.BodySemi)
            }
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = deadline ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    deadline = dpState.selectedDateMillis
                    showDatePicker = false
                }) { Text("ОК", color = FosColors.Positive) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Отмена", color = FosColors.TextSecondary)
                }
            },
        ) {
            DatePicker(state = dpState)
        }
    }
}

@Composable
private fun sheetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = FosColors.Info,
    unfocusedBorderColor = FosColors.BorderStrong,
    focusedLabelColor    = FosColors.Info,
    unfocusedLabelColor  = FosColors.TextMuted,
    cursorColor          = FosColors.Info,
    focusedTextColor     = FosColors.TextPrimary,
    unfocusedTextColor   = FosColors.TextPrimary,
    errorBorderColor     = FosColors.Negative,
)
