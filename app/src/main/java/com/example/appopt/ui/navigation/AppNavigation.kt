package com.example.appopt.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.appopt.AuthenticatorApp
import com.example.appopt.ui.screens.home.HomeScreen
import com.example.appopt.ui.screens.home.HomeViewModel
import com.example.appopt.ui.screens.lock.LockScreen
import com.example.appopt.ui.screens.settings.SettingsScreen
import com.example.appopt.ui.screens.trash.RecentlyDeletedScreen
import com.example.appopt.performance.PerformanceFpsOverlay
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion

/**
 * Rutas de navegación de primer nivel en Jetpack Compose.
 */
sealed class Screen(val route: String) {
    /** Pantalla principal con la lista de cuentas y códigos en vivo. */
    data object Home : Screen("home")

    /** Pantalla de diagnóstico de seguridad y generación de Recovery Key. */
    data object Settings : Screen("settings")

    /** Pantalla de papelera y servicios eliminados recientemente (30 días). */
    data object RecentlyDeleted : Screen("recently_deleted")
}

/**
 * Grafo principal de navegación y control de acceso de la aplicación.
 *
 * Principio de compuerta de seguridad (*Security Gate Layer*) e idempotencia:
 * - Mantiene el grafo [NavHost] pre-renderizado en segundo plano para respuesta en 0ms.
 * - [LockScreen] se sitúa como una capa opaca superior (*Z-Index Overlay*) cuando la bóveda está bloqueada.
 * - Protege todas las transiciones verificando [Lifecycle.State.RESUMED] y `launchSingleTop = true` para impedir
 *   navegaciones duplicadas o vaciado accidental de la pila por toques múltiples en barras de menú.
 */
@Composable
fun AppNavigation() {
    val appLockManager = AuthenticatorApp.instance.appLockManager
    val isUnlocked by appLockManager.isUnlocked.collectAsStateWithLifecycle()
    val preferencesManager = AuthenticatorApp.instance.preferencesManager
    val isFpsOverlayEnabled by preferencesManager.isFpsOverlayEnabledFlow.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize(),
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
            composable(Screen.Home.route) { backStackEntry ->
                val homeViewModel: HomeViewModel = viewModel()
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToSettings = {
                        if (backStackEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                            navController.navigate(Screen.Settings.route) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onNavigateToRecentlyDeleted = {
                        if (backStackEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                            navController.navigate(Screen.RecentlyDeleted.route) {
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }

            composable(
                route = Screen.Settings.route,
                enterTransition = {
                    scaleIn(
                        initialScale = Motion.Scale.NAV_SCREEN_ENTER_SCALE,
                        transformOrigin = NavigationOriginTracker.settingsOrigin,
                        animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                    ) + fadeIn(animationSpec = Motion.Spec.quickFadeSpec())
                },
                popExitTransition = {
                    scaleOut(
                        targetScale = Motion.Scale.NAV_SCREEN_ENTER_SCALE,
                        transformOrigin = NavigationOriginTracker.settingsOrigin,
                        animationSpec = Motion.Spec.navButtonCollapseScaleSpec()
                    ) + fadeOut(animationSpec = Motion.Spec.navButtonCollapseFadeSpec())
                }
            ) { backStackEntry ->
                SettingsScreen(
                    onNavigateBack = {
                        if (backStackEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(
                route = Screen.RecentlyDeleted.route,
                enterTransition = {
                    scaleIn(
                        initialScale = Motion.Scale.NAV_SCREEN_ENTER_SCALE,
                        transformOrigin = NavigationOriginTracker.recentlyDeletedOrigin,
                        animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                    ) + fadeIn(animationSpec = Motion.Spec.quickFadeSpec())
                },
                popExitTransition = {
                    scaleOut(
                        targetScale = Motion.Scale.NAV_SCREEN_ENTER_SCALE,
                        transformOrigin = NavigationOriginTracker.recentlyDeletedOrigin,
                        animationSpec = Motion.Spec.navButtonCollapseScaleSpec()
                    ) + fadeOut(animationSpec = Motion.Spec.navButtonCollapseFadeSpec())
                }
            ) { backStackEntry ->
                RecentlyDeletedScreen(
                    onNavigateBack = {
                        if (backStackEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                            navController.popBackStack()
                        }
                    }
                )
            }
        }

        // Overlay de diagnóstico FPS (superpuesto a todo el contenido únicamente si está activo)
        AnimatedVisibility(
            visible = isFpsOverlayEnabled,
            enter = fadeIn(animationSpec = Motion.Spec.quickFadeSpec()),
            exit = fadeOut(animationSpec = Motion.Spec.quickFadeSpec()),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Dimensions.Spacing.lg)
                .zIndex(50f)
        ) {
            PerformanceFpsOverlay()
        }

        // Compuerta de seguridad: LockScreen se superpone a todo cuando la bóveda está bloqueada
        if (!isUnlocked) {
            LockScreen(
                onUnlocked = { appLockManager.unlock() },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
            )
        }
    }
}
