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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.financeos.hub.features.analytics.AnalyticsState
import com.financeos.hub.ui.components.LineChart
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
        verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
    ) {
        Spacer(Modifier.height(FosDimens.ItemGap))

        Text("РАСХОДЫ ПО ДНЯМ", style = FosType.SectionCap, color = FosColors.TextMuted)

        // SVG line chart — primary display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(FosDimens.RadiusCard))
                .background(FosColors.Surface)
                .padding(FosDimens.CardPadding),
        ) {
            if (state.dailyExpenses.isEmpty()) {
                Text(
                    "Нет данных за текущий месяц",
                    style = FosType.Body,
                    color = FosColors.TextMuted,
                )
            } else {
                LineChart(
                    data  = state.dailyExpenses.map { it.second.toFloat() },
                    color = FosColors.Negative,
                )
            }
        }

        // X-axis labels
        if (state.dailyExpenses.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val first = state.dailyExpenses.first().first
                val last  = state.dailyExpenses.last().first
                Text(FosFormatter.dayLabel(first), style = FosType.Micro, color = FosColors.TextMuted)
                Text(FosFormatter.dayLabel(last),  style = FosType.Micro, color = FosColors.TextMuted)
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}
