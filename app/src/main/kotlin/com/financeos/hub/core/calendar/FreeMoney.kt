package com.financeos.hub.core.calendar

import java.time.LocalDate

/**
 * «Свободно» — сколько можно потратить, ничего не сломав.
 *
 * Это не остаток и не прогноз, а третье число, которого в приложении до сих пор не было. Остаток
 * отвечает «сколько у меня есть», прогноз — «сколько я потрачу к концу месяца». Ни то, ни другое не
 * годится в магазине: чтобы решить, можно ли купить, нужно знать, что уже обещано другим.
 *
 * ```
 * Свободно = деньги на счетах − незакрытые обязательства до горизонта − резерв
 * ```
 *
 * Четыре решения, каждое из которых можно было принять иначе:
 *
 * 1. **Только CASH-счета.** Кредитный лимит — деньги банка, а не ваши. Инвариант #12 уже требует
 *    этого от нетто-капитала, виджета и подушки в оценке; «Свободно» подчиняется тому же правилу.
 *
 * 2. **Ожидаемые поступления НЕ прибавляются.** Считать неполученную зарплату тратимой — прямой
 *    путь к перерасходу, а «Свободно» существует ровно для того, чтобы его не было. Сумма всё же
 *    считается и лежит в [FreeMoneyBreakdown.expectedIncomeKopecks], но отдельной строкой: видеть
 *    её полезно, складывать — нет.
 *
 * 3. **Не всякое событие вычитается.** Только с `affectsFree = true`. Конец беспроцентного периода
 *    и срок цели — даты, а не списания.
 *
 * 4. **Валюты не смешиваются.** Курса у приложения нет, оно работает офлайн. Обязательства в чужой
 *    валюте не пересчитываются и не выбрасываются — они уходят в
 *    [FreeMoneyBreakdown.foreignObligations], чтобы экран мог о них сказать. Молча проигнорировать
 *    долларовую подписку значило бы завысить свободные деньги.
 */
object FreeMoney {

    data class FreeMoneyBreakdown(
        val currency              : String,
        /** Остатки CASH-счетов в этой валюте. */
        val onAccountsKopecks     : Long,
        /** Незакрытые обязательства до горизонта, которые двигают деньги. */
        val obligationsKopecks    : Long,
        val reserveKopecks        : Long,
        /**
         * Сколько обязательств попало в [obligationsKopecks]. Считается ЗДЕСЬ, а не на экране:
         * посчитанные отдельно, число и сумма разъезжаются — «учтено 2 платежа на 0 ₽», если одно
         * из них в другой валюте.
         */
        val obligationCount       : Int,
        /** Ожидаемые поступления. Показываются, но в [freeKopecks] НЕ входят. */
        val expectedIncomeKopecks : Long,
        val horizon               : LocalDate,
        /** Сколько дней осталось до горизонта, включая сегодня. Минимум 1. */
        val daysLeft              : Int,
        /** Обязательства в других валютах: `код → сумма`. Не сложены ни с чем. */
        val foreignObligations    : Map<String, Long>,
    ) {
        val freeKopecks: Long
            get() = onAccountsKopecks - obligationsKopecks - reserveKopecks

        /**
         * Сколько можно тратить в день, чтобы дожить до горизонта. `null`, если свободных денег
         * нет: «тратьте по −400 ₽ в день» — не совет, а издевательство.
         */
        val dailyAllowanceKopecks: Long?
            get() = freeKopecks.takeIf { it > 0 }?.div(daysLeft)
    }

    /**
     * @param onAccountsKopecks сумма CASH-остатков в [currency].
     * @param events события календаря от сегодня до [horizon] включительно.
     * @param today нужен, чтобы посчитать оставшиеся дни; события раньше сегодняшнего дня
     *              учитываются как просроченные — деньги по ним всё ещё должны уйти.
     */
    fun compute(
        currency          : String,
        onAccountsKopecks : Long,
        events            : List<CalendarEvent>,
        reserveKopecks    : Long,
        today             : LocalDate,
        horizon           : LocalDate,
    ): FreeMoneyBreakdown {
        val live = events.filter { it.affectsFree && !it.settled && !it.date.isAfter(horizon) }

        val due = live.filter { it.direction == EventDirection.OUT && it.currency == currency }
        val obligations = due.sumOf { it.amountKopecks }

        val expectedIncome = live
            .filter { it.direction == EventDirection.IN && it.currency == currency }
            .sumOf { it.amountKopecks }

        val foreign = live
            .filter { it.direction == EventDirection.OUT && it.currency != currency }
            .groupBy { it.currency }
            .mapValues { (_, list) -> list.sumOf { it.amountKopecks } }

        // Горизонт в прошлом означал бы деление на ноль или отрицательное число дней; берём минимум
        // один день — «всё нужно прожить сегодня».
        val days = (java.time.temporal.ChronoUnit.DAYS.between(today, horizon).toInt() + 1)
            .coerceAtLeast(1)

        return FreeMoneyBreakdown(
            currency              = currency,
            onAccountsKopecks     = onAccountsKopecks,
            obligationsKopecks    = obligations,
            reserveKopecks        = reserveKopecks,
            obligationCount       = due.size,
            expectedIncomeKopecks = expectedIncome,
            horizon               = horizon,
            daysLeft              = days,
            foreignObligations    = foreign,
        )
    }

    /**
     * До какой даты считаем.
     *
     * По умолчанию — до следующего ожидаемого ПОСТУПЛЕНИЯ: честная формулировка вопроса не «сколько
     * до конца месяца», а «на сколько должно хватить до следующих денег». Если поступлений в
     * календаре нет, откатываемся к концу текущего месяца — это хотя бы понятный человеку рубеж.
     */
    fun defaultHorizon(events: List<CalendarEvent>, today: LocalDate): LocalDate {
        events
            // Закрытые исключаются ровно так же, как в [compute]. Зарплата, сопоставленная на пару
            // дней раньше срока, иначе схлопывала бы горизонт на свою же дату: из расчёта выпадал
            // бы весь остаток месяца, и «Свободно» показывало бы завышенное число — сразу после
            // получки, когда на счёте максимум и тратить хочется больше всего.
            .filter { it.direction == EventDirection.IN && !it.settled && it.date.isAfter(today) }
            .minByOrNull { it.date }
            ?.let { return it.date }

        // Конец текущего месяца — но только пока он ВПЕРЕДИ. Тридцать первого числа он совпадает с
        // сегодня, окно схлопывается в один день, все обязательства выпадают из расчёта, и
        // «Свободно» разово показывает весь остаток. Раз в месяц, и именно в тот день, когда
        // платежей больше всего.
        val endOfThis = today.withDayOfMonth(today.lengthOfMonth())
        if (endOfThis.isAfter(today)) return endOfThis
        val next = today.plusMonths(1)
        return next.withDayOfMonth(next.lengthOfMonth())
    }
}
