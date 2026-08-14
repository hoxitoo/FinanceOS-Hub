package com.financeos.hub.core.finance

import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Накопления: сколько вырастет, за сколько накопится, сколько нужно откладывать.
 *
 * Всё считается ОДНОЙ помесячной симуляцией — [simulate]. Замкнутая формула аннуитета короче, но
 * она разваливается на первом же реальном требовании: капитализация раз в квартал, взнос в начале
 * месяца, ежегодная индексация взноса. Симуляция описывает эти правила буквально, и её можно
 * проверить, разложив по месяцам на бумаге.
 *
 * Деньги — копейки (`Long`), как везде в приложении. Внутри симуляции — `Double`, округление один
 * раз на выходе: округлять каждый месяц значит копить ошибку округления там, где её быть не должно.
 *
 * Чего эта математика НЕ знает и не делает вид, что знает:
 *  - реальную ставку по вашему вкладу после первой пролонгации (банк её меняет);
 *  - необлагаемый минимум по НДФЛ с процентов (он привязан к ключевой ставке и меняется каждый год);
 *  - что вы пропустите взнос.
 * Поэтому результат — оценка, и экран обязан называть её оценкой.
 */
object SavingsMath {

    /** Дальше 50 лет считать бессмысленно: ставка, инфляция и сам вкладчик изменятся много раз. */
    const val MAX_MONTHS = 600

    /** Как часто банк присоединяет начисленные проценты к телу вклада. */
    enum class Compounding(val monthsPerPeriod: Int) {
        /** Ежемесячно — самый частый вариант у накопительных счетов. */
        Monthly(1),
        Quarterly(3),
        Annually(12),
        /** Без капитализации: проценты копятся отдельно и не приносят своих процентов. */
        None(0),
    }

    /** Взнос в начале месяца успевает заработать процент за этот же месяц, в конце — нет. */
    enum class Timing { Start, End }

    data class Plan(
        val initialKopecks : Long,
        val monthlyKopecks : Long,
        val months         : Int,
        /** Годовая ставка в базисных пунктах: 16,5 % → 1650. Ноль = обычная копилка без процентов. */
        val annualRateBp   : Int = 0,
        val compounding    : Compounding = Compounding.Monthly,
        val timing         : Timing      = Timing.End,
        /** Ежегодная индексация взноса в б.п.: откладывать на 10 % больше каждый год → 1000. */
        val contributionGrowthBp: Int = 0,
        /** Годовая инфляция в б.п. — только для пересчёта итога в сегодняшние деньги. */
        val inflationBp    : Int = 0,
        /** НДФЛ на процентный доход в б.п.: 13 % → 1300. Ноль — не считать налог. */
        val taxBp          : Int = 0,
    )

    data class YearPoint(
        val year                       : Int,
        val balanceKopecks             : Long,
        val contributedTotalKopecks    : Long,
        val interestTotalKopecks       : Long,
        val contributedThisYearKopecks : Long,
        val interestThisYearKopecks    : Long,
    )

    data class Result(
        /** Сколько будет на счёте в конце срока. */
        val finalKopecks        : Long,
        /** Сколько из этого — ваши деньги (стартовая сумма + все взносы). */
        val contributedKopecks  : Long,
        /** Сколько заработали проценты. */
        val interestKopecks     : Long,
        val taxKopecks          : Long,
        /** Проценты за вычетом налога. */
        val netInterestKopecks  : Long,
        /** Итог в сегодняшних деньгах: столько это будет стоить по нынешним ценам. */
        val realFinalKopecks    : Long,
        /** Эффективная годовая ставка с учётом капитализации — она выше номинальной. */
        val effectiveAnnualRatePercent: Double,
        /** Первый год, в котором проценты за год превысили ваши взносы за год. null — не наступил. */
        val crossoverYear       : Int?,
        val schedule            : List<YearPoint>,
    )

    // ── Прямая задача: что накопится ─────────────────────────────────────────────

    fun project(plan: Plan): Result {
        val months = plan.months.coerceIn(0, MAX_MONTHS)
        val sim    = simulate(plan.copy(months = months))

        val finalK       = sim.balance.roundToLong()
        val contributedK = sim.contributed.roundToLong()
        // Из округлённых величин, а не из округления разности: иначе итог и разбивка под ним
        // расходятся на копейку, и это первое, что замечает глаз.
        val interestK    = finalK - contributedK
        val taxK         = if (interestK > 0) (interestK * plan.taxBp / 10_000.0).roundToLong() else 0L

        val years    = months / 12.0
        val realK    = if (plan.inflationBp > 0 && years > 0)
            (finalK / (1 + plan.inflationBp / 10_000.0).pow(years)).roundToLong()
        else finalK

        return Result(
            finalKopecks        = finalK,
            contributedKopecks  = contributedK,
            interestKopecks     = interestK,
            taxKopecks          = taxK,
            netInterestKopecks  = interestK - taxK,
            realFinalKopecks    = realK,
            effectiveAnnualRatePercent = effectiveAnnualRatePercent(plan.annualRateBp, plan.compounding),
            crossoverYear       = sim.schedule.firstOrNull {
                it.interestThisYearKopecks > it.contributedThisYearKopecks
            }?.year,
            schedule            = sim.schedule,
        )
    }

