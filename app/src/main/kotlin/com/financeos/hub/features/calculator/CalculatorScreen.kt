package com.financeos.hub.features.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.core.finance.SavingsMath
import com.financeos.hub.features.dashboard.accountSheetFieldColors
import com.financeos.hub.ui.components.FosExplain
import com.financeos.hub.ui.components.FosSectionHeader
import com.financeos.hub.ui.theme.AmountVisualTransformation
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.fosCard
import com.financeos.hub.ui.theme.fosHeroCard
import com.financeos.hub.ui.theme.fosInset
import java.time.LocalDate

/**
 * Калькулятор накоплений: что вырастет, за сколько наберётся, сколько для этого откладывать.
 *
 * Отличие от калькулятора на сайте банка — он знает ваши данные: предлагает ваш собственный темп
 * накопления (средний остаток за три закрытых месяца) и подставляет сумму любой вашей цели. Обе
 * подстановки одноразовые: дальше поле обычное и правится руками, потому что калькулятор — это
 * «что если», а не отчёт.
 *
 * Числа здесь — ОЦЕНКА, и экран говорит это словами. Банк меняет ставку при пролонгации, НДФЛ с
 * процентов имеет необлагаемый минимум, привязанный к ключевой ставке, а взнос можно и пропустить.
 * Точна ровно одна цифра — та, что при нулевой ставке: сумма взносов.
 */
