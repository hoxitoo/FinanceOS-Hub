package com.financeos.hub.features.investor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosType

private const val WALLET   = "Кошелёк"
private const val INVESTOR = "Инвестор"

private val HEIGHT      = 38.dp
private val INSET       = 3.dp          // зазор между ползунком и краем дорожки
private val SEGMENT_PAD = 14.dp
private val LABEL       = FosType.Label.copy(fontWeight = FontWeight.Bold)

// Цвета ползунка по режиму: верх и низ градиента — объём, светлый блик сверху, тень снизу.
// «Кошелёк» — светлое серебро, «Инвестор» — индиго режима. Мятный не берём: он значит «доход» (#1).
private val WalletTop    = Color(0xFFF2F4F8)
private val WalletBottom = Color(0xFFB9C1D1)
private val InvestTop    = Color(0xFFB4BEFF)
private val InvestBottom = Color(0xFF6E7EEA)
private val OnThumb      = Color(0xFF0E1220)   // тёмная подпись на светлом ползунке — читается в обоих

/**
 * «Кошелёк | Инвестор» — что показывает главная.
 *
 * Выбранный режим — выпуклый ползунок, который переезжает под нужную подпись, и его цвет перетекает
 * вместе с ним: серебро — кошелёк, индиго — инвестор. Рамка дорожки и свечение под ползунком
 * подкрашиваются цветом режима, поэтому, что включено, видно краем глаза, не читая подписи.
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
    /**
     * Янтарная точка на «Инвестор»: брокер прислал предупреждение, требующее действия, а открыт
     * кошелёк. Только знак «загляни» — ни сумм, ни текста кошелёк не показывает (инвариант #44).
     */
    investorAttention: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val density  = LocalDensity.current
    // Ширина сегмента — по более длинной подписи: оба сегмента равны, иначе ползунок менял бы размер.
    val fullSegment = remember(measurer, density) {
        val px = maxOf(measurer.measure(WALLET, LABEL).size.width, measurer.measure(INVESTOR, LABEL).size.width)
        with(density) { px.toDp() } + SEGMENT_PAD * 2
    }
    val compactSegment = 44.dp

    // Анимации — без условий и до любого ветвления (инвариант #4).
    val thumbTop    by animateColorAsState(if (investor) InvestTop else WalletTop, tween(280), label = "thumbTop")
    val thumbBottom by animateColorAsState(if (investor) InvestBottom else WalletBottom, tween(280), label = "thumbBottom")
    val rim         by animateColorAsState(
        if (investor) FosColors.Invest.copy(alpha = 0.55f) else FosColors.BorderStrong.copy(alpha = 0.7f),
        tween(280), label = "rim",
    )
    val glow        by animateColorAsState(
        if (investor) FosColors.Invest else Color(0xFFDDE3EE), tween(280), label = "glow",
    )

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val compact = maxWidth < fullSegment * 2 + INSET * 2 + 2.dp
        val segment = if (compact) compactSegment else fullSegment
        val offset  by animateDpAsState(
            targetValue   = if (investor) segment else 0.dp,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label         = "thumbOffset",
        )
        val track = RoundedCornerShape(HEIGHT / 2)
        val pill  = RoundedCornerShape((HEIGHT - INSET * 2) / 2)

        Box(
            modifier = Modifier
                .height(HEIGHT)
                .width(segment * 2 + INSET * 2)
                .clip(track)
                // Утопленная дорожка: темнее сверху, светлее снизу — ползунок над ней выглядит выпуклым.
                .background(Brush.verticalGradient(listOf(Color(0xFF0B0F17), FosColors.Surface2)))
                .border(1.dp, rim, track)
                .padding(INSET),
        ) {
            // Ползунок: тень цвета режима, градиент сверху вниз и тонкий блик по верхней кромке.
            Box(
                modifier = Modifier
                    .offset(x = offset)
                    .width(segment)
                    .fillMaxHeight()
                    .shadow(8.dp, pill, ambientColor = glow, spotColor = glow)
                    .clip(pill)
                    .background(Brush.verticalGradient(listOf(thumbTop, thumbBottom)))
                    .border(
                        1.dp,
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.65f), Color.Transparent)),
                        pill,
                    ),
            )
            Row(Modifier.fillMaxHeight()) {
                Segment(WALLET, if (compact) "👛" else WALLET, selected = !investor, width = segment) { onChange(false) }
                Segment(
                    INVESTOR, if (compact) "📈" else INVESTOR, selected = investor, width = segment,
                    dot = investorAttention,
                ) { onChange(true) }
            }
        }
    }
}

@Composable
private fun Segment(
    name    : String,
    label   : String,
    selected: Boolean,
    width   : Dp,
    dot     : Boolean = false,
    onClick : () -> Unit,
) {
    val color by animateColorAsState(
        if (selected) OnThumb else FosColors.TextSecondary, tween(220), label = "segmentText",
    )
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(HEIGHT / 2))
            // Без ряби: подсветку нажатия даёт сам переезжающий ползунок, рябь поверх него — шум.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                role              = Role.Tab,
                onClick           = onClick,
            )
            .semantics {
                contentDescription = if (dot) "$name, есть предупреждение брокера" else name
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = LABEL, color = color, maxLines = 1)
        // Без условного хука: точка — обычный элемент, не анимация (инвариант #4 не задет).
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 7.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(FosColors.Warning),
            )
        }
    }
}
