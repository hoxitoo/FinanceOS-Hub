package com.financeos.hub.features.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.analytics.SubscriptionDetector
import com.financeos.hub.core.calendar.CalendarBuilder
import com.financeos.hub.core.calendar.CalendarEvent
import com.financeos.hub.core.calendar.FreeMoney
import com.financeos.hub.core.calendar.ObligationMatcher
import com.financeos.hub.core.calendar.PaymentDates
import com.financeos.hub.core.credit.creditCycle
import com.financeos.hub.core.credit.duePayment
import com.financeos.hub.core.credit.nearestInterestFreeWindow
import com.financeos.hub.core.credit.statementDueDebt
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.AccountKind
import com.financeos.hub.core.database.entities.PaymentDirection
import com.financeos.hub.core.database.entities.PaymentSchedule
import com.financeos.hub.core.database.entities.PlannedPaymentEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.data.preferences.UserPreferences
import com.financeos.hub.data.repositories.AccountRepository
import com.financeos.hub.data.repositories.GoalRepository
import com.financeos.hub.data.repositories.PlannedPaymentRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

data class CalendarState(
    val isLoading  : Boolean = true,
    val free       : FreeMoney.FreeMoneyBreakdown? = null,
    /** События от сегодня до горизонта, по возрастанию даты. */
    val upcoming   : List<CalendarEvent> = emptyList(),
    /**
     * Всё окно построения — шире горизонта и в обе стороны от сегодня. Нужно сетке месяца: горизонт
     * обычно короче месяца (до следующей зарплаты), и по [upcoming] сетка была бы пустой с середины.
     */
    val all        : List<CalendarEvent> = emptyList(),
    /** Границы окна: дальше них у сетки просто нет данных, и листать туда нечего. */
    val windowFrom : LocalDate = LocalDate.now(),
    val windowTo   : LocalDate = LocalDate.now(),
    /** Уже закрытые обязательства за последний месяц — чтобы было видно, что сопоставление работает. */
    val settled    : List<CalendarEvent> = emptyList(),
    /** Найденные, но не подтверждённые подписки: «похоже на регулярный платёж». */
    val suggestions: List<SubscriptionDetector.Subscription> = emptyList(),
    /** Свои счета — для формы: к какому счёту привязать обязательство. */
    val accounts   : List<AccountEntity> = emptyList(),
    /** Объявленные обязательства, чтобы открыть строку на редактирование по её sourceId. */
    val planned    : List<PlannedPaymentEntity> = emptyList(),
    val today      : LocalDate = LocalDate.now(),
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val plannedRepo: PlannedPaymentRepository,
    private val prefs      : UserPreferences,
    private val txRepo     : TransactionRepository,
    accountRepo            : AccountRepository,
    goalRepo               : GoalRepository,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    init {
        syncMatches()
    }

    /**
     * Отмечает обязательства закрытыми, когда в истории появляется подходящая операция.
     *
     * Это **запись**, и поэтому она живёт отдельно от [state]. Считать сопоставление внутри
     * построения календаря нельзя: там чистая функция от данных, её результат исчезает вместе с
     * экраном, и обязательство осталось бы незакрытым до тех пор, пока на календарь кто-нибудь не
     * посмотрит. Отметка нужна и виджету, и плитке на главной, и самому «Свободно».
     *
     * Цикл «запись → перечитывание → запись» здесь конечный: закрытая дата уходит из
     * [ObligationMatcher.openDueDates], следующий проход не находит для неё кандидата и ничего не
     * пишет. Уже занятые операции исключаются заранее, иначе один платёж закрывал бы соседние
     * обязательства по кругу.
     */
    private fun syncMatches() = viewModelScope.launch(Dispatchers.Default) {
        combine(plannedRepo.observeActive(), txRepo.observeAll()) { planned, txList ->
            planned to txList
        }.collectLatest { (planned, txList) ->
            val today = LocalDate.now()
            val since = System.currentTimeMillis() - MATCH_WINDOW_MS
            val taken = planned.mapNotNullTo(HashSet()) { it.lastMatchedTxId }

            val matches = ObligationMatcher.match(
                payments     = planned,
                dueDates     = ObligationMatcher.openDueDates(planned, today, zone, OVERDUE_LOOKBACK),
                transactions = txList.filter { it.timestamp >= since && it.id !in taken },
                zone         = zone,
            )
            for (m in matches) {
                if (m.payment.lastMatchedTxId == m.transaction.id) continue
                plannedRepo.markMatched(
                    id                 = m.payment.id,
                    txId               = m.transaction.id,
                    throughEpochMillis = m.dueDate.atStartOfDay(zone).toInstant().toEpochMilli(),
                )
            }
        }
    }

    val state = combine(
        plannedRepo.observeActive(),
        accountRepo.observeAll(),
        txRepo.observeAll(),
        goalRepo.observeActive(),
        prefs.freeMoneyReserve,
    ) { planned, accounts, txList, goals, reserve ->
        val today = LocalDate.now()
        val now   = System.currentTimeMillis()

        // Подписки считаются той же машинкой, что и на своём экране: другой ответ на одни и те же
        // данные означал бы два разных «следующих списания» в одном приложении.
        val subscriptions = SubscriptionDetector.detect(
            charges = txList
                .filter { it.type == TransactionType.EXPENSE && it.timestamp >= now - SUB_WINDOW_MS }
                .map {
                    SubscriptionDetector.Charge(
                        timestamp     = it.timestamp,
                        amountKopecks = abs(it.amountKopecks),
                        currency      = it.currency,
                        merchant      = it.merchant,
                        description   = it.description,
                        categoryId    = it.categoryId,
                    )
                },
            now = now,
        )

        val credit = accounts
            .filter { it.kind == AccountKind.CREDIT && it.isActive }
            .map { account ->
                val accountTx = txList.filter { it.accountId == account.id }
                val cycle = creditCycle(account.statementDay, account.dueDays, today)
                CalendarBuilder.CreditObligation(
                    accountId = account.id,
                    title     = account.name,
                    duePayment = duePayment(
                        reportedAmountKopecks = account.duePaymentKopecks,
                        reportedDueDate       = account.duePaymentAt
                            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() },
                        cycle                 = cycle,
                        // Тот же расчёт, что и на экране кредитки. Взять здесь весь текущий долг
                        // значило бы показать в календаре одну сумму к оплате, а на карте — другую,
                        // и обе выдать за платёж по одной и той же выписке.
                        statementDebtKopecks  = statementDueDebt(account, accountTx, cycle, zone),
                        today                 = today,
                    ),
                    interestFree = nearestInterestFreeWindow(
                        transactions     = accountTx,
                        interestFreeDays = account.interestFreeDays,
                        today            = today,
                        zone             = zone,
                    ),
                )
            }

        // Уже подтверждённые подписки берём ИЗ ВСЕХ строк, а не только из активных: удалённое
        // обязательство иначе всплывало бы обратно и как предложение, и как выведенное событие.
        val claimed = plannedRepo.claimedAutoSources()

        // Окно начинается В ПРОШЛОМ. Просроченный платёж — это деньги, которые всё ещё должны
        // уйти; считая от сегодня, мы переставали их вычитать на следующий день после срока, и
        // «Свободно» само собой подрастало ровно тогда, когда человек задолжал.
        val from = today.minusDays(OVERDUE_LOOKBACK)
        val to   = today.plusDays(PROBE_DAYS)
        val probe = CalendarBuilder.build(planned, credit, subscriptions, goals, from, to, zone, claimed)
        val horizon = FreeMoney.defaultHorizon(probe, today)

        val upcoming = probe.filter { !it.date.isAfter(horizon) }

        val onAccounts = accounts
            .filter { it.kind == AccountKind.CASH && it.isActive && it.currency == BASE_CURRENCY }
            .sumOf { it.balanceKopecks }

        CalendarState(
            isLoading = false,
            free = FreeMoney.compute(
                currency          = BASE_CURRENCY,
                onAccountsKopecks = onAccounts,
                events            = upcoming,
                reserveKopecks    = reserve,
                today             = today,
                horizon           = horizon,
            ),
            upcoming   = upcoming.filter { !it.settled },
            all        = probe,
            windowFrom = from,
            windowTo   = to,
            // Закрытое за последний месяц: доказательство, что сопоставление сработало, и место,
            // где его можно отменить, если оно ошиблось.
            settled = CalendarBuilder
                .fromPlanned(planned, today.minusDays(30), today, zone)
                .filter { it.settled },
            suggestions = subscriptions.filter { it.key !in claimed && !it.isMissed },
            accounts = accounts,
            planned  = planned,
            today    = today,
        )
    }
        // 800 дней операций, поиск подписок и построение на 90 дней вперёд — это не работа для
        // главного потока, тем более что тот же поток теперь крутится и на главном экране ради
        // плитки. Без flowOn всё это считалось бы в Dispatchers.Main.immediate.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

    // ── Действия ─────────────────────────────────────────────────────────────────

    fun save(payment: PlannedPaymentEntity) = viewModelScope.launch { plannedRepo.save(payment) }

    fun deactivate(id: String) = viewModelScope.launch { plannedRepo.deactivate(id) }

    fun setReserve(kopecks: Long) = viewModelScope.launch { prefs.setFreeMoneyReserve(kopecks) }

    /** Отвязать найденную операцию: обязательство снова считается незакрытым. */
    fun unmatch(id: String) = viewModelScope.launch { plannedRepo.unmatch(id) }

    /**
     * Подтвердить найденную подписку — превратить догадку в обязательство.
     *
     * `autoSource` запоминает, из какой подписки она выросла: так она перестанет предлагаться
     * повторно и не попадёт в календарь дважды — и как найденная, и как объявленная.
     */
    fun confirmSubscription(sub: SubscriptionDetector.Subscription) = viewModelScope.launch {
        val next = sub.nextExpectedAt ?: sub.lastChargeAt
        plannedRepo.save(
            PlannedPaymentEntity(
                id            = UUID.randomUUID().toString(),
                title         = sub.title,
                amountKopecks = sub.typicalKopecks,
                currency      = sub.currency,
                direction     = PaymentDirection.OUT,
                schedule      = when (sub.period) {
                    SubscriptionDetector.Period.Weekly    -> PaymentSchedule.WEEKLY
                    SubscriptionDetector.Period.Quarterly -> PaymentSchedule.QUARTERLY
                    SubscriptionDetector.Period.Yearly    -> PaymentSchedule.YEARLY
                    // Период неизвестен — берём месяц: у подписки это подавляющий случай, а форма
                    // редактирования рядом и правится одним касанием.
                    else                                  -> PaymentSchedule.MONTHLY
                },
                anchorDate    = next,
                dayOfMonth    = Instant.ofEpochMilli(next).atZone(zone).toLocalDate().dayOfMonth,
                categoryId    = sub.categoryId,
                autoSource    = sub.key,
            )
        )
    }

    /** Ближайшая дата обязательства — нужна форме редактирования и сопоставлению. */
    fun nextDate(payment: PlannedPaymentEntity): LocalDate? =
        PaymentDates.nextOccurrence(payment, LocalDate.now(), zone)

    private companion object {
        /** Пока всё в рублях: курса у приложения нет, а «Свободно» обязано быть в одной валюте. */
        const val BASE_CURRENCY = "RUB"
        /** Окно поиска подписок — как на их собственном экране. */
        const val SUB_WINDOW_MS = 800L * 24 * 60 * 60 * 1000
        /** Насколько вперёд строим события, чтобы найти ближайшее поступление и задать горизонт. */
        const val PROBE_DAYS = 90L
        /** Насколько далеко назад ищем непогашенные просроченные обязательства. */
        const val OVERDUE_LOOKBACK = 60L
        /**
         * Какие операции вообще рассматриваются как закрывающие. Шире окна поиска дат: операция
         * может прийти на неделю позже срока, и запас нужен с обеих сторон.
         */
        const val MATCH_WINDOW_MS = (OVERDUE_LOOKBACK + 14L) * 24 * 60 * 60 * 1000
    }
}
