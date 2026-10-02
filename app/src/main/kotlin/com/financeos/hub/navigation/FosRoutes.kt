package com.financeos.hub.navigation

sealed class FosRoute(val route: String) {
    object Onboarding   : FosRoute("onboarding")
    object Dashboard    : FosRoute("dashboard")
    object Transactions : FosRoute("transactions") {
        /** Composable registration template — optional categoryId query param */
        const val routeWithArgs = "transactions?categoryId={categoryId}"
        /** Navigate to transactions pre-filtered by category */
        fun withCategory(categoryId: String) = "transactions?categoryId=$categoryId"
    }
    object Analytics    : FosRoute("analytics")
    object Budget       : FosRoute("budget")
    object Goals        : FosRoute("goals")
    object Settings     : FosRoute("settings")
    object Categories   : FosRoute("categories")
    object Subscriptions: FosRoute("subscriptions")
    object CreditCards  : FosRoute("credit")
    object Calculator   : FosRoute("calculator")
    object Calendar     : FosRoute("calendar")
    object Lifetime     : FosRoute("lifetime")
    // Вкладки режима «Инвестор» (#50). «Портфель» — это Dashboard в режиме инвестора.
    object InvestOps      : FosRoute("invest_ops")
    object InvestAnalytics: FosRoute("invest_analytics")
    object InvestCalendar : FosRoute("invest_calendar")
    object InvestAccounts : FosRoute("invest_accounts")

    companion object {
        /** Routes that may be opened via an external deep link (notification intent). */
        private val deepLinkable = setOf(
            "dashboard", "transactions", "analytics", "budget",
            "goals", "settings", "categories", "subscriptions", "credit", "calculator",
            "calendar", "lifetime",
        )

        /**
         * Validates an externally-supplied deep-link route before navigation.
         * MainActivity is exported, so the route string is attacker-controllable;
         * navigating to an unknown destination would crash with IllegalArgumentException.
         */
        fun sanitizeDeepLink(route: String?): String? {
            if (route.isNullOrBlank()) return null
            return if (route.substringBefore('?') in deepLinkable) route else null
        }
    }
}
