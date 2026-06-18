package com.financeos.hub.features.analytics.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.financeos.hub.features.analytics.AnalyticsState
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

@Composable
fun CategoriesTab(state: AnalyticsState) {
    val sorted = state.categoryExpenses.entries.sortedByDescending { it.value }
    val total  = state.categoryExpenses.values.sum().coerceAtLeast(1L)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FosColors.Background)
            .padding(horizontal = FosDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Spacer(Modifier.height(FosDimens.ItemGap)) }
        items(sorted) { (catId, kopecks) ->
            val isFixed   = state.fixedVariable?.fixed?.contains(catId) == true
            val isVariable= state.fixedVariable?.variable?.contains(catId) == true

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
                    .background(FosColors.Surface)
                    .padding(FosDimens.CardPaddingSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.categoryNames[catId] ?: "Другое",
                        style = FosType.BodySemi,
                        color = FosColors.TextPrimary,
                    )
                    val tag = when {
                        isFixed    -> "постоянные · "
                        isVariable -> "переменные · "
                        else       -> ""
                    }
                    Text(
                        "$tag${(kopecks.toFloat() / total * 100).toInt()}% от расходов",
                        style = FosType.Micro,
                        color = if (isFixed) FosColors.Info else FosColors.TextSecondary,
                    )
                }
                Text(
                    FosFormatter.compact(kopecks),
                    style = FosType.SmallBold,
                    color = FosColors.Negative,
                )
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
