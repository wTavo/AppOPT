package com.example.appopt.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.appopt.AuthenticatorApp
import com.example.appopt.ui.screens.add.AddAccountScreen
import com.example.appopt.ui.screens.add.AddAccountViewModel
import com.example.appopt.ui.screens.home.HomeScreen
import com.example.appopt.ui.screens.home.HomeViewModel
import com.example.appopt.ui.screens.lock.LockScreen
import com.example.appopt.ui.screens.scan.QrScannerScreen
import com.example.appopt.ui.screens.settings.SettingsScreen

/**
 * Grafo principal de navegación y control de acceso de la aplicación.
 *
 * Principio de compuerta de seguridad (*Security Gate*):
 * - Evalúa reactivamente el estado de [AppLockManager.isUnlocked].
 * - Si la app está bloqueada, interrumpe cualquier flujo y muestra exclusivamente [LockScreen].
 * - Al desbloquearse, restaura la navegación entre las pantallas de la aplicación.
 */
@Composable
fun AppNavigation() {
    val appLockManager = AuthenticatorApp.instance.appLockManager
    val isUnlocked by appLockManager.isUnlocked.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = isUnlocked,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "AppLockTransition"
    ) { unlocked ->
        if (!unlocked) {
            LockScreen(
                onUnlocked = { appLockManager.unlock() }
            )
        } else {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) {
                    val homeViewModel: HomeViewModel = viewModel()
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToScanQr = { navController.navigate(Screen.ScanQr.route) },
                        onNavigateToAddManual = { navController.navigate(Screen.AddManual.route) },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                    )
                }

                composable(Screen.ScanQr.route) {
                    QrScannerScreen(
                        onScanSuccess = {
                            navController.popBackStack(Screen.Home.route, false)
                        },
                        onNavigateToManual = {
                            navController.navigate(Screen.AddManual.route) {
                                popUpTo(Screen.ScanQr.route) { inclusive = true }
                            }
                        },
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(Screen.AddManual.route) {
                    val addViewModel: AddAccountViewModel = viewModel()
                    AddAccountScreen(
                        viewModel = addViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToScanQr = { navController.navigate(Screen.ScanQr.route) }
                    )
                }
            }
        }
    }
}
