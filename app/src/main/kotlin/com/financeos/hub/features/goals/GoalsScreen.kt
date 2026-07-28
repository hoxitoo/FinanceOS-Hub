package com.financeos.hub.features.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.financeos.hub.ui.components.GoalArtBackdrop
import com.financeos.hub.ui.components.GoalRing
import com.financeos.hub.ui.components.goalArtFor
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(vm: GoalsViewModel = hiltViewModel()) {
    val state       by vm.state.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var contributeTarget by remember { mutableStateOf<GoalEntity?>(null) }
    var contributeText   by remember { mutableStateOf("") }

    var editTarget    by remember { mutableStateOf<GoalEntity?>(null) }
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var linkTarget    by remember { mutableStateOf<GoalEntity?>(null) }
    val linkSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var historyTarget    by remember { mutableStateOf<GoalEntity?>(null) }
    val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                        onEdit            = { editTarget = goal },
                        onAddContribution = {
                            contributeTarget = goal
                            contributeText   = ""
                        },
                        onLink    = { linkTarget = goal },
                        onHistory = { historyTarget = goal },
                        onDelete  = { vm.deleteGoal(goal.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddSheet) {
        AddGoalSheet(
            sheetState = addSheetState,
            accounts   = state.accounts,
            onDismiss  = { showAddSheet = false },
            onSave     = { name, emoji, targetKopecks, deadline, linkedAccountId ->
                vm.createGoal(name, emoji, targetKopecks, deadline, linkedAccountId)
            },
        )
    }

    // Edit existing goal
    editTarget?.let { goal ->
        AddGoalSheet(
            sheetState = editSheetState,
            existing   = goal,
            accounts   = state.accounts,
            onDismiss  = { editTarget = null },
            onSave     = { name, emoji, targetKopecks, deadline, _ ->
                vm.updateGoal(goal, name, emoji, targetKopecks, deadline)
                editTarget = null
            },
        )
    }

    // Contribute dialog
    contributeTarget?.let { goal ->
        val kopecks = FosFormatter.parseAmountInput(contributeText) ?: 0L
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
                        value           = FosFormatter.groupAmountInput(contributeText),
                        onValueChange   = { input ->
                            contributeText = input.filter { it.isDigit() || it == ',' || it == '.' }
                        },
                        label           = { Text("Сумма, ₽", style = FosType.Label) },
                        singleLine      = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        ),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = FosColors.Info,
                            unfocusedBorderColor = FosColors.BorderStrong,
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

    // Auto-fund link sheet
    linkTarget?.let { goal ->
        LinkTransferRouteSheet(
            goal           = goal,
            sheetState     = linkSheetState,
            routes         = state.routes,
            cardMasks      = state.cardMasks,
            accounts       = state.accounts,
            onLinkCard     = { mask -> vm.linkCard(goal.id, mask) },
            onLinkKeyword  = { kw -> vm.linkKeyword(goal.id, kw) },
            onLinkAccount  = { accountId -> vm.linkAccount(goal.id, accountId) },
            onUnlink       = { routeId -> vm.unlink(routeId) },
            onDismiss      = { linkTarget = null },
        )
    }

    // Goal history — every operation routed to this goal, with dates
    historyTarget?.let { goal ->
        GoalHistorySheet(
            goal       = goal,
            vm         = vm,
            sheetState = historySheetState,
            onDismiss  = { historyTarget = null },
        )
    }
}

@Composable
private fun GoalCard(
    goal             : GoalEntity,
    onEdit           : () -> Unit,
    onAddContribution: () -> Unit,
    onLink           : () -> Unit,
    onHistory        : () -> Unit,
    onDelete         : () -> Unit,
) {
    val ratio = if (goal.targetKopecks > 0)
        goal.savedKopecks.toFloat() / goal.targetKopecks else 0f
    val complete = ratio >= 1f
    val artKind  = remember(goal.emoji, goal.name) { goalArtFor(goal) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCard))
            .background(FosColors.Surface)
            .clickable { onEdit() },
    ) {
        // Themed pixel-art backdrop (falls back to a themed gradient until the art is bundled).
        GoalArtBackdrop(kind = artKind, modifier = Modifier.matchParentSize())

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            Spacer(Modifier.height(4.dp))
            // Tap to see every operation that funded (or drew from) this goal, with dates.
            Text(
                "История ›",
                style    = FosType.Micro,
                color    = FosColors.Info,
                modifier = Modifier.clickable { onHistory() },
            )
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
                onClick        = onLink,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("🔗", style = FosType.Micro, color = FosColors.TextSecondary)
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
}
