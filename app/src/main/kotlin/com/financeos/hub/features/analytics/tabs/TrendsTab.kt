package com.financeos.hub.features.analytics.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.financeos.hub.core.analytics.HeatmapData
import com.financeos.hub.features.analytics.AnalyticsState
import com.financeos.hub.ui.components.DonutSlice
import com.financeos.hub.ui.components.FatigueBars
import com.financeos.hub.ui.components.MoMComparison
import com.financeos.hub.ui.components.SectionHeader
import com.financeos.hub.ui.components.SegmentedDonut
import com.financeos.hub.ui.components.SpendTimeline
import com.financeos.hub.ui.components.FosSectionHeader
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.fosCard
import com.financeos.hub.ui.theme.fosHeroCard
import com.financeos.hub.ui.theme.fosInset
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

private val DAY_NAMES  = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val DAY_COLORS = listOf(
    Color(0xFF4D9FFF), Color(0xFF4DFFA0), Color(0xFFFFB84D), Color(0xFF9B5CFF),
    Color(0xFF2DD4BF), Color(0xFFFF87C2), Color(0xFFF87171),
)
/** 4-hour buckets: 24 individually-tappable hour slices would be far too small on a phone. */
private val HOUR_BUCKETS = listOf(
    "00–04" to 0, "04–08" to 4, "08–12" to 8,
    "12–16" to 12, "16–20" to 16, "20–24" to 20,
)
private val HOUR_COLORS = listOf(
    Color(0xFF3B4A6B), Color(0xFF4D9FFF), Color(0xFF2DD4BF),
    Color(0xFFFFB84D), Color(0xFFFF9F43), Color(0xFF9B5CFF),
)

