package com.financeos.hub.core.calendar

import com.financeos.hub.core.analytics.SubscriptionDetector
import com.financeos.hub.core.credit.DuePayment
import com.financeos.hub.core.credit.InterestFreeWindow
import com.financeos.hub.core.credit.PaymentSource
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.core.database.entities.PaymentDirection
import com.financeos.hub.core.database.entities.PlannedPaymentEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Сведение календаря из источников.
 *
 * Каждый источник — отдельная функция, и это не декоративное разделение: у источников разная
 * надёжность, разные правила «просрочено» и разный ответ на вопрос, двигает ли событие деньги.
 * Свалив их в один цикл, эти различия неизбежно потеряешь.
 *
 * Функции чистые и принимают уже загруженные данные — так же, как [SubscriptionDetector] и
 * `SavingsMath`. Загрузка живёт во ViewModel, здесь только правила.
 *
 * Добавить инвестиции = дописать `fromInvestments(...)` и вызвать её в [build]. Ни модель события,
 * ни расчёт «Свободно», ни экран при этом не трогаются.
 */
object CalendarBuilder {

    /** Кредитка, приведённая к тому минимуму, который нужен календарю. */
    data class CreditObligation(
        val accountId   : String,
        val title       : String,
        val duePayment  : DuePayment?,
        val interestFree: InterestFreeWindow?,
    )

    fun build(
        planned      : List<PlannedPaymentEntity>,
        credit       : List<CreditObligation>,
        subscriptions: List<SubscriptionDetector.Subscription>,
        goals        : List<GoalEntity>,
        from         : LocalDate,
        to           : LocalDate,
        zone         : ZoneId = ZoneId.systemDefault(),
        /**
         * Ключи подписок, уже подтверждённых как обязательства — включая удалённые. Считать их по
         * активным строкам недостаточно: удалённое обязательство иначе всплывает обратно и как
         * предложение, и как выведенное событие.
         */
        claimed      : Set<String> = emptySet(),
    ): List<CalendarEvent> =
        (fromPlanned(planned, from, to, zone) +
            fromCredit(credit, from, to) +
            fromSubscriptions(subscriptions, claimed, from, to, zone) +
            fromGoals(goals, from, to, zone))
            .sortedWith(compareBy({ it.date }, { it.kind.ordinal }, { -it.amountKopecks }))

    // ── Объявленные платежи ──────────────────────────────────────────────────────

    fun fromPlanned(
        payments: List<PlannedPaymentEntity>,
        from    : LocalDate,
        to      : LocalDate,
        zone    : ZoneId = ZoneId.systemDefault(),
    ): List<CalendarEvent> = payments
        .filter { it.isActive }
        .flatMap { p ->
            val settledThrough = p.matchedThrough
                ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            PaymentDates.occurrencesIn(p, from, to, zone).map { date ->
                CalendarEvent(
                    id            = "planned:${p.id}:$date",
                    date          = date,
                    title         = p.title,
                    amountKopecks = p.amountKopecks,
                    currency      = p.currency,
                    direction     = if (p.direction == PaymentDirection.OUT) EventDirection.OUT
                                    else EventDirection.IN,
                    kind          = EventKind.PLANNED,
                    confidence    = EventConfidence.DECLARED,
                    affectsFree   = true,
                    accountId     = p.accountId,
                    categoryId    = p.categoryId,
                    sourceId      = p.id,
                    // Закрытым считается период НЕ ПОЗЖЕ отметки: более поздние повторения ещё
                    // впереди и из «Свободно» выпадать не должны.
                    settled       = settledThrough != null && !date.isAfter(settledThrough),
                )
            }
        }

    // ── Кредитные карты ──────────────────────────────────────────────────────────

