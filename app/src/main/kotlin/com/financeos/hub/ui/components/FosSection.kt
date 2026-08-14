package com.financeos.hub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType

/**
 * Section heading: a coloured tick, the label, and a hairline rule running to the right edge.
 *
 * A bare uppercase caption floating above a card is easy to miss on a long scroll — it reads as
 * part of the card below it rather than as a divider between two groups. The rule gives the eye a
 * horizontal line to catch, and the tick carries the section's tone where it has one, so «Расходы»
 * and «Доходы» are told apart before either word is read.
 */
@Composable
fun FosSectionHeader(
    title   : String,
    modifier: Modifier = Modifier,
    tone    : FosTone  = FosTone.Neutral,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier          = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val accent = tone.accent
        if (accent != null) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 11.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(title, style = FosType.SectionCap, color = FosColors.TextMuted)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(FosColors.Border)
        )
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/**
 * A «?» that opens a plain-language explanation of the number above it.
 *
 * The app already refuses to print a figure it cannot justify. This is the other half of that:
 * where a figure IS shown, the reader can ask how it was reached without leaving the screen. Kept
 * collapsed by default — an explanation permanently on screen becomes furniture and stops being
 * read.
 */
@Composable
fun FosExplain(
    text    : String,
    modifier: Modifier = Modifier,
    label   : String   = "как это считается",
) {
    // Declared before any early return and never conditionally — the slot table must not change
    // shape when the panel toggles (Rules of Hooks).
    var open by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(FosDimens.RadiusIcon))
                .clickable { open = !open }
                .padding(vertical = 3.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(FosColors.info(0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("?", style = FosType.Micro, color = FosColors.Info)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                if (open) "свернуть" else label,
                style = FosType.Micro,
                color = FosColors.Info,
            )
        }
        AnimatedVisibility(
            visible = open,
            enter   = fadeIn() + expandVertically(),
            exit    = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(FosDimens.RadiusInset))
                    .background(FosColors.SurfaceSunken)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text, style = FosType.Micro, color = FosColors.TextSecondary)
            }
        }
    }
}

/** Hairline divider that matches the card borders, for splitting content inside one card. */
@Composable
fun FosHairline(modifier: Modifier = Modifier, color: Color = FosColors.Border) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}
