package com.financeos.hub.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object FosType {
    // Крупные суммы (Net Worth, баланс счёта)
    val HeroAmount = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.4).sp,
        fontFeatureSettings = "tnum"
    )

    // Заголовки экранов
    val ScreenTitle = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.6).sp
    )

    // Карточные суммы
    val CardAmount = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = "tnum"
    )

    // Строки транзакций
    val TxMerchant = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    val TxAmount   = TextStyle(
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum"
    )

    // Лейблы и мета
    val Label      = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
    val Micro      = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
    val SectionCap = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)

    // Дополнительные стили
    val Body       = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val BodySemi   = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val SmallBold  = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum"
    )
    val SubHeader  = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)

    // Иконки (глифы) — нижняя панель навигации и кнопки-действия в шапке
    val NavIcon    = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Medium)
    val IconAction = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Bold)
    val HeroLarge  = TextStyle(
        fontSize = 40.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.8).sp,
        fontFeatureSettings = "tnum"
    )
    val HeroMinimal = TextStyle(
        fontSize = 42.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-2.0).sp,
        fontFeatureSettings = "tnum"
    )

    // Compact multi-currency: 3 lines don't overflow at any screen width
    val HeroAmountMulti = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.8).sp,
        fontFeatureSettings = "tnum"
    )

    // Bank symbol badge inside the card (letter abbreviation)
    val BadgeSymbol = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
}