    /**
     * Эффективная годовая ставка: 16 % с ежемесячной капитализацией — это 17,23 % в год.
     * Без капитализации эффективная равна номинальной.
     */
    fun effectiveAnnualRatePercent(annualRateBp: Int, compounding: Compounding): Double {
        val nominal = annualRateBp / 100.0
        if (annualRateBp <= 0 || compounding == Compounding.None) return nominal
        val n = 12.0 / compounding.monthsPerPeriod
        return ((1 + (annualRateBp / 10_000.0) / n).pow(n) - 1) * 100.0
    }

    // ── Обратная задача 1: за сколько накопится нужная сумма ─────────────────────

    /**
     * Сколько месяцев копить до [targetKopecks]. `null` — не накопится никогда: без взносов и без
     * ставки сумма не растёт, а с крошечным взносом упирается в потолок [MAX_MONTHS].
     *
     * Возвращает 0, если стартовой суммы уже достаточно.
     */
    fun monthsToReach(targetKopecks: Long, plan: Plan): Int? {
        if (targetKopecks <= plan.initialKopecks) return 0
        if (plan.monthlyKopecks <= 0L && plan.annualRateBp <= 0) return null

        val target = targetKopecks.toDouble()
        var found = 0
        simulate(plan.copy(months = MAX_MONTHS)) { month, balance ->
            if (balance >= target) { found = month; false } else true
        }
        return found.takeIf { it > 0 }
    }

    // ── Обратная задача 2: сколько откладывать в месяц ───────────────────────────

    /**
     * Какой ежемесячный взнос приведёт к [targetKopecks] за `plan.months`.
     *
     * Итог линейно зависит от взноса при прочих равных (проценты начисляются на сумму, а сумма —
     * сумма вкладов), поэтому хватает двух прогонов: без взноса и с пробным. Подбор половинным
     * делением дал бы тот же ответ за двадцать прогонов.
     *
     * `null` — недостижимо: срок нулевой либо взнос ничего не меняет. 0 — уже достаточно без взносов.
     */
    fun requiredMonthly(targetKopecks: Long, plan: Plan): Long? {
        val months = plan.months.coerceIn(0, MAX_MONTHS)
        if (months <= 0) return null
        val basePlan = plan.copy(months = months)

        val base = simulate(basePlan.copy(monthlyKopecks = 0L)).balance
        if (base >= targetKopecks) return 0L

        val probe = 10_000_00L   // 10 000 ₽ — достаточно крупный шаг, чтобы не ловить шум double
        val slope = (simulate(basePlan.copy(monthlyKopecks = probe)).balance - base) / probe.toDouble()
        if (slope <= 0.0) return null

        return ceil((targetKopecks - base) / slope).toLong().coerceAtLeast(0L)
    }

    // ── Симуляция ────────────────────────────────────────────────────────────────

    private class Sim(
        val balance    : Double,
        val contributed: Double,
        val schedule   : List<YearPoint>,
    )

    /**
     * Помесячный проход. [onMonth] вызывается после каждого месяца с накопленным балансом; вернув
     * `false`, он останавливает симуляцию — так [monthsToReach] находит месяц, не считая всё до конца.
     */
    private fun simulate(
        plan: Plan,
        onMonth: ((month: Int, balance: Double) -> Boolean)? = null,
    ): Sim {
        val monthlyRate = plan.annualRateBp / 10_000.0 / 12.0
        val growth      = plan.contributionGrowthBp / 10_000.0

        // principal — то, на что начисляется процент; pending — начисленное, но ещё не присоединённое.
        // Без капитализации pending так и не попадает в principal и своих процентов не приносит.
        var principal   = plan.initialKopecks.toDouble()
        var pending     = 0.0
        var contributed = plan.initialKopecks.toDouble()

        val schedule = ArrayList<YearPoint>(plan.months / 12 + 1)
        var yearContrib  = 0.0
        var yearInterest = 0.0

        for (month in 1..plan.months) {
            val yearIndex    = (month - 1) / 12
            val contribution = plan.monthlyKopecks * (1 + growth).pow(yearIndex)

            if (plan.timing == Timing.Start) {
                principal   += contribution
                contributed += contribution
                yearContrib += contribution
            }

            val interest = principal * monthlyRate
            pending      += interest
            yearInterest += interest

            if (plan.compounding != Compounding.None &&
                month % plan.compounding.monthsPerPeriod == 0
            ) {
                principal += pending
                pending    = 0.0
            }

            if (plan.timing == Timing.End) {
                principal   += contribution
                contributed += contribution
                yearContrib += contribution
            }

            val balance = principal + pending

            if (month % 12 == 0 || month == plan.months) {
                // Проценты — РАЗНОСТЬ округлённых величин, а не округление разности. Иначе строка
                // таблицы не сходится сама с собой на копейку: «ваши + проценты ≠ итог».
                val balanceK     = balance.roundToLong()
                val contributedK = contributed.roundToLong()
                schedule += YearPoint(
                    year                       = (month + 11) / 12,
                    balanceKopecks             = balanceK,
                    contributedTotalKopecks    = contributedK,
                    interestTotalKopecks       = balanceK - contributedK,
                    contributedThisYearKopecks = yearContrib.roundToLong(),
                    interestThisYearKopecks    = yearInterest.roundToLong(),
                )
                yearContrib  = 0.0
                yearInterest = 0.0
            }

            if (onMonth != null && !onMonth(month, balance)) {
                return Sim(balance, contributed, schedule)
            }
        }

        return Sim(principal + pending, contributed, schedule)
    }
}
