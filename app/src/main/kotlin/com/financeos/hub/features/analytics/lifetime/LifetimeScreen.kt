package com.financeos.hub.features.analytics.lifetime

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.core.analytics.LifetimeStats
import com.financeos.hub.ui.components.DonutSlice
import com.financeos.hub.ui.components.FosSectionHeader
import com.financeos.hub.ui.components.SegmentedDonut
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.fosCard
import com.financeos.hub.ui.theme.categoryColor
import com.financeos.hub.ui.theme.fosHeroCard
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SINCE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))

/** Сколько источников видно сразу. Остальные — под «Показать все». */
private const val SOURCES_PREVIEW = 12

/**
 * «Всего потрачено, всего заработано» — вся история деньгами, за годы.
 *
 * Всё на экране подчинено одному выбору — горизонту (год … всё время): итоги, график, бары, доли и
 * источники всегда говорят об одном и том же отрезке. Разные окна у соседних блоков давали бы цифры,
 * которые не сходятся между собой, и человек перестал бы верить всем.
 */
@Composable
fun LifetimeScreen(
    onBack: () -> Unit,
    vm    : LifetimeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val result = state.result
    val cats   = state.categories

    // Цвет категории один на весь экран — в барах, в долях и в легенде. Иначе «Продукты» на баре
    // синие, а на круге оранжевые, и связь между блоками теряется.
    val colorOf: (String?) -> Color = remember(cats) {
        { id -> if (id == null) FosColors.TextMuted else categoryColor(cats[id]?.color, id) }
    }
    // Символ основной валюты экрана: у человека с одними сомами это «сом», а не «₽».
    val sym = FosFormatter.currencySymbol(result?.currency ?: LifetimeStats.BASE_CURRENCY)
    val nameOf: (String?) -> String = { id -> id?.let { cats[it]?.name } ?: "Без категории" }

    // Выбор сбрасывается при смене горизонта: выбранного года в новом окне может не быть.
    var curveSel      by remember(state.horizon, state.step) { mutableStateOf<Int?>(null) }
    var spentYear     by remember(state.horizon) { mutableStateOf<Int?>(null) }
    var earnedYear    by remember(state.horizon) { mutableStateOf<Int?>(null) }
    var spentSlice    by remember(state.horizon) { mutableStateOf<Int?>(null) }
    var earnedSlice   by remember(state.horizon) { mutableStateOf<Int?>(null) }
    var allSpentSrc   by remember(state.horizon) { mutableStateOf(false) }
    var allEarnedSrc  by remember(state.horizon) { mutableStateOf(false) }

    LazyColumn(
        modifier            = Modifier.fillMaxSize().background(FosColors.Background),
        contentPadding      = PaddingValues(horizontal = FosDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
    ) {
        item(key = "top") { Spacer(Modifier.height(16.dp)) }
        item(key = "title") {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("За всё время", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
                TextButton(onClick = onBack) {
                    Text("← Назад", style = FosType.Label, color = FosColors.TextSecondary)
                }
            }
        }
        item(key = "horizon") {
            ChipRow(
                items    = LifetimeStats.Horizon.entries,
                selected = state.horizon,
                label    = { it.label },
                onSelect = vm::setHorizon,
            )
        }

        when {
            state.isLoading -> Unit
            result == null || result.isEmpty -> item(key = "empty") {
                Column(Modifier.fillMaxWidth().fosCard(FosCardStyle.Outline)) {
                    Text("За этот срок операций нет", style = FosType.BodySemi, color = FosColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Выберите срок подлиннее — или подождите, пока накопится история.",
                        style = FosType.Micro,
                        color = FosColors.TextMuted,
                    )
                }
            }
            else -> {
                // ── Итоги ────────────────────────────────────────────────────────────
                item(key = "totals") { TotalsHero(result) }

                // ── Ползущий итог ────────────────────────────────────────────────────
                if (result.curve.isNotEmpty()) {
                    item(key = "curve_h") { FosSectionHeader("НАРАСТАЮЩИЙ ИТОГ") }
                    item(key = "step") {
                        ChipRow(
                            items    = LifetimeStats.Step.entries,
                            selected = state.step,
                            label    = { it.label },
                            onSelect = vm::setStep,
                        )
                    }
                    item(key = "curve") {
                        CumulativeDualChart(
                            points   = result.curve,
                            step     = state.step,
                            selected = curveSel,
                            onSelect = { curveSel = it },
                            modifier = Modifier.fillMaxWidth().fosCard(),
                            sym      = sym,
                        )
                    }
                }

                // ── Траты по годам ───────────────────────────────────────────────────
                if (result.spentByYear.isNotEmpty()) {
                    item(key = "spent_y_h") { FosSectionHeader("ТРАТЫ ПО ГОДАМ", tone = FosTone.Negative) }
                    item(key = "spent_y") {
                        YearBlock(
                            bars     = result.spentByYear,
                            selected = spentYear,
                            accent   = FosColors.Negative,
                            colorOf  = colorOf,
                            nameOf   = nameOf,
                            sym      = sym,
                            onSelect = { spentYear = it },
                        )
                    }
                }

                // ── Заработок по годам ───────────────────────────────────────────────
                if (result.earnedByYear.isNotEmpty()) {
                    item(key = "earned_y_h") { FosSectionHeader("ЗАРАБОТОК ПО ГОДАМ", tone = FosTone.Positive) }
                    item(key = "earned_y") {
                        YearBlock(
                            bars     = result.earnedByYear,
                            selected = earnedYear,
                            accent   = FosColors.Positive,
                            colorOf  = colorOf,
                            nameOf   = nameOf,
                            sym      = sym,
                            onSelect = { earnedYear = it },
                        )
                    }
                }

                // ── Доли ─────────────────────────────────────────────────────────────
                if (result.spentByCategory.isNotEmpty()) {
                    item(key = "spent_c_h") { FosSectionHeader("НА ЧТО УШЛО", tone = FosTone.Negative) }
                    item(key = "spent_c") {
                        ShareDonut(
                            shares   = result.spentByCategory,
                            title    = "потрачено",
                            selected = spentSlice,
                            onSelect = { spentSlice = it },
                            colorOf  = colorOf,
                            nameOf   = nameOf,
                            sym      = sym,
                        )
                    }
                }
                if (result.earnedByCategory.isNotEmpty()) {
                    item(key = "earned_c_h") { FosSectionHeader("ОТКУДА ПРИШЛО", tone = FosTone.Positive) }
                    item(key = "earned_c") {
                        ShareDonut(
                            shares   = result.earnedByCategory,
                            title    = "заработано",
                            selected = earnedSlice,
                            onSelect = { earnedSlice = it },
                            colorOf  = colorOf,
                            nameOf   = nameOf,
                            sym      = sym,
                        )
                    }
                }

                // ── Источники ────────────────────────────────────────────────────────
                if (result.spentSources.isNotEmpty()) {
                    item(key = "spent_s_h") { FosSectionHeader("КОМУ УШЛИ ДЕНЬГИ", tone = FosTone.Negative) }
                    item(key = "spent_s") {
                        SourceList(
                            sources  = result.spentSources,
                            showAll  = allSpentSrc,
                            onToggle = { allSpentSrc = !allSpentSrc },
                            colorOf  = colorOf,
                            nameOf   = nameOf,
                            sym      = sym,
                        )
                    }
                }
                if (result.earnedSources.isNotEmpty()) {
                    item(key = "earned_s_h") { FosSectionHeader("ОТ КОГО ПРИШЛИ", tone = FosTone.Positive) }
                    item(key = "earned_s") {
                        SourceList(
                            sources  = result.earnedSources,
                            showAll  = allEarnedSrc,
                            onToggle = { allEarnedSrc = !allEarnedSrc },
                            colorOf  = colorOf,
                            nameOf   = nameOf,
                            sym      = sym,
                        )
                    }
                }
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun <T> ChipRow(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick  = { onSelect(item) },
                label    = { Text(label(item), style = FosType.Label) },
                shape    = RoundedCornerShape(FosDimens.RadiusChip),
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = FosColors.Info.copy(alpha = 0.15f),
                    selectedLabelColor     = FosColors.Info,
                    containerColor         = FosColors.Surface2,
                    labelColor             = FosColors.TextSecondary,
                ),
            )
        }
    }
}

/** Главный блок экрана: сколько потрачено, сколько заработано, что осталось. */
@Composable
private fun TotalsHero(result: LifetimeStats.Result) {
    val base   = result.totals.firstOrNull { it.currency == result.currency }
    val others = result.totals.filter { it.currency != result.currency }
    val sym    = FosFormatter.currencySymbol(result.currency)
    Column(Modifier.fillMaxWidth().fosHeroCard()) {
        result.firstDate?.let {
            Text("с ${it.format(SINCE)}", style = FosType.Micro, color = FosColors.TextMuted)
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Потрачено", style = FosType.Micro, color = FosColors.TextSecondary)
                Text(FosFormatter.amount(base?.spent ?: 0L, sym), style = FosType.CardAmount, color = FosColors.Negative)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Заработано", style = FosType.Micro, color = FosColors.TextSecondary)
                Text(FosFormatter.amount(base?.earned ?: 0L, sym), style = FosType.CardAmount, color = FosColors.Positive)
            }
        }
        base?.let {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Осталось от заработанного", style = FosType.Micro, color = FosColors.TextMuted)
                Text(
                    FosFormatter.signedAmount(it.net, sym),
                    style = FosType.SmallBold,
                    color = if (it.net >= 0) FosColors.Positive else FosColors.Negative,
                )
            }
        }
        // Другие валюты — отдельными строками. Сложить их с основной нечем: курса у приложения нет,
        // а выбросить нельзя — долларовая подписка молча пропала бы из «всего потрачено».
        others.forEach { t ->
            val otherSym = FosFormatter.currencySymbol(t.currency)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("в ${t.currency}", style = FosType.Micro, color = FosColors.TextMuted)
                Text(
                    "−${FosFormatter.amount(t.spent, otherSym)} · +${FosFormatter.amount(t.earned, otherSym)}",
                    style = FosType.MicroNum,
                    color = FosColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Переводы между своими счетами не считаются ни тратой, ни заработком.",
            style = FosType.Micro,
            color = FosColors.TextMuted,
        )
    }
}

