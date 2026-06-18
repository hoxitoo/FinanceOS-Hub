package com.financeos.hub.features.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.data.repositories.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GoalsState(val goals: List<GoalEntity> = emptyList())

@HiltViewModel
class GoalsViewModel @Inject constructor(
    goalRepo: GoalRepository,
) : ViewModel() {
    val state = goalRepo.observeActive()
        .map { GoalsState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsState())
}
