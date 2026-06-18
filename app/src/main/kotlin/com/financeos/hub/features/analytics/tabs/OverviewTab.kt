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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.financeos.hub.features.analytics.AnalyticsState
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

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

        // Top 5 categories by expense
        item {
            Text("ТОП КАТЕГОРИИ", style = FosType.SectionCap, color = FosColors.TextMuted)
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FosDimens.RadiusCard))
                    .background(FosColors.Surface)
                    .padding(FosDimens.CardPadding),
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

        item { Spacer(Modifier.height(80.dp)) }
    }
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
