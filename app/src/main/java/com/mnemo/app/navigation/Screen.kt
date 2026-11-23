package com.mnemo.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Screen routes for Mnemo navigation.
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Chat : Screen("chat")
    object Memories : Screen("memories")
    object Diagnostics : Screen("diagnostics")
    object FeaturePlaceholder : Screen("feature/{featureName}") {
        fun createRoute(featureName: String) = "feature/$featureName"
    }
}

/**
 * Destinations displayed in the Bottom Navigation Bar.
 */
sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem(Screen.Home.route, "Home", Icons.Default.Home)
    object Chat : BottomNavItem(Screen.Chat.route, "Assistant", Icons.Default.Assistant)
    object Memories : BottomNavItem(Screen.Memories.route, "Memories", Icons.Default.Psychology)
    object Diagnostics : BottomNavItem(Screen.Diagnostics.route, "Privacy", Icons.Default.Shield)

    companion object {
        val items = listOf(Home, Chat, Memories, Diagnostics)
    }
}
