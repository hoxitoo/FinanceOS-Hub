package com.financeos.hub.features.investor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.invest.Portfolio
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

/** «История» — движения денег у брокера и прошлые предупреждения, новые сверху. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestHistorySheet(portfolio: Portfolio.Result, onEventClick: (com.financeos.hub.core.invest.BrokerEvent) -> Unit, onDismiss: () -> Unit) {
    val feed = historyFeed(portfolio, null)
    InvestListSheet(
        title     = "История",
        empty     = "Пополнений, выводов и переводов между счетами пока не было.",
        // Подсказка — только когда есть что нажимать: строки примера не нажимаются.
        hint      = if (portfolio.movements.any { it.id != null }) "Нажмите на операцию, чтобы удалить её." else null,
        count     = feed.size,
        onDismiss = onDismiss,
    ) { index -> val (_, e) = feed[index]; FeedRow(e, portfolio, onEventClick) }
}

/** «Заявки» — активные сверху, затем исполненные и отменённые. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestOrdersSheet(portfolio: Portfolio.Result, onEventClick: (com.financeos.hub.core.invest.BrokerEvent) -> Unit, onDismiss: () -> Unit) {
    val orders = portfolio.activeOrders + portfolio.history
    InvestListSheet(
        title     = "Заявки и сделки",
        empty     = "Заявок пока не было.",
        hint      = if (portfolio.history.any { it.id != null }) "Нажмите на исполненную или отменённую сделку, чтобы удалить её." else null,
        count     = orders.size,
        onDismiss = onDismiss,
    ) { index -> OrderRow(orders[index], onEventClick) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvestListSheet(
    title    : String,
    empty    : String,
    hint     : String?,
    count    : Int,
    onDismiss: () -> Unit,
    row      : @Composable (Int) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = FosColors.Surface) {
        LazyColumn(
            modifier            = Modifier.fillMaxWidth(),
            contentPadding      = PaddingValues(start = FosDimens.ScreenPadding, end = FosDimens.ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "title") { Text(title, style = FosType.ScreenTitle, color = FosColors.TextPrimary) }
            if (count > 0 && hint != null) item(key = "hint") { Text(hint, style = FosType.Micro, color = FosColors.TextMuted) }
            if (count == 0) {
                item(key = "empty") {
                    Text(empty, style = FosType.Body, color = FosColors.TextMuted, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            // Индекс в ключе: один и тот же пуш может прийти дважды, одинаковый ключ уронил бы список.
            itemsIndexed(List(count) { it }, key = { i, _ -> "row_$i" }) { i, _ -> row(i) }
        }
    }
}