@Composable
fun CalculatorScreen(
    onBack: () -> Unit = {},
    vm    : CalculatorViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val input  = rememberCalcInputs()

    // Симуляция чистая и дешёвая (максимум 600 шагов), но пересчитывать её на КАЖДУЮ рекомпозицию
    // — включая те, что вызваны раскрытием панели — незачем. Ключом идёт всё, что влияет на ответ.
    val answer = remember(
        input.mode, input.initial, input.monthly, input.years, input.extraMonths,
        input.rate, input.target, input.compounding, input.timing,
        input.growth, input.inflation, input.taxOn,
    ) { solve(input) }

    LazyColumn(
        modifier            = Modifier.fillMaxSize().background(FosColors.Background),
        contentPadding      = PaddingValues(horizontal = FosDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
    ) {
        item { Spacer(Modifier.height(16.dp)) }

        item {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Калькулятор", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
                TextButton(onClick = onBack) {
                    Text("← Назад", style = FosType.Label, color = FosColors.TextSecondary)
                }
            }
        }

        // ── Режим ─────────────────────────────────────────────────────────────
        item {
            Row(
                modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CalcMode.entries.forEach { m ->
                    FilterChip(
                        selected = input.mode == m,
                        onClick  = { input.mode = m },
                        label    = { Text(m.title, style = FosType.Label) },
                        colors   = calcChipColors(),
                        border   = null,
                    )
                }
            }
        }
        item {
            Text(input.mode.question, style = FosType.Micro, color = FosColors.TextMuted)
        }

        // ── Ответ ─────────────────────────────────────────────────────────────
        item { AnswerCard(answer, input) }

        // ── Ввод ──────────────────────────────────────────────────────────────
        item { FosSectionHeader("ИСХОДНЫЕ ДАННЫЕ") }
        item {
            Column(
                modifier            = Modifier.fillMaxWidth().fosCard(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (input.mode != CalcMode.Grow) {
                    MoneyField(
                        value    = input.target,
                        onChange = { input.target = FosFormatter.sanitizeAmountInput(it) },
                        label    = "Цель, ₽",
                    )
                    if (state.goals.isNotEmpty()) {
                        Text("Подставить из цели", style = FosType.Micro, color = FosColors.TextMuted)
                        Row(
                            modifier              = Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.goals.forEach { goal ->
                                FilterChip(
                                    selected = false,
                                    onClick  = {
                                        // Цель целиком в «цель», накопленное — в «уже накоплено».
                                        // Считать целью ОСТАТОК и одновременно ставить накопленное
                                        // стартом значило бы вычесть накопленное дважды.
                                        input.target  = FosFormatter.plainAmountInput(goal.targetKopecks)
                                        input.initial = FosFormatter.plainAmountInput(goal.savedKopecks)
                                    },
                                    label  = { Text("${goal.emoji} ${goal.name}", style = FosType.Micro) },
                                    colors = calcChipColors(),
                                    border = null,
                                )
                            }
                        }
                    }
                }

                MoneyField(
                    value    = input.initial,
                    onChange = { input.initial = FosFormatter.sanitizeAmountInput(it) },
                    label    = "Уже накоплено, ₽",
                )

                if (input.mode != CalcMode.Contribution) {
                    MoneyField(
                        value    = input.monthly,
                        onChange = { input.monthly = FosFormatter.sanitizeAmountInput(it) },
                        label    = "Пополнение в месяц, ₽",
                    )
                    state.suggestedMonthlyKopecks?.let { pace ->
                        Text(
                            "Ваш темп: ${FosFormatter.compact(pace)} в месяц — подставить",
                            style    = FosType.Micro,
                            color    = FosColors.Info,
                            modifier = Modifier.clickable {
                                input.monthly = FosFormatter.plainAmountInput(pace)
                            },
                        )
                    }
                }

                if (input.mode != CalcMode.Time) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IntField(
                            value    = input.years,
                            onChange = { input.years = digitsOnly(it, max = 3) },
                            label    = "Лет",
                            modifier = Modifier.weight(1f),
                        )
                        IntField(
                            value    = input.extraMonths,
                            onChange = { input.extraMonths = digitsOnly(it, max = 2) },
                            label    = "и месяцев",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier              = Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(1, 3, 5, 10, 20).forEach { y ->
                            FilterChip(
                                selected = input.years == y.toString() && input.extraMonths.isBlank(),
                                onClick  = { input.years = y.toString(); input.extraMonths = "" },
                                label    = { Text("$y ${pluralYears(y)}", style = FosType.Micro) },
                                colors   = calcChipColors(),
                                border   = null,
                            )
                        }
                    }
                }

                PercentField(
                    value    = input.rate,
                    onChange = { input.rate = FosFormatter.sanitizeAmountInput(it) },
                    label    = "Ставка, % годовых",
                    hint     = "Ноль — обычная копилка без процентов.",
                )
            }
        }

        // ── Тонкая настройка ──────────────────────────────────────────────────
        item {
            Text(
                if (input.advancedOpen) "Свернуть настройки ▴" else "Тонкая настройка ▾",
                style    = FosType.Micro,
                color    = FosColors.Info,
                modifier = Modifier
                    .clickable { input.advancedOpen = !input.advancedOpen }
                    .padding(vertical = 4.dp),
            )
        }
        if (input.advancedOpen) {
            item { AdvancedBlock(input) }
        }

        // ── Разбор ответа ─────────────────────────────────────────────────────
        val result = (answer as? CalcAnswer.Amount)?.result
            ?: (answer as? CalcAnswer.Duration)?.result
            ?: (answer as? CalcAnswer.Monthly)?.result

        if (result != null && result.schedule.isNotEmpty()) {
            item { FosSectionHeader("ИЗ ЧЕГО СКЛАДЫВАЕТСЯ", tone = FosTone.Positive) }
            item { BreakdownCard(result, input) }
            item { FosSectionHeader("ПО ГОДАМ") }
            item { SavingsChart(result.schedule) }
            item { YearTable(result.schedule) }
        }

        item {
            Column(
                modifier            = Modifier.fillMaxWidth().fosCard(FosCardStyle.Outline, FosTone.Warning),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Это оценка", style = FosType.BodySemi, color = FosColors.Warning)
                Text(
                    "Банк меняет ставку при пролонгации вклада, налог с процентов считается с " +
                        "необлагаемым минимумом (он привязан к ключевой ставке и меняется каждый " +
                        "год), а взнос можно пропустить. Точна ровно одна цифра — сумма ваших " +
                        "взносов при нулевой ставке.",
                    style = FosType.Micro,
                    color = FosColors.TextSecondary,
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Ответ ────────────────────────────────────────────────────────────────────

@Composable
private fun AnswerCard(answer: CalcAnswer, input: CalcInputs) {
    if (answer is CalcAnswer.Impossible) {
        Column(
            modifier            = Modifier.fillMaxWidth().fosCard(FosCardStyle.Outline, FosTone.Info),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Пока не хватает данных", style = FosType.BodySemi, color = FosColors.TextPrimary)
            Text(answer.reason, style = FosType.Micro, color = FosColors.TextSecondary)
        }
        return
    }

    val (caption, value, sub) = when (answer) {
        is CalcAnswer.Amount   -> Triple(
            "БУДЕТ ЧЕРЕЗ ${monthsLabel(input.months)}",
            FosFormatter.amount(answer.kopecks),
            "из них ваших — ${FosFormatter.compact(answer.result.contributedKopecks)}",
        )
        is CalcAnswer.Duration -> Triple(
            "НАБЕРЁТСЯ ЧЕРЕЗ",
            monthsLabel(answer.months),
            "примерно к ${FosFormatter.monthYear(LocalDate.now().plusMonths(answer.months.toLong()))}",
        )
        is CalcAnswer.Monthly  -> Triple(
            "ОТКЛАДЫВАТЬ В МЕСЯЦ",
            FosFormatter.amount(answer.kopecks),
            "чтобы через ${monthsLabel(input.months)} было ${FosFormatter.compact(input.targetKopecks)}",
        )
        else -> Triple("", "", "")
    }

    Column(
        modifier            = Modifier.fillMaxWidth().fosHeroCard(FosTone.Positive),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(caption, style = FosType.SectionCap, color = FosColors.TextMuted)
        Text(value, style = FosType.HeroAmountMulti, color = FosColors.Positive)
        Text(sub, style = FosType.Micro, color = FosColors.TextSecondary)
    }
}

// ── Разбор ───────────────────────────────────────────────────────────────────

@Composable
private fun BreakdownCard(result: SavingsMath.Result, input: CalcInputs) {
    Column(
        modifier            = Modifier.fillMaxWidth().fosCard(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Полоса «своё / проценты» — она отвечает на главный вопрос быстрее любой таблицы:
        // сколько из итога вы принесли сами, а сколько сделала ставка.
        val total = (result.contributedKopecks + result.interestKopecks).coerceAtLeast(1L)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FosDimens.BarHeightLg)
                .clip(RoundedCornerShape(FosDimens.RadiusBar))
                .background(FosColors.SurfaceSunken),
        ) {
            if (result.contributedKopecks > 0) {
                Box(
                    Modifier
                        .weight(result.contributedKopecks.toFloat() / total)
                        .fillMaxHeight()
                        .background(FosColors.Info)
                )
            }
            if (result.interestKopecks > 0) {
                Box(
                    Modifier
                        .weight(result.interestKopecks.toFloat() / total)
                        .fillMaxHeight()
                        .background(FosColors.Positive)
                )
            }
        }
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LegendDot("Ваши деньги", FosColors.Info)
            LegendDot("Проценты", FosColors.Positive)
        }

        Line("Ваши взносы", FosFormatter.amount(result.contributedKopecks), FosColors.TextPrimary)
        Line("Проценты", FosFormatter.amount(result.interestKopecks), FosColors.Positive)
        if (result.taxKopecks > 0) {
            // На счёте лежит сумма ДО налога, и это не упрощение: НДФЛ с процентов начисляет
            // налоговая по итогам года и платится отдельно, вклад он не уменьшает. Поэтому
            // главное число остаётся валовым, а «на руки» показано здесь явной строкой — иначе
            // пришлось бы либо соврать в итоге, либо спрятать налог совсем.
            Line("НДФЛ 13 % (платится отдельно)", "−" + FosFormatter.amount(result.taxKopecks), FosColors.Negative)
            Line(
                "Останется после налога",
                FosFormatter.amount(result.finalKopecks - result.taxKopecks),
                FosColors.TextPrimary,
            )
        }
        if (input.inflationBp > 0) {
            Line(
                "В сегодняшних деньгах",
                FosFormatter.amount(result.realFinalKopecks),
                FosColors.TextSecondary,
            )
        }
        if (input.rateBp > 0 && input.compounding != SavingsMath.Compounding.None) {
            Line(
                "Эффективная ставка",
                String.format(java.util.Locale("ru"), "%.2f %%", result.effectiveAnnualRatePercent),
                FosColors.TextSecondary,
            )
        }

        result.crossoverYear?.let { year ->
            Column(Modifier.fillMaxWidth().fosInset(FosTone.Positive)) {
                Text(
                    "С $year-го года проценты приносят больше, чем вы вносите",
                    style = FosType.SmallBold,
                    color = FosColors.Positive,
                )
                Text(
                    "С этого момента накопления растут в основном сами.",
                    style = FosType.Micro,
                    color = FosColors.TextSecondary,
                )
            }
        }

        FosExplain(
            "Проценты начисляются каждый месяц на текущую сумму: ставка ÷ 12. " +
                "При капитализации они прибавляются к телу вклада и в следующем месяце сами " +
                "приносят процент — поэтому эффективная ставка выше номинальной.\n\n" +
                "«В сегодняшних деньгах» — итог, поделённый на инфляцию за срок: столько " +
                "накопленное будет стоить по нынешним ценам.\n\n" +
                "Налог считается простыми 13 % от процентного дохода и НЕ вычитается из итога: " +
                "НДФЛ с процентов начисляет налоговая по итогам года, и платите вы его отдельно, " +
                "а не со счёта. Строка «останется после налога» показывает, сколько денег будет " +
                "по-настоящему вашими.\n\n" +
                "Настоящий НДФЛ с вкладов берётся только с суммы сверх необлагаемого минимума " +
                "(он привязан к ключевой ставке), поэтому реальный налог будет МЕНЬШЕ показанного, " +
                "а не больше."
        )
    }
}

@Composable
private fun Line(label: String, value: String, color: Color) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, style = FosType.Body, color = FosColors.TextSecondary)
        Spacer(Modifier.width(12.dp))
        Text(value, style = FosType.SmallBold, color = color)
    }
}

