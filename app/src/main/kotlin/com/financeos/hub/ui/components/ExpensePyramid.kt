package com.financeos.hub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

private val MANDATORY_CATS    = setOf("cat_housing", "cat_telecom", "cat_health")
private val REGULAR_CATS      = setOf("cat_food", "cat_grocery", "cat_transport")
// Everything else → Discretionary

/**
 * 3-tier expense pyramid: Mandatory → Regular → Discretionary.
 * Rendered as stacked horizontal bars (widest = mandatory = base).
 */
@Composable
fun ExpensePyramid(
    categoryExpenses: Map<String, Long>,
    categoryNames   : Map<String, String>,
    modifier        : Modifier = Modifier,
) {
    val mandatory = remember(categoryExpenses) {
        categoryExpenses.filter { it.key in MANDATORY_CATS }.values.sum()
    }
    val regular = remember(categoryExpenses) {
        categoryExpenses.filter { it.key in REGULAR_CATS }.values.sum()
    }
    val discretionary = remember(categoryExpenses) {
        categoryExpenses.filterNot { it.key in MANDATORY_CATS || it.key in REGULAR_CATS }.values.sum()
    }
    val total = (mandatory + regular + discretionary).coerceAtLeast(1L)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCard))
            .background(FosColors.Surface)
            .padding(FosDimens.CardPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("СТРУКТУРА РАСХОДОВ", style = FosType.SectionCap, color = FosColors.TextMuted)
        Spacer(Modifier.height(2.dp))

        // Top tier — Discretionary (smallest, narrowest visual bar)
        PyramidTier(
            label      = "Дискреционные",
            sublabel   = "покупки, развлечения",
            kopecks    = discretionary,
            total      = total,
            fillRatio  = (discretionary.toFloat() / total).coerceIn(0f, 1f),
            color      = FosColors.Info,
            barWidthFraction = 0.65f,
        )

        // Mid tier — Regular
        PyramidTier(
            label      = "Регулярные",
            sublabel   = "еда, транспорт",
            kopecks    = regular,
            total      = total,
            fillRatio  = (regular.toFloat() / total).coerceIn(0f, 1f),
            color      = FosColors.Warning,
            barWidthFraction = 0.82f,
        )

        // Base tier — Mandatory (widest)
        PyramidTier(
            label      = "Обязательные",
            sublabel   = "жильё, связь, здоровье",
            kopecks    = mandatory,
            total      = total,
            fillRatio  = (mandatory.toFloat() / total).coerceIn(0f, 1f),
            color      = FosColors.Negative,
            barWidthFraction = 1f,
        )
    }
}

@Composable
private fun PyramidTier(
    label           : String,
    sublabel        : String,
    kopecks         : Long,
    total           : Long,
    fillRatio       : Float,
    color           : Color,
    barWidthFraction: Float,
) {
    val pct = (kopecks.toFloat() / total * 100).toInt()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Bottom,
        ) {
            Column {
                Text(label,    style = FosType.BodySemi, color = FosColors.TextPrimary)
                Text(sublabel, style = FosType.Micro,    color = FosColors.TextMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(FosFormatter.compact(kopecks), style = FosType.SmallBold, color = color)
                Text("$pct%", style = FosType.Micro, color = FosColors.TextMuted)
            }
        }
        Spacer(Modifier.height(4.dp))
        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth(barWidthFraction)
                .height(6.dp)
                .clip(RoundedCornerShape(FosDimens.RadiusBar))
                .background(FosColors.Surface2),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillRatio / barWidthFraction.coerceAtLeast(0.01f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(FosDimens.RadiusBar))
                    .background(color.copy(alpha = 0.8f)),
            )
        }
    }
}
