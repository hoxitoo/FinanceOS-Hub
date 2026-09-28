package com.financeos.hub.core.analytics

import com.financeos.hub.core.database.entities.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * «Месяц к месяцу»: каждый месяц окна против СВОЕГО предыдущего.
 *
 * Сравнение идёт через границу года — январь сравнивается с декабрём прошлого года. Поэтому окно
 * всегда тянет на месяц больше, чем показывает: у первого бара предыдущий месяц лежит за краем
 * окна, и без него первому бару было бы не с чем сравниваться.
 *
 * **Полный месяц сравнивается с полным.** Текущий месяц не закончен, и его цифра сравнивается с
 * полным предыдущим честно — как есть, — а сам месяц помечается [MonthBar.isComplete] = false.
 * Экран обязан это сказать: иначе почти каждый конец графика выглядел бы «экономией», которой нет.
 *
 * Правила — те же, что у «За всё время» ([LifetimeStats]): переводы не считаются, валюты не
 * складываются (всё в [LifetimeStats.primaryCurrency]), трата без категории — «Другое», будущие
 * даты — ошибка ввода и отбрасываются.
 */
object MonthOverMonth {

    /** Сколько месяцев показывать. Считается всегда год — шесть месяцев это его хвост. */
    enum class Window(val months: Int, val label: String) {
        SIX(6, "6 мес"),
        YEAR(12, "Год"),
    }

    data class CategoryDelta(val categoryId: String?, val current: Long, val previous: Long)

    data class MonthBar(
        val month     : YearMonth,
        val kopecks   : Long,
        /** Тот же показатель за предыдущий месяц — даже если он лежит за краем окна. */
        val previous  : Long,
        /** Месяц закончился. У текущего — false: цифра ещё будет расти. */
        val isComplete: Boolean,
        /** Разбивка по категориям — для касания по бару. Крупные изменения первыми. */
        val categories: List<CategoryDelta>,
    ) {
        val delta: Long get() = kopecks - previous

        /**
         * Изменение в процентах; `null`, когда в прошлом месяце было ноль — «рост на бесконечность
         * процентов» ничего не сообщает, и экран пишет «новое».
         */
        val deltaPercent: Int? get() =
            if (previous == 0L) null else (delta * 100.0 / previous).roundToInt()
    }

    data class Result(
        val currency: String,
        val spent   : List<MonthBar>,
        val earned  : List<MonthBar>,
        /** Сколько дней текущего месяца уже прошло и сколько в нём всего — для подписи. */
        val daysPassed: Int,
        val daysInMonth: Int,
    ) {
        /** Последние [window].months баров — окно «6 мес» это хвост года. */
        fun spentIn(window: Window)  = spent.takeLast(window.months)
        fun earnedIn(window: Window) = earned.takeLast(window.months)
    }

    /** Самое длинное окно; короткие берутся его хвостом. */
    private val LONGEST = Window.entries.maxOf { it.months }

    fun compute(
        entries: List<LifetimeStats.Entry>,
        today  : LocalDate,
        zone   : ZoneId = ZoneId.systemDefault(),
    ): Result {
        val current = YearMonth.from(today)
        // На месяц больше окна: у первого бара должен быть свой «предыдущий».
        val first   = current.minusMonths(LONGEST.toLong())

        fun monthOf(e: LifetimeStats.Entry) =
            YearMonth.from(Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate())

        val inRange = entries.filter { e ->
            e.type != TransactionType.TRANSFER &&
                !Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate().isAfter(today) &&
                !monthOf(e).isBefore(first)
        }
        val currency = LifetimeStats.primaryCurrency(LifetimeStats.totals(inRange))
        val primary  = inRange.filter { it.currency == currency }

        return Result(
            currency    = currency,
            spent       = bars(primary.filter { it.type == TransactionType.EXPENSE }, current, ::monthOf),
            earned      = bars(primary.filter { it.type == TransactionType.INCOME }, current, ::monthOf),
            daysPassed  = today.dayOfMonth,
            daysInMonth = current.lengthOfMonth(),
        )
    }

    private fun bars(
        list   : List<LifetimeStats.Entry>,
        current: YearMonth,
        monthOf: (LifetimeStats.Entry) -> YearMonth,
    ): List<MonthBar> {
        val byMonth = list.groupBy(monthOf)
        fun total(m: YearMonth) = byMonth[m].orEmpty().sumOf { abs(it.amountKopecks) }
        fun byCategory(m: YearMonth): Map<String?, Long> = byMonth[m].orEmpty()
            .groupBy { it.categoryId }
            .mapValues { (_, rows) -> rows.sumOf { abs(it.amountKopecks) } }

        // Пустые месяцы остаются барами нулевой высоты: выбросить месяц без трат значило бы сдвинуть
        // соседей и сравнить март с январём, выдав это за «месяц к месяцу».
        return (LONGEST - 1 downTo 0).map { back ->
            val month = current.minusMonths(back.toLong())
            val prev  = month.minusMonths(1)
            val now   = byCategory(month)
            val was   = byCategory(prev)
            MonthBar(
                month      = month,
                kopecks    = total(month),
                previous   = total(prev),
                isComplete = month != current,
                categories = (now.keys + was.keys)
                    .map { CategoryDelta(it, now[it] ?: 0L, was[it] ?: 0L) }
                    .sortedByDescending { abs(it.current - it.previous) },
            )
        }
    }
}
