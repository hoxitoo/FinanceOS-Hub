package com.financeos.hub.features.analytics.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.financeos.hub.features.analytics.AnalyticsState
import com.financeos.hub.features.analytics.AnalyticsViewModel
import com.financeos.hub.ui.components.Pie3D
import com.financeos.hub.ui.components.PieSlice
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.fosCard
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

/** Fallback palette for categories whose stored colour can't be parsed. */
private val FALLBACK_COLORS = listOf(
    Color(0xFFFFB84D), Color(0xFF4D9FFF), Color(0xFF9B5CFF), Color(0xFF2DD4BF),
    Color(0xFFFF87C2), Color(0xFF60A5FA), Color(0xFFFB923C), Color(0xFF34D399),
    Color(0xFFA78BFA), Color(0xFFE879F9), Color(0xFFF87171), Color(0xFF94A3B8),
)

private fun parseColor(hex: String?, fallback: Color): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(fallback)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesTab(state: AnalyticsState, vm: AnalyticsViewModel) {
    val sorted = state.categoryExpenses.entries.sortedByDescending { it.value }
    val total  = state.categoryExpenses.values.sum().coerceAtLeast(1L)

    var selectedSlice by remember { mutableStateOf<String?>(null) }
    var drillCategory by remember { mutableStateOf<String?>(null) }
    val drillSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Colour per category: its own stored colour, falling back to the palette by position.
    val sliceColor: (String, Int) -> Color = { catId, index ->
        parseColor(state.categoryColors[catId], FALLBACK_COLORS[index % FALLBACK_COLORS.size])
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FosColors.Background)
            .padding(horizontal = FosDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Spacer(Modifier.height(FosDimens.ItemGap)) }

        if (sorted.isEmpty()) {
            item {
                Text(
                    "Нет расходов за выбранный период",
                    style = FosType.Body,
                    color = FosColors.TextMuted,
                )
            }
        } else {
            // ── Interactive 3D pie ────────────────────────────────────────────
            item {
                val slices = sorted.mapIndexed { i, (catId, kopecks) ->
                    PieSlice(
                        id      = catId,
                        label   = state.categoryNames[catId] ?: "Другое",
                        kopecks = kopecks,
                        color   = sliceColor(catId, i),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fosCard(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Pie3D(
                        slices   = slices,
                        selected = selectedSlice,
                        onSelect = { selectedSlice = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    )

                    // Readout: the tapped slice, or the total when nothing is selected.
                    val sel = slices.firstOrNull { it.id == selectedSlice }
                    if (sel != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(sel.color)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                "${state.categoryEmojis[sel.id].orEmpty()} ${sel.label}",
                                style = FosType.BodySemi,
                                color = FosColors.TextPrimary,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${FosFormatter.compact(sel.kopecks)} · " +
                                "${(sel.kopecks.toFloat() / total * 100).toInt()}% от расходов",
                            style = FosType.Micro,
                            color = FosColors.TextSecondary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Показать операции ›",
                            style    = FosType.Micro,
                            color    = FosColors.Info,
                            modifier = Modifier.clickable { drillCategory = sel.id },
                        )
                    } else {
                        Text("Всего расходов", style = FosType.Micro, color = FosColors.TextMuted)
                        Text(
                            FosFormatter.compact(total),
                            style = FosType.BodySemi,
                            color = FosColors.TextPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Нажмите на сектор, чтобы посмотреть категорию",
                            style = FosType.Micro,
                            color = FosColors.TextMuted,
                        )
                    }
                }
            }

            // ── Top-3 ────────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(FosDimens.ItemGap))
                Text("ТОП-3 ТРАТЫ", style = FosType.SectionCap, color = FosColors.TextMuted)
                Spacer(Modifier.height(6.dp))
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sorted.take(3).forEachIndexed { i, (catId, kopecks) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
                                .background(FosColors.Surface)
                                .clickable { drillCategory = catId }
                                .padding(FosDimens.CardPaddingSmall),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${i + 1}",
                                    style    = FosType.BodySemi,
                                    color    = sliceColor(catId, sorted.indexOfFirst { it.key == catId }),
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Column {
                                    Text(
                                        "${state.categoryEmojis[catId].orEmpty()} " +
                                            (state.categoryNames[catId] ?: "Другое"),
                                        style = FosType.BodySemi,
                                        color = FosColors.TextPrimary,
                                    )
                                    Text(
                                        "${(kopecks.toFloat() / total * 100).toInt()}% от расходов",
                                        style = FosType.Micro,
                                        color = FosColors.TextSecondary,
                                    )
                                }
                            }
                            Text(
                                FosFormatter.compact(kopecks),
                                style = FosType.SmallBold,
                                color = FosColors.Negative,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(FosDimens.ItemGap))
                Text("ВСЕ КАТЕГОРИИ", style = FosType.SectionCap, color = FosColors.TextMuted)
                Spacer(Modifier.height(6.dp))
            }
        }

        // ── Full list, every row drills down ─────────────────────────────────
        items(sorted, key = { it.key }) { (catId, kopecks) ->
            val isFixed    = state.fixedVariable?.fixed?.contains(catId) == true
            val isVariable = state.fixedVariable?.variable?.contains(catId) == true
            val index      = sorted.indexOfFirst { it.key == catId }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
                    .background(FosColors.Surface)
                    .clickable {
                        selectedSlice = catId
                        drillCategory = catId
                    }
                    .padding(FosDimens.CardPaddingSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(sliceColor(catId, index))
                )
                Spacer(Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${state.categoryEmojis[catId].orEmpty()} ${state.categoryNames[catId] ?: "Другое"}",
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

    drillCategory?.let { catId ->
        CategoryOpsSheet(
            categoryTitle = "${state.categoryEmojis[catId].orEmpty()} " +
                (state.categoryNames[catId] ?: "Другое"),
            categoryId    = catId,
            vm            = vm,
            sheetState    = drillSheetState,
            onDismiss     = { drillCategory = null },
        )
    }
}
