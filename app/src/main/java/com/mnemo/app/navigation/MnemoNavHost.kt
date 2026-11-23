package com.mnemo.app.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mnemo.app.MnemoApplication
import com.mnemo.app.di.MnemoViewModelFactory
import com.mnemo.app.feature.chat.ChatScreen
import com.mnemo.app.feature.chat.ChatViewModel
import com.mnemo.app.feature.memory.MemoriesScreen
import com.mnemo.app.feature.memory.MemoriesViewModel
import com.mnemo.app.ui.diagnostics.DiagnosticScreen
import com.mnemo.app.ui.home.HomeScreen
import com.mnemo.app.ui.home.HomeViewModel
import com.mnemo.app.ui.theme.Sky400

@Composable
fun MnemoNavHost(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val app = context.applicationContext as MnemoApplication
    val activity = context as? ComponentActivity
    val factory = MnemoViewModelFactory(app.container)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = BottomNavItem.items
    val isBottomNavVisible = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isBottomNavVisible) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title
                                )
                            },
                            label = { Text(item.title) },
                            selected = selected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Sky400.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = if (activity != null) {
                    viewModel(viewModelStoreOwner = activity, factory = factory)
                } else {
                    viewModel(factory = factory)
                }
                val homeUiState by homeViewModel.uiState.collectAsState()

                HomeScreen(
                    uiState = homeUiState,
                    onNavigateToChat = {
                        navController.navigate(Screen.Chat.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToMemories = {
                        navController.navigate(Screen.Memories.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToDiagnostics = {
                        navController.navigate(Screen.Diagnostics.route)
                    },
                    onDeleteRecentMemory = homeViewModel::deleteMemory
                )
            }

            composable(Screen.Chat.route) {
                val chatViewModel: ChatViewModel = if (activity != null) {
                    viewModel(viewModelStoreOwner = activity, factory = factory)
                } else {
                    viewModel(factory = factory)
                }
                val chatUiState by chatViewModel.uiState.collectAsState()

                ChatScreen(
                    uiState = chatUiState,
                    onInputTextChanged = chatViewModel::onInputTextChanged,
                    onSendMessage = chatViewModel::sendMessage,
                    onSaveMemory = chatViewModel::saveDirectMemory,
                    onClearChat = chatViewModel::clearChat,
                    onClearError = chatViewModel::clearError
                )
            }

            composable(Screen.Memories.route) {
                val memoriesViewModel: MemoriesViewModel = if (activity != null) {
                    viewModel(viewModelStoreOwner = activity, factory = factory)
                } else {
                    viewModel(factory = factory)
                }
                val memoriesUiState by memoriesViewModel.uiState.collectAsState()

                MemoriesScreen(
                    uiState = memoriesUiState,
                    onSearchQueryChanged = memoriesViewModel::onSearchQueryChanged,
                    onAddMemory = memoriesViewModel::addMemory,
                    onDeleteMemory = memoriesViewModel::deleteMemory,
                    onSeedDemoData = memoriesViewModel::seedDemoData,
                    onClearNotification = memoriesViewModel::clearNotification
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
}
