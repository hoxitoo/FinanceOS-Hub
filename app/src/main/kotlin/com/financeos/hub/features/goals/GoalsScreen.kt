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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.core.database.entities.TransferMatchType
import com.financeos.hub.ui.components.GoalArtBackdrop
import com.financeos.hub.ui.components.GoalRing
import com.financeos.hub.ui.components.accent
import com.financeos.hub.ui.components.goalArtFor
import com.financeos.hub.ui.theme.AmountVisualTransformation
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.fosCard
import com.financeos.hub.ui.theme.fosCardEdge
import com.financeos.hub.ui.theme.fosCardSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    onCalculatorClick: () -> Unit = {},
    vm: GoalsViewModel = hiltViewModel(),
) {
    val state       by vm.state.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }

    var contributeTarget by remember { mutableStateOf<GoalEntity?>(null) }
    var contributeText   by remember { mutableStateOf("") }

    var editTarget    by remember { mutableStateOf<GoalEntity?>(null) }

    var linkTarget    by remember { mutableStateOf<GoalEntity?>(null) }

    var historyTarget    by remember { mutableStateOf<GoalEntity?>(null) }
    val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var deleteTarget   by remember { mutableStateOf<GoalEntity?>(null) }
    var completedOpen  by remember { mutableStateOf(false) }

    // Выполненной считается цель, на которой действительно лежит целевая сумма, а не только флаг:
    // после снятия денег через «−» флаг снимается не сразу, и цель уехала бы под свёрнутый
    // заголовок вместе с деньгами, которых на ней уже нет.
    val reached  = { g: GoalEntity -> g.targetKopecks > 0 && g.savedKopecks >= g.targetKopecks }
    val active    = remember(state.goals) { state.goals.filterNot(reached) }
    val completed = remember(state.goals) { state.goals.filter(reached) }

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
                Box(
                    modifier          = Modifier.fillMaxWidth(),
                    contentAlignment  = Alignment.CenterStart,
                ) {
                    Text("Цели", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
                    // Калькулятор живёт здесь, а не в настройках: вопрос «за сколько я это накоплю»
                    // возникает ровно в тот момент, когда смотришь на недособранную цель. По центру
                    // и словом, а не эмодзи: 🧮 в углу читался как украшение заголовка, и на него
                    // не нажимали — а это единственный вход в калькулятор.
                    TextButton(
                        onClick        = onCalculatorClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier       = Modifier.align(Alignment.Center),
                    ) {
                        Text(
                            "Калькулятор",
                            // На 15sp вместо прежних 11: это единственный вход в калькулятор,
                            // и подписью в углу его просто не замечали.
                            style = FosType.SubHeader.copy(fontSize = 15.sp),
                            color = FosColors.Info,
                        )
                    }
                }
            }

            if (state.goals.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = FosDimens.SectionGap)
                            .fosCard(FosCardStyle.Outline, FosTone.Info),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Целей пока нет", style = FosType.BodySemi, color = FosColors.TextPrimary)
                        Text(
                            "Нажмите +, чтобы добавить первую. Пополнять можно вручную или " +
                                "привязать цель к счёту — тогда переводы будут учитываться сами.",
                            style = FosType.Micro,
                            color = FosColors.TextSecondary,
                        )
                    }
                }
            } else {
                items(active, key = { it.id }) { goal ->
                    GoalCard(
                        goal              = goal,
                        onEdit            = { editTarget = goal },
                        onAdjust = {
                            contributeTarget = goal
                            // Поле открывается с ТЕКУЩЕЙ суммой: человек правит остаток, а не
                            // вводит его с нуля, и случайное «Сохранить» ничего не меняет.
                            contributeText   = FosFormatter.plainAmountInput(goal.savedKopecks)
                        },
                        onLink    = { linkTarget = goal },
                        onHistory = { historyTarget = goal },
                        onDelete  = { deleteTarget = goal },
                    )
                }

                // Выполненные — вниз и под сворачиваемый заголовок. Удалять их нельзя (это история
                // накоплений, и на них ещё лежат деньги), а место в начале списка они занимали
                // наравне с теми, на которые ещё копят.
                if (completed.isNotEmpty()) {
                    item(key = "completed_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = FosDimens.ItemGap)
                                .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
                                .clickable { completedOpen = !completedOpen }
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Выполненные",
                                style = FosType.SectionCap,
                                color = FosColors.TextSecondary,
                            )
                            Text(
                                completed.size.toString(),
                                style = FosType.MicroNum,
                                color = FosColors.Positive,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (completedOpen) "▲" else "▼",
                                style = FosType.Micro,
                                color = FosColors.TextMuted,
                            )
                        }
                    }
                    if (completedOpen) {
                        items(completed, key = { it.id }) { goal ->
                            GoalCard(
                                goal      = goal,
                                onEdit    = { editTarget = goal },
                                onAdjust  = {
                                    contributeTarget = goal
                                    contributeText   = FosFormatter.plainAmountInput(goal.savedKopecks)
                                },
                                onLink    = { linkTarget = goal },
                                onHistory = { historyTarget = goal },
                                onDelete  = { deleteTarget = goal },
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddSheet) {
        AddGoalSheet(
            accounts    = state.accounts,
            paceKopecks = state.paceKopecks,
            onDismiss   = { showAddSheet = false },
            onSave     = { name, emoji, targetKopecks, deadline, startedAt, accountIds ->
                vm.createGoal(name, emoji, targetKopecks, deadline, startedAt, accountIds)
            },
        )
    }

    // Edit existing goal
    editTarget?.let { goal ->
        // Привязки цели живут в маршрутах, а не в самой цели, поэтому форма получает их отдельно —
        // и, в отличие от прежней версии, действительно сохраняет.
        val linked = remember(state.routes, goal.id) {
            state.routes
                .filter { it.goalId == goal.id && it.matchType == TransferMatchType.ACCOUNT }
                .map { it.matchValue }
                .toSet()
        }
        AddGoalSheet(
            existing         = goal,
            accounts         = state.accounts,
            linkedAccountIds = linked,
            paceKopecks      = state.paceKopecks,
            onDismiss        = { editTarget = null },
            onSave           = { name, emoji, targetKopecks, deadline, startedAt, accountIds ->
                vm.updateGoal(goal, name, emoji, targetKopecks, deadline, startedAt)
                vm.syncAccountRoutes(goal.id, accountIds)
                editTarget = null
            },
        )
    }

    // Сколько на цели лежит СЕЙЧАС — вместо «прибавить/снять».
    contributeTarget?.let { goal ->
        val entered = FosFormatter.parseAmountInput(contributeText)
        val delta   = entered?.let { it - goal.savedKopecks }
        val canApply = entered != null && delta != null && delta != 0L

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
                        "Сколько отложено на цель сейчас",
                        style = FosType.Body,
                        color = FosColors.TextSecondary,
                    )
                    OutlinedTextField(
                        value           = contributeText,
                        onValueChange   = { contributeText = FosFormatter.sanitizeAmountInput(it) },
                        visualTransformation = AmountVisualTransformation,
                        label           = { Text("Сумма на цели, ₽", style = FosType.Label) },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = FosColors.Info,
                            unfocusedBorderColor = FosColors.BorderStrong,
                            focusedLabelColor    = FosColors.Info,
                            unfocusedLabelColor  = FosColors.TextMuted,
                            cursorColor          = FosColors.Info,
                            focusedTextColor     = FosColors.TextPrimary,
                            unfocusedTextColor   = FosColors.TextPrimary,
                        ),
                    )
                    // Разницу считает приложение и показывает ДО нажатия: человек вводит остаток,
                    // и именно она уйдёт в историю цели.
                    Text(
                        when {
                            delta == null -> "Было ${FosFormatter.amount(goal.savedKopecks)}"
                            delta > 0L    -> "Было ${FosFormatter.amount(goal.savedKopecks)} · " +
                                "прибавится ${FosFormatter.amount(delta)}"
                            delta < 0L    -> "Было ${FosFormatter.amount(goal.savedKopecks)} · " +
                                "убавится ${FosFormatter.amount(-delta)}"
                            else          -> "Столько и было — менять нечего"
                        },
                        style = FosType.MicroNum,
                        color = when {
                            delta == null || delta == 0L -> FosColors.TextMuted
                            delta > 0L                   -> FosColors.Positive
                            else                         -> FosColors.Warning
                        },
                    )
                    Text(
                        "Это правка суммы, отложенной на цель, — операция по счетам не создаётся. " +
                            "Пригодится, когда пуш банка не пришёл или цель ведётся вручную.",
                        style = FosType.Micro,
                        color = FosColors.TextMuted,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (entered != null) vm.setSavedTotal(goal, entered)
                        contributeTarget = null
                    },
                    enabled = canApply,
                ) {
                    Text("Сохранить", color = FosColors.Positive)
                }
            },
            dismissButton = {
                TextButton(onClick = { contributeTarget = null }) {
                    Text("Отмена", color = FosColors.TextSecondary)
                }
            },
        )
    }

    // Удаление цели — с вопросом. Крестик стоит в одном ряду с «±» и историей, попасть по нему
    // мимо соседа легко, а отменить нечем: вместе с целью уходят её привязки и вся история
    // зачислений. Сумма в вопросе — чтобы было видно, что именно теряется.
    deleteTarget?.let { goal ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = FosColors.Surface,
            title = {
                Text("Удалить цель?", style = FosType.BodySemi, color = FosColors.TextPrimary)
            },
            text = {
                Text(
                    "«${goal.name}» — отложено ${FosFormatter.amount(goal.savedKopecks)}. " +
                        "Цель и её привязки к счетам исчезнут, история зачислений перестанет " +
                        "показываться. Деньги на счетах останутся на месте.",
                    style = FosType.Body,
                    color = FosColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteGoal(goal.id); deleteTarget = null }) {
                    Text("Удалить", color = FosColors.Negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Отмена", color = FosColors.Info)
                }
            },
        )
    }

    // Auto-fund link sheet
    linkTarget?.let { goal ->
        LinkTransferRouteSheet(
            goal           = goal,
            routes         = state.routes,
            cardMasks      = state.cardMasks,
            accounts       = state.accounts,
            cardOwners     = state.cardOwners,
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
    onAdjust         : () -> Unit,
    onLink           : () -> Unit,
    onHistory        : () -> Unit,
    onDelete         : () -> Unit,
) {
    val ratio = if (goal.targetKopecks > 0)
        goal.savedKopecks.toFloat() / goal.targetKopecks else 0f
    val complete = ratio >= 1f
    val artKind  = remember(goal.emoji, goal.name) { goalArtFor(goal) }

    // Достигнутая цель — единственный случай, когда зелёная огранка честна по правилам цвета.
    val tone  = if (complete) FosTone.Positive else FosTone.Neutral
    val style = if (complete) FosCardStyle.Rail else FosCardStyle.Plain

    // Цвет кольца — тон ТЕМЫ цели (тот же, которым покрашена её подложка), а не общий зелёный:
    // пять целей с одинаковыми зелёными кольцами превращались в один однообразный список, и это
    // ровно то, на что жаловались. Достигнутая цель зелёная по правилу #1 — успех.
    val ringColor = if (complete) FosColors.Positive else artKind.accent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fosCardSurface(style, tone)
            // Рамка поверх содержимого: арт цели закрашивает карточку целиком и обычную рамку
            // просто перекрывает — соседние цели сливались в одно полотно.
            .fosCardEdge(style, tone)
            .clickable { onEdit() },
    ) {
        // Themed pixel-art backdrop (falls back to a themed gradient until the art is bundled).
        GoalArtBackdrop(kind = artKind, modifier = Modifier.matchParentSize())

        // Высота карточки задаётся содержимым, а не артом: раньше три кнопки стояли столбиком
        // справа и растягивали карточку до 140 dp, из которых половина была пустым фоном.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FosDimens.CardPaddingSmall, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            GoalRing(
                progress = ratio,
                color    = ringColor,
                modifier = Modifier.size(54.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${goal.emoji} ${goal.name}",
                    style    = FosType.BodySemi,
                    color    = if (complete) FosColors.Positive else FosColors.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    "${FosFormatter.compact(goal.savedKopecks)} из ${FosFormatter.compact(goal.targetKopecks)}",
                    style = FosType.MicroNum,
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

            // Действия в РЯД, а не столбиком: столбик из трёх кнопок был самой высокой частью
            // карточки и диктовал ей высоту. «История» переехала сюда же — это такое же действие,
            // как остальные, и отдельной строкой она только добавляла карточке ещё один этаж.
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                GlyphAction("≡", FosColors.Info, onHistory)
                // ± rather than +: the dialog behind it both adds and withdraws. Shown on a
                // completed goal too — that is precisely when the money gets spent and has to
                // come back out, and the card used to hide the control at exactly that point.
                GlyphAction("±", FosColors.Info, onAdjust)
                GlyphAction("🔗", FosColors.TextSecondary, onLink)
                GlyphAction("×", FosColors.Negative, onDelete)
            }
        }
    }
}

/**
 * Кнопка-глиф в карточке цели.
 *
 * `TextButton` сам по себе не меньше 58×40 dp — четыре таких в ряд не помещаются на узком экране.
 * Явный `size` перебивает этот минимум, но 36 dp всё ещё выше порога уверенного попадания пальцем,
 * а padding нулевой, чтобы глиф стоял по центру.
 */
@Composable
private fun GlyphAction(glyph: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    TextButton(
        onClick        = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier       = Modifier.size(36.dp),
    ) {
        Text(glyph, style = FosType.BodySemi, color = color)
    }
}
