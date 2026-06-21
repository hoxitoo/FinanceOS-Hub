package com.financeos.hub.ui.theme

import androidx.compose.ui.graphics.Color

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
 * "SBER" all resolve to the same green.
 */
fun bankBrand(bank: String): BankBrand {
    val b = bank.lowercase()
    return when {
        "сбер" in b || "sber" in b ->
            BankBrand(Color(0xFF1A9F29), WHITE)
        "т-банк" in b || "т банк" in b || "тинь" in b || "tinkoff" in b || "tbank" in b ->
            BankBrand(Color(0xFFFFDD2D), DARK)
        "втб" in b || "vtb" in b ->
            BankBrand(Color(0xFF009FDF), WHITE)
        "альфа" in b || "alfa" in b || "alpha" in b ->
            BankBrand(Color(0xFFEF3124), WHITE)
        "газпром" in b || "gazprom" in b || "гпб" in b ->
            BankBrand(Color(0xFF1F4C92), WHITE)
        "райф" in b || "raiff" in b ->
            BankBrand(Color(0xFFFEE600), DARK)
        "росбанк" in b || "rosbank" in b ->
            BankBrand(Color(0xFFC8102E), WHITE)
        "открыт" in b || "otkritie" in b ->
            BankBrand(Color(0xFF00AEEF), WHITE)
        "мтс" in b || "mts" in b ->
            BankBrand(Color(0xFFE30611), WHITE)
        "почта" in b || "posta" in b || "post bank" in b ->
            BankBrand(Color(0xFF1A468C), WHITE)
        "россельхоз" in b || "рсхб" in b || "rshb" in b ->
            BankBrand(Color(0xFF006B3F), WHITE)
        "мбанк" in b || "mbank" in b || "кыргыз" in b ->
            BankBrand(Color(0xFF0076BE), WHITE)
        else ->
            BankBrand(Color(0xFF3A4358), WHITE)   // neutral slate for unknown banks
    }
}
