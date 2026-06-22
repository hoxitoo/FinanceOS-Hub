package com.financeos.hub.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavType
import androidx.navigation.navArgument
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
import com.financeos.hub.features.categories.CategoriesScreen
import com.financeos.hub.features.dashboard.DashboardScreen
import com.financeos.hub.features.subscriptions.SubscriptionsScreen
import com.financeos.hub.features.goals.GoalsScreen
import com.financeos.hub.features.onboarding.OnboardingScreen
import com.financeos.hub.features.settings.SettingsScreen
import com.financeos.hub.features.transactions.TransactionsScreen
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.LocalShimmer

@Composable
fun FosNavHost(initialDeepRoute: String? = null) {
    val navController = rememberNavController()
    // «Анимации» layer: emerge-from-dark fade + slight scale on screen change; off = instant.
    val transitions = LocalShimmer.current.screenTransitions
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Navigate to deep-link target once nav graph is ready.
    // Re-validate here too — navigating to an unknown route would crash.
    LaunchedEffect(initialDeepRoute) {
        FosRoute.sanitizeDeepLink(initialDeepRoute)?.let { route ->
            navController.navigate(route) {
                popUpTo(FosRoute.Dashboard.route) { saveState = true }
                launchSingleTop = true
                restoreState    = true
            }
        }
    }

    // destination.route returns the template string for routes with args
    val showBottomBar = currentRoute != null && (
        currentRoute == FosRoute.Dashboard.route ||
        currentRoute.startsWith(FosRoute.Transactions.route) ||
        currentRoute == FosRoute.Analytics.route ||
        currentRoute == FosRoute.Budget.route ||
        currentRoute == FosRoute.Goals.route
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
            enterTransition  = {
                if (transitions) fadeIn(tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220))
                else EnterTransition.None
            },
            exitTransition   = {
                if (transitions) fadeOut(tween(160)) else ExitTransition.None
            },
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
            composable(
                route     = FosRoute.Transactions.routeWithArgs,
                arguments = listOf(
                    navArgument("categoryId") {
                        type         = NavType.StringType
                        nullable     = true
                        defaultValue = null
                    }
                ),
            ) { TransactionsScreen() }
            composable(FosRoute.Analytics.route)    { AnalyticsScreen() }
            composable(FosRoute.Budget.route) {
                BudgetScreen(
                    onSubscriptionsClick = { navController.navigate(FosRoute.Subscriptions.route) },
                )
            }
            composable(FosRoute.Goals.route)        { GoalsScreen() }
            composable(FosRoute.Settings.route)     {
                SettingsScreen(
                    onBack            = { navController.popBackStack() },
                    onCategoriesClick = { navController.navigate(FosRoute.Categories.route) },
                )
            }
            composable(FosRoute.Categories.route) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }
            composable(FosRoute.Subscriptions.route) {
                SubscriptionsScreen(
                    onBack         = { navController.popBackStack() },
                    onCategoryClick = { catId ->
                        navController.navigate(FosRoute.Transactions.withCategory(catId)) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                )
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
            val selected = currentRoute == item.route ||
                currentRoute?.startsWith(item.route + "?") == true
            NavigationBarItem(
                selected = selected,
                onClick  = { onNavigate(item.route) },
                icon     = {
                    Text(
                        text  = navEmoji(item.icon),
                        style = FosType.NavIcon,
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