    fun fromCredit(
        cards: List<CreditObligation>,
        from : LocalDate,
        to   : LocalDate,
    ): List<CalendarEvent> = cards.flatMap { card ->
        buildList {
            card.duePayment?.let { due ->
                if (due.dueDate in from..to) {
                    add(
                        CalendarEvent(
                            id            = "credit_due:${card.accountId}:${due.dueDate}",
                            date          = due.dueDate,
                            title         = "Платёж · ${card.title}",
                            amountKopecks = due.amountKopecks,
                            currency      = "RUB",
                            direction     = EventDirection.OUT,
                            kind          = EventKind.CREDIT_DUE,
                            confidence    = if (due.source == PaymentSource.BANK) EventConfidence.BANK
                                            else EventConfidence.INFERRED,
                            affectsFree   = true,
                            accountId     = card.accountId,
                            sourceId      = card.accountId,
                        )
                    )
                }
            }
            card.interestFree?.let { window ->
                if (window.deadline in from..to) {
                    add(
                        CalendarEvent(
                            id            = "credit_grace:${card.accountId}:${window.deadline}",
                            date          = window.deadline,
                            title         = "Конец беспроцентного периода · ${card.title}",
                            amountKopecks = window.purchaseKopecks,
                            currency      = "RUB",
                            direction     = EventDirection.OUT,
                            kind          = EventKind.CREDIT_GRACE,
                            confidence    = EventConfidence.INFERRED,
                            // СРОК, а не платёж. Вычесть его из «Свободно» значило бы посчитать один
                            // и тот же долг дважды — он уже сидит в платеже по карте.
                            affectsFree   = false,
                            accountId     = card.accountId,
                            sourceId      = card.accountId,
                        )
                    )
                }
            }
        }
    }

    // ── Подписки ─────────────────────────────────────────────────────────────────

    /**
     * Найденные подписки становятся событиями, только пока их не подтвердили вручную: подтверждённая
     * уже лежит в [PlannedPaymentEntity] и попала бы в календарь дважды.
     */
    fun fromSubscriptions(
        subscriptions: List<SubscriptionDetector.Subscription>,
        claimed      : Set<String>,
        from         : LocalDate,
        to           : LocalDate,
        zone         : ZoneId = ZoneId.systemDefault(),
    ): List<CalendarEvent> {
        return subscriptions
            .filter { it.key !in claimed && !it.isMissed }
            .mapNotNull { sub ->
                val at = sub.nextExpectedAt ?: return@mapNotNull null
                val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
                if (date !in from..to) return@mapNotNull null
                CalendarEvent(
                    id            = "sub:${sub.key}:$date",
                    date          = date,
                    title         = sub.title,
                    amountKopecks = sub.typicalKopecks,
                    currency      = sub.currency,
                    direction     = EventDirection.OUT,
                    kind          = EventKind.SUBSCRIPTION,
                    confidence    = EventConfidence.INFERRED,
                    affectsFree   = true,
                    categoryId    = sub.categoryId,
                    sourceId      = sub.key,
                )
            }
    }

    // ── Цели ─────────────────────────────────────────────────────────────────────

    fun fromGoals(
        goals: List<GoalEntity>,
        from : LocalDate,
        to   : LocalDate,
        zone : ZoneId = ZoneId.systemDefault(),
    ): List<CalendarEvent> = goals
        .filter { !it.isCompleted }
        .mapNotNull { goal ->
            val at   = goal.deadlineAt ?: return@mapNotNull null
            val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
            if (date !in from..to) return@mapNotNull null
            CalendarEvent(
                id            = "goal:${goal.id}:$date",
                date          = date,
                title         = "${goal.emoji} ${goal.name}",
                amountKopecks = (goal.targetKopecks - goal.savedKopecks).coerceAtLeast(0L),
                currency      = "RUB",
                direction     = EventDirection.OUT,
                kind          = EventKind.GOAL,
                confidence    = EventConfidence.DECLARED,
                // Срок цели — это дата, а не списание: деньги никуда не уходят, и уменьшать
                // свободные из-за наступления даты неправильно.
                affectsFree   = false,
                sourceId      = goal.id,
            )
        }
}
