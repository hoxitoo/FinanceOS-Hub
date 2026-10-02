package com.financeos.hub.features.investor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.invest.Portfolio
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

/**
 * «Выбор счёта» — как в приложении брокера: весь портфель или один счёт. Счета узнаются из пушей
 * (номер и название в скобках), поэтому здесь только те, о которых брокер хоть раз написал.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrokerAccountSheet(
    contracts: List<Portfolio.Contract>,
    selected : String?,
    onSelect : (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = FosColors.Surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FosDimens.ScreenPadding)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Выбор счёта", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
            AccountOption("Весь портфель", null, selected == null) { onSelect(null) }
            contracts.forEach { c ->
                AccountOption(c.title, c.broker, selected == c.key) { onSelect(c.key) }
            }
            Text(
                "Счета появляются здесь, когда брокер упоминает их в пуше. Остатка по каждому счёту нет — " +
                    "сделки приходят без номера счёта.",
                style    = FosType.Micro,
                color    = FosColors.TextMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun AccountOption(title: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusChip))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FosType.BodySemi, color = FosColors.TextPrimary)
            sub?.let { Text(it, style = FosType.Micro, color = FosColors.TextSecondary) }
        }
        RadioButton(
            selected = selected,
            onClick  = onClick,
            colors   = RadioButtonDefaults.colors(selectedColor = FosColors.Invest),
        )
    }
}