@Composable
private fun LegendDot(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, style = FosType.Micro, color = FosColors.TextMuted, maxLines = 1)
    }
}

// ── Тонкая настройка ─────────────────────────────────────────────────────────

@Composable
private fun AdvancedBlock(input: CalcInputs) {
    Column(
        modifier            = Modifier.fillMaxWidth().fosCard(FosCardStyle.Sunken),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Капитализация", style = FosType.SectionCap, color = FosColors.TextMuted)
        Text(
            "Как часто банк прибавляет начисленные проценты к телу вклада.",
            style = FosType.Micro, color = FosColors.TextMuted,
        )
        Row(
            modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SavingsMath.Compounding.entries.forEach { c ->
                FilterChip(
                    selected = input.compounding == c,
                    onClick  = { input.compounding = c },
                    label    = { Text(compoundingLabel(c), style = FosType.Micro) },
                    colors   = calcChipColors(),
                    border   = null,
                )
            }
        }

        Text("Когда вносите", style = FosType.SectionCap, color = FosColors.TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SavingsMath.Timing.entries.forEach { t ->
                FilterChip(
                    selected = input.timing == t,
                    onClick  = { input.timing = t },
                    label    = {
                        Text(
                            if (t == SavingsMath.Timing.Start) "В начале месяца" else "В конце месяца",
                            style = FosType.Micro,
                        )
                    },
                    colors = calcChipColors(),
                    border = null,
                )
            }
        }

        PercentField(
            value    = input.growth,
            onChange = { input.growth = FosFormatter.sanitizeAmountInput(it) },
            label    = "Индексация взноса, % в год",
            hint     = "Каждый год откладываете на столько процентов больше. Пусто — поровну.",
        )
        PercentField(
            value    = input.inflation,
            onChange = { input.inflation = FosFormatter.sanitizeAmountInput(it) },
            label    = "Инфляция, % в год",
            hint     = "Нужна только чтобы показать итог в сегодняшних деньгах.",
        )

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Показывать НДФЛ 13 %", style = FosType.BodySemi, color = FosColors.TextPrimary)
                Text(
                    "Налог платится отдельно и вклад не уменьшает, поэтому итог остаётся прежним — " +
                        "в разборе появляется строка «останется после налога». Упрощённо, без " +
                        "необлагаемого минимума.",
                    style = FosType.Micro, color = FosColors.TextMuted,
                )
            }
            Switch(
                checked         = input.taxOn,
                onCheckedChange = { input.taxOn = it },
                colors          = SwitchDefaults.colors(
                    checkedThumbColor   = FosColors.Background,
                    checkedTrackColor   = FosColors.Positive,
                    uncheckedThumbColor = FosColors.TextMuted,
                    uncheckedTrackColor = FosColors.Surface2,
                ),
            )
        }
    }
}

