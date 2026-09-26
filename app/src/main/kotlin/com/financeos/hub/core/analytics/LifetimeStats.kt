package com.financeos.hub.core.analytics

import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.MerchantNames
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * «Всего потрачено, всего заработано» — за годы, а не за месяц.
 *
 * Отдельно от [AnalyticsEngine], и не из педантизма: тот отвечает на вопросы этого месяца, и все его
 * окна — дни и недели. Здесь вопрос другой — куда ушли деньги за десять лет, — и шаг другой: месяц,
 * полгода, год. Смешать их значило бы тащить в месячную аналитику всю историю, а в годовую — дневную
 * дробность, которую на таком масштабе глаз не различает.
 *
 * Правила, общие для всего экрана:
 * - **Переводы не считаются** ни тратой, ни заработком. Перекладывание между своими счетами денег не
 *   тратит и не приносит; посчитай их — «заработано» выросло бы на каждое пополнение копилки.
 * - **Валюты не складываются** (курса у офлайн-приложения нет). Итоги — по каждой валюте отдельно;
 *   графики, бары, доли и источники — в рублях. Долларовая подписка не пропадает: она есть в итогах.
 * - Всё считается из одного прохода по операциям, поэтому новая операция сразу меняет все цифры
 *   экрана — «постоянное суммирование», а не пересчёт раз в сутки.
 */
object LifetimeStats {

    const val BASE_CURRENCY = "RUB"

    /** Насколько назад смотреть. Скользящее окно от сегодняшнего дня, а не календарные годы. */
    enum class Horizon(val years: Long?, val label: String) {
        YEAR(1, "Год"),
        TWO_YEARS(2, "2 года"),
        TEN_YEARS(10, "10 лет"),
        TWENTY_YEARS(20, "20 лет"),
        ALL(null, "Всё время"),
    }

    /** Шаг «ползущего» графика. Корзины выровнены по календарю — полугодие это январь и июль. */
    enum class Step(val months: Int, val label: String) {
        MONTH(1, "Месяц"),
        HALF_YEAR(6, "Полгода"),
        YEAR(12, "Год"),
        TWO_YEARS(24, "2 года"),
    }

    /** Одна операция в том виде, который нужен расчёту. */
    data class Entry(
        val timestamp    : Long,
        /** Знаковая сумма, как в базе. Знак здесь не используется — направление берётся из [type]. */
        val amountKopecks: Long,
        val type         : TransactionType,
        val currency     : String,
        val categoryId   : String?,
        /** Продавец или отправитель, как прислал банк; нормализуется через [MerchantNames.display]. */
        val party        : String?,
    )

    data class CurrencyTotal(val currency: String, val spent: Long, val earned: Long) {
        val net: Long get() = earned - spent
    }

    /** Нарастающий итог НА КОНЕЦ корзины, начинающейся с [periodStart]. */
    data class CurvePoint(val periodStart: LocalDate, val spent: Long, val earned: Long)

    data class CategoryShare(val categoryId: String?, val kopecks: Long)

    data class YearBar(val year: Int, val total: Long, val segments: List<CategoryShare>)

    /**
     * Кому ушли или от кого пришли деньги. [label] `null` — продавец не назван (зарплата
     * «Зачисление», перевод без имени), и тогда источником служит категория [categoryId].
     */
    data class SourceTotal(
        val label     : String?,
        val categoryId: String?,
        val kopecks   : Long,
        val count     : Int,
        val lastAt    : Long,
    )

    data class Result(
        val totals          : List<CurrencyTotal>,
        val curve           : List<CurvePoint>,
        val spentByYear     : List<YearBar>,
        val earnedByYear    : List<YearBar>,
        val spentByCategory : List<CategoryShare>,
        val earnedByCategory: List<CategoryShare>,
        val spentSources    : List<SourceTotal>,
        val earnedSources   : List<SourceTotal>,
        /** С какой даты реально есть данные в окне; `null` — операций нет. */
        val firstDate       : LocalDate?,
    ) {
        val isEmpty: Boolean get() = totals.isEmpty()
    }

    fun entriesOf(transactions: List<TransactionEntity>): List<Entry> = transactions
        .filter { !it.isDeleted }
        .map { Entry(it.timestamp, it.amountKopecks, it.type, it.currency, it.categoryId, it.merchant) }

    /** Итоги по валютам — для плитки на экране аналитики, без всего остального расчёта. */
    fun totals(entries: List<Entry>): List<CurrencyTotal> = entries
        .filter { it.type != TransactionType.TRANSFER }
        .groupBy { it.currency }
        .map { (currency, list) ->
            CurrencyTotal(
                currency = currency,
                spent    = list.filter { it.type == TransactionType.EXPENSE }.sumOf { abs(it.amountKopecks) },
                earned   = list.filter { it.type == TransactionType.INCOME }.sumOf { abs(it.amountKopecks) },
            )
        }
        // Рубль первым, остальные — по обороту: главная цифра не должна прыгать от того, в какой
        // валюте была последняя подписка.
        .sortedWith(compareByDescending<CurrencyTotal> { it.currency == BASE_CURRENCY }
            .thenByDescending { it.spent + it.earned })

