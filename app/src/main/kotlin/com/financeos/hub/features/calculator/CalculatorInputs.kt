package com.financeos.hub.features.calculator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.financeos.hub.core.finance.SavingsMath
import com.financeos.hub.ui.theme.FosFormatter

/**
 * Что именно спрашивает калькулятор. Три режима — это три разные неизвестные в ОДНОМ уравнении,
 * а не три разных калькулятора: набор полей отличается ровно тем, какое из них стало ответом.
 */
enum class CalcMode(val title: String, val question: String) {
    Grow        ("Что накопится", "Сколько будет через выбранный срок"),
    Time        ("Сколько копить", "Через сколько наберётся нужная сумма"),
    Contribution("Сколько откладывать", "Какой взнос приведёт к цели в срок"),
}

/**
 * Ввод хранится СТРОКАМИ, а не числами.
 *
 * Это не небрежность, а требование: поле денег форматируется через
 * [com.financeos.hub.ui.theme.AmountVisualTransformation], и стоит начать хранить `Long` и
 * форматировать `value` — каретка уезжает, а «12345» превращается в «12354». Разбор происходит
 * один раз, при построении плана.
 */
@Stable
class CalcInputs(
    // Имена параметров намеренно НЕ совпадают с именами свойств: `var mode by mutableStateOf(mode)`
    // читается двусмысленно, и цена ошибки здесь — поле, инициализированное само собой.
    startMode       : CalcMode = CalcMode.Grow,
    startInitial    : String = "",
    startMonthly    : String = "10000",
    startYears      : String = "5",
    startExtraMonths: String = "",
    startRate       : String = "16",
    startTarget     : String = "",
    startCompounding: SavingsMath.Compounding = SavingsMath.Compounding.Monthly,
    startTiming     : SavingsMath.Timing = SavingsMath.Timing.End,
    startGrowth     : String = "",
    startInflation  : String = "",
    startTaxOn      : Boolean = false,
    startAdvanced   : Boolean = false,
) {
    var mode        by mutableStateOf(startMode)
    var initial     by mutableStateOf(startInitial)
    var monthly     by mutableStateOf(startMonthly)
    var years       by mutableStateOf(startYears)
    var extraMonths by mutableStateOf(startExtraMonths)
    var rate        by mutableStateOf(startRate)
    var target      by mutableStateOf(startTarget)
    var compounding by mutableStateOf(startCompounding)
    var timing      by mutableStateOf(startTiming)
    var growth      by mutableStateOf(startGrowth)
    var inflation   by mutableStateOf(startInflation)
    var taxOn       by mutableStateOf(startTaxOn)
    var advancedOpen by mutableStateOf(startAdvanced)

    val initialKopecks: Long get() = FosFormatter.parseAmountInput(initial) ?: 0L
    val monthlyKopecks: Long get() = FosFormatter.parseAmountInput(monthly) ?: 0L
    val targetKopecks : Long get() = FosFormatter.parseAmountInput(target)  ?: 0L

    /** Срок в месяцах. Поля «лет» и «мес.» складываются, чтобы «1 год 6 мес.» вводилось как есть. */
    val months: Int
        get() = ((years.toIntOrNull() ?: 0) * 12 + (extraMonths.toIntOrNull() ?: 0))
            .coerceIn(0, SavingsMath.MAX_MONTHS)

    /** Ставка/индексация/инфляция вводятся процентами, а живут базисными пунктами: «16,5» → 1650. */
    private fun bp(raw: String): Int =
        ((FosFormatter.parseAmountInput(raw) ?: 0L).coerceIn(0L, 100_00L)).toInt()

    val rateBp     : Int get() = bp(rate)
    val growthBp   : Int get() = bp(growth)
    val inflationBp: Int get() = bp(inflation)

    /**
     * План для прямого расчёта. В режимах [CalcMode.Time] и [CalcMode.Contribution] неизвестное
     * поле подставляется вызывающим кодом уже после того, как обратная задача его нашла.
     */
    fun plan(
        monthsOverride : Int? = null,
        monthlyOverride: Long? = null,
    ) = SavingsMath.Plan(
        initialKopecks       = initialKopecks,
        monthlyKopecks       = monthlyOverride ?: monthlyKopecks,
        months               = monthsOverride ?: months,
        annualRateBp         = rateBp,
        compounding          = compounding,
        timing               = timing,
        contributionGrowthBp = growthBp,
        inflationBp          = inflationBp,
        taxBp                = if (taxOn) NDFL_BP else 0,
    )

    companion object {
        /** НДФЛ с процентного дохода. Необлагаемый минимум не моделируется — см. экран. */
        const val NDFL_BP = 1300

        /**
         * Пережить поворот экрана. Через [listSaver], а не самодельный `Saver`: сохраняются только
         * строки и флаги, каждый из которых Bundle умеет положить. Порядок фиксирован — перестановка
         * молча подставит ставку в поле суммы, и заметить это будет нечем.
         *
         * Сами перечисления едут именами: `ordinal` сломается при добавлении режима в середину.
         */
        val Saver = listSaver<CalcInputs, Any>(
            save = {
                listOf(
                    it.mode.name, it.initial, it.monthly, it.years, it.extraMonths, it.rate,
                    it.target, it.compounding.name, it.timing.name, it.growth, it.inflation,
                    it.taxOn, it.advancedOpen,
                )
            },
            restore = {
                CalcInputs(
                    startMode        = CalcMode.valueOf(it[0] as String),
                    startInitial     = it[1] as String,
                    startMonthly     = it[2] as String,
                    startYears       = it[3] as String,
                    startExtraMonths = it[4] as String,
                    startRate        = it[5] as String,
                    startTarget      = it[6] as String,
                    startCompounding = SavingsMath.Compounding.valueOf(it[7] as String),
                    startTiming      = SavingsMath.Timing.valueOf(it[8] as String),
                    startGrowth      = it[9] as String,
                    startInflation   = it[10] as String,
                    startTaxOn       = it[11] as Boolean,
                    startAdvanced    = it[12] as Boolean,
                )
            },
        )
    }
}