@Composable
private fun YearBlock(
    bars    : List<LifetimeStats.YearBar>,
    selected: Int?,
    accent  : Color,
    colorOf : (String?) -> Color,
    nameOf  : (String?) -> String,
    sym     : String,
    onSelect: (Int?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().fosCard()) {
        YearStackedBars(bars = bars, colorOf = colorOf, selected = selected, accent = accent, onSelect = onSelect, sym = sym)
        Spacer(Modifier.height(8.dp))
        val bar = bars.firstOrNull { it.year == selected }
        if (bar == null) {
            Text("Нажмите на год — покажу, из чего он состоял", style = FosType.Micro, color = FosColors.TextMuted)
        } else {
            Text("${bar.year}: ${FosFormatter.amount(bar.total, sym)}", style = FosType.SmallBold, color = accent)
            Spacer(Modifier.height(4.dp))
            bar.segments.forEach { seg ->
                ShareRow(colorOf(seg.categoryId), nameOf(seg.categoryId), seg.kopecks, bar.total, sym)
            }
        }
    }
}

@Composable
private fun ShareDonut(
    shares  : List<LifetimeStats.CategoryShare>,
    title   : String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    colorOf : (String?) -> Color,
    nameOf  : (String?) -> String,
    sym     : String,
) {
    val total = shares.sumOf { it.kopecks }
    Column(Modifier.fillMaxWidth().fosCard(), horizontalAlignment = Alignment.CenterHorizontally) {
        SegmentedDonut(
            slices      = shares.map { DonutSlice(nameOf(it.categoryId), it.kopecks, colorOf(it.categoryId)) },
            selected    = selected,
            onSelect    = onSelect,
            modifier    = Modifier.size(200.dp),
            centreTitle = title,
        )
        Spacer(Modifier.height(10.dp))
        shares.forEach { s ->
            ShareRow(colorOf(s.categoryId), nameOf(s.categoryId), s.kopecks, total, sym)
        }
    }
}

