package com.financeos.hub.features.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.analytics.SubscriptionDetector
import com.financeos.hub.core.calendar.CalendarBuilder
import com.financeos.hub.core.calendar.CalendarEvent
import com.financeos.hub.core.calendar.FreeMoney
import com.financeos.hub.core.calendar.PaymentDates
import com.financeos.hub.core.credit.creditCycle
import com.financeos.hub.core.credit.duePayment
import com.financeos.hub.core.credit.nearestInterestFreeWindow
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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
    accountRepo            : AccountRepository,
    txRepo                 : TransactionRepository,
    goalRepo               : GoalRepository,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

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
                        statementDebtKopecks  = -account.balanceKopecks.coerceAtMost(0L),
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

        // Горизонт считаем по СОБЫТИЯМ, а не по календарной дате: «на сколько хватит до следующих
        // денег» — вопрос про поступления, и узнать их можно только собрав события заранее.
        val probe = CalendarBuilder.build(
            planned, credit, subscriptions, goals, today, today.plusDays(PROBE_DAYS), zone,
        )
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
            upcoming = upcoming.filter { !it.settled },
            // Закрытое за последний месяц: доказательство, что сопоставление сработало, и место,
            // где его можно отменить, если оно ошиблось.
            settled = CalendarBuilder
                .fromPlanned(planned, today.minusDays(30), today, zone)
                .filter { it.settled },
            suggestions = subscriptions.filter { sub ->
                sub.key !in planned.mapNotNull { it.autoSource } && !sub.isMissed
            },
            accounts = accounts,
            planned  = planned,
            today    = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

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
    }
}
