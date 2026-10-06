package com.financeos.hub.features.investor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.invest.BrokerPushParser
import com.financeos.hub.core.invest.DepositLinks
import com.financeos.hub.core.invest.Portfolio
import com.financeos.hub.core.invest.isManual
import com.financeos.hub.data.preferences.UserPreferences
import com.financeos.hub.data.repositories.AccountRepository
import com.financeos.hub.data.repositories.BrokerEventRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Режим «Инвестор» на главной.
 *
 * Портфель считается из `broker_events` — пушей найденного приложения брокера (инвариант #47).
 * Пока своих событий нет, экран показывает честное пустое состояние и, по кнопке, [sample]:
 * портфель, посчитанный ТЕМ ЖЕ разбором и ТЕМ ЖЕ расчётом из реальных пушей БКС. Пример помечен.
 */
@HiltViewModel
class InvestorViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val brokerEvents: BrokerEventRepository,
    private val selection: InvestSelection,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
) : ViewModel() {

    /** Свои события брокера как есть — лист правки собирает по ним запись целиком (#51). */
    private val events: StateFlow<List<com.financeos.hub.core.invest.BrokerEvent>> = brokerEvents.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Портфель из своих событий. Считается вне главного потока: история растёт с каждым пушем. */
    val portfolio: StateFlow<Portfolio.Result> = events
        .map { Portfolio.compute(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Portfolio.EMPTY)

    /**
     * Склейка пополнений (#53): переводы кошелька брокеру ↔ зачисления у брокера. Кошелёк отдаёт сюда
     * только СВОИ переводы с категорией «Инвестиции» — сам он о брокере не узнаёт ничего, кроме итога.
     */
    val links: StateFlow<DepositLinks.Result> = combine(
        transactions.observeAll(), accounts.observeAll(), events, prefs.dismissedBrokerLegs, hourly,
    ) { txs, accs, evs, dismissed, now ->
        DepositLinks.link(txs.mapNotNull { DepositLinks.legOf(it, accs) }, evs, dismissed, now)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DepositLinks.EMPTY)

    /**
     * Часы раз в час: перевод, которому брокер так и не ответил, становится предложением по
     * прошествии времени, а не по чужому изменению данных.
     */
    private val hourly get() = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(3_600_000L)
        }
    }

    /** «Записать пополнение» по переводу кошелька, о котором брокер не прислал пуша. */
    fun recordDeposit(leg: DepositLinks.WalletLeg) {
        val event = DepositLinks.depositFor(leg, portfolio.value.contracts)
        viewModelScope.launch { brokerEvents.addManual(listOf(event)) }
    }

    /** «Это не пополнение» — больше не предлагать. */
    fun dismissLeg(leg: DepositLinks.WalletLeg) {
        viewModelScope.launch { prefs.dismissBrokerLeg(leg.txId) }
    }

    /**
     * Есть ли предупреждение брокера, требующее действия. Нужно и КОШЕЛЬКУ — точка на «Инвестор» в
     * переключателе: человек, сидящий в кошельке, иначе не узнал бы, что брокер грозит закрыть
     * позиции. Сумм и текста кошелёк не получает (инвариант #44).
     */
    val hasOpenAlert: StateFlow<Boolean> = portfolio
        .map { it.openAlerts.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Выбранный счёт брокера (ключ [com.financeos.hub.core.invest.contractKey]); `null` — весь портфель. */
    private val _selectedContract = selection.contract
    val selectedContract: StateFlow<String?> = _selectedContract.asStateFlow()

    fun selectContract(key: String?) { _selectedContract.value = key }

    // ── Ручной ввод (инвариант #49) ──────────────────────────────────────────────

    fun addEvents(events: List<com.financeos.hub.core.invest.BrokerEvent>) {
        viewModelScope.launch { brokerEvents.addManual(events) }
    }

    /**
     * Вся запись, к которой относится строка: у ручной — строки с общим префиксом группы (актив =
     * пополнение + покупка + цена), у пуша — она сама.
     */
    fun recordOf(e: com.financeos.hub.core.invest.BrokerEvent): List<com.financeos.hub.core.invest.BrokerEvent> {
        val id = e.id ?: return listOf(e)
        if (!e.isManual) return listOf(e)
        val group = id.substringBeforeLast('_') + "_"
        return events.value.filter { it.id?.startsWith(group) == true }.ifEmpty { listOf(e) }
    }

    /** Сохранить правку записи [id] (#51). */
    fun replaceEvents(id: String?, events: List<com.financeos.hub.core.invest.BrokerEvent>) {
        if (id == null || events.isEmpty()) return
        viewModelScope.launch { brokerEvents.replace(id, events) }
    }

    fun saveAccount(broker: String, contract: String, label: String?) {
        viewModelScope.launch { brokerEvents.saveAccount(broker, contract, label) }
    }

    fun hideAccount(broker: String, contract: String) {
        viewModelScope.launch {
            brokerEvents.hideAccount(broker, contract)
            // Удалённый счёт не может оставаться выбранным — экран показал бы пустоту.
            if (_selectedContract.value == com.financeos.hub.core.invest.contractKey(contract)) _selectedContract.value = null
        }
    }

    /** Удалить операцию (у примера id нет — удалять нечего). */
    fun deleteEvent(id: String?) {
        if (id == null) return
        viewModelScope.launch { brokerEvents.deleteEvent(id) }
    }

    fun deleteAsset(broker: String, ticker: String) {
        viewModelScope.launch { brokerEvents.deleteTicker(broker, ticker) }
    }

    /** «Указать цену»: текущая цена бумаги на сейчас. */
    fun setPrice(broker: String, ticker: String, priceMicros: Long, currency: String) {
        viewModelScope.launch {
            brokerEvents.addManual(listOf(
                com.financeos.hub.core.invest.BrokerPriceMark(broker, System.currentTimeMillis(), ticker, priceMicros, currency),
            ))
        }
    }

    /** «Закрыть» на карточке предупреждения. У примера id нет — закрывать в базе нечего. */
    fun dismissAlert(id: String?) {
        if (id == null) return
        viewModelScope.launch { brokerEvents.dismissAlert(id) }
    }

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
        viewModelScope.launch {
            // Сначала события ошибочного приложения: иначе они остались бы в портфеле навсегда.
            brokerPackage.value?.let { brokerEvents.forgetPackage(it) }
            prefs.clearBrokerPackage()
        }
    }

    /** Портфель из реальных пушей БКС (1–2 октября) — для оценки экрана, пока своих данных нет. */
    val sample: Portfolio.Result by lazy {
        val minute = 60_000L
        val start  = 1_790_837_580_000L   // 1 октября 2026, 09:53 МСК
        val pushes = listOf(
            0L  to "Пополнение счета Вы пополнили счет №580922/19-м на 10 000 RUB",
            3L  to "LQDT: заявка активна Лимитная заявка на покупку 4760 лотов LQDT по 2.0984",
            11L to "LQDT: заявка отменена Лимитная заявка на покупку 4760 лотов LQDT по 2.0984",
            12L to "LQDT: заявка исполнена Лимитная заявка на покупку 4760 лотов LQDT по 2.0985",
            // 2 октября, 11:24: предупреждение и перевод, который его покрыл (пришёл следом).
            1531L to "Критично низкий баланс счета 3468071/25 (Облигации) Пополните счет 3468071/25 " +
                "(Облигации) на сумму от 188.02. Если стоимость портфеля станет ниже 0, брокер приступит " +
                "к закрытию ваших позиций. Уведомление о маржин-колле направлено на ваш e-mail.",
            1532L to "Перевод между счетами 189 RUB. Со счета №580922/19-м на счет 3468071/25 (Облигации)",
        )
        Portfolio.compute(pushes.mapNotNull { (m, text) -> BrokerPushParser.parse(text, start + m * minute) })
    }
}
