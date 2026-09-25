package com.financeos.hub.features.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.CardEntity
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.core.database.entities.TransferMatchType
import com.financeos.hub.core.database.entities.TransferRouteEntity
import com.financeos.hub.data.repositories.AccountRepository
import com.financeos.hub.data.repositories.CardRepository
import com.financeos.hub.data.repositories.GoalRepository
import com.financeos.hub.data.repositories.TransferRouteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class GoalsState(
    val goals: List<GoalEntity> = emptyList(),
    val routes: List<TransferRouteEntity> = emptyList(),
    val cardMasks: List<String> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    /**
     * Маска карты → имя счёта, к которому она привязана.
     *
     * Без этого список карт в листе автопополнения — шестнадцать голых четырёхзначных чисел, и
     * выбрать из них нужную можно только угадыванием.
     */
    val cardOwners: Map<String, String> = emptyMap(),
    /**
     * Собственный темп накопления — средний остаток за три ЗАКРЫТЫХ месяца.
     *
     * Текущий месяц исключён намеренно (та же ловушка, что в калькуляторе и в пилларах оценки):
     * 3-го числа зарплата уже пришла, а расходы ещё нет, и темп вышел бы вдвое выше правды.
     * `null` — истории ещё нет, и тогда расчёт молчит вместо того, чтобы обещать срок.
     */
    val paceKopecks: Long? = null,
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepo: GoalRepository,
    private val transferRouteRepo: TransferRouteRepository,
    private val accountRepo: AccountRepository,
    private val cardRepo: CardRepository,
    private val txRepo: com.financeos.hub.data.repositories.TransactionRepository,
) : ViewModel() {

    // One shared flow per goal — see AnalyticsViewModel.categoryOperations: creating a stateIn
    // inside a function leaks a coroutine per call site invocation.
    private val historyCache =
        mutableMapOf<String, kotlinx.coroutines.flow.StateFlow<List<com.financeos.hub.core.database.entities.TransactionEntity>>>()

    /** Operations routed to [goalId], newest first — shown in the goal's history sheet. */
    fun historyFor(goalId: String) = historyCache.getOrPut(goalId) {
        txRepo.observeByGoal(goalId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    /**
     * Собственный темп накопления. Считается ОДИН раз на подписку, а не на каждое изменение
     * истории.
     *
     * Темп — средний остаток за три ЗАКРЫТЫХ месяца, и сегодняшняя операция на него не влияет по
     * определению. Подписка на `observeAll()` тянула бы всю таблицу операций при каждой правке
     * ради значения, которое от неё не зависит. Экран целей открыт подолгу, история бывает в
     * десятки тысяч строк — это чистая трата на горячем пути.
     *
     * Свежести хватает: `WhileSubscribed(5_000)` отпускает поток через пять секунд после ухода с
     * экрана, и следующее открытие считает заново.
     */
    private val pace = flow { emit(txRepo.averageMonthlyNet(3)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state = combine(
        goalRepo.observeActive(),
        transferRouteRepo.observeAll(),
        accountRepo.observeAll(),
        cardRepo.observeAll(),
        pace,
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        val goals    = arr[0] as List<GoalEntity>
        @Suppress("UNCHECKED_CAST")
        val routes   = arr[1] as List<TransferRouteEntity>
        @Suppress("UNCHECKED_CAST")
        val accounts = arr[2] as List<AccountEntity>
        @Suppress("UNCHECKED_CAST")
        val cards    = arr[3] as List<CardEntity>
        val paceNow  = arr[4] as Long?

        val masks = (accounts.mapNotNull { it.cardMask } + cards.map { it.cardMask }).distinct()
        val byId  = accounts.associateBy { it.id }
        val owners = buildMap {
            // Сначала карты (их привязка к счёту явная), затем маска самого счёта — она и есть
            // последнее слово, если одна и та же маска встретилась дважды.
            cards.forEach { c -> byId[c.accountId]?.let { put(c.cardMask, it.name) } }
            accounts.forEach { a -> a.cardMask?.let { put(it, a.name) } }
        }
        GoalsState(
            goals      = goals,
            routes     = routes,
            cardMasks  = masks,
            accounts    = accounts,
            cardOwners  = owners,
            paceKopecks = paceNow,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsState())

    fun createGoal(
        name            : String,
        emoji           : String,
        targetKopecks   : Long,
        deadlineAt      : Long?,
        startedAt       : Long? = null,
        linkedAccountIds: Set<String> = emptySet(),
    ) {
        viewModelScope.launch {
            val goalId = UUID.randomUUID().toString()
            goalRepo.upsert(
                GoalEntity(
                    id            = goalId,
                    name          = name,
                    emoji         = emoji,
                    targetKopecks = targetKopecks,
                    savedKopecks  = 0L,
                    deadlineAt    = deadlineAt,
                    startedAt     = startedAt,
                )
            )
            linkedAccountIds.forEach { accountId ->
                transferRouteRepo.addRoute(
                    TransferRouteEntity(
                        id         = UUID.randomUUID().toString(),
                        goalId     = goalId,
                        matchType  = TransferMatchType.ACCOUNT,
                        matchValue = accountId,
                    )
                )
            }
        }
    }

    /**
     * Ставит НА цели ровно [totalKopecks]: приложение само считает, сколько прибавилось или убыло.
     *
     * Так человек и знает свои накопления — «на счёте лежит 45 000», а не «с прошлого раза стало
     * больше на 5 000». Разностью приходилось считать в уме, и ошибка в этом счёте попадала прямо
     * в цель: ввёл полную сумму вместо прибавки — цель прыгнула вдвое, и откатить это можно было
     * только такой же ручной разностью.
     *
     * Под капотом — та же [GoalRepository.contribute] со знаковой разностью: у неё один мьютекс с
     * автоматическими зачислениями и одно место, где цель закрывается и открывается обратно.
     * Отдельная «установка» мимо неё разошлась бы с этой логикой при первой же правке.
     */
    fun setSavedTotal(goal: GoalEntity, totalKopecks: Long) {
        val delta = totalKopecks.coerceAtLeast(0L) - goal.savedKopecks
        if (delta == 0L) return
        viewModelScope.launch { goalRepo.contribute(goal.id, delta) }
    }

    fun updateGoal(
        goal          : GoalEntity,
        name          : String,
        emoji         : String,
        targetKopecks : Long,
        deadlineAt    : Long?,
        startedAt     : Long? = goal.startedAt,
    ) {
        viewModelScope.launch {
            goalRepo.upsert(
                goal.copy(
                    name          = name,
                    emoji         = emoji,
                    targetKopecks = targetKopecks,
                    deadlineAt    = deadlineAt,
                    startedAt     = startedAt,
                    isCompleted   = goal.savedKopecks >= targetKopecks,
                    updatedAt     = System.currentTimeMillis(),
                )
            )
        }
    }

    /**
     * Приводит привязки цели к счетам в точности к [accountIds]: недостающие добавляет, лишние
     * снимает.
     *
     * Отдельным действием, а не внутри [updateGoal], потому что это запись в ДРУГУЮ таблицу
     * (`transfer_routes`), и у неё своя семантика: снятая привязка — это `is_active = 0`, а не
     * удаление строки. Трогаются только маршруты типа ACCOUNT: привязки по карте и ключевому слову
     * живут в отдельном листе, и форма цели о них ничего не знает — стереть их заодно значило бы
     * молча отменить чужую настройку.
     */
    fun syncAccountRoutes(goalId: String, accountIds: Set<String>) {
        viewModelScope.launch {
            val current = transferRouteRepo.getAllActive()
                .filter { it.goalId == goalId && it.matchType == TransferMatchType.ACCOUNT }

            current.filterNot { it.matchValue in accountIds }
                .forEach { transferRouteRepo.removeRoute(it.id) }

            val already = current.map { it.matchValue }.toSet()
            (accountIds - already).forEach { accountId ->
                transferRouteRepo.addRoute(
                    TransferRouteEntity(
                        id         = UUID.randomUUID().toString(),
                        goalId     = goalId,
                        matchType  = TransferMatchType.ACCOUNT,
                        matchValue = accountId,
                    )
                )
            }
        }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch { goalRepo.delete(id) }
    }

    // --- Auto-fund (transfer routing) links ---

    /**
     * Inserts a route unless an identical one (same goal + type + value) already exists.
     * Guards the free-text card/keyword entry, which can otherwise create duplicate rows that
     * route the same transfer twice (or, for an account already linked elsewhere, ambiguously).
     */
    private fun addRouteIfAbsent(goalId: String, type: TransferMatchType, value: String) {
        viewModelScope.launch {
            val exists = transferRouteRepo.getAllActive().any {
                it.goalId == goalId && it.matchType == type && it.matchValue.equals(value, ignoreCase = true)
            }
            if (!exists) {
                transferRouteRepo.addRoute(
                    TransferRouteEntity(
                        id         = UUID.randomUUID().toString(),
                        goalId     = goalId,
                        matchType  = type,
                        matchValue = value,
                    )
                )
            }
        }
    }

    fun linkCard(goalId: String, mask: String) =
        addRouteIfAbsent(goalId, TransferMatchType.CARD, mask.lowercase())

    fun linkAccount(goalId: String, accountId: String) =
        addRouteIfAbsent(goalId, TransferMatchType.ACCOUNT, accountId)

    fun linkKeyword(goalId: String, keyword: String) =
        addRouteIfAbsent(goalId, TransferMatchType.KEYWORD, keyword.trim().lowercase())

    fun unlink(routeId: String) {
        viewModelScope.launch { transferRouteRepo.removeRoute(routeId) }
    }
}
