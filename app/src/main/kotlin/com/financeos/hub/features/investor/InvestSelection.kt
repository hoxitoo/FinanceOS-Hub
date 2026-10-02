package com.financeos.hub.features.investor

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Выбранный счёт брокера — общий для всех вкладок инвестора (#50).
 *
 * У каждой вкладки свой `InvestorViewModel` (он привязан к своему экрану в навигации), и выбор,
 * сделанный на «Счетах» («Открыть»), иначе не дошёл бы до «Портфеля». Не сохраняется между
 * запусками: после перезапуска — снова весь портфель.
 */
@Singleton
class InvestSelection @Inject constructor() {
    val contract = MutableStateFlow<String?>(null)
}