/**
 * Источники — с полной суммой и числом операций. Каждая новая операция сразу добавляется в свою
 * строку: экран читает живой поток операций, и список пересчитывается вместе с ним.
 */
@Composable
private fun SourceList(
    sources : List<LifetimeStats.SourceTotal>,
    showAll : Boolean,
    onToggle: () -> Unit,
    colorOf : (String?) -> Color,
    nameOf  : (String?) -> String,
    sym     : String,
) {
    val shown = if (showAll) sources else sources.take(SOURCES_PREVIEW)
    Column(Modifier.fillMaxWidth().fosCard()) {
        shown.forEach { s ->
            Row(
                modifier          = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        s.label ?: nameOf(s.categoryId),
                        style    = FosType.BodySemi,
                        color    = FosColors.TextPrimary,
                        maxLines = 1,
                    )
                    Text(
                        "${nameOf(s.categoryId)} · ${pluralOps(s.count)}",
                        style = FosType.Micro,
                        color = colorOf(s.categoryId),
                    )
                }
                Text(FosFormatter.amount(s.kopecks, sym), style = FosType.SmallBold, color = FosColors.TextPrimary)
            }
        }
        if (sources.size > SOURCES_PREVIEW) {
            TextButton(onClick = onToggle) {
                Text(
                    if (showAll) "Свернуть" else "Показать все (${sources.size})",
                    style = FosType.Label,
                    color = FosColors.Info,
                )
            }
        }
    }
}

private fun pluralOps(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    val word = when {
        mod10 == 1 && mod100 != 11                         -> "операция"
        mod10 in 2..4 && mod100 !in 12..14                 -> "операции"
        else                                               -> "операций"
    }
    return "$n $word"
}
