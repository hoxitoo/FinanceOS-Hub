package com.financeos.hub.features.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.data.repositories.GoalRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Всё, что калькулятор берёт ИЗ приложения, а не из полей ввода.
 *
 * Сама математика ([com.financeos.hub.core.finance.SavingsMath]) чистая и синхронная, поэтому она
 * считается прямо в composable через `remember` — гонять каждое нажатие клавиши через ViewModel
 * значило бы добавить слой состояния ради ничего. Здесь остаётся ровно то, за чем нужно идти в базу.
 */
@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val goalRepo: GoalRepository,
    private val txRepo  : TransactionRepository,
) : ViewModel() {

    data class State(
        val goals: List<GoalEntity> = emptyList(),
        /** Средний остаток за 3 закрытых месяца. `null` — истории не хватает, кнопку не показываем. */
        val suggestedMonthlyKopecks: Long? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            goalRepo.observeActive().collectLatest { goals ->
                _state.value = _state.value.copy(goals = goals)
            }
        }
        viewModelScope.launch {
            // Отрицательный средний остаток предлагать нечего: «откладывайте −4 000 ₽» — это не совет.
            val net = txRepo.averageMonthlyNet()
            _state.value = _state.value.copy(
                suggestedMonthlyKopecks = net?.takeIf { it > 0L },
            )
        }
    }
}
