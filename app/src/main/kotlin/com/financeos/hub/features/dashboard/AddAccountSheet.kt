package com.financeos.hub.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
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
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

private val BANKS = listOf("Сбербанк", "Т-Банк", "ВТБ", "Альфа-Банк", "Газпромбанк", "Другой")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountSheet(
    sheetState: SheetState,
    onDismiss : () -> Unit,
    onSave    : (name: String, bank: String, cardMask: String?, balanceKopecks: Long) -> Unit,
) {
    var selectedBank by remember { mutableStateOf(BANKS[0]) }
    var name         by remember { mutableStateOf("") }
    var cardMaskText by remember { mutableStateOf("") }
    var balanceText  by remember { mutableStateOf("") }

    val balanceKopecks = balanceText.replace(",", ".").toDoubleOrNull()?.let { (it * 100).toLong() } ?: 0L
    val canSave        = name.isNotBlank()

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
            Text("Новый счёт", style = FosType.ScreenTitle, color = FosColors.TextPrimary)

            // Bank selector
            Text("Банк", style = FosType.SectionCap, color = FosColors.TextMuted)
            LazyRow(
                contentPadding        = PaddingValues(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(BANKS.size) { i ->
                    val bank     = BANKS[i]
                    val selected = selectedBank == bank
                    FilterChip(
                        selected = selected,
                        onClick  = { selectedBank = bank },
                        label    = { Text(bank, style = FosType.Label) },
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

            // Account name
            OutlinedTextField(
                value           = name,
                onValueChange   = { name = it },
                label           = { Text("Название счёта", style = FosType.Label) },
                placeholder     = { Text("Например: Зарплатная", style = FosType.Body, color = FosColors.TextMuted) },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors          = accountSheetFieldColors(),
                modifier        = Modifier.fillMaxWidth(),
            )

            // Card mask (last 4 digits)
            OutlinedTextField(
                value         = cardMaskText,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) cardMaskText = it },
                label         = { Text("Последние 4 цифры карты (необязательно)", style = FosType.Label) },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction    = ImeAction.Next,
                ),
                colors   = accountSheetFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            // Initial balance
            OutlinedTextField(
                value           = balanceText,
                onValueChange   = { balanceText = it },
                label           = { Text("Текущий баланс, ₽", style = FosType.Label) },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction    = ImeAction.Done,
                ),
                colors   = accountSheetFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick  = {
                    onSave(
                        name.trim(),
                        selectedBank,
                        cardMaskText.takeIf { it.length == 4 },
                        balanceKopecks,
                    )
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
                Text("Добавить счёт", style = FosType.BodySemi)
            }
        }
    }
}

@Composable
private fun accountSheetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = FosColors.Info,
    unfocusedBorderColor = FosColors.BorderStrong,
    focusedLabelColor    = FosColors.Info,
    unfocusedLabelColor  = FosColors.TextMuted,
    cursorColor          = FosColors.Info,
    focusedTextColor     = FosColors.TextPrimary,
    unfocusedTextColor   = FosColors.TextPrimary,
    errorBorderColor     = FosColors.Negative,
)
