package com.financeos.hub.ui.theme

import androidx.compose.ui.graphics.Color
import com.financeos.hub.core.bank.BankRegistry

/**
 * Brand colour for an account card.
 * @param bg   card background (the bank's brand colour)
 * @param onBg text/content colour with good contrast on [bg]
 */
data class BankBrand(val bg: Color, val onBg: Color)

private val WHITE = Color(0xFFFFFFFF)
private val DARK  = Color(0xFF14181F)

/**
 * Maps a bank name (free-form, as stored on the account) to its brand colours.
 * Matching is case-insensitive and substring-based so "Сбербанк", "Сбер",
 * "SBER" all resolve to the same green. The banks themselves live in [BankRegistry].
 */
fun bankBrand(bank: String): BankBrand {
    val spec = BankRegistry.find(bank)
        ?: return BankBrand(Color(BankRegistry.UNKNOWN_ARGB), WHITE)   // neutral slate for unknown banks
    return BankBrand(Color(spec.brandArgb), if (spec.lightBrand) DARK else WHITE)
}
