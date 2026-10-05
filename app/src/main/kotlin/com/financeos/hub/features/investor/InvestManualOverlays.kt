package com.financeos.hub.features.investor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.financeos.hub.core.invest.BrokerEvent
import com.financeos.hub.core.invest.BrokerOrder
import com.financeos.hub.core.invest.ManualEntry
import com.financeos.hub.core.invest.Portfolio
import com.financeos.hub.core.invest.isManual

/**
 * Что из ручного ввода сейчас открыто на экране инвестора (#49, #50, #51): лист «Добавить»,
 * карточка правки операции, подтверждение удаления, лист бумаги. Одно состояние на экран —
 * «Портфель», «Операции» и «Счета» открывают одни и те же листы и ведут себя одинаково.
 */
@Stable
class InvestManualState {
    var add      by mutableStateOf<InvestAdd?>(null)
    /** Нажатая операция: открывается карточка правки, удаление — внутри неё (#51). */
    var editing  by mutableStateOf<BrokerEvent?>(null)
    var deleting by mutableStateOf<BrokerEvent?>(null)
    var position by mutableStateOf<Portfolio.Position?>(null)
}

@Composable
fun rememberInvestManualState(): InvestManualState = remember { InvestManualState() }

/** Листы и диалоги ручного ввода. Пишут только в СВОИ данные — счета примера настоящими не являются. */
@Composable
fun InvestManualOverlays(state: InvestManualState, vm: InvestorViewModel, portfolio: Portfolio.Result) {
    when (state.add) {
        InvestAdd.MENU -> BrokerAddMenuSheet(
            onOperation = { state.add = InvestAdd.OPERATION },
            onAsset     = { state.add = InvestAdd.ASSET },
            onAccount   = { state.add = InvestAdd.ACCOUNT },
            onDismiss   = { state.add = null },
        )
        InvestAdd.OPERATION -> BrokerOperationSheet(portfolio.contracts, { vm.addEvents(it) }, { state.add = null })
        InvestAdd.ASSET     -> BrokerAssetSheet(portfolio.contracts, { vm.addEvents(it) }, { state.add = null })
        InvestAdd.ACCOUNT   -> BrokerNewAccountSheet(
            onSave    = { broker, contract, label -> vm.saveAccount(broker, contract, label) },
            onDismiss = { state.add = null },
        )
        null -> Unit
    }
    state.position?.let { p ->
        BrokerPositionSheet(
            position  = p,
            onPrice   = { vm.setPrice(p.broker, p.ticker, it, p.currency) },
            onDelete  = { vm.deleteAsset(p.broker, p.ticker) },
            onDismiss = { state.position = null },
            trades    = portfolio.history.filter {
                it.broker == p.broker && it.ticker == p.ticker && it.currency == p.currency
            },
            estimates = portfolio.estimates,
            // Карточка позиции закрывается, открывается карточка правки сделки.
            onTrade   = { o -> state.position = null; state.editing = o },
        )
    }
    state.editing?.let { e ->
        // Запись целиком: у актива — пополнение, покупка и цена; у пуша — он сам.
        val record = remember(e) { vm.recordOf(e) }
        val draft  = remember(e) { ManualEntry.draftOf(e, record) }
        val delete = { state.editing = null; state.deleting = e }
        when {
            // Править нечего (не операция) — остаётся удаление. Состояние меняется вне композиции.
            draft == null -> LaunchedEffect(e) { state.editing = null; state.deleting = e }
            draft.asset -> BrokerAssetSheet(
                contracts = portfolio.contracts,
                onSave    = { vm.replaceEvents(e.id, it) },
                onDismiss = { state.editing = null },
                initial   = draft,
                onDelete  = delete,
            )
            else -> BrokerOperationSheet(
                contracts  = portfolio.contracts,
                onSave     = { vm.replaceEvents(e.id, ManualEntry.keepPushed(e, it)) },
                onDismiss  = { state.editing = null },
                initial    = draft,
                pushedOrder = e is BrokerOrder && !e.isManual,
                onDelete   = delete,
            )
        }
    }
    state.deleting?.let { e ->
        ConfirmDelete(
            title = "Удалить операцию?",
            text  = if (e.isManual) "Операция введена вручную и уйдёт вместе со всей своей записью " +
                "(у актива — и деньги, заведённые на его покупку)."
            else "Операция пришла пушем. Удалите её, если брокер прислал неверное; повторно тот же пуш " +
                "её не вернёт, но копия, снятая раньше, при восстановлении — вернёт.",
            onConfirm = { vm.deleteEvent(e.id); state.deleting = null },
            onDismiss = { state.deleting = null },
        )
    }
}