    fun compute(
        entries: List<Entry>,
        horizon: Horizon,
        step   : Step,
        today  : LocalDate,
        zone   : ZoneId = ZoneId.systemDefault(),
    ): Result {
        val from = horizon.years?.let { today.minusYears(it) }
        fun dateOf(e: Entry) = Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate()

        val inWindow = entries.filter { e ->
            e.type != TransactionType.TRANSFER &&
                // «Год» — это последние двенадцать месяцев, включая сегодняшний день, и не дальше
                // него: операция с будущей датой — ошибка ввода, а не прогноз.
                dateOf(e).let { d -> (from == null || d.isAfter(from)) && !d.isAfter(today) }
        }
        val base   = inWindow.filter { it.currency == BASE_CURRENCY }
        val spent  = base.filter { it.type == TransactionType.EXPENSE }
        val earned = base.filter { it.type == TransactionType.INCOME }

        return Result(
            totals           = totals(inWindow),
            curve            = curve(base, step, today, ::dateOf),
            spentByYear      = byYear(spent, ::dateOf),
            earnedByYear     = byYear(earned, ::dateOf),
            spentByCategory  = byCategory(spent),
            earnedByCategory = byCategory(earned),
            spentSources     = sources(spent),
            earnedSources    = sources(earned),
            firstDate        = inWindow.minOfOrNull { dateOf(it) },
        )
    }

    /**
     * Нарастающие итоги по корзинам. Пустые корзины НЕ пропускаются: месяц без трат на графике —
     * горизонтальный участок, и выбросить его значило бы сжать время и показать рост круче, чем он был.
     */
    private fun curve(
        base  : List<Entry>,
        step  : Step,
        today : LocalDate,
        dateOf: (Entry) -> LocalDate,
    ): List<CurvePoint> {
        if (base.isEmpty()) return emptyList()
        val bucketOf = { d: LocalDate -> bucketStart(d, step) }
        val byBucket = base.groupBy { bucketOf(dateOf(it)) }

        val out = ArrayList<CurvePoint>()
        var cumSpent  = 0L
        var cumEarned = 0L
        var cursor = bucketOf(base.minOf { dateOf(it) })
        val last   = bucketOf(today)
        while (!cursor.isAfter(last)) {
            byBucket[cursor].orEmpty().forEach { e ->
                when (e.type) {
                    TransactionType.EXPENSE  -> cumSpent  += abs(e.amountKopecks)
                    TransactionType.INCOME   -> cumEarned += abs(e.amountKopecks)
                    TransactionType.TRANSFER -> Unit
                }
            }
            out += CurvePoint(cursor, cumSpent, cumEarned)
            cursor = cursor.plusMonths(step.months.toLong())
        }
        return out
    }

    /** Начало корзины: январь/июль для полугодия, чётный год для двухлетки. */
    fun bucketStart(d: LocalDate, step: Step): LocalDate {
        val monthIndex = d.year * 12 + (d.monthValue - 1)
        val aligned    = monthIndex - Math.floorMod(monthIndex, step.months)
        return LocalDate.of(aligned / 12, aligned % 12 + 1, 1)
    }

    private fun byYear(list: List<Entry>, dateOf: (Entry) -> LocalDate): List<YearBar> = list
        .groupBy { dateOf(it).year }
        .map { (year, rows) ->
            val segments = byCategory(rows)
            YearBar(year = year, total = segments.sumOf { it.kopecks }, segments = segments)
        }
        .sortedBy { it.year }

    private fun byCategory(list: List<Entry>): List<CategoryShare> = list
        .groupBy { it.categoryId }
        .map { (cat, rows) -> CategoryShare(cat, rows.sumOf { abs(it.amountKopecks) }) }
        .filter { it.kopecks > 0L }
        .sortedByDescending { it.kopecks }

    /**
     * Источники: продавцы и отправители. Группировка — по ОТОБРАЖАЕМОМУ имени, а не по сырой строке
     * банка: «RECR GOOGLE *ChatGPT, 855-…» и «ChatGPT» — один получатель, и в списке «кому ушли
     * деньги за десять лет» он обязан быть одной строкой, а не двумя с половиной суммы каждая.
     */
    private fun sources(list: List<Entry>): List<SourceTotal> = list
        .groupBy { e ->
            MerchantNames.display(e.party)?.let { "n:${it.lowercase()}" } ?: "c:${e.categoryId}"
        }
        .map { (_, rows) ->
            val latest = rows.maxBy { it.timestamp }
            SourceTotal(
                // Имя — как в последний раз прислал банк: оно узнаваемо.
                label      = MerchantNames.display(latest.party),
                categoryId = latest.categoryId,
                kopecks    = rows.sumOf { abs(it.amountKopecks) },
                count      = rows.size,
                lastAt     = latest.timestamp,
            )
        }
        .sortedByDescending { it.kopecks }
}
