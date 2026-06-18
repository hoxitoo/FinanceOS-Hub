package com.financeos.hub.features.budget

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
import com.financeos.hub.core.database.entities.BudgetPeriod
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBudgetSheet(
    sheetState : SheetState,
    categories : List<CategoryEntity>,
    onDismiss  : () -> Unit,
    onSave     : (categoryId: String, limitKopecks: Long, period: BudgetPeriod) -> Unit,
) {
    var limitText    by remember { mutableStateOf("") }
    var categoryId   by remember { mutableStateOf<String?>(null) }
    var period       by remember { mutableStateOf(BudgetPeriod.MONTHLY) }

    val limitKopecks = limitText.replace(",", ".").toDoubleOrNull()?.let { (it * 100).toLong() } ?: 0L
    val canSave      = categoryId != null && limitKopecks > 0

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
            Text("Новый бюджет", style = FosType.ScreenTitle, color = FosColors.TextPrimary)

            // Period toggle
            Text("Период", style = FosType.SectionCap, color = FosColors.TextMuted)
            LazyRow(
                contentPadding        = PaddingValues(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(BudgetPeriod.entries.size) { i ->
                    val p        = BudgetPeriod.entries[i]
                    val selected = period == p
                    FilterChip(
                        selected = selected,
                        onClick  = { period = p },
                        label    = { Text(if (p == BudgetPeriod.MONTHLY) "Месяц" else "Неделя", style = FosType.Label) },
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

            // Category chips
            Text("Категория", style = FosType.SectionCap, color = FosColors.TextMuted)
            LazyRow(
                contentPadding        = PaddingValues(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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

            // Limit
            OutlinedTextField(
                value           = limitText,
                onValueChange   = { limitText = it },
                label           = { Text("Лимит, ₽", style = FosType.Label) },
                isError         = limitText.isNotBlank() && limitKopecks == 0L,
                singleLine      = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction    = ImeAction.Done,
                ),
                colors          = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = FosColors.Info,
                    unfocusedBorderColor = FosColors.Border,
                    focusedLabelColor    = FosColors.Info,
                    unfocusedLabelColor  = FosColors.TextMuted,
                    cursorColor          = FosColors.Info,
                    focusedTextColor     = FosColors.TextPrimary,
                    unfocusedTextColor   = FosColors.TextPrimary,
                    errorBorderColor     = FosColors.Negative,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick  = {
                    categoryId?.let { catId ->
                        onSave(catId, limitKopecks, period)
                        onDismiss()
                    }
                },
                enabled  = canSave,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(FosDimens.RadiusCard),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = FosColors.Positive,
                    contentColor   = FosColors.Background,
                ),
            ) {
                Text("Создать бюджет", style = FosType.BodySemi)
            }
        }
    }
}
