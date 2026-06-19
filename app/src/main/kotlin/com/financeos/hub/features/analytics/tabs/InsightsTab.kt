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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.analytics.CategoryAnomaly
import com.financeos.hub.core.analytics.Insight
import com.financeos.hub.core.analytics.InsightSeverity
import com.financeos.hub.core.analytics.NarrativeInsight
import com.financeos.hub.features.analytics.AnalyticsState
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

@Composable
fun InsightsTab(state: AnalyticsState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FosColors.Background)
            .padding(horizontal = FosDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(FosDimens.ItemGap),
    ) {
        item { Spacer(Modifier.height(FosDimens.ItemGap)) }

        // ── Alerts (sorted CRITICAL first) ────────────────────────────────────
        if (state.insights.isNotEmpty()) {
            item {
                Text("ОПОВЕЩЕНИЯ", style = FosType.SectionCap, color = FosColors.TextMuted)
            }
            items(state.insights, key = { it.id }) { insight ->
                InsightCard(insight = insight)
            }
        }

        // ── Category anomalies ────────────────────────────────────────────────
        if (state.categoryAnomalies.isNotEmpty()) {
            item {
                Spacer(Modifier.height(FosDimens.SectionGap - FosDimens.ItemGap))
                Text("АНОМАЛИИ РАСХОДОВ", style = FosType.SectionCap, color = FosColors.TextMuted)
            }
            items(state.categoryAnomalies, key = { it.categoryId }) { anomaly ->
                AnomalyCard(
                    anomaly  = anomaly,
                    catName  = state.categoryNames[anomaly.categoryId] ?: "Другое",
                )
            }
        }

        // ── Narrative stories ─────────────────────────────────────────────────
        if (state.narratives.isNotEmpty()) {
            item {
                Spacer(Modifier.height(FosDimens.SectionGap - FosDimens.ItemGap))
                Text("НАБЛЮДЕНИЯ", style = FosType.SectionCap, color = FosColors.TextMuted)
            }
            items(state.narratives, key = { it }) { narrative ->
                NarrativeCard(narrative = narrative)
            }
        }

        if (state.insights.isEmpty() && state.narratives.isEmpty() && state.categoryAnomalies.isEmpty()) {
            item {
                Text(
                    "Недостаточно данных. Добавьте транзакции или дайте приложению время.",
                    style = FosType.Body,
                    color = FosColors.TextMuted,
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// InsightCard: left border ONLY — no icon per design rules
@Composable
private fun InsightCard(insight: Insight) {
    val borderColor: Color = when (insight.severity) {
        InsightSeverity.CRITICAL -> FosColors.Negative
        InsightSeverity.WARNING  -> FosColors.Warning
        InsightSeverity.INFO     -> FosColors.Info
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
            .background(FosColors.Surface),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(60.dp)
                .background(borderColor),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Text(
                text  = insight.text,
                style = FosType.Body,
                color = FosColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun AnomalyCard(anomaly: CategoryAnomaly, catName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
            .background(FosColors.Surface),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(60.dp)
                .background(FosColors.Warning),
        )
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(catName, style = FosType.BodySemi, color = FosColors.TextPrimary)
                Text(
                    "Обычно ${FosFormatter.compact(anomaly.avgKopecks)} в месяц",
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                )
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(
                    FosFormatter.compact(anomaly.currentKopecks),
                    style = FosType.SmallBold,
                    color = FosColors.Negative,
                )
                Text(
                    "+${anomaly.deltaPercent}%",
                    style = FosType.Micro,
                    color = FosColors.Warning,
                )
            }
        }
    }
}

@Composable
private fun NarrativeCard(narrative: NarrativeInsight) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
            .background(FosColors.Surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text     = narrative.icon,
            style    = FosType.Body,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text  = narrative.text,
            style = FosType.Body,
            color = FosColors.TextPrimary,
        )
    }
}
