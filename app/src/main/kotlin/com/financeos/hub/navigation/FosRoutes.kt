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
}