@Composable
fun rememberCalcInputs(): CalcInputs = rememberSaveable(saver = CalcInputs.Saver) { CalcInputs() }

/** Ответ калькулятора: главное число + полная раскладка того же плана. */
sealed interface CalcAnswer {
    /** Итоговая сумма (режим «что накопится»). */
    data class Amount(val kopecks: Long, val result: SavingsMath.Result) : CalcAnswer
    /** Срок (режим «сколько копить»). */
    data class Duration(val months: Int, val result: SavingsMath.Result) : CalcAnswer
    /** Ежемесячный взнос (режим «сколько откладывать»). */
    data class Monthly(val kopecks: Long, val result: SavingsMath.Result) : CalcAnswer
    /** Ответа нет, и вот почему — вместо прочерка без объяснения. */
    data class Impossible(val reason: String) : CalcAnswer
}

/** Единственная точка, где ввод превращается в ответ. Чистая функция — считается в `remember`. */
fun solve(input: CalcInputs): CalcAnswer = when (input.mode) {
    CalcMode.Grow -> {
        if (input.months <= 0) CalcAnswer.Impossible("Укажите срок больше нуля.")
        else {
            val r = SavingsMath.project(input.plan())
            CalcAnswer.Amount(r.finalKopecks, r)
        }
    }

    CalcMode.Time -> {
        val target = input.targetKopecks
        when {
            target <= 0L -> CalcAnswer.Impossible("Укажите, какую сумму нужно накопить.")
            else -> {
                val months = SavingsMath.monthsToReach(target, input.plan())
                if (months == null) {
                    CalcAnswer.Impossible(
                        if (input.monthlyKopecks <= 0L && input.rateBp <= 0)
                            "Без взносов и без ставки сумма не растёт — накопить не получится."
                        else
                            "При таком взносе цель не наберётся и за 50 лет. " +
                                "Увеличьте взнос или уменьшите цель."
                    )
                } else {
                    CalcAnswer.Duration(months, SavingsMath.project(input.plan(monthsOverride = months)))
                }
            }
        }
    }

    CalcMode.Contribution -> {
        val target = input.targetKopecks
        when {
            target <= 0L      -> CalcAnswer.Impossible("Укажите, какую сумму нужно накопить.")
            input.months <= 0 -> CalcAnswer.Impossible("Укажите срок больше нуля.")
            else -> {
                val need = SavingsMath.requiredMonthly(target, input.plan())
                if (need == null) CalcAnswer.Impossible("При таких условиях цель недостижима.")
                else CalcAnswer.Monthly(need, SavingsMath.project(input.plan(monthlyOverride = need)))
            }
        }
    }
}