// ── Поля ─────────────────────────────────────────────────────────────────────

@Composable
private fun MoneyField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value                = value,
        onValueChange        = onChange,
        visualTransformation = AmountVisualTransformation,
        label                = { Text(label, style = FosType.Label) },
        singleLine           = true,
        keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        colors               = accountSheetFieldColors(),
        modifier             = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PercentField(value: String, onChange: (String) -> Unit, label: String, hint: String?) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onChange,
        label           = { Text(label, style = FosType.Label) },
        supportingText  = hint?.let { { Text(it, style = FosType.Micro, color = FosColors.TextMuted) } },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        colors          = accountSheetFieldColors(),
        modifier        = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IntField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onChange,
        label           = { Text(label, style = FosType.Label) },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
        colors          = accountSheetFieldColors(),
        modifier        = modifier,
    )
}

@Composable
private fun calcChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = FosColors.Info.copy(alpha = 0.15f),
    selectedLabelColor     = FosColors.Info,
    containerColor         = FosColors.Surface2,
    labelColor             = FosColors.TextSecondary,
)

// ── Мелочи ───────────────────────────────────────────────────────────────────

/** Поле «лет»/«месяцев» принимает только цифры и не даёт ввести год длиной в пять знаков. */
private fun digitsOnly(raw: String, max: Int): String =
    raw.filter { it.isDigit() }.take(max)

