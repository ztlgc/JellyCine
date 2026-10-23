package com.jellycine.app.ui.screens.dashboard

import androidx.navigation.NavController

internal fun NavController.returnToDashboardHome() {
    // Back must discard the outgoing page; restoring tab state can reopen it immediately.
    if (!popBackStack(DashboardDestination.Home.route, inclusive = false)) {
        navigate(DashboardDestination.Home.route) {
            popUpTo(graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }
}
