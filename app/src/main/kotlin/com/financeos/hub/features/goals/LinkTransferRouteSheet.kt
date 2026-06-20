package com.financeos.hub.features.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.core.database.entities.TransferMatchType
import com.financeos.hub.core.database.entities.TransferRouteEntity
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

/**
 * Bottom sheet that links a savings [goal] to one or more transfer "routes":
 * a card last-4 the user transfers TO, or a keyword that appears in the bank SMS.
 * Any matching outgoing transfer is then auto-added to the goal.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LinkTransferRouteSheet(
    goal         : GoalEntity,
    sheetState   : SheetState,
    routes       : List<TransferRouteEntity>,
    cardMasks    : List<String>,
    onLinkCard   : (mask: String) -> Unit,
    onLinkKeyword: (keyword: String) -> Unit,
    onUnlink     : (routeId: String) -> Unit,
    onDismiss    : () -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    val goalRoutes = routes.filter { it.goalId == goal.id }

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
                "Авто-пополнение цели «${goal.name}»",
                style = FosType.ScreenTitle,
                color = FosColors.TextPrimary,
            )
            Text(
                "Любой перевод на эту карту или содержащий слово будет автоматически добавлен в цель.",
                style = FosType.Body,
                color = FosColors.TextSecondary,
            )

            // Card masks
            if (cardMasks.isNotEmpty()) {
                Text("КАРТА НАЗНАЧЕНИЯ", style = FosType.SectionCap, color = FosColors.TextMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp),
                ) {
                    cardMasks.forEach { mask ->
                        val linked = goalRoutes.any {
                            it.matchType == TransferMatchType.CARD && it.matchValue.equals(mask, ignoreCase = true)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(FosDimens.RadiusButton))
                                .background(
                                    if (linked) FosColors.Info.copy(alpha = 0.15f) else FosColors.Surface2
                                )
                                .border(
                                    1.dp,
                                    if (linked) FosColors.Info else FosColors.BorderStrong,
                                    RoundedCornerShape(FosDimens.RadiusButton),
                                )
                                .clickable(enabled = !linked) { onLinkCard(mask) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "•• $mask",
                                style = FosType.Label,
                                color = if (linked) FosColors.Info else FosColors.TextPrimary,
                            )
                        }
                    }
                }
            }

            // Keyword
            Text("СЛОВО В СМС", style = FosType.SectionCap, color = FosColors.TextMuted)
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value           = keyword,
                    onValueChange   = { keyword = it },
                    label           = { Text("например: вклад", style = FosType.Label) },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors          = sheetFieldColors(),
                    modifier        = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (keyword.isNotBlank()) {
                            onLinkKeyword(keyword)
                            keyword = ""
                        }
                    },
                    enabled = keyword.isNotBlank(),
                    shape   = RoundedCornerShape(FosDimens.RadiusButton),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = FosColors.Positive,
                        contentColor   = FosColors.Background,
                    ),
                ) {
                    Text("Добавить", style = FosType.Label)
                }
            }

            // Existing routes
            if (goalRoutes.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("ПРИВЯЗКИ", style = FosType.SectionCap, color = FosColors.TextMuted)
                goalRoutes.forEach { route ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(FosDimens.RadiusButton))
                            .background(FosColors.Surface2)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        val label = when (route.matchType) {
                            TransferMatchType.CARD    -> "карта •• ${route.matchValue}"
                            TransferMatchType.KEYWORD -> "слово «${route.matchValue}»"
                        }
                        Text(label, style = FosType.Body, color = FosColors.TextPrimary)
                        Text(
                            "× Отвязать",
                            style    = FosType.Label,
                            color    = FosColors.Negative,
                            modifier = Modifier.clickable { onUnlink(route.id) },
                        )
                    }
                }
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
