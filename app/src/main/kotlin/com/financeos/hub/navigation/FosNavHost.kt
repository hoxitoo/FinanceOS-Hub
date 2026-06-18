package com.financeos.hub.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.financeos.hub.features.analytics.AnalyticsScreen
import com.financeos.hub.features.budget.BudgetScreen
import com.financeos.hub.features.dashboard.DashboardScreen
import com.financeos.hub.features.goals.GoalsScreen
import com.financeos.hub.features.onboarding.OnboardingScreen
import com.financeos.hub.features.settings.SettingsScreen
import com.financeos.hub.features.transactions.TransactionsScreen
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosType

@Composable
fun FosNavHost(initialDeepRoute: String? = null) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Navigate to deep-link target once nav graph is ready
    LaunchedEffect(initialDeepRoute) {
        if (initialDeepRoute != null) {
            navController.navigate(initialDeepRoute) {
                popUpTo(FosRoute.Dashboard.route) { saveState = true }
                launchSingleTop = true
                restoreState    = true
            }
        }
    }

    val showBottomBar = currentRoute in listOf(
        FosRoute.Dashboard.route,
        FosRoute.Transactions.route,
        FosRoute.Analytics.route,
        FosRoute.Budget.route,
        FosRoute.Goals.route,
    )

    Scaffold(
        containerColor = FosColors.Background,
        bottomBar = {
            if (showBottomBar) {
                FosBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }
        }
    ) { inner ->
        NavHost(
            navController    = navController,
            startDestination = FosRoute.Onboarding.route,
            modifier         = Modifier.padding(inner),
        ) {
            composable(FosRoute.Onboarding.route) {
                OnboardingScreen(onFinished = {
                    navController.navigate(FosRoute.Dashboard.route) {
                        popUpTo(FosRoute.Onboarding.route) { inclusive = true }
                    }
                })
            }
            composable(FosRoute.Dashboard.route)    {
                DashboardScreen(onSettingsClick = {
                    navController.navigate(FosRoute.Settings.route)
                })
            }
            composable(FosRoute.Transactions.route) { TransactionsScreen() }
            composable(FosRoute.Analytics.route)    { AnalyticsScreen() }
            composable(FosRoute.Budget.route)       { BudgetScreen() }
            composable(FosRoute.Goals.route)        { GoalsScreen() }
            composable(FosRoute.Settings.route)     {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private data class NavItem(val route: String, val label: String, val icon: String)

private val NAV_ITEMS = listOf(
    NavItem(FosRoute.Dashboard.route,    "Главная",    "home"),
    NavItem(FosRoute.Transactions.route, "Операции",   "list"),
    NavItem(FosRoute.Analytics.route,    "Аналитика",  "chart"),
    NavItem(FosRoute.Budget.route,       "Бюджет",     "budget"),
    NavItem(FosRoute.Goals.route,        "Цели",       "goal"),
)

@Composable
private fun FosBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar(
        containerColor = FosColors.Surface,
        tonalElevation = 0.dp,
    ) {
        NAV_ITEMS.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick  = { onNavigate(item.route) },
                icon     = {
                    Text(
                        text  = navEmoji(item.icon),
                        style = FosType.Label,
                    )
                },
                label    = {
                    Text(
                        text  = item.label,
                        style = FosType.Micro,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor       = FosColors.Positive,
                    selectedTextColor       = FosColors.Positive,
                    unselectedIconColor     = FosColors.TextMuted,
                    unselectedTextColor     = FosColors.TextMuted,
                    indicatorColor          = FosColors.Surface2,
                ),
            )
        }
    }
}

private fun navEmoji(icon: String): String = when (icon) {
    "home"   -> "⊞"
    "list"   -> "≡"
    "chart"  -> "∿"
    "budget" -> "▣"
    "goal"   -> "◎"
    else     -> "•"
}
