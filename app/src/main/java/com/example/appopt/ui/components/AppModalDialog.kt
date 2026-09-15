package com.example.appopt.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.appopt.ui.navigation.NavigationOriginTracker
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion

/**
 * Proveedor de contexto local para invocar el cierre animado del diálogo modal (retorno al botón de origen).
 */
val LocalModalDismissHandler = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Contenedor modal centralizado con ventana estática a pantalla completa, animación de expansión y repliegue idéntica a la navegación de pantallas y renderizado 100% en Compose GPU.
 *
 * Resuelve de forma definitiva el conflicto de redimensionamiento nativo en Android:
 * Al fijar [DialogProperties.usePlatformDefaultWidth] en `false`, la ventana del sistema operativo
 * permanece a pantalla completa sin enviar llamadas IPC continuas a `WindowManagerService`.
 * La capa modal nace y se expande con la misma animación (`scaleIn` + `scaleOut`) de las pantallas
 * desde el centro del botón que disparó la acción ([NavigationOriginTracker.currentOrigin]),
 * y se repliega fluidamente de regreso a ese mismo botón al cerrarse antes de desmontar el diálogo.
 *
 * @param onDismissRequest Callback invocado para desmontar el diálogo tras finalizar la animación de salida.
 * @param modifier Modificador Compose opcional para la tarjeta visual.
 * @param transformOrigin Punto pivote normalizado de origen para la animación de escala (por defecto toma las coordenadas del emisor en [NavigationOriginTracker]).
 * @param onBackStep Callback opcional para navegación defensiva en modales multietapa: si retorna `true`, consume el evento retrocediendo un paso internamente sin desmontar la tarjeta; si retorna `false` o es `null`, repliega y desmonta el diálogo.
 * @param properties Propiedades de configuración del diálogo modal.
 * @param content Contenido interno del diálogo (usualmente envuelto en un `AnimatedContent` monolítico).
 */
@Composable
fun AppModalDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    transformOrigin: TransformOrigin = NavigationOriginTracker.currentOrigin,
    onBackStep: (() -> Boolean)? = null,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false
    ),
    content: @Composable () -> Unit
) {
    val visibleState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }

    // Al concluir la animación de salida hacia el botón, desmonta el diálogo
    LaunchedEffect(visibleState.isIdle, visibleState.currentState, visibleState.targetState) {
        if (visibleState.isIdle && !visibleState.currentState && !visibleState.targetState) {
            onDismissRequest()
        }
    }

    val dismissWithAnimation = remember(visibleState) {
        {
            if (visibleState.targetState) {
                visibleState.targetState = false
            }
        }
    }

    val handleDismissOrBack = remember(onBackStep, dismissWithAnimation) {
        {
            val handledInternally = onBackStep?.invoke() ?: false
            if (!handledInternally) {
                dismissWithAnimation()
            }
        }
    }

    Dialog(
        onDismissRequest = handleDismissOrBack,
        properties = properties
    ) {
        val scrimAlpha by animateFloatAsState(
            targetValue = if (visibleState.targetState) 0.55f else 0.0f,
            animationSpec = tween(
                durationMillis = Motion.Duration.FAST,
                easing = Motion.EasingCurve.Standard
            ),
            label = "modal_scrim_alpha"
        )

        CompositionLocalProvider(LocalModalDismissHandler provides dismissWithAnimation) {
            // Fondo oscurecido (Scrim) a pantalla completa con descarte al hacer clic afuera
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = handleDismissOrBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Animación exacta de expansión y repliegue contextual idéntica a las pantallas de navegación
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = scaleIn(
                        initialScale = Motion.Scale.NAV_BUTTON_COLLAPSE,
                        transformOrigin = transformOrigin,
                        animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                    ),
                    exit = scaleOut(
                        targetScale = Motion.Scale.NAV_BUTTON_COLLAPSE,
                        transformOrigin = transformOrigin,
                        animationSpec = Motion.Spec.navButtonExpandScaleSpec()
                    ) + fadeOut(animationSpec = Motion.Spec.navButtonCollapseFadeSpec()),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Tarjeta modal del diálogo
                        Surface(
                            modifier = modifier
                                .safeDrawingPadding()
                                .padding(vertical = Dimensions.Spacing.xl)
                                .fillMaxWidth(0.86f)
                                .widthIn(min = 280.dp, max = 400.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {} // Intercepta clics dentro de la tarjeta para evitar descarte accidental
                                ),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.large),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = Dimensions.Elevation.modal,
                            shadowElevation = Dimensions.Elevation.modal
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Dimensions.Spacing.lg)
                            ) {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }
}
