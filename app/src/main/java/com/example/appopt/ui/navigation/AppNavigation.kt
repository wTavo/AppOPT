package com.example.appopt.ui.navigation

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.appopt.AuthenticatorApp
import com.example.appopt.ui.components.ModalOverlayController
import com.example.appopt.ui.screens.home.HomeScreen
import com.example.appopt.ui.screens.home.HomeViewModel
import com.example.appopt.ui.screens.lock.LockScreen
import com.example.appopt.ui.screens.settings.SettingsScreen
import com.example.appopt.ui.screens.trash.RecentlyDeletedScreen
import com.example.appopt.performance.PerformanceFpsOverlay
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion

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
    val isModalActive by ModalOverlayController.isModalActive.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val targetBlurPx = remember(density) { with(density) { Dimensions.ComponentSize.modalBlurRadius.toPx() } }
    val blurProgress by animateFloatAsState(
        targetValue = if (isModalActive) 1.0f else 0.0f,
        animationSpec = if (isModalActive) Motion.Spec.modalScrimEnterSpec() else Motion.Spec.modalScrimExitSpec(),
        label = "app_navigation_modal_blur_progress"
    )

    // Defensa en profundidad: si la bóveda se bloquea, limpia cualquier registro residual
    LaunchedEffect(isUnlocked) {
        if (!isUnlocked) {
            ModalOverlayController.clearAll()
        }
    }

    // Prevención de desenfoque huérfano (Ghost Blur) ante paso a segundo plano o minimización
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                ModalOverlayController.clearAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isApi31Plus = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isApi31Plus && blurProgress > 0.001f) {
                        val currentRadius = targetBlurPx * blurProgress
                        renderEffect = android.graphics.RenderEffect.createBlurEffect(
                            currentRadius.coerceAtLeast(1f),
                            currentRadius.coerceAtLeast(1f),
                            android.graphics.Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    }
                },
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
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToRecentlyDeleted = { navController.navigate(Screen.RecentlyDeleted.route) }
                )
            }

            composable(
                route = Screen.Settings.route,
                enterTransition = {
                    scaleIn(
                        initialScale = Motion.Scale.NAV_SCREEN_ENTER_SCALE,
                        transformOrigin = NavigationOriginTracker.currentOrigin,
                        animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                    ) + fadeIn(animationSpec = Motion.Spec.quickFadeSpec())
                },
                popExitTransition = {
                    scaleOut(
                        targetScale = Motion.Scale.NAV_SCREEN_ENTER_SCALE,
                        transformOrigin = NavigationOriginTracker.currentOrigin,
                        animationSpec = Motion.Spec.navButtonCollapseScaleSpec()
                    ) + fadeOut(animationSpec = Motion.Spec.navButtonCollapseFadeSpec())
                }
            ) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.RecentlyDeleted.route,
                enterTransition = {
                    scaleIn(
                        initialScale = Motion.Scale.NAV_SCREEN_ENTER_SCALE,
                        transformOrigin = NavigationOriginTracker.currentOrigin,
                        animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                    ) + fadeIn(animationSpec = Motion.Spec.quickFadeSpec())
                },
                popExitTransition = {
                    scaleOut(
                        targetScale = Motion.Scale.NAV_SCREEN_ENTER_SCALE,
                        transformOrigin = NavigationOriginTracker.currentOrigin,
                        animationSpec = Motion.Spec.navButtonCollapseScaleSpec()
                    ) + fadeOut(animationSpec = Motion.Spec.navButtonCollapseFadeSpec())
                }
            ) {
                RecentlyDeletedScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // Overlay de diagnóstico FPS (superpuesto a todo el contenido si está activo)
        PerformanceFpsOverlay(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Dimensions.Spacing.lg)
                .zIndex(50f)
        )

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