private fun compoundingLabel(c: SavingsMath.Compounding): String = when (c) {
    SavingsMath.Compounding.Monthly   -> "Ежемесячно"
    SavingsMath.Compounding.Quarterly -> "Раз в квартал"
    SavingsMath.Compounding.Annually  -> "Раз в год"
    SavingsMath.Compounding.None      -> "Без капитализации"
}

private fun pluralYears(n: Int): String {
    val mod100 = n % 100
    val mod10  = n % 10
    return when {
        mod100 in 11..14 -> "лет"
        mod10 == 1       -> "год"
        mod10 in 2..4    -> "года"
        else             -> "лет"
    }
}

private fun pluralMonths(n: Int): String {
    val mod100 = n % 100
    val mod10  = n % 10
    return when {
        mod100 in 11..14 -> "месяцев"
        mod10 == 1       -> "месяц"
        mod10 in 2..4    -> "месяца"
        else             -> "месяцев"
    }
}

/** «26 месяцев» читается хуже, чем «2 года 2 мес.» — на длинных сроках это заметно. */
internal fun monthsLabel(months: Int): String {
    if (months <= 0) return "0 месяцев"
    val y = months / 12
    val m = months % 12
    return when {
        y == 0 -> "$m ${pluralMonths(m)}"
        m == 0 -> "$y ${pluralYears(y)}"
        else   -> "$y ${pluralYears(y)} $m мес."
    }
}
