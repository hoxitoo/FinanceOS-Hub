package com.financeos.hub.features.investor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

/**
 * «Кошелёк | Инвестор» — что показывает главная.
 *
 * Выбранный «Инвестор» горит своим индиго, а «Кошелёк» — нейтрально: мятный в этом приложении
 * значит «доход» (правило #1), и красить им режим нельзя.
 */
@Composable
fun ModeSwitch(
    investor : Boolean,
    onChange : (Boolean) -> Unit,
    modifier : Modifier = Modifier,
) {
    val shape = RoundedCornerShape(FosDimens.RadiusChip)
    Row(
        modifier = modifier
            .clip(shape)
            .background(FosColors.Surface2)
            .border(1.dp, if (investor) FosColors.Invest.copy(alpha = 0.35f) else FosColors.Border, shape)
            .padding(2.dp),
    ) {
        Segment("Кошелёк", selected = !investor, accent = FosColors.TextPrimary, fill = FosColors.SurfaceRaised) {
            onChange(false)
        }
        Segment("Инвестор", selected = investor, accent = FosColors.Invest, fill = FosColors.Invest.copy(alpha = 0.18f)) {
            onChange(true)
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, accent: Color, fill: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(FosDimens.RadiusChip)
    Text(
        text     = label,
        style    = FosType.Label,
        color    = if (selected) accent else FosColors.TextMuted,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) fill else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
