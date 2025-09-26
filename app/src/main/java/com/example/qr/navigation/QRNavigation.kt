package com.example.qr.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.qr.QRApplication
import com.example.qr.ui.screens.ExcelExportScreen
import com.example.qr.ui.screens.QRScannerScreen
import com.example.qr.ui.screens.SettingsScreen
import com.example.qr.ui.screens.UserRegistrationScreen
import com.example.qr.viewmodel.ViewModelFactory

sealed class Screen(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object UserRegistration : Screen("user_registration", "사용자 등록", Icons.Filled.Person)
    object QRScanner : Screen("qr_scanner", "QR 스캐너", Icons.Filled.Camera)
    object ExcelExport : Screen("excel_export", "엑셀 추출", Icons.Filled.Save)
    object Settings : Screen("settings", "설정", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRNavigationApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val context = LocalContext.current
    val application = context.applicationContext as QRApplication
    val viewModelFactory = ViewModelFactory(application)

    val screens = listOf(
        Screen.UserRegistration,
        Screen.QRScanner,
        Screen.ExcelExport,
        Screen.Settings
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = screens.find {
                            currentDestination?.hierarchy?.any { destination ->
                                destination.route == it.route
                            } == true
                        }?.title ?: "QR 출입 관리",
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.UserRegistration.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.UserRegistration.route) {
                UserRegistrationScreen(
                    viewModel = viewModel(factory = viewModelFactory)
                )
            }
            composable(Screen.QRScanner.route) {
                QRScannerScreen(
                    viewModel = viewModel(factory = viewModelFactory)
                )
            }
            composable(Screen.ExcelExport.route) {
                ExcelExportScreen(
                    viewModel = viewModel(factory = viewModelFactory)
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel(factory = viewModelFactory)
                )
            }
        }
    }
}