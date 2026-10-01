package com.financeos.hub.features.investor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

private val SEGMENT_PAD = 10.dp
private const val WALLET   = "Кошелёк"
private const val INVESTOR = "Инвестор"

/**
 * «Кошелёк | Инвестор» — что показывает главная.
 *
 * Выбранный «Инвестор» горит своим индиго, а «Кошелёк» — нейтрально: мятный в этом приложении
 * значит «доход» (правило #1), и красить им режим нельзя.
 *
 * Шапка главной делит ширину с заголовком, котом и шестерёнкой, а шрифт может быть увеличен в
 * системе. Обрезанное «Инвесто» хуже значка, поэтому переключатель меряет свои подписи и, если они
 * не помещаются, показывает значки 👛 / 📈 (подписи остаются для TalkBack).
 */
@Composable
fun ModeSwitch(
    investor : Boolean,
    onChange : (Boolean) -> Unit,
    modifier : Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density  = LocalDensity.current
    // Ширина обеих подписей с отступами сегментов, внутренней рамкой и самой рамкой. От плотности
    // зависит через масштаб шрифта: увеличили шрифт в системе — пересчитали.
    val fullWidth = remember(measurer, density) {
        val px = measurer.measure(WALLET, FosType.Label).size.width +
            measurer.measure(INVESTOR, FosType.Label).size.width
        with(density) { px.toDp() } + SEGMENT_PAD * 4 + 4.dp + 2.dp
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val compact = maxWidth < fullWidth
        val shape = RoundedCornerShape(FosDimens.RadiusChip)
        Row(
            modifier = Modifier
                .clip(shape)
                .background(FosColors.Surface2)
                .border(1.dp, if (investor) FosColors.Invest.copy(alpha = 0.35f) else FosColors.Border, shape)
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Segment(WALLET, if (compact) "👛" else WALLET, selected = !investor,
                accent = FosColors.TextPrimary, fill = FosColors.SurfaceRaised) { onChange(false) }
            Segment(INVESTOR, if (compact) "📈" else INVESTOR, selected = investor,
                accent = FosColors.Invest, fill = FosColors.Invest.copy(alpha = 0.18f)) { onChange(true) }
        }
    }
}

@Composable
private fun Segment(
    name    : String,
    label   : String,
    selected: Boolean,
    accent  : Color,
    fill    : Color,
    onClick : () -> Unit,
) {
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
            .semantics { contentDescription = name }
            // Высота как у чипов фильтров (~32 dp). Растянуть до 48 dp через
            // minimumInteractiveComponentSize нельзя: сегменты внутри общей рамки, и она раздулась бы
            // выше заголовка «Главная».
            .padding(horizontal = SEGMENT_PAD, vertical = 8.dp),
    )
}
