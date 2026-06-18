package com.financeos.hub.features.goals

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.ui.components.GoalRing
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

@Composable
fun GoalsScreen(vm: GoalsViewModel = hiltViewModel()) {
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
            Text("Цели", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
        }

        if (state.goals.isEmpty()) {
            item {
                Text(
                    "Нет активных целей",
                    style = FosType.Body,
                    color = FosColors.TextMuted,
                    modifier = Modifier.padding(top = FosDimens.SectionGap),
                )
            }
        } else {
            items(state.goals) { goal ->
                GoalCard(goal = goal)
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun GoalCard(goal: GoalEntity) {
    val ratio = if (goal.targetKopecks > 0)
        goal.savedKopecks.toFloat() / goal.targetKopecks else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCard))
            .background(FosColors.Surface)
            .padding(FosDimens.CardPadding),
        horizontalArrangement = Arrangement.spacedBy(FosDimens.CardPadding),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        GoalRing(
            progress = ratio,
            modifier = Modifier.size(64.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(goal.name, style = FosType.BodySemi, color = FosColors.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                "${FosFormatter.compact(goal.savedKopecks)} из ${FosFormatter.compact(goal.targetKopecks)}",
                style = FosType.Micro,
                color = FosColors.TextSecondary,
            )
            goal.deadlineAt?.let {
                Text(
                    "до ${FosFormatter.dayLabel(it)}",
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                )
            }
        }

        Text(
            "${(ratio * 100).toInt()}%",
            style = FosType.SmallBold,
            color = FosColors.Positive,
        )
    }
}
