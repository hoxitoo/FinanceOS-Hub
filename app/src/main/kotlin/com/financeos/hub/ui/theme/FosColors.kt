package com.financeos.hub.ui.theme

import androidx.compose.ui.graphics.Color

object FosColors {
    // Фоны
    val Background  = Color(0xFF0A0D12)
    val Surface     = Color(0xFF111620)
    val Surface2    = Color(0xFF181E2A)
    val Border      = Color(0xFF1E2738)
    val BorderStrong = Color(0xFF3D4860)   // более контрастная граница для полей ввода

    // Акценты — строго семантические
    val Positive    = Color(0xFF4DFFA0)   // доход, рост, успех, savings
    val Negative    = Color(0xFFFF6B6B)   // расход, тревога, превышение
    val Warning     = Color(0xFFFFB84D)   // предупреждение, прогресс 70-90%
    val Info        = Color(0xFF4D9FFF)   // информация, ссылки

    // Текст
    val TextPrimary   = Color(0xFFE8ECF4)
    val TextSecondary = Color(0xFF7A8499)
    val TextMuted     = Color(0xFF5A6478)
    val TextDark      = Color(0xFF3A4358)

    // Полупрозрачные оверлеи (для фонов иконок, бейджей)
    fun positive(alpha: Float) = Positive.copy(alpha = alpha)
    fun negative(alpha: Float) = Negative.copy(alpha = alpha)
    fun warning(alpha: Float)  = Warning.copy(alpha = alpha)
    fun info(alpha: Float)     = Info.copy(alpha = alpha)
    fun border(alpha: Float)   = Border.copy(alpha = alpha)

    /**
     * Палитра темы «Мерцание» (Shimmer / биолюминесценция) — слой-надстройка, НЕ вторая тема.
     *
     * Только декоративные значения: глубокий фон и «glow»-акценты для частиц/свечения/стекла.
     * Семантические токены (Positive/Negative/Warning/Info) НЕ дублируются и НЕ переопределяются —
     * единственное тёпло-зелёное свечение в UI остаётся [Positive].
     *
     * Циан намеренно НЕ используется: он читается глазом как доход (#4DFFA0). Декоративные glow
     * уведены в индиго/фиолет/розовый — физически далеко от green(доход) и red(расход), поэтому
     * атмосферный свет и семантика никогда не путаются.
     */
    object Shimmer {
        val Background = Color(0xFF05070F)   // глубже базового #0A0D12 — «погружение»
        val Surface    = Color(0xFF0A0A20)
        val Surface2   = Color(0xFF0D0D25)

        val GlowIndigo = Color(0xFF6E7BFF)   // основной декоративный акцент
        val GlowViolet = Color(0xFF9B5CFF)
        val GlowPink   = Color(0xFFFF77C2)   // «медуза»

        /** Стеклянные поверхности — ультра-прозрачная белая заливка + тонкая граница. */
        val GlassFill   = Color(0x0AFFFFFF)  // ~4% белого
        val GlassFillLo = Color(0x03FFFFFF)  // ~1% белого
        val GlassBorder = Color(0x14FFFFFF)  // ~8% белого

        fun glowIndigo(alpha: Float) = GlowIndigo.copy(alpha = alpha)
        fun glowViolet(alpha: Float) = GlowViolet.copy(alpha = alpha)
        fun glowPink(alpha: Float)   = GlowPink.copy(alpha = alpha)
    }
}
