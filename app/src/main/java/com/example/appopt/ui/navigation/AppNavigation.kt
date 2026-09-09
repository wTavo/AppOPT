package com.example.appopt.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
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
import com.example.appopt.ui.screens.trash.RecentlyDeletedScreen
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.util.PerformanceFpsOverlay

/**
 * Grafo principal de navegación y control de acceso de la aplicación.
 *
 * Principio de compuerta de seguridad (*Security Gate Layer*):
 * - Mantiene el grafo [NavHost] pre-renderizado en segundo plano para respuesta en 0ms.
 * - [LockScreen] se sitúa como una capa opaca superior (*Z-Index Overlay*) cuando la bóveda está bloqueada.
 * - Al autenticar exitosamente, la capa de bloqueo se retira de inmediato mostrando los servicios sin pausas ni pantallas vacías.
 * - Incorpora la superposición de diagnóstico [PerformanceFpsOverlay] en la capa superior si está activada.
 * - Transiciones Contextuales: Expansión y repliegue focalizados desde el botón de invocación específico en el dock inferior.
 */
@Composable
fun AppNavigation() {
    val appLockManager = AuthenticatorApp.instance.appLockManager
    val isUnlocked by appLockManager.isUnlocked.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            exitTransition = {
                scaleOut(
                    targetScale = Motion.Scale.NAV_BACKGROUND_SHRINK,
                    animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                )
            },
            popEnterTransition = {
                scaleIn(
                    initialScale = Motion.Scale.NAV_BACKGROUND_SHRINK,
                    animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                )
            }
        ) {
            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = viewModel()
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToScanQr = { navController.navigate(Screen.ScanQr.route) },
                    onNavigateToAddManual = { navController.navigate(Screen.AddManual.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToRecentlyDeleted = { navController.navigate(Screen.RecentlyDeleted.route) }
                )
            }

            composable(
                route = Screen.ScanQr.route,
                enterTransition = {
                    scaleIn(
                        initialScale = Motion.Scale.NAV_BUTTON_COLLAPSE,
                        transformOrigin = NavigationOriginTracker.currentOrigin,
                        animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                    )
                },
                popExitTransition = {
                    scaleOut(
                        targetScale = Motion.Scale.NAV_BUTTON_COLLAPSE,
                        transformOrigin = NavigationOriginTracker.currentOrigin,
                        animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                    ) + fadeOut(animationSpec = Motion.Spec.navButtonCollapseFadeSpec())
                }
            ) {
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

            composable(
                route = Screen.AddManual.route,
                enterTransition = {
                    scaleIn(
                        initialScale = Motion.Scale.NAV_BUTTON_COLLAPSE,
                        transformOrigin = NavigationOriginTracker.currentOrigin,
                        animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                    )
                },
                popExitTransition = {
                    scaleOut(
                        targetScale = Motion.Scale.NAV_BUTTON_COLLAPSE,
                        transformOrigin = NavigationOriginTracker.currentOrigin,
                        animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                    ) + fadeOut(animationSpec = Motion.Spec.navButtonCollapseFadeSpec())
                }
            ) {
                val addViewModel: AddAccountViewModel = viewModel()
                AddAccountScreen(
                    viewModel = addViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Settings.route,
                enterTransition = {
                    scaleIn(
                        initialScale = Motion.Scale.NAV_BUTTON_COLLAPSE,
                        transformOrigin = NavigationOriginTracker.currentOrigin,
                        animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                    )
                },
                popExitTransition = {
                    scaleOut(
                        targetScale = Motion.Scale.NAV_BUTTON_COLLAPSE,
                        transformOrigin = NavigationOriginTracker.currentOrigin,
                        animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                    ) + fadeOut(animationSpec = Motion.Spec.navButtonCollapseFadeSpec())
                }
            ) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToScanQr = { navController.navigate(Screen.ScanQr.route) }
                )
            }

            composable(
                route = Screen.RecentlyDeleted.route,
                enterTransition = {
                    scaleIn(
                        initialScale = Motion.Scale.NAV_BUTTON_COLLAPSE,
                        transformOrigin = NavigationOriginTracker.currentOrigin,
                        animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                    )
                },
                popExitTransition = {
                    scaleOut(
                        targetScale = Motion.Scale.NAV_BUTTON_COLLAPSE,
                        transformOrigin = NavigationOriginTracker.currentOrigin,
                        animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                    ) + fadeOut(animationSpec = Motion.Spec.navButtonCollapseFadeSpec())
                }
            ) {
                RecentlyDeletedScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        val prefs = remember { AuthenticatorApp.instance.preferencesManager }
        val isFpsOverlayEnabled by prefs.isFpsOverlayEnabledFlow.collectAsStateWithLifecycle()

        if (isUnlocked && isFpsOverlayEnabled) {
            PerformanceFpsOverlay(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = Dimensions.Spacing.xxl + Dimensions.Spacing.md, end = Dimensions.Spacing.md)
                    .zIndex(99f)
            )
        }

        if (!isUnlocked) {
            LockScreen(
                onUnlocked = { appLockManager.unlock() }
            )
        }
    }
}
