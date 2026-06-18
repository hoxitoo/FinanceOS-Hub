package com.financeos.hub.navigation

sealed class FosRoute(val route: String) {
    object Onboarding   : FosRoute("onboarding")
    object Dashboard    : FosRoute("dashboard")
    object Transactions : FosRoute("transactions")
    object Analytics    : FosRoute("analytics")
    object Budget       : FosRoute("budget")
    object Goals        : FosRoute("goals")
    object Settings     : FosRoute("settings")
}
