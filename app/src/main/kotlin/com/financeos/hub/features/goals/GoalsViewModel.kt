package com.financeos.hub.features.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.data.repositories.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class GoalsState(val goals: List<GoalEntity> = emptyList())

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepo: GoalRepository,
) : ViewModel() {

    val state = goalRepo.observeActive()
        .map { GoalsState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsState())

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

    fun deleteGoal(id: String) {
        viewModelScope.launch { goalRepo.delete(id) }
    }
}
