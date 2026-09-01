package com.financeos.hub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.bankBrand

/**
 * One selectable money-source in the picker: a specific card (or the account itself when it has
 * no card). Several options can share the same [accountId] — a second card on an account is the
 * same balance, so picking either attributes the operation to that one account; [mask] only records
 * which physical card it left from.
 */
data class SourceOption(
    val accountId     : String,
    val accountName   : String,
    val bank          : String,
    val mask          : String?,
    val balanceKopecks: Long,
    val currency      : String,
) {
    val key: String get() = "${accountId}_${mask ?: "_"}"
}

/**
 * Two-step money-source picker: banks first, then that bank's accounts (mask + balance).
 *
 * Replaces the single horizontal strip of every card, which forced a long scroll to find the right
 * one — the slowest step when logging a transfer the bank never pushed. The selected bank expands
 * automatically, and picking an account collapses the list back to a compact summary.
 */
@Composable
fun AccountPicker(
    title      : String,
    options    : List<SourceOption>,
    selectedKey: String?,
    accent     : Color,
    onSelect   : (String?) -> Unit,
) {
    val selected = options.firstOrNull { it.key == selectedKey }
    val banks    = remember(options) { options.groupBy { it.bank }.toList() }
    // Start expanded on the selected bank; null = nothing expanded.
    var expandedBank by remember(selectedKey) { mutableStateOf(selected?.bank) }

    Text(title, style = FosType.SectionCap, color = FosColors.TextMuted)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        banks.forEach { (bank, bankOptions) ->
            val brand      = bankBrand(bank)
            val isExpanded = expandedBank == bank
            val hasPick    = bankOptions.any { it.key == selectedKey }

            // Bank row — tap to reveal its accounts.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
                    .background(if (hasPick) accent.copy(alpha = 0.10f) else FosColors.Surface2)
                    .clickable { expandedBank = if (isExpanded) null else bank }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                // Brand badge: first letter of the bank on its brand colour.
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brand.bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        bank.trim().take(1).uppercase(),
                        style = FosType.SmallBold,
                        color = brand.onBg,
                    )
                }
                Text(
                    bank,
                    style    = FosType.Body,
                    color    = FosColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                // Collapsed + chosen → show which account is picked, so the choice stays visible.
                if (hasPick && !isExpanded && selected != null) {
                    Text(
                        selected.mask?.let { "••$it" } ?: selected.accountName,
                        style = FosType.Micro,
                        color = accent,
                    )
                }
                Text(if (isExpanded) "▲" else "▼", style = FosType.Micro, color = FosColors.TextMuted)
            }

            if (isExpanded) {
                bankOptions.forEach { opt ->
                    val isPicked = opt.key == selectedKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp)
                            .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
                            .background(if (isPicked) accent.copy(alpha = 0.16f) else FosColors.Surface)
                            .clickable { onSelect(if (isPicked) null else opt.key) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                opt.accountName,
                                style    = FosType.Body,
                                color    = if (isPicked) accent else FosColors.TextPrimary,
                                maxLines = 1,
                            )
                            opt.mask?.let {
                                Text("••$it", style = FosType.Micro, color = FosColors.TextSecondary)
                            }
                        }
                        // Balance makes "which account do I actually have money on?" obvious.
                        Text(
                            FosFormatter.compact(
                                opt.balanceKopecks,
                                FosFormatter.currencySymbol(opt.currency),
                            ),
                            style = FosType.SmallBold,
                            color = if (opt.balanceKopecks >= 0) FosColors.TextSecondary else FosColors.Negative,
                        )
                    }
                }
            }
        }
    }
}
