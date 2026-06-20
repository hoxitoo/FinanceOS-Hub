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
    val cardMasks: List<String> = emptyList(),  // distinct masks from accounts + cards, for the picker
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
        GoalsState(goals = goals, routes = routes, cardMasks = masks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsState())

    fun createGoal(name: String, emoji: String, targetKopecks: Long, deadlineAt: Long?) {
        viewModelScope.launch {
            goalRepo.upsert(
                GoalEntity(
                    id            = UUID.randomUUID().toString(),
                    name          = name,
                    emoji         = emoji,
                    targetKopecks = targetKopecks,
                    savedKopecks  = 0L,
                    deadlineAt    = deadlineAt,
                )
            )
        }
    }

    fun addContribution(goal: GoalEntity, amountKopecks: Long) {
        viewModelScope.launch {
            val newSaved = goal.savedKopecks + amountKopecks
            val completed = newSaved >= goal.targetKopecks
            goalRepo.upsert(
                goal.copy(
                    savedKopecks = newSaved.coerceAtMost(goal.targetKopecks),
                    isCompleted  = completed,
                    completedAt  = if (completed) System.currentTimeMillis() else null,
                )
            )
        }
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

    fun linkCard(goalId: String, mask: String) {
        viewModelScope.launch {
            transferRouteRepo.addRoute(
                TransferRouteEntity(
                    id         = UUID.randomUUID().toString(),
                    goalId     = goalId,
                    matchType  = TransferMatchType.CARD,
                    matchValue = mask.lowercase(),
                )
            )
        }
    }

    fun linkKeyword(goalId: String, keyword: String) {
        viewModelScope.launch {
            transferRouteRepo.addRoute(
                TransferRouteEntity(
                    id         = UUID.randomUUID().toString(),
                    goalId     = goalId,
                    matchType  = TransferMatchType.KEYWORD,
                    matchValue = keyword.trim().lowercase(),
                )
            )
        }
    }

    fun unlink(routeId: String) {
        viewModelScope.launch { transferRouteRepo.removeRoute(routeId) }
    }
}
