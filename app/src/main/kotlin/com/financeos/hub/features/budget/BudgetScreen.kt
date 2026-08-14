package com.financeos.hub.features.budget

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.ui.components.FosSectionHeader
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.fosCard
import com.financeos.hub.ui.theme.fosHeroCard
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onSubscriptionsClick: () -> Unit = {},
    vm: BudgetViewModel = hiltViewModel(),
) {
    val state        by vm.state.collectAsState()
    var showAddSheet  by remember { mutableStateOf(false) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text("Бюджет", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
                    TextButton(
                        onClick        = onSubscriptionsClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("↻ Подписки", style = FosType.Label, color = FosColors.TextSecondary)
                    }
                }
            }

            if (state.envelopes.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = FosDimens.SectionGap)
                            .fosCard(FosCardStyle.Outline, FosTone.Info),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Бюджетов пока нет", style = FosType.BodySemi, color = FosColors.TextPrimary)
                        Text(
                            "Нажмите +, чтобы задать лимит на категорию. Приложение будет " +
                                "предупреждать, когда лимит подходит к концу.",
                            style = FosType.Micro,
                            color = FosColors.TextSecondary,
                        )
                    }
                }
            } else {
                item { BudgetTotalCard(state.envelopes) }
                item { FosSectionHeader("КОНВЕРТЫ") }
                items(state.envelopes, key = { it.budgetId }) { env ->
                    BudgetEnvelopeCard(
                        envelope = env,
                        onDelete = { vm.deleteBudget(env.budgetId) },
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddSheet) {
        AddBudgetSheet(
            sheetState = addSheetState,
            categories = state.categories,
            onDismiss  = { showAddSheet = false },
            onSave     = { catId, limitKopecks, period ->
                vm.createBudget(catId, limitKopecks, period)
            },
        )
    }
}

@Composable
private fun BudgetEnvelopeCard(envelope: BudgetEnvelope, onDelete: () -> Unit) {
    val ratio = if (envelope.limitKopecks > 0)
        envelope.spentKopecks.toFloat() / envelope.limitKopecks else 0f

    val barColor: Color = when {
        ratio >= 0.9f -> FosColors.Negative
        ratio >= 0.7f -> FosColors.Warning
        else          -> FosColors.Positive
    }

    // Огранка карточки повторяет состояние конверта: пока запас есть — обычная карточка,
    // на подходе к лимиту — жёлтая полоса по краю, за лимитом — красная. Понятно до чтения цифр.
    val tone = when {
        ratio >= 0.9f -> FosTone.Negative
        ratio >= 0.7f -> FosTone.Warning
        else          -> FosTone.Neutral
    }
    val style = if (tone == FosTone.Neutral) FosCardStyle.Plain else FosCardStyle.Rail

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fosCard(style, tone),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(envelope.categoryName, style = FosType.BodySemi, color = FosColors.TextPrimary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    "${FosFormatter.compact(envelope.spentKopecks)} / ${FosFormatter.compact(envelope.limitKopecks)}",
                    style = FosType.SmallBold,
                    color = FosColors.TextSecondary,
                )
                TextButton(
                    onClick        = onDelete,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text("×", style = FosType.BodySemi, color = FosColors.Negative)
                }
            }
        }

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FosDimens.BarHeightLg)
                .clip(RoundedCornerShape(FosDimens.RadiusBar))
                .background(FosColors.Surface2),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .height(FosDimens.BarHeightLg)
                    .clip(RoundedCornerShape(FosDimens.RadiusBar))
                    .background(barColor),
            )
        }

        Text(
            "${envelope.spentPercent}% использовано",
            style = FosType.Micro,
            color = barColor,
        )
    }
}

/**
 * Один ответ на вопрос «сколько ещё можно потратить» поверх всех конвертов.
 *
 * Раньше экран начинался сразу со списка, и общий остаток приходилось складывать в уме. Карточка
 * приподнята и скруглена сильнее остальных — это главный блок экрана, и он должен читаться первым.
 */
@Composable
private fun BudgetTotalCard(envelopes: List<BudgetEnvelope>) {
    val limit = envelopes.sumOf { it.limitKopecks }
    val spent = envelopes.sumOf { it.spentKopecks }
    val left  = limit - spent
    val ratio = if (limit > 0) spent.toFloat() / limit else 0f

    val tone = when {
        ratio >= 1f   -> FosTone.Negative
        ratio >= 0.8f -> FosTone.Warning
        else          -> FosTone.Positive
    }
    val accent = tone.accent ?: FosColors.TextPrimary

    Column(
        modifier            = Modifier.fillMaxWidth().fosHeroCard(tone),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (left >= 0) "ОСТАЛОСЬ В БЮДЖЕТЕ" else "ПЕРЕРАСХОД",
            style = FosType.SectionCap,
            color = FosColors.TextMuted,
        )
        Text(
            FosFormatter.amount(kotlin.math.abs(left)),
            style = FosType.HeroAmount,
            color = accent,
        )
        Text(
            "потрачено ${FosFormatter.compact(spent)} из ${FosFormatter.compact(limit)} " +
                "· ${envelopes.size} ${pluralEnvelopes(envelopes.size)}",
            style = FosType.Micro,
            color = FosColors.TextSecondary,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FosDimens.BarHeightLg)
                .clip(RoundedCornerShape(FosDimens.RadiusBar))
                .background(FosColors.SurfaceSunken),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .height(FosDimens.BarHeightLg)
                    .clip(RoundedCornerShape(FosDimens.RadiusBar))
                    .background(accent),
            )
        }
    }
}

private fun pluralEnvelopes(n: Int): String {
    val mod100 = n % 100
    val mod10  = n % 10
    return when {
        mod100 in 11..14 -> "конвертов"
        mod10 == 1       -> "конверт"
        mod10 in 2..4    -> "конверта"
        else             -> "конвертов"
    }
}
