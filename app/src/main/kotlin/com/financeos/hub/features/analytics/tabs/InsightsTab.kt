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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.analytics.Insight
import com.financeos.hub.core.analytics.InsightSeverity
import com.financeos.hub.features.analytics.AnalyticsState
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
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

        if (state.insights.isEmpty()) {
            item {
                Text(
                    "Недостаточно данных для инсайтов",
                    style = FosType.Body,
                    color = FosColors.TextMuted,
                )
            }
        } else {
            items(state.insights) { insight ->
                InsightCard(insight = insight)
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
