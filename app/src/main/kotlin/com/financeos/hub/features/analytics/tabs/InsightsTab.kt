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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.analytics.CategoryAnomaly
import com.financeos.hub.core.analytics.Insight
import com.financeos.hub.core.analytics.InsightSeverity
import com.financeos.hub.core.analytics.NarrativeInsight
import com.financeos.hub.features.analytics.AnalyticsState
import com.financeos.hub.ui.components.FosSectionHeader
import com.financeos.hub.ui.components.ParticleLayer
import com.financeos.hub.ui.components.PawParticleLayer
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.LocalShimmer
import com.financeos.hub.ui.theme.fosCard
import com.financeos.hub.ui.theme.fosCardSurface

/**
 * Everything the app has to *say*, as opposed to everything it has counted.
 *
 * All three block types are rail cards, so a glance down the scroll reads as a column of severities
 * — red, amber, blue — before a single word is read. Design rule #4 still holds: the severity is
 * carried by the edge only, never by an icon inside the card.
 */
@Composable
fun InsightsTab(state: AnalyticsState) {
    val shimmer = LocalShimmer.current
    Box(modifier = Modifier.fillMaxSize().background(FosColors.Background)) {
        if (shimmer.catPawParticles) {
            PawParticleLayer(count = 12, animated = shimmer.catParticlePulse, modifier = Modifier.matchParentSize())
        } else if (shimmer.particles) {
            ParticleLayer(count = 20, animated = shimmer.particlePulse, modifier = Modifier.matchParentSize())
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FosDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(FosDimens.ItemGap),
        ) {
            item { Spacer(Modifier.height(FosDimens.ItemGap)) }

            // ── Alerts (sorted CRITICAL first) ────────────────────────────────
            if (state.insights.isNotEmpty()) {
                item {
                    // The worst thing on the screen gives the header its colour.
                    FosSectionHeader("ОПОВЕЩЕНИЯ", tone = toneOf(state.insights.first().severity))
                }
                items(state.insights, key = { it.id }) { insight ->
                    InsightCard(insight = insight)
                }
            }

            // ── Category anomalies ────────────────────────────────────────────
            if (state.categoryAnomalies.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(FosDimens.SectionGap - FosDimens.ItemGap))
                    FosSectionHeader("АНОМАЛИИ РАСХОДОВ", tone = FosTone.Warning)
                }
                items(state.categoryAnomalies, key = { it.categoryId }) { anomaly ->
                    AnomalyCard(
                        anomaly = anomaly,
                        catName = state.categoryNames[anomaly.categoryId] ?: "Другое",
                    )
                }
            }

            // ── Narrative stories ─────────────────────────────────────────────
            if (state.narratives.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(FosDimens.SectionGap - FosDimens.ItemGap))
                    FosSectionHeader("НАБЛЮДЕНИЯ", tone = FosTone.Info)
                }
                itemsIndexed(state.narratives, key = { _, n -> n.id }) { _, narrative ->
                    NarrativeCard(narrative = narrative)
                }
            }

            if (state.insights.isEmpty() && state.narratives.isEmpty() && state.categoryAnomalies.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().fosCard(FosCardStyle.Sunken)) {
                        Text(
                            "Недостаточно данных. Добавьте транзакции или дайте приложению время.",
                            style = FosType.Body,
                            color = FosColors.TextMuted,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private fun toneOf(severity: InsightSeverity): FosTone = when (severity) {
    InsightSeverity.CRITICAL -> FosTone.Negative
    InsightSeverity.WARNING  -> FosTone.Warning
    InsightSeverity.INFO     -> FosTone.Info
}

// InsightCard: left border ONLY — no icon per design rules
@Composable
private fun InsightCard(insight: Insight) {
    val tone        = toneOf(insight.severity)
    val borderColor = tone.accent ?: FosColors.Info
    // «Атмосфера» layer: the left border glows inward by severity (still no icon — rule #4 intact).
    val glow = LocalShimmer.current.insightBorderGlow

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fosCardSurface(FosCardStyle.Rail, tone, FosDimens.RadiusCardSmall),
    ) {
        if (glow) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0f    to borderColor.copy(alpha = 0.18f),
                            0.28f to Color.Transparent,
                        )
                    ),
            )
        }
        Text(
            text     = insight.text,
            style    = FosType.Body,
            color    = FosColors.TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
        )
    }
}

@Composable
private fun AnomalyCard(anomaly: CategoryAnomaly, catName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fosCardSurface(FosCardStyle.Rail, FosTone.Warning, FosDimens.RadiusCardSmall)
            .padding(start = 16.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
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
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
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

@Composable
private fun NarrativeCard(narrative: NarrativeInsight) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .fosCardSurface(FosCardStyle.Rail, FosTone.Info, FosDimens.RadiusCardSmall)
            .padding(start = 16.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
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
