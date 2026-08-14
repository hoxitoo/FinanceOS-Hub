package com.financeos.hub.ui.theme

import androidx.compose.ui.unit.dp

object FosDimens {
    val ScreenPadding    = 16.dp
    val CardPadding      = 16.dp
    val CardPaddingSmall = 14.dp
    val CardGap          = 10.dp
    val ItemGap          = 8.dp
    val SectionGap       = 20.dp

    // ── Радиусы: огранка по роли ─────────────────────────────────────────────
    // Разный радиус — половина того, что отличает главный блок от рядового. Когда всё скруглено
    // одинаково, экран читается как один список; когда радиус растёт вместе с важностью,
    // иерархия видна до чтения текста.
    val RadiusHero       = 24.dp   // главный блок экрана
    val RadiusCard       = 18.dp   // обычная карточка
    val RadiusCardSmall  = 14.dp   // плитка в сетке
    val RadiusInset      = 10.dp   // вложенный ряд внутри карточки
    val RadiusChip       = 20.dp
    val RadiusIcon       = 10.dp
    val RadiusButton     = 12.dp
    val RadiusBar        = 5.dp

    /** Толщина акцентного канта слева у тонированной карточки. */
    val RailWidth        = 3.dp

    // Component sizes
    val IconSizeSm       = 32.dp
    val IconSizeMd       = 36.dp
    val IconSizeLg       = 40.dp
    val NavBarIconSize   = 21.dp
    val NavBarPillW      = 54.dp
    val NavBarPillH      = 28.dp
    val NavBarPillRadius = 14.dp
    val AvatarSize       = 40.dp

    // Progress bars
    val BarHeightLg  = 8.dp
    val BarHeightMd  = 6.dp
    val BarHeightSm  = 5.dp
}
