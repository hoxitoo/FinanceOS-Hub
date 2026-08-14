package com.financeos.hub.features.analytics.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.financeos.hub.features.analytics.AnalyticsState
import com.financeos.hub.ui.components.ExpensePyramid
import com.financeos.hub.ui.components.FORECAST_EXPLANATION
import com.financeos.hub.ui.components.FosExplain
import com.financeos.hub.ui.components.FosSectionHeader
import com.financeos.hub.ui.components.ScoreDonut
import com.financeos.hub.ui.components.scoreSegments
import com.financeos.hub.ui.components.WhatIfSimulator
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.fosCard
import com.financeos.hub.ui.theme.fosHeroCard

@Composable
fun OverviewTab(state: AnalyticsState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FosColors.Background)
            .padding(horizontal = FosDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
    ) {
        item { Spacer(Modifier.height(FosDimens.ItemGap)) }

        // Score card
        state.score?.let { score ->
            item { FosSectionHeader("ФИНАНСОВОЕ ЗДОРОВЬЕ") }
            item {
                // The one block this screen is about, so it is the only raised card here.
                Row(
                    modifier = Modifier.fillMaxWidth().fosHeroCard(),
                    horizontalArrangement = Arrangement.spacedBy(FosDimens.CardPadding),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    val segments = scoreSegments(score)
                    ScoreDonut(
                        segments = segments,
                        total    = score.total,
                        modifier = Modifier.size(80.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        segments.forEach { seg ->
                            ScoreRow(seg.label, seg.earned, seg.max, seg.color)
                        }
                    }
                }
            }
        }

        // User archetype (ML)
        state.userArchetype?.let { archetype ->
            item { FosSectionHeader("ВАШ ФИНАНСОВЫЙ ПРОФИЛЬ") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().fosCard(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(archetype.archetype, style = FosType.BodySemi, color = FosColors.Positive)
                        Text(
                            "Уверенность: ${(archetype.confidence * 100).toInt()}%",
                            style = FosType.Micro,
                            color = FosColors.TextMuted,
                        )
                    }
                    Text(
                        archetypeEmoji(archetype.clusterIdx),
                        style = FosType.CardAmount,
                        color = FosColors.Positive,
                    )
                }
            }
        }

        // Forecast
        if (state.forecastKopecks > 0) {
            item { FosSectionHeader("ПРОГНОЗ НА КОНЕЦ МЕСЯЦА", tone = FosTone.Warning) }
            item {
                // A forecast is not a fact, so it carries the amber rail rather than sitting in a
                // neutral card that looks exactly like the measured figures above it.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fosCard(FosCardStyle.Rail, FosTone.Warning),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("Расходы к концу месяца", style = FosType.Body, color = FosColors.TextSecondary)
                        Text(
                            FosFormatter.compact(state.forecastKopecks),
                            style = FosType.SmallBold,
                            color = FosColors.Warning,
                        )
                    }
                    FosExplain(FORECAST_EXPLANATION)
                }
            }
        }

        // Expense pyramid
        if (state.categoryExpenses.isNotEmpty()) {
            item {
                ExpensePyramid(
                    categoryExpenses = state.categoryExpenses,
                    categoryNames    = state.categoryNames,
                    modifier         = Modifier.fillMaxWidth(),
                )
            }
        }

        // Top 5 categories
        item { FosSectionHeader("ТОП КАТЕГОРИИ", tone = FosTone.Negative) }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().fosCard(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val total = state.categoryExpenses.values.sum().coerceAtLeast(1L)
                state.categoryExpenses.entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .forEach { (catId, kopecks) ->
                        CategoryBar(
                            name    = state.categoryNames[catId] ?: "Другое",
                            kopecks = kopecks,
                            ratio   = kopecks.toFloat() / total,
                        )
                    }
            }
        }

        // What-if simulator
        if (state.categoryExpenses.isNotEmpty()) {
            item { FosSectionHeader("ЧТО ЕСЛИ", tone = FosTone.Info) }
            item {
                WhatIfSimulator(
                    categoryExpenses = state.categoryExpenses,
                    categoryNames    = state.categoryNames,
                    modifier         = Modifier.fillMaxWidth(),
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ScoreRow(label: String, value: Int, max: Int, dotColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // Colour dot ties each row to its slice in the donut.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = FosType.Micro, color = FosColors.TextSecondary)
        }
        Text("$value/$max", style = FosType.Micro, color = FosColors.TextPrimary)
    }
}

private fun archetypeEmoji(idx: Int): String = when (idx) {
    0 -> "📊"  // Плановик
    1 -> "🌙"  // Импульсивный
    2 -> "🍽"  // Гурман
    3 -> "💰"  // Экономный
    4 -> "✈"  // Путешественник
    else -> "👤"
}

@Composable
private fun CategoryBar(name: String, kopecks: Long, ratio: Float) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(name, style = FosType.BodySemi, color = FosColors.TextPrimary)
            Text(FosFormatter.compact(kopecks), style = FosType.SmallBold, color = FosColors.TextSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FosDimens.BarHeightMd)
                .clip(RoundedCornerShape(FosDimens.RadiusBar))
                .background(FosColors.Surface2)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .height(FosDimens.BarHeightMd)
                    .clip(RoundedCornerShape(FosDimens.RadiusBar))
                    .background(FosColors.Info)
            )
        }
    }
}
