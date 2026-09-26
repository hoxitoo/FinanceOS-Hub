package com.financeos.hub.features.analytics.lifetime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.analytics.LifetimeStats
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.data.repositories.CategoryRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class LifetimeState(
    val isLoading : Boolean                    = true,
    val horizon   : LifetimeStats.Horizon      = LifetimeStats.Horizon.ALL,
    val step      : LifetimeStats.Step         = LifetimeStats.Step.MONTH,
    val result    : LifetimeStats.Result?      = null,
    val categories: Map<String, CategoryEntity> = emptyMap(),
)

@HiltViewModel
class LifetimeViewModel @Inject constructor(
    txRepo      : TransactionRepository,
    categoryRepo: CategoryRepository,
) : ViewModel() {

    private val horizon = MutableStateFlow(LifetimeStats.Horizon.ALL)
    private val step    = MutableStateFlow(LifetimeStats.Step.MONTH)

    val state = combine(
        txRepo.observeAll(),
        categoryRepo.observeAll(),
        horizon,
        step,
    ) { txList, cats, h, s ->
        LifetimeState(
            isLoading  = false,
            horizon    = h,
            step       = s,
            result     = LifetimeStats.compute(
                entries = LifetimeStats.entriesOf(txList),
                horizon = h,
                step    = s,
                today   = LocalDate.now(),
                zone    = ZoneId.systemDefault(),
            ),
            categories = cats.associateBy { it.id },
        )
    }
        // Вся история за годы — не работа для главного потока: тот же выбор, что у календаря.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LifetimeState())

    fun setHorizon(h: LifetimeStats.Horizon) { horizon.value = h }
    fun setStep(s: LifetimeStats.Step)       { step.value = s }
}
