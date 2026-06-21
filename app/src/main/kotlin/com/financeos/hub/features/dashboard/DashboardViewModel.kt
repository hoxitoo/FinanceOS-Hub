package com.financeos.hub.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.analytics.AnalyticsEngine
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.CardEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.data.preferences.UserPreferences
import com.financeos.hub.data.repositories.AccountRepository
import com.financeos.hub.data.repositories.CardRepository
import com.financeos.hub.data.repositories.CategoryRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DashboardState(
    val heroVariant         : String                    = "CALM",
    val netWorthKopecks     : Long                      = 0L,
    val netWorthByCurrency  : Map<String, Long>         = emptyMap(),
    val incomeKopecks       : Long                      = 0L,
    val expenseKopecks      : Long                      = 0L,
    val forecastKopecks     : Long                      = 0L,
    val financialScore      : Int                       = 0,
    val sparkline           : List<Float>               = emptyList(),
    val accounts            : List<AccountEntity>       = emptyList(),
    val cards               : List<CardEntity>          = emptyList(),
    val recentTransactions  : List<TransactionEntity>   = emptyList(),
    private val categories  : Map<String, String>       = emptyMap(),
) {
    fun categoryName(id: String?): String = id?.let { categories[it] } ?: "Другое"
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val txRepo      : TransactionRepository,
    private val accountRepo : AccountRepository,
    private val cardRepo    : CardRepository,
    categoryRepo            : CategoryRepository,
    private val prefs       : UserPreferences,
    private val engine      : AnalyticsEngine,
) : ViewModel() {

    private val _score     = MutableStateFlow(0)
    private val _forecast  = MutableStateFlow(0L)
    private val _sparkline = MutableStateFlow<List<Float>>(emptyList())

    init {
        viewModelScope.launch {
            txRepo.observeCurrentMonth().collect {
                runCatching { engine.computeScore().total }.onSuccess  { _score.value     = it }
                runCatching { engine.forecastMonthEnd() }.onSuccess    { _forecast.value  = it }
                runCatching { engine.sparkline30Days() }.onSuccess     { _sparkline.value = it }
            }
        }
    }

    val state = combine(
        combine(
            txRepo.observeCurrentMonth(),
            accountRepo.observeAll(),
            categoryRepo.observeAll(),
            cardRepo.observeAll(),
        ) { arr ->
            @Suppress("UNCHECKED_CAST")
            val tx    = arr[0] as List<TransactionEntity>
            @Suppress("UNCHECKED_CAST")
            val accts = arr[1] as List<AccountEntity>
            @Suppress("UNCHECKED_CAST")
            val cats  = arr[2] as List<com.financeos.hub.core.database.entities.CategoryEntity>
            @Suppress("UNCHECKED_CAST")
            val cards = arr[3] as List<CardEntity>
            listOf<Any?>(tx, accts, cats, cards)
        },
        combine(
            prefs.heroVariant,
            _score,
            _forecast,
            _sparkline,
        ) { hero, score, forecast, sparkline ->
            listOf<Any?>(hero, score, forecast, sparkline)
        },
    ) { inner, meta ->
        @Suppress("UNCHECKED_CAST")
        val txList    = inner[0] as List<TransactionEntity>
        @Suppress("UNCHECKED_CAST")
        val accounts  = inner[1] as List<AccountEntity>
        @Suppress("UNCHECKED_CAST")
        val categories = inner[2] as List<com.financeos.hub.core.database.entities.CategoryEntity>
        @Suppress("UNCHECKED_CAST")
        val cards     = inner[3] as List<CardEntity>

        @Suppress("UNCHECKED_CAST")
        val heroVariant = meta[0] as String
        val score       = meta[1] as Int
        val forecast    = meta[2] as Long
        @Suppress("UNCHECKED_CAST")
        val sparkline   = meta[3] as List<Float>

        val catMap   = categories.associate { it.id to it.name }
        val income   = txList.filter { it.type == TransactionType.INCOME }
            .sumOf { it.amountKopecks }
        val expense  = txList.filter { it.type == TransactionType.EXPENSE }
            .sumOf { kotlin.math.abs(it.amountKopecks) }
        val netWorth = accounts.sumOf { it.balanceKopecks }
        val netWorthByCurrency = accounts.groupBy { it.currency }
            .mapValues { (_, list) -> list.sumOf { it.balanceKopecks } }

        DashboardState(
            heroVariant          = heroVariant,
            netWorthKopecks      = netWorth,
            netWorthByCurrency   = netWorthByCurrency,
            incomeKopecks        = income,
            expenseKopecks       = expense,
            forecastKopecks      = forecast,
            financialScore       = score,
            sparkline            = sparkline,
            accounts             = accounts,
            cards                = cards,
            recentTransactions   = txList.take(5),
            categories           = catMap,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    fun createAccount(name: String, bank: String, cardMask: String?, balanceKopecks: Long, currency: String = "RUB") {
        viewModelScope.launch {
            accountRepo.upsert(
                AccountEntity(
                    id             = UUID.randomUUID().toString(),
                    name           = name,
                    bank           = bank,
                    cardMask       = cardMask,
                    balanceKopecks = balanceKopecks,
                    currency       = currency,
                )
            )
        }
    }

    fun updateAccountBalance(account: AccountEntity, newBalanceKopecks: Long) {
        viewModelScope.launch {
            accountRepo.upsert(account.copy(
                balanceKopecks = newBalanceKopecks,
                updatedAt      = System.currentTimeMillis(),
            ))
        }
    }

    fun deleteAccount(id: String) {
        viewModelScope.launch { accountRepo.deactivate(id) }
    }

    fun addCard(card: CardEntity) {
        viewModelScope.launch { cardRepo.addCard(card) }
    }

    fun deleteCard(id: String) {
        viewModelScope.launch { cardRepo.deactivate(id) }
    }
}
