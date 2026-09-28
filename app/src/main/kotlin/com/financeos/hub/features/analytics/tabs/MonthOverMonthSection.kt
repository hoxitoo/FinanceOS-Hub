package com.financeos.hub.features.analytics.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.analytics.MonthOverMonth
import com.financeos.hub.core.analytics.WaterfallBar
import com.financeos.hub.ui.components.MoMComparison
import com.financeos.hub.ui.components.SectionHeader
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.fosCard
import java.time.YearMonth
import kotlin.math.abs

private val MONTH_SHORT = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
private val MONTH_FULL  = listOf(
    "январь", "февраль", "март", "апрель", "май", "июнь",
    "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
)
/** Родительный падеж — после «против»: «против августа», а не «против августом». */
private val MONTH_GEN   = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

/** Сколько категорий показывать в разбивке месяца: дальше — мелочь, которая только растягивает блок. */
private const val CATEGORY_ROWS = 6

/**
 * «Месяц к месяцу» — самый верх вкладки «Тренды».
 *
 * Один переключатель окна на оба блока: траты и доход смотрят на одни и те же месяцы, иначе их
 * пришлось бы сверять, переключая дважды.
 */
@Composable
fun MonthOverMonthSection(
    mom          : MonthOverMonth.Result,
    categoryNames: Map<String, String>,
) {
    // Переживает уход вкладки из пейджера: вернувшись, человек видит то окно, что выбрал.
    var window by rememberSaveable { mutableStateOf(MonthOverMonth.Window.SIX) }
    val sym = FosFormatter.currencySymbol(mom.currency)

    Column(verticalArrangement = Arrangement.spacedBy(FosDimens.ItemGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MonthOverMonth.Window.entries.forEach { w ->
                FilterChip(
                    selected = window == w,
                    onClick  = { window = w },
                    label    = { Text(w.label, style = FosType.Label) },
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

        MomBlock(
            title         = "ТРАТЫ: МЕСЯЦ К МЕСЯЦУ",
            tone          = FosTone.Negative,
            color         = FosColors.Negative,
            isIncome      = false,
            bars          = mom.spentIn(window),
            mom           = mom,
            sym           = sym,
            categoryNames = categoryNames,
        )
        MomBlock(
            title         = "ДОХОД: МЕСЯЦ К МЕСЯЦУ",
            tone          = FosTone.Positive,
            color         = FosColors.Positive,
            isIncome      = true,
            bars          = mom.earnedIn(window),
            mom           = mom,
            sym           = sym,
            categoryNames = categoryNames,
        )
    }
}

@Composable
private fun MomBlock(
    title        : String,
    tone         : FosTone,
    color        : Color,
    isIncome     : Boolean,
    bars         : List<MonthOverMonth.MonthBar>,
    mom          : MonthOverMonth.Result,
    sym          : String,
    categoryNames: Map<String, String>,
) {
    // Выбор хранится МЕСЯЦЕМ, а не индексом: при смене окна «6 мес ↔ Год» индексы сдвигаются, а
    // месяц остаётся тем же. По умолчанию — последний месяц, ради которого на график и смотрят.
    var selectedMonth by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = bars.firstOrNull { it.month.toString() == selectedMonth } ?: bars.lastOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(FosDimens.ItemGap)) {
        SectionHeader(
            title     = title,
            infoTitle = if (isIncome) "Доход месяц к месяцу" else "Траты месяц к месяцу",
            infoBody  = "Каждый месяц сравнивается со своим предыдущим — в том числе через границу " +
                "года: январь сравнивается с декабрём.\n\n" +
                "Яркий бар — сам месяц, бледный за ним — предыдущий. Разница видна по высоте, " +
                "над баром — изменение в процентах.\n\n" +
                "Сравнивается полный месяц с полным. Текущий месяц ещё не закончен, и его бар " +
                "помечен: цифра будет расти до конца месяца, поэтому его изменение показано " +
                "серым, а не красным или зелёным.\n\n" +
                "Переводы между своими счетами не считаются. Нажмите на месяц — ниже появится, " +
                "из чего сложилась разница.",
            tone      = tone,
        )
        // Блок с финансовым направлением — Rail в тоне направления (правило огранки #7).
        Column(Modifier.fillMaxWidth().fosCard(FosCardStyle.Rail, tone)) {
            if (bars.all { it.kopecks == 0L && it.previous == 0L }) {
                Text(
                    if (isIncome) "За этот период поступлений нет" else "За этот период трат нет",
                    style = FosType.Body,
                    color = FosColors.TextMuted,
                )
            } else {
                OverlaidMonthBars(
                    bars     = bars,
                    color    = color,
                    isIncome = isIncome,
                    selected = selected?.month,
                    onSelect = { selectedMonth = it.toString() },
                )
                selected?.let { bar ->
                    Spacer(Modifier.height(12.dp))
                    MonthDetail(bar, isIncome, mom, sym, categoryNames)
                }
            }
        }
    }
}

/**
 * Наложенные бары: бледный бар предыдущего месяца стоит ЗА баром самого месяца. Разница читается
 * без цифр — по тому, насколько яркий выше или ниже бледного.
 *
 * Все бары делят ширину поровну, без прокрутки: двенадцать баров по ~26 dp помещаются на телефоне,
 * а прокрутка спрятала бы половину года, ради сравнения с которым график и открывают.
 */
@Composable
private fun OverlaidMonthBars(
    bars    : List<MonthOverMonth.MonthBar>,
    color   : Color,
    isIncome: Boolean,
    selected: YearMonth?,
    onSelect: (YearMonth) -> Unit,
) {
    val max = bars.maxOf { maxOf(it.kopecks, it.previous) }.coerceAtLeast(1L)
    val chartHeight = 120.dp
    val narrow = bars.size > MonthOverMonth.Window.SIX.months

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment     = Alignment.Bottom,
    ) {
        bars.forEach { bar ->
            val isSel = bar.month == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSel) FosColors.Surface2 else Color.Transparent)
                    .clickable { onSelect(bar.month) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    deltaShort(bar, narrow),
                    style    = FosType.MicroNum,
                    color    = deltaColor(bar, isIncome),
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier         = Modifier.fillMaxWidth().height(chartHeight),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // Предыдущий месяц — широкий и бледный, позади.
                    Box(
                        Modifier
                            .fillMaxWidth(0.92f)
                            .height(chartHeight * (bar.previous.toFloat() / max))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(color.copy(alpha = 0.22f)),
                    )
                    // Сам месяц — уже и ярче, впереди. Незаконченный — полупрозрачный: он ещё растёт.
                    Box(
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height(chartHeight * (bar.kopecks.toFloat() / max))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(color.copy(alpha = if (bar.isComplete) 1f else 0.5f)),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    // «•» у незаконченного месяца — только на широких барах: на узких «сен•» не
                    // влезает, а незаконченность там видна по бледности бара.
                    MONTH_SHORT[bar.month.monthValue - 1] + if (bar.isComplete || narrow) "" else "•",
                    style    = FosType.Micro,
                    color    = if (isSel) FosColors.TextPrimary else FosColors.TextMuted,
                    maxLines = 1,
                    softWrap = false,
                )
                // Год — только под январём: там, где он меняется. Под каждым баром он был бы шумом.
                Text(
                    if (bar.month.monthValue == 1) "${bar.month.year % 100}" else " ",
                    style     = FosType.MicroNum,
                    color     = FosColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Подпись над баром. На шести барах — «▲12» / «▼8», на двенадцати — только стрелка: колонка там
 * ~22 dp, и «▲125» обрезалось бы до «▲12» — неверная цифра хуже, чем её отсутствие. Полное
 * изменение — в детализации под графиком. Больше 999 % — «999+»: рост с копеек даёт миллионы.
 */
private fun deltaShort(bar: MonthOverMonth.MonthBar, narrow: Boolean): String {
    val pct = bar.deltaPercent ?: return if (bar.kopecks > 0L) (if (narrow) "•" else "нов.") else ""
    val arrow = when {
        pct > 0 -> "▲"
        pct < 0 -> "▼"
        else    -> ""
    }
    if (narrow) return arrow.ifEmpty { "0" }
    val magnitude = abs(pct)
    return if (pct == 0) "0" else arrow + if (magnitude > 999) "999+" else "$magnitude"
}

/**
 * Цвет изменения — «лучше / хуже», а не «больше / меньше»: рост трат красный, рост дохода зелёный.
 * У незаконченного месяца изменение серое: сравнение его частичной суммы с полным месяцем почти
 * всегда выглядит «экономией», и зелёный цвет выдал бы её за результат.
 */
private fun deltaColor(bar: MonthOverMonth.MonthBar, isIncome: Boolean): Color {
    // «0 %» после округления — без цвета: красный ноль читается как ошибка.
    if (!bar.isComplete || bar.delta == 0L || bar.deltaPercent == 0) return FosColors.TextMuted
    val better = if (isIncome) bar.delta > 0 else bar.delta < 0
    return when {
        better   -> FosColors.Positive
        // Красный — только траты и перерасход (правило #2). Упавший доход — предупреждение, а не
        // трата: янтарный говорит «хуже», не выдавая это за расход.
        isIncome -> FosColors.Warning
        else     -> FosColors.Negative
    }
}

@Composable
private fun MonthDetail(
    bar          : MonthOverMonth.MonthBar,
    isIncome     : Boolean,
    mom          : MonthOverMonth.Result,
    sym          : String,
    categoryNames: Map<String, String>,
) {
    val month = bar.month
    val prev  = month.minusMonths(1)
    Text(
        "${MONTH_FULL[month.monthValue - 1].replaceFirstChar { it.uppercase() }} ${month.year} " +
            "против ${MONTH_GEN[prev.monthValue - 1]}" + if (prev.year != month.year) " ${prev.year}" else "",
        style = FosType.BodySemi.copy(fontFeatureSettings = "tnum"),
        color = FosColors.TextPrimary,
    )
    Spacer(Modifier.height(2.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            "${FosFormatter.amount(bar.previous, sym)} → ${FosFormatter.amount(bar.kopecks, sym)}",
            style = FosType.SmallBold,
            color = FosColors.TextSecondary,
        )
        Text(
            bar.deltaPercent?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + "${abs(it)} %" }
                ?: if (bar.kopecks > 0L) "новое" else "—",
            style = FosType.SmallBold,
            color = deltaColor(bar, isIncome),
        )
    }
    if (!bar.isComplete) {
        Spacer(Modifier.height(2.dp))
        Text(
            // «Идёт 28-й день из 30» — без склонения «дней»: «из 31 дней» и «прошло 1 из 30 дней»
            // читались бы с ошибкой каждый день в текущем месяце.
            "Месяц не закончен: идёт ${mom.daysPassed}-й день из ${mom.daysInMonth}. " +
                "Сравнение с полным предыдущим месяцем — цифра ещё будет расти.",
            style = FosType.MicroNum,
            color = FosColors.TextMuted,
        )
    }
    val rows = bar.categories.filter { it.current != it.previous }.take(CATEGORY_ROWS)
    if (rows.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        MoMComparison(
            bars = rows.map { c ->
                WaterfallBar(
                    // Удалённая категория — не «без категории»: деньги в ней были, просто её больше
                    // нет в списке активных.
                    label          = when (val id = c.categoryId) {
                        null -> "Без категории"
                        else -> categoryNames[id] ?: "Удалённая категория"
                    },
                    delta          = c.current - c.previous,
                    prevKopecks    = c.previous,
                    currentKopecks = c.current,
                    isIncome       = isIncome,
                )
            },
            sym = sym,
        )
    }
}
