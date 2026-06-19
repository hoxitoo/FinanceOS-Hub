package com.financeos.hub.features.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

private val GOAL_EMOJIS = listOf(
    "🏠", "🚗", "✈", "📱", "💻", "📚", "🎓", "💍",
    "🏖", "🎸", "🏋", "💊", "🛋", "🎁", "💰", "⭐",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGoalSheet(
    sheetState: SheetState,
    onDismiss : () -> Unit,
    onSave    : (name: String, emoji: String, targetKopecks: Long, deadlineAt: Long?) -> Unit,
) {
    var name          by remember { mutableStateOf("") }
    var targetText    by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf(GOAL_EMOJIS[0]) }

    val targetKopecks = targetText.replace(",", ".").toDoubleOrNull()?.let { (it * 100).toLong() } ?: 0L
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
            Text("Новая цель", style = FosType.ScreenTitle, color = FosColors.TextPrimary)

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
                                if (selected) FosColors.Positive else FosColors.Border,
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

            // Target amount
            OutlinedTextField(
                value           = targetText,
                onValueChange   = { targetText = it },
                label           = { Text("Целевая сумма, ₽", style = FosType.Label) },
                isError         = targetText.isNotBlank() && targetKopecks == 0L,
                singleLine      = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction    = ImeAction.Done,
                ),
                colors          = sheetFieldColors(),
                modifier        = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick  = {
                    onSave(name.trim(), selectedEmoji, targetKopecks, null)
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
                Text("Создать цель", style = FosType.BodySemi)
            }
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
