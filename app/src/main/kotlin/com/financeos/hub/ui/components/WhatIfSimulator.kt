package com.financeos.hub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

/**
 * Interactive "what-if" savings simulator.
 *
 * Shows the top categories by spend and lets the user drag sliders to reduce
 * each by 0–50%. Instantly projects monthly and annual savings.
 *
 * @param categoryExpenses  map of categoryId → monthly kopecks
 * @param categoryNames     map of categoryId → display name
 */
@Composable
fun WhatIfSimulator(
    categoryExpenses: Map<String, Long>,
    categoryNames   : Map<String, String>,
    modifier        : Modifier = Modifier,
) {
    val top5 = remember(categoryExpenses) {
        categoryExpenses.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }
    }

    // sliderValues: categoryId → cut percentage (0..50)
    val sliderValues = remember(top5) {
        mutableStateMapOf<String, Float>().also { map ->
            top5.forEach { (id, _) -> map[id] = 0f }
        }
    }

    val savedKopecks = remember(sliderValues.toMap(), top5) {
        top5.sumOf { (id, amount) ->
            val pct = (sliderValues[id] ?: 0f) / 100f
            (amount * pct).toLong()
        }
    }

    Column(modifier = modifier) {
        // Summary banner
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(FosDimens.RadiusCard))
                .background(FosColors.Surface)
                .padding(FosDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("ЕСЛИ СОКРАТИТЬ РАСХОДЫ", style = FosType.SectionCap, color = FosColors.TextMuted)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Bottom,
            ) {
                Column {
                    Text("Экономия в месяц", style = FosType.Micro, color = FosColors.TextMuted)
                    Text(
                        FosFormatter.compact(savedKopecks),
                        style = FosType.CardAmount,
                        color = if (savedKopecks > 0) FosColors.Positive else FosColors.TextSecondary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("За год", style = FosType.Micro, color = FosColors.TextMuted)
                    Text(
                        FosFormatter.compact(savedKopecks * 12),
                        style = FosType.BodySemi,
                        color = if (savedKopecks > 0) FosColors.Positive else FosColors.TextSecondary,
                    )
                }
            }
            if (savedKopecks > 0) {
                Spacer(Modifier.height(4.dp))
                ProjectionRow(savedKopecks)
            }
        }

        Spacer(Modifier.height(FosDimens.ItemGap))

        // Per-category sliders
        top5.forEach { (catId, amount) ->
            val pct = sliderValues[catId] ?: 0f
            CategorySlider(
                name         = categoryNames[catId] ?: "Другое",
                originalKopecks = amount,
                cutPercent   = pct,
                onCutChange  = { sliderValues[catId] = it },
            )
            Spacer(Modifier.height(FosDimens.ItemGap))
        }
    }
}

@Composable
private fun ProjectionRow(savedPerMonth: Long) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf(6 to "6 мес", 12 to "12 мес", 24 to "24 мес").forEach { (months, label) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = FosType.Micro, color = FosColors.TextMuted)
                Text(
                    FosFormatter.compact(savedPerMonth * months),
                    style = FosType.Micro,
                    color = FosColors.Positive,
                )
            }
        }
    }
}

@Composable
private fun CategorySlider(
    name            : String,
    originalKopecks : Long,
    cutPercent      : Float,
    onCutChange     : (Float) -> Unit,
) {
    val savedKopecks = (originalKopecks * cutPercent / 100f).toLong()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
            .background(FosColors.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(name, style = FosType.BodySemi, color = FosColors.TextPrimary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    FosFormatter.compact(originalKopecks),
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                )
                if (savedKopecks > 0) {
                    Text(
                        "−${cutPercent.toInt()}%",
                        style = FosType.Micro,
                        color = FosColors.Positive,
                    )
                }
            }
        }
        Slider(
            value         = cutPercent,
            onValueChange = onCutChange,
            valueRange    = 0f..50f,
            steps         = 9,   // 0,5,10,...,50 — 11 positions, steps = positions-2
            modifier      = Modifier.fillMaxWidth(),
            colors        = SliderDefaults.colors(
                thumbColor       = FosColors.Positive,
                activeTrackColor = FosColors.Positive.copy(alpha = 0.6f),
            ),
        )
        if (savedKopecks > 0) {
            Text(
                "Экономия: ${FosFormatter.compact(savedKopecks)}/мес · ${FosFormatter.compact(savedKopecks * 12)}/год",
                style = FosType.Micro,
                color = FosColors.Positive,
            )
        }
    }
}
