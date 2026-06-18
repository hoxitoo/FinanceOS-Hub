package com.financeos.hub.features.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.ui.components.GoalRing
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(vm: GoalsViewModel = hiltViewModel()) {
    val state       by vm.state.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    val addSheetState = rememberModalBottomSheetState(skipPartialExpansion = true)

    var contributeTarget by remember { mutableStateOf<GoalEntity?>(null) }
    var contributeText   by remember { mutableStateOf("") }

    Scaffold(
        containerColor = FosColors.Background,
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showAddSheet = true },
                containerColor = FosColors.Positive,
                contentColor   = FosColors.Background,
                shape          = CircleShape,
                modifier       = Modifier.size(56.dp),
            ) {
                Text("+", style = FosType.ScreenTitle, color = FosColors.Background)
            }
        },
    ) { inner ->
        LazyColumn(
            modifier              = Modifier
                .fillMaxSize()
                .background(FosColors.Background)
                .padding(inner),
            contentPadding        = PaddingValues(horizontal = FosDimens.ScreenPadding),
            verticalArrangement   = Arrangement.spacedBy(FosDimens.CardGap),
        ) {
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Text("Цели", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
            }

            if (state.goals.isEmpty()) {
                item {
                    Text(
                        "Нажмите + чтобы добавить первую цель",
                        style    = FosType.Body,
                        color    = FosColors.TextMuted,
                        modifier = Modifier.padding(top = FosDimens.SectionGap),
                    )
                }
            } else {
                items(state.goals, key = { it.id }) { goal ->
                    GoalCard(
                        goal              = goal,
                        onAddContribution = {
                            contributeTarget = goal
                            contributeText   = ""
                        },
                        onDelete = { vm.deleteGoal(goal.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddSheet) {
        AddGoalSheet(
            sheetState = addSheetState,
            onDismiss  = { showAddSheet = false },
            onSave     = { name, emoji, targetKopecks, deadline ->
                vm.createGoal(name, emoji, targetKopecks, deadline)
            },
        )
    }

    // Contribute dialog
    contributeTarget?.let { goal ->
        val kopecks = contributeText.replace(",", ".").toDoubleOrNull()?.let { (it * 100).toLong() } ?: 0L
        AlertDialog(
            onDismissRequest = { contributeTarget = null },
            containerColor   = FosColors.Surface,
            title = {
                Text(
                    "${goal.emoji} ${goal.name}",
                    style = FosType.BodySemi,
                    color = FosColors.TextPrimary,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Добавить сбережения:",
                        style = FosType.Body,
                        color = FosColors.TextSecondary,
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value           = contributeText,
                        onValueChange   = { contributeText = it },
                        label           = { Text("Сумма, ₽", style = FosType.Label) },
                        singleLine      = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        ),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = FosColors.Info,
                            unfocusedBorderColor = FosColors.Border,
                            focusedLabelColor    = FosColors.Info,
                            unfocusedLabelColor  = FosColors.TextMuted,
                            cursorColor          = FosColors.Info,
                            focusedTextColor     = FosColors.TextPrimary,
                            unfocusedTextColor   = FosColors.TextPrimary,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick  = {
                        if (kopecks > 0) vm.addContribution(goal, kopecks)
                        contributeTarget = null
                    },
                    enabled = kopecks > 0,
                ) {
                    Text("Добавить", color = FosColors.Positive)
                }
            },
            dismissButton = {
                TextButton(onClick = { contributeTarget = null }) {
                    Text("Отмена", color = FosColors.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun GoalCard(
    goal             : GoalEntity,
    onAddContribution: () -> Unit,
    onDelete         : () -> Unit,
) {
    val ratio = if (goal.targetKopecks > 0)
        goal.savedKopecks.toFloat() / goal.targetKopecks else 0f
    val complete = ratio >= 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCard))
            .background(FosColors.Surface)
            .padding(FosDimens.CardPadding),
        horizontalArrangement = Arrangement.spacedBy(FosDimens.CardPadding),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        GoalRing(
            progress = ratio,
            modifier = Modifier.size(64.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${goal.emoji} ${goal.name}",
                style = FosType.BodySemi,
                color = if (complete) FosColors.Positive else FosColors.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${FosFormatter.compact(goal.savedKopecks)} из ${FosFormatter.compact(goal.targetKopecks)}",
                style = FosType.Micro,
                color = FosColors.TextSecondary,
            )
            goal.deadlineAt?.let {
                Text(
                    "до ${FosFormatter.dayLabel(it)}",
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (complete) "✓" else "${(ratio * 100).toInt()}%",
                style = FosType.SmallBold,
                color = if (complete) FosColors.Positive else FosColors.TextSecondary,
            )
            if (!complete) {
                TextButton(
                    onClick      = onAddContribution,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("+", style = FosType.BodySemi, color = FosColors.Info)
                }
            }
            TextButton(
                onClick        = onDelete,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("×", style = FosType.BodySemi, color = FosColors.Negative)
            }
        }
    }
}
