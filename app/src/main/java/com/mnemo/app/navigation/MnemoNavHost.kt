package com.mnemo.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mnemo.app.ui.diagnostics.DiagnosticScreen
import com.mnemo.app.ui.home.HomeScreen

@Composable
fun MnemoNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToDiagnostics = {
                    navController.navigate(Screen.Diagnostics.route)
                },
                onFeatureCardClick = { _ ->
                    // In Phase 0, all feature cards open diagnostics or stay on home
                    navController.navigate(Screen.Diagnostics.route)
                }
            )
        }

        composable(Screen.Diagnostics.route) {
            DiagnosticScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
