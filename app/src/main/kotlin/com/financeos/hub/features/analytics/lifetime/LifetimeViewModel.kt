package com.financeos.hub.features.analytics.lifetime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.analytics.LifetimeStats
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.data.repositories.CategoryRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LifetimeViewModel @Inject constructor(
    txRepo      : TransactionRepository,
    categoryRepo: CategoryRepository,
) : ViewModel() {

    private val horizon = MutableStateFlow(LifetimeStats.Horizon.ALL)
    private val step    = MutableStateFlow(LifetimeStats.Step.MONTH)

    /**
     * Всё, что не зависит от шага: группировка истории по годам, категориям и продавцам. Смена шага
     * «месяц / полгода / год» пересчитывает только кривую — не нормализацию имён десятков тысяч
     * операций. `mapLatest`: новое окно отменяет недосчитанное старое, а не встаёт за ним в очередь.
     */
    private val base = combine(txRepo.observeAll(), horizon) { txList, h -> txList to h }
        .mapLatest { (txList, h) ->
            h to LifetimeStats.computeBase(
                entries = LifetimeStats.entriesOf(txList),
                horizon = h,
                today   = LocalDate.now(),
                zone    = ZoneId.systemDefault(),
            )
        }
        .flowOn(Dispatchers.Default)

    val state = combine(base, step, categoryRepo.observeAll()) { (h, b), s, cats ->
        LifetimeState(
            isLoading  = false,
            horizon    = h,
            step       = s,
            result     = b.result.copy(curve = LifetimeStats.curveOf(b, s)),
            categories = cats.associateBy { it.id },
        )
    }
        // Вся история за годы — не работа для главного потока: тот же выбор, что у календаря.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LifetimeState())

    fun setHorizon(h: LifetimeStats.Horizon) { horizon.value = h }
    fun setStep(s: LifetimeStats.Step)       { step.value = s }
}
