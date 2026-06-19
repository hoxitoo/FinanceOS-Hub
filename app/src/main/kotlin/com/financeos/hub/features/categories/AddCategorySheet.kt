package com.financeos.hub.features.categories

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

internal val CATEGORY_EMOJIS = listOf(
    "🏠", "🚗", "✈", "📱", "💻", "📚", "🎓", "💍",
    "🏖", "🎸", "🏋", "💊", "🛋", "🎁", "💰", "⭐",
    "🍔", "☕", "🛒", "🚇", "💅", "🐾", "🎬", "🎮",
)

internal val CATEGORY_COLORS = listOf(
    "#FFB84D", "#4DFFA0", "#4D9FFF", "#FF6B6B", "#C084FC", "#F472B6",
    "#34D399", "#A78BFA", "#60A5FA", "#FB923C", "#E879F9", "#2DD4BF",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCategorySheet(
    sheetState: SheetState,
    onDismiss : () -> Unit,
    onSave    : (name: String, emoji: String, color: String) -> Unit,
) {
    var name          by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf(CATEGORY_EMOJIS[0]) }
    var selectedColor by remember { mutableStateOf(CATEGORY_COLORS[0]) }

    val canSave = name.isNotBlank()

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
            Text("Новая категория", style = FosType.ScreenTitle, color = FosColors.TextPrimary)

            // Emoji picker
            Text("Иконка", style = FosType.SectionCap, color = FosColors.TextMuted)
            LazyRow(
                contentPadding        = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(CATEGORY_EMOJIS.size) { i ->
                    val emoji    = CATEGORY_EMOJIS[i]
                    val selected = emoji == selectedEmoji
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(FosDimens.RadiusIcon))
                            .background(
                                if (selected) Color(android.graphics.Color.parseColor(selectedColor)).copy(alpha = 0.20f)
                                else FosColors.Surface2
                            )
                            .border(
                                1.dp,
                                if (selected) Color(android.graphics.Color.parseColor(selectedColor)) else FosColors.Border,
                                RoundedCornerShape(FosDimens.RadiusIcon),
                            )
                            .clickable { selectedEmoji = emoji },
                    ) {
                        Text(emoji, style = FosType.BodySemi)
                    }
                }
            }

            // Color picker
            Text("Цвет", style = FosType.SectionCap, color = FosColors.TextMuted)
            LazyRow(
                contentPadding        = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(CATEGORY_COLORS.size) { i ->
                    val hex      = CATEGORY_COLORS[i]
                    val parsed   = Color(android.graphics.Color.parseColor(hex))
                    val selected = selectedColor == hex
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(parsed)
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = if (selected) FosColors.TextPrimary else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable { selectedColor = hex },
                    )
                }
            }

            // Name
            OutlinedTextField(
                value           = name,
                onValueChange   = { name = it },
                label           = { Text("Название категории", style = FosType.Label) },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors          = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = FosColors.Info,
                    unfocusedBorderColor = FosColors.BorderStrong,
                    focusedLabelColor    = FosColors.Info,
                    unfocusedLabelColor  = FosColors.TextMuted,
                    cursorColor          = FosColors.Info,
                    focusedTextColor     = FosColors.TextPrimary,
                    unfocusedTextColor   = FosColors.TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick  = {
                    onSave(name.trim(), selectedEmoji, selectedColor)
                    onDismiss()
                },
                enabled  = canSave,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(FosDimens.RadiusCard),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Color(android.graphics.Color.parseColor(selectedColor)),
                    contentColor   = FosColors.Background,
                ),
            ) {
                Text("Создать категорию", style = FosType.BodySemi)
            }
        }
    }
}
