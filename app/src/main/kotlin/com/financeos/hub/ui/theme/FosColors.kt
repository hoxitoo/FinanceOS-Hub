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
}
