package com.jellycine.app.ui.screens.dashboard

import android.app.Application
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DashboardNavigationTest {
    private fun controller(start: String = DashboardDestination.Home.route) =
        NavHostController(RuntimeEnvironment.getApplication()).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            graph = createGraph(startDestination = start) {
                composable(DashboardDestination.Home.route) {}
                composable(DashboardDestination.Search.route) {}
                composable(DashboardDestination.Favorites.route) {}
                composable(DashboardDestination.Settings.route) {}
            }
        }

    @Test fun backFromSettingsReturnsHomeAndDoesNotRestoreSettings() {
        val navController = controller()
        repeat(3) {
            navController.navigate(DashboardDestination.Settings.route)
            navController.returnToDashboardHome()
            assertEquals(DashboardDestination.Home.route, navController.currentDestination?.route)
            assertNull(navController.previousBackStackEntry)
        }
    }

    @Test fun backFromOtherTabsReturnsHome() {
        val navController = controller()
        listOf(DashboardDestination.Search, DashboardDestination.Favorites).forEach { destination ->
            navController.navigate(destination.route)
            navController.returnToDashboardHome()
            assertEquals(DashboardDestination.Home.route, navController.currentDestination?.route)
        }
    }

    @Test fun missingHomeEntryStillReturnsHome() {
        val navController = controller(start = DashboardDestination.Settings.route)
        navController.returnToDashboardHome()
        assertEquals(DashboardDestination.Home.route, navController.currentDestination?.route)
        assertNull(navController.previousBackStackEntry)
    }
}
