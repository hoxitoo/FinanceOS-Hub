package com.financeos.hub.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Палитра на случай, если сохранённый цвет категории не разбирается. Красного в ней нет: красный
 * занят тратами (правило #2), и категория им окрашенная читалась бы как ошибка.
 */
private val CATEGORY_FALLBACK = listOf(
    Color(0xFFFFB84D), Color(0xFF4D9FFF), Color(0xFF9B5CFF), Color(0xFF2DD4BF),
    Color(0xFFFF87C2), Color(0xFF60A5FA), Color(0xFFFB923C), Color(0xFF34D399),
    Color(0xFFA78BFA), Color(0xFFE879F9), Color(0xFF94A3B8), Color(0xFFFACC15),
)

/**
 * Цвет категории — один на всё приложение.
 *
 * Запасной цвет выбирается по САМОЙ категории ([key]), а не по месту в списке: у вкладки
 * «Категории» и экрана «За всё время» списки разные, и по месту одна и та же категория на двух
 * экранах получала бы разные цвета. Раньше палитр было две, и они уже разошлись.
 */
fun categoryColor(hex: String?, key: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }
        .getOrElse { CATEGORY_FALLBACK[Math.floorMod((key ?: "").hashCode(), CATEGORY_FALLBACK.size)] }
