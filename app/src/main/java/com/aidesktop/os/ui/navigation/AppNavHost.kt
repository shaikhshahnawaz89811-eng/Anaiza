package com.aidesktop.os.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aidesktop.os.ui.desktop.DesktopScreen
import com.aidesktop.os.ui.home.HomeScreen

private object Routes {
    const val HOME = "home"
    const val DESKTOP = "desktop"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onOpenDesktop = { navController.navigate(Routes.DESKTOP) })
        }
        composable(Routes.DESKTOP) {
            DesktopScreen(onExitToHome = { navController.popBackStack() })
        }
    }
}
