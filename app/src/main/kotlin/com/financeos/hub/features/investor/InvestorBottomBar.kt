package com.financeos.hub.features.investor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeos.hub.navigation.FosRoute
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosType

private data class InvestTab(val route: String, val label: String, val glyph: String)

/** Решение пользователя (#50): пять вкладок, «Портфель» — посередине. */
private val LEFT = listOf(
    InvestTab(FosRoute.InvestOps.route,       "Операции",  "≡"),
    InvestTab(FosRoute.InvestAnalytics.route, "Аналитика", "∿"),
)
private val RIGHT = listOf(
    InvestTab(FosRoute.InvestCalendar.route,  "Календарь", "▦"),
    InvestTab(FosRoute.InvestAccounts.route,  "Счета",     "▤"),
)

/**
 * Нижняя панель режима «Инвестор» — своя, не вкладки кошелька (#44, #50).
 *
 * «Портфель» стоит посередине, крупнее остальных и всегда подсвечен индиго режима — это главный
 * экран инвестора, и до него один палец из любой вкладки. Выбранная вкладка — индиго, остальные —
 * приглушённые; мятный не используется (он значит «доход», #1). Пока открыто предупреждение
 * брокера, на «Портфеле» горит янтарная точка — карточка предупреждения живёт там.
 */
@Composable
fun InvestorBottomBar(currentRoute: String?, alert: Boolean, onNavigate: (String) -> Unit) {
    NavigationBar(containerColor = FosColors.Surface, tonalElevation = 0.dp) {
        LEFT.forEach { Tab(it, currentRoute, onNavigate) }
        PortfolioTab(
            selected = currentRoute == FosRoute.Dashboard.route,
            alert    = alert,
            onClick  = { onNavigate(FosRoute.Dashboard.route) },
            // По центру строки — через align, НЕ fillMaxHeight: у NavigationBar только МИНИМАЛЬНАЯ
            // высота, и fillMaxHeight растягивал панель на весь экран, закрывая всё приложение.
            modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
        )
        RIGHT.forEach { Tab(it, currentRoute, onNavigate) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tab(
    tab: InvestTab,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBarItem(
        selected = currentRoute == tab.route,
        onClick  = { onNavigate(tab.route) },
        icon     = { Text(tab.glyph, style = FosType.NavIcon) },
        label    = { Text(tab.label, style = FosType.Micro, maxLines = 1) },
        colors   = NavigationBarItemDefaults.colors(
            selectedIconColor   = FosColors.Invest,
            selectedTextColor   = FosColors.Invest,
            unselectedIconColor = FosColors.TextMuted,
            unselectedTextColor = FosColors.TextMuted,
            indicatorColor      = FosColors.Surface2,
        ),
    )
}

/** Центральная кнопка: круг индиго крупнее значков соседей, подпись под ним. */
@Composable
private fun PortfolioTab(selected: Boolean, alert: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                role              = Role.Tab,
                onClick           = onClick,
            )
            .semantics {
                contentDescription = if (alert) "Портфель, есть предупреждение брокера" else "Портфель"
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .shadow(if (selected) 10.dp else 4.dp, CircleShape, ambientColor = FosColors.Invest, spotColor = FosColors.Invest)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFB4BEFF), Color(0xFF6E7EEA))))
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent)),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("◈", style = FosType.NavIcon.copy(fontSize = 26.sp), color = Color(0xFF0E1220))
            }
            if (alert) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(FosColors.Warning),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "Портфель",
            style = FosType.Micro.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) FosColors.Invest else FosColors.TextSecondary,
            maxLines = 1,
        )
    }
}
