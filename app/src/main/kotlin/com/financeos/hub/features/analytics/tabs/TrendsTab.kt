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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.financeos.hub.features.analytics.AnalyticsState
import com.financeos.hub.ui.components.HeatmapGrid
import com.financeos.hub.ui.components.LineChart
import com.financeos.hub.ui.components.WaterfallChart
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

@Composable
fun TrendsTab(state: AnalyticsState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FosColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FosDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
    ) {
        Spacer(Modifier.height(FosDimens.ItemGap))

        // ── 1. Daily expense line chart ───────────────────────────────────────
        Section(title = "РАСХОДЫ ПО ДНЯМ") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(FosDimens.RadiusCard))
                    .background(FosColors.Surface)
                    .padding(FosDimens.CardPadding),
            ) {
                if (state.dailyExpenses.isEmpty()) {
                    Text("Нет данных за текущий месяц", style = FosType.Body, color = FosColors.TextMuted)
                } else {
                    LineChart(
                        data  = state.dailyExpenses.map { it.second.toFloat() },
                        color = FosColors.Negative,
                    )
                }
            }
            if (state.dailyExpenses.size >= 2) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(FosFormatter.dayLabel(state.dailyExpenses.first().first), style = FosType.Micro, color = FosColors.TextMuted)
                    Text(FosFormatter.dayLabel(state.dailyExpenses.last().first),  style = FosType.Micro, color = FosColors.TextMuted)
                }
            }
        }

        // ── 2. Heatmap ────────────────────────────────────────────────────────
        Section(title = "КОГДА ТЫ ТРАТИШЬ") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FosDimens.RadiusCard))
                    .background(FosColors.Surface)
                    .padding(FosDimens.CardPadding),
            ) {
                if (state.heatmap == null) {
                    Text("Загрузка…", style = FosType.Body, color = FosColors.TextMuted)
                } else {
                    HeatmapGrid(data = state.heatmap)
                }
            }
            Text(
                "Ось X — день недели, ось Y — час дня. Насыщенность = сумма расходов.",
                style = FosType.Micro,
                color = FosColors.TextMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        // ── 3. Budget fatigue curve ───────────────────────────────────────────
        state.fatigueCurve?.let { fatigue ->
            Section(title = "УСТАЛОСТЬ БЮДЖЕТА") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(FosDimens.RadiusCard))
                        .background(FosColors.Surface)
                        .padding(FosDimens.CardPadding),
                ) {
                    val values = fatigue.dailyAverages.map { it.second.toFloat() }
                    if (values.any { it > 0f }) {
                        LineChart(data = values, color = FosColors.Warning)
                    } else {
                        Text("Недостаточно данных", style = FosType.Body, color = FosColors.TextMuted)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("1-е число", style = FosType.Micro, color = FosColors.TextMuted)
                    Text("31-е число", style = FosType.Micro, color = FosColors.TextMuted)
                }
                Text(
                    "Средние расходы по дням месяца за ${fatigue.monthCount} мес.",
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        // ── 4. Waterfall MoM ─────────────────────────────────────────────────
        if (state.waterfallBars.isNotEmpty()) {
            Section(title = "МЕСЯЦ К МЕСЯЦУ") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(FosDimens.RadiusCard))
                        .background(FosColors.Surface)
                        .padding(FosDimens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                    ) {
                        WaterfallChart(bars = state.waterfallBars)
                    }
                    // Legend
                    state.waterfallBars.forEach { bar ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(bar.label, style = FosType.Micro, color = FosColors.TextSecondary)
                            Text(
                                FosFormatter.signedAmount(bar.delta),
                                style = FosType.Micro,
                                color = if (bar.delta >= 0) FosColors.Positive else FosColors.Negative,
                            )
                        }
                    }
                }
            }
        }

        // ── 5. Impulse stats ──────────────────────────────────────────────────
        state.impulseStats?.let { stats ->
            if (stats.impulseCount > 0) {
                Section(title = "ИМПУЛЬСИВНОСТЬ") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(FosDimens.RadiusCard))
                            .background(FosColors.Surface)
                            .padding(FosDimens.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatRow("Импульсивных покупок", "${stats.impulseCount} (${(stats.impulsePercent * 100).toInt()}%)", FosColors.Warning)
                        StatRow("Запланированных", "${stats.plannedCount}", FosColors.Positive)
                        StatRow("Сумма импульсивных", FosFormatter.compact(stats.impulseKopecks), FosColors.Warning)
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FosDimens.ItemGap)) {
        Text(title, style = FosType.SectionCap, color = FosColors.TextMuted)
        content()
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = FosType.Body, color = FosColors.TextSecondary)
        Text(value, style = FosType.BodySemi, color = valueColor)
    }
}
