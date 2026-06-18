package com.financeos.hub.features.budget

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

@Composable
fun BudgetScreen(vm: BudgetViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FosColors.Background)
            .padding(horizontal = FosDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
    ) {
        item { Spacer(Modifier.height(16.dp)) }
        item {
            Text("Бюджет", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
        }

        if (state.envelopes.isEmpty()) {
            item {
                Text(
                    "Бюджеты не настроены",
                    style = FosType.Body,
                    color = FosColors.TextMuted,
                    modifier = Modifier.padding(top = FosDimens.SectionGap),
                )
            }
        } else {
            items(state.envelopes) { env ->
                BudgetEnvelopeCard(envelope = env)
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun BudgetEnvelopeCard(envelope: BudgetEnvelope) {
    val ratio = if (envelope.limitKopecks > 0)
        envelope.spentKopecks.toFloat() / envelope.limitKopecks else 0f

    // Dynamic bar color: green → warning at 70% → red at 90%
    val barColor: Color = when {
        ratio >= 0.9f -> FosColors.Negative
        ratio >= 0.7f -> FosColors.Warning
        else          -> FosColors.Positive
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCard))
            .background(FosColors.Surface)
            .padding(FosDimens.CardPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(envelope.categoryName, style = FosType.BodySemi, color = FosColors.TextPrimary)
            Text(
                "${FosFormatter.compact(envelope.spentKopecks)} / ${FosFormatter.compact(envelope.limitKopecks)}",
                style = FosType.SmallBold,
                color = FosColors.TextSecondary,
            )
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
            "${(ratio * 100).toInt()}% использовано",
            style = FosType.Micro,
            color = barColor,
        )
    }
}