@Composable
fun TrendsTab(state: AnalyticsState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FosColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FosDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
    ) {
        Spacer(Modifier.height(FosDimens.ItemGap))

        // ── 1. Daily expense timeline ─────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(FosDimens.ItemGap)) {
            SectionHeader(
                title     = "РАСХОДЫ ПО ДНЯМ",
                infoTitle = "Расходы по дням",
                infoBody  = "Каждый столбик — сколько вы потратили за один день. Дни без трат " +
                    "показаны пустыми: раньше они просто выпадали из графика, и соседние " +
                    "покупки с разницей в неделю рисовались рядом, как будто они были подряд.\n\n" +
                    "Серая линия — ваш средний день за период, подпись справа сверху — самый " +
                    "дорогой. Ярким выделен пик.\n\n" +
                    "Нажмите на столбик (или проведите пальцем вдоль графика), чтобы увидеть " +
                    "дату и точную сумму.\n\n" +
                    "На длинных периодах дни объединяются: до 45 дней — по дням, дальше — " +
                    "по неделям, а на очень больших промежутках — по месяцам. Иначе столбики " +
                    "становятся тоньше волоса и график превращается в шум.",
                tone      = FosTone.Negative,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fosHeroCard(),
            ) {
                SpendTimeline(daily = state.dailyExpenses)
            }
        }

        // ── 2. «Когда ты тратишь» — two compact, tappable donuts ──────────────
        state.heatmap?.let { heat -> WhenYouSpendCard(heat) }

        // ── 3. Budget fatigue ─────────────────────────────────────────────────
        state.fatigueCurve?.let { fatigue ->
            Column(verticalArrangement = Arrangement.spacedBy(FosDimens.ItemGap)) {
                SectionHeader(
                    title     = "УСТАЛОСТЬ БЮДЖЕТА",
                    infoTitle = "Усталость бюджета",
                    infoBody  = "Мы берём ваши расходы за ${fatigue.monthCount} мес. и считаем, " +
                        "сколько в среднем вы тратите в каждый день месяца: все траты 1-го числа " +
                        "складываются и делятся на число месяцев, потом 2-го и так далее.\n\n" +
                        "Столбик — средняя трата в этот день месяца. Серая линия — ваш средний день. " +
                        "Оранжевые столбики выше нормы, красный — пик.\n\n" +
                        "Зачем: видно, где деньги «утекают» — например, всплеск сразу после зарплаты " +
                        "и спад к концу месяца, когда бюджет уже устал.",
                    tone      = FosTone.Warning,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fosCard(),
                ) {
                    if (fatigue.dailyAverages.any { it.second > 0L }) {
                        FatigueBars(dailyAverages = fatigue.dailyAverages)
                    } else {
                        Text("Недостаточно данных", style = FosType.Body, color = FosColors.TextMuted)
                    }
                }
            }
        }

        // ── 4. Month over month ───────────────────────────────────────────────
        if (state.waterfallBars.isNotEmpty()) {
            val total = state.waterfallBars.firstOrNull { it.isTotal }
            // Красная огранка, если расходы выросли, зелёная — если снизились. Направление читается
            // с края карточки раньше, чем взгляд доходит до цифры.
            val momTone = when {
                total == null                              -> FosTone.Neutral
                total.currentKopecks > total.prevKopecks    -> FosTone.Negative
                else                                       -> FosTone.Positive
            }
            Column(verticalArrangement = Arrangement.spacedBy(FosDimens.ItemGap)) {
                SectionHeader(
                    title     = "МЕСЯЦ К МЕСЯЦУ",
                    infoTitle = "Месяц к месяцу",
                    infoBody  = "Сравниваем текущий месяц с прошлым по каждой категории.\n\n" +
                        "Полоса ВЛЕВО и зелёный минус — в этом месяце вы потратили МЕНЬШЕ, чем в " +
                        "прошлом. Полоса ВПРАВО и красный плюс — потратили БОЛЬШЕ.\n\n" +
                        "Под каждой строкой видно «было → стало»: сумма за прошлый месяц и за " +
                        "текущий.\n\nВажно: в начале месяца текущий период ещё неполный, поэтому " +
                        "почти всё будет выглядеть как экономия — это нормально.",
                    tone      = momTone,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fosCard(FosCardStyle.Rail, momTone),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Plain-language headline first — the one line that answers "так лучше или хуже?"
                    total?.let { t ->
                        val spentMore = t.currentKopecks > t.prevKopecks
                        val diff      = abs(t.currentKopecks - t.prevKopecks)
                        Text(
                            if (spentMore) "Расходы выросли на ${FosFormatter.compact(diff)}"
                            else           "Расходы снизились на ${FosFormatter.compact(diff)}",
                            style = FosType.BodySemi,
                            color = if (spentMore) FosColors.Negative else FosColors.Positive,
                        )
                        Text(
                            "прошлый месяц ${FosFormatter.compact(t.prevKopecks)} → " +
                                "текущий ${FosFormatter.compact(t.currentKopecks)}",
                            style = FosType.Micro,
                            color = FosColors.TextMuted,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    MoMComparison(bars = state.waterfallBars)
                }
            }
        }

        // ── 5. Impulse ────────────────────────────────────────────────────────
        state.impulseStats?.let { stats ->
            if (stats.impulseCount > 0 || stats.plannedCount > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(FosDimens.ItemGap)) {
                    SectionHeader(
                        title     = "ИМПУЛЬСИВНОСТЬ",
                        infoTitle = "Как считается импульсивность",
                        infoBody  = "Импульсивная покупка — трата МЕНЬШЕ 2 000 ₽, совершённая ночью " +
                            "(с 21:00 до 06:00). Небольшие суммы поздно вечером почти всегда " +
                            "спонтанные: доставка, такси, подписки, маркетплейсы «на эмоциях».\n\n" +
                            "Плановая покупка — трата БОЛЬШЕ 5 000 ₽ в будний день утром " +
                            "(с 08:00 до 13:00). Так обычно оплачивают то, что решили заранее.\n\n" +
                            "Остальное — нейтральные траты.\n\n" +
                            "Это эвристика по времени и сумме, а не чтение мыслей: проверьте " +
                            "список ниже — если покупка на самом деле плановая, просто не " +
                            "обращайте на неё внимания.",
                        tone      = FosTone.Warning,
                    )
                    ImpulseCard(stats)
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

/** Two side-by-side tappable donuts: weekdays and 4-hour buckets. */
@Composable
private fun WhenYouSpendCard(heat: HeatmapData) {
    var selDay  by remember { mutableStateOf<Int?>(null) }
    var selHour by remember { mutableStateOf<Int?>(null) }

    val daySlices = remember(heat) {
        DAY_NAMES.mapIndexed { i, name ->
            DonutSlice(name, heat.grid[i].sum(), DAY_COLORS[i])
        }
    }
    val hourSlices = remember(heat) {
        HOUR_BUCKETS.mapIndexed { i, (label, startHour) ->
            val sum = (startHour until startHour + 4).sumOf { h -> heat.grid.sumOf { it[h] } }
            DonutSlice(label, sum, HOUR_COLORS[i])
        }
    }
    val hasData = daySlices.any { it.kopecks > 0 }

    Column(verticalArrangement = Arrangement.spacedBy(FosDimens.ItemGap)) {
        SectionHeader(
            title     = "КОГДА ТЫ ТРАТИШЬ",
            infoTitle = "Когда ты тратишь",
            infoBody  = "Слева — как расходы распределены по дням недели, справа — по времени суток " +
                "(интервалы по 4 часа).\n\nНажмите на сегмент, чтобы увидеть его долю и сумму. " +
                "Повторное нажатие снимает выделение.\n\nЗачем: помогает заметить свои шаблоны — " +
                "например, что почти всё уходит в выходные или поздно вечером.",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fosCard(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!hasData) {
                Text("Пока нет расходов для анализа", style = FosType.Body, color = FosColors.TextMuted)
            } else {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier            = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SegmentedDonut(
                            slices      = daySlices,
                            selected    = selDay,
                            onSelect    = { selDay = it },
                            centreTitle = "по дням",
                            modifier    = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                    }
                    Column(
                        modifier            = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SegmentedDonut(
                            slices      = hourSlices,
                            selected    = selHour,
                            onSelect    = { selHour = it },
                            centreTitle = "по часам",
                            modifier    = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                    }
                }

                // Compact legends
                LegendRow(daySlices)
                LegendRow(hourSlices)

                // Headline insight: the peak day and peak time, in words.
                val topDay  = daySlices.withIndex().maxByOrNull { it.value.kopecks }
                val topHour = hourSlices.withIndex().maxByOrNull { it.value.kopecks }
                if (topDay != null && topHour != null) {
                    Text(
                        "Больше всего вы тратите в ${dayInCase(topDay.index)}, " +
                            "чаще всего в ${topHour.value.label} ч.",
                        style = FosType.Micro,
                        color = FosColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendRow(slices: List<DonutSlice>) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        slices.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(s.color)
                )
                Spacer(Modifier.size(3.dp))
                Text(s.label, style = FosType.Micro, color = FosColors.TextMuted, maxLines = 1)
            }
        }
    }
}

private fun dayInCase(index: Int): String = when (index) {
    0 -> "понедельник"; 1 -> "вторник"; 2 -> "среду"; 3 -> "четверг"
    4 -> "пятницу";     5 -> "субботу"; else -> "воскресенье"
}

/** Impulse card: share bar, headline numbers, and the actual purchases that were flagged. */
@Composable
private fun ImpulseCard(stats: com.financeos.hub.core.analytics.ImpulseStats) {
    val zone = remember { ZoneId.systemDefault() }
    val total = (stats.impulseCount + stats.plannedCount + stats.neutralCount).coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fosCard(FosCardStyle.Rail, FosTone.Warning),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Headline: the share of MONEY, which matters more than the share of receipts.
        Text(
            "${(stats.impulseShareOfSpend * 100).toInt()}% денег ушло на импульсивные покупки",
            style = FosType.BodySemi,
            color = FosColors.TextPrimary,
        )
        Text(
            "${FosFormatter.compact(stats.impulseKopecks)} из ${FosFormatter.compact(stats.totalKopecks)}",
            style = FosType.Micro,
            color = FosColors.TextMuted,
        )

        // Proportion bar: impulse / planned / neutral by transaction count.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
        ) {
            // weight() already fixes each segment's width inside the Row — adding fillMaxWidth here
            // would fight it, so children only fill the Row's 10dp height.
            if (stats.impulseCount > 0) {
                Box(Modifier.weight(stats.impulseCount.toFloat()).fillMaxHeight().background(FosColors.Warning))
            }
            if (stats.plannedCount > 0) {
                Box(Modifier.weight(stats.plannedCount.toFloat()).fillMaxHeight().background(FosColors.Positive))
            }
            if (stats.neutralCount > 0) {
                Box(Modifier.weight(stats.neutralCount.toFloat()).fillMaxHeight().background(FosColors.Surface2))
            }
        }
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LegendDot("Импульсивные ${stats.impulseCount}", FosColors.Warning)
            LegendDot("Плановые ${stats.plannedCount}", FosColors.Positive)
            LegendDot("Обычные ${stats.neutralCount}", FosColors.TextMuted)
        }

        // The proof: which purchases were actually flagged, with the hour that triggered it.
        if (stats.samples.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            FosSectionHeader("ЧТО ИМЕННО")
            stats.samples.forEach { tx ->
                val ldt = Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDateTime()
                Row(
                    // Sunken: these rows are evidence for the card above them, not cards of their
                    // own. Recessing them keeps the nesting visible one level deep.
                    modifier              = Modifier.fillMaxWidth().fosInset(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tx.merchant?.takeIf { it.isNotBlank() } ?: "Операция",
                            style = FosType.Body, color = FosColors.TextPrimary, maxLines = 1,
                        )
                        Text(
                            "${FosFormatter.dayLabel(tx.timestamp)} · " +
                                "%02d:%02d".format(ldt.hour, ldt.minute),
                            style = FosType.Micro, color = FosColors.TextSecondary,
                        )
                    }
                    Text(
                        FosFormatter.compact(abs(tx.amountKopecks), FosFormatter.currencySymbol(tx.currency)),
                        style = FosType.SmallBold,
                        color = FosColors.Warning,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.size(4.dp))
        Text(text, style = FosType.Micro, color = FosColors.TextMuted, maxLines = 1)
    }
}
