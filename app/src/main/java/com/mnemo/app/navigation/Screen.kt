package com.mnemo.app.navigation

/**
 * Screen routes for Mnemo navigation.
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Diagnostics : Screen("diagnostics")
    object FeaturePlaceholder : Screen("feature/{featureName}") {
        fun createRoute(featureName: String) = "feature/$featureName"
    }
}
