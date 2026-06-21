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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class GoalsState(
    val goals: List<GoalEntity> = emptyList(),
    val routes: List<TransferRouteEntity> = emptyList(),
    val cardMasks: List<String> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepo: GoalRepository,
    private val transferRouteRepo: TransferRouteRepository,
    private val accountRepo: AccountRepository,
    private val cardRepo: CardRepository,
) : ViewModel() {

    val state = combine(
        goalRepo.observeActive(),
        transferRouteRepo.observeAll(),
        accountRepo.observeAll(),
        cardRepo.observeAll(),
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        val goals    = arr[0] as List<GoalEntity>
        @Suppress("UNCHECKED_CAST")
        val routes   = arr[1] as List<TransferRouteEntity>
        @Suppress("UNCHECKED_CAST")
        val accounts = arr[2] as List<AccountEntity>
        @Suppress("UNCHECKED_CAST")
        val cards    = arr[3] as List<CardEntity>

        val masks = (accounts.mapNotNull { it.cardMask } + cards.map { it.cardMask }).distinct()
        GoalsState(goals = goals, routes = routes, cardMasks = masks, accounts = accounts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsState())

    fun createGoal(
        name           : String,
        emoji          : String,
        targetKopecks  : Long,
        deadlineAt     : Long?,
        linkedAccountId: String? = null,
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
                )
            )
            if (linkedAccountId != null) {
                transferRouteRepo.addRoute(
                    TransferRouteEntity(
                        id         = UUID.randomUUID().toString(),
                        goalId     = goalId,
                        matchType  = TransferMatchType.ACCOUNT,
                        matchValue = linkedAccountId,
                    )
                )
            }
        }
    }

    fun addContribution(goal: GoalEntity, amountKopecks: Long) {
        // Delegate to the single clamping/completion code path so manual and routed
        // contributions behave identically (floor-clamp, target-clamp, updatedAt refresh).
        viewModelScope.launch { goalRepo.contribute(goal.id, amountKopecks) }
    }

    fun updateGoal(
        goal          : GoalEntity,
        name          : String,
        emoji         : String,
        targetKopecks : Long,
        deadlineAt    : Long?,
    ) {
        viewModelScope.launch {
            goalRepo.upsert(
                goal.copy(
                    name          = name,
                    emoji         = emoji,
                    targetKopecks = targetKopecks,
                    deadlineAt    = deadlineAt,
                    isCompleted   = goal.savedKopecks >= targetKopecks,
                    updatedAt     = System.currentTimeMillis(),
                )
            )
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
