package com.financeos.hub.features.investor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.invest.BrokerPushParser
import com.financeos.hub.core.invest.Portfolio
import com.financeos.hub.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Режим «Инвестор» на главной — подготовительный шаг.
 *
 * Хранилища брокерских событий ещё нет: пуши БКС пока только распознаются (и по ним замечается имя
 * пакета приложения брокера). Поэтому настоящего портфеля экран не показывает — он показывает
 * честное пустое состояние и, по кнопке, [sample]: портфель, посчитанный ТЕМ ЖЕ разбором и ТЕМ ЖЕ
 * расчётом из реальных пушей БКС от 1 октября. Пример помечен как пример.
 */
@HiltViewModel
class InvestorViewModel @Inject constructor(
    private val prefs: UserPreferences,
) : ViewModel() {

    /**
     * `null`, пока настройка не прочитана: иначе при запуске в режиме инвестора главная на долю
     * секунды показала бы кошелёк — и человек увидел бы не те деньги.
     */
    val investorMode: StateFlow<Boolean?> = prefs.investorMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Имя пакета приложения брокера, если служба уведомлений его уже заметила. */
    val brokerPackage: StateFlow<String?> = prefs.brokerPackage
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setInvestorMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setInvestorMode(enabled) }
    }

    /** Найдено не то приложение — забыть его, и служба уведомлений начнёт искать заново. */
    fun resetBrokerPackage() {
        viewModelScope.launch { prefs.clearBrokerPackage() }
    }

    /** Портфель из реальных пушей БКС (1 октября) — для оценки экрана, пока своих данных нет. */
    val sample: Portfolio.Result by lazy {
        val minute = 60_000L
        val start  = 1_790_837_580_000L   // 1 октября 2026, 09:53 МСК
        val pushes = listOf(
            0L  to "Пополнение счета Вы пополнили счет №580922/19-м на 10 000 RUB",
            3L  to "LQDT: заявка активна Лимитная заявка на покупку 4760 лотов LQDT по 2.0984",
            11L to "LQDT: заявка отменена Лимитная заявка на покупку 4760 лотов LQDT по 2.0984",
            12L to "LQDT: заявка исполнена Лимитная заявка на покупку 4760 лотов LQDT по 2.0985",
        )
        Portfolio.compute(pushes.mapNotNull { (m, text) -> BrokerPushParser.parse(text, start + m * minute) })
    }
}
