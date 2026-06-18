package com.financeos.hub.features.subscriptions

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

@Composable
fun SubscriptionsScreen(
    onBack         : () -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    vm             : SubscriptionsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    LazyColumn(
        modifier            = Modifier
            .fillMaxSize()
            .background(FosColors.Background),
        contentPadding      = PaddingValues(horizontal = FosDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
    ) {
        item { Spacer(Modifier.height(16.dp)) }

        // Header
        item {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Подписки", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
                    if (!state.isLoading && state.totalMonthlyKopecks > 0) {
                        Text(
                            "~${FosFormatter.compact(state.totalMonthlyKopecks)} в месяц",
                            style = FosType.Body,
                            color = FosColors.TextMuted,
                        )
                    }
                }
                TextButton(onClick = onBack) {
                    Text("← Назад", style = FosType.Label, color = FosColors.TextSecondary)
                }
            }
        }

        if (!state.isLoading && state.active.isEmpty() && state.missed.isEmpty()) {
            item {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Регулярных расходов не обнаружено.\nПоявятся после 3+ месяцев данных.",
                        style = FosType.Body,
                        color = FosColors.TextMuted,
                    )
                }
            }
        }

        // Missed subscriptions
        if (state.missed.isNotEmpty()) {
            item {
                Text(
                    "ПРОПУЩЕНЫ",
                    style = FosType.SectionCap,
                    color = FosColors.Negative,
                )
            }
            items(state.missed, key = { it.categoryId }) { sub ->
                SubscriptionCard(sub = sub, isMissed = true, onClick = { onCategoryClick(sub.categoryId) })
            }
        }

        // Active subscriptions
        if (state.active.isNotEmpty()) {
            item {
                Text("РЕГУЛЯРНЫЕ РАСХОДЫ", style = FosType.SectionCap, color = FosColors.TextMuted)
            }
            items(state.active, key = { it.categoryId }) { sub ->
                SubscriptionCard(sub = sub, isMissed = false, onClick = { onCategoryClick(sub.categoryId) })
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun SubscriptionCard(sub: SubscriptionInfo, isMissed: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCard))
            .background(FosColors.Surface)
            .clickable(onClick = onClick)
            .padding(FosDimens.CardPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(sub.categoryName, style = FosType.BodySemi, color = FosColors.TextPrimary)
            if (isMissed) {
                Text(
                    "Нет платежа в этом месяце",
                    style = FosType.Micro,
                    color = FosColors.Negative,
                )
            } else {
                Text(
                    "Последний: ${FosFormatter.dayLabel(sub.lastPaymentAt)}",
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                )
            }
            Text(
                "${sub.monthsPresent} мес. из 6",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                "~${FosFormatter.compact(sub.avgMonthlyKopecks)}",
                style = FosType.SmallBold,
                color = if (isMissed) FosColors.Negative else FosColors.TextPrimary,
            )
            Text(
                "в месяц",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
        }
    }
}
