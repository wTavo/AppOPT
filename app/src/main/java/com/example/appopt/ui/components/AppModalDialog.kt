package com.example.appopt.ui.components

import java.util.UUID
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.appopt.ui.navigation.NavigationOriginTracker
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion

/**
 * Tono semántico y nivel de severidad visual para diálogos modales.
 */
enum class ModalTone {
    /**
     * Diálogo estándar para flujos constructivos, configuración y operaciones seguras.
     */
    STANDARD,

    /**
     * Diálogo de alerta crítica o destructiva ("negativo"): sobrescrituras, desvinculaciones o eliminaciones irreversibles.
     * Incorpora un fondo sutilmente teñido de advertencia y un borde perimetral en tono de error para realzar su severidad.
     */
    DESTRUCTIVE
}

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
 * @param tone Tono semántico de severidad ([ModalTone.STANDARD] neutro por defecto, o [ModalTone.DESTRUCTIVE] con fondo y bordes de alerta para acciones críticas).
 * @param properties Propiedades de configuración del diálogo modal.
 * @param content Contenido interno del diálogo (usualmente envuelto en un `AnimatedContent` monolítico).
 */
@Composable
fun AppModalDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    transformOrigin: TransformOrigin = NavigationOriginTracker.currentOrigin,
    onBackStep: (() -> Boolean)? = null,
    tone: ModalTone = ModalTone.STANDARD,
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

    val modalId = remember { UUID.randomUUID().toString() }

    // Al concluir la animación de salida hacia el botón, desmonta el diálogo
    LaunchedEffect(visibleState.isIdle, visibleState.currentState, visibleState.targetState) {
        if (visibleState.isIdle && !visibleState.currentState && !visibleState.targetState) {
            onDismissRequest()
        }
    }

    // Sincroniza el desenfoque en la capa de navegación inferior (Compose Skia GPU).
    // Garantiza matemáticamente que al componerse se registre y que al desmontarse
    // (por salida animada, forzada o bloqueo de app) se dé de baja indefectiblemente.
    DisposableEffect(modalId) {
        ModalOverlayController.registerModal(modalId)
        onDispose {
            ModalOverlayController.unregisterModal(modalId)
        }
    }

    // Al iniciar la animación de salida (repliegue hacia el botón), disipa el desenfoque
    // en paralelo con la tarjeta hacia Dimensions.Spacing.none (0.dp).
    LaunchedEffect(visibleState.targetState) {
        if (!visibleState.targetState) {
            ModalOverlayController.unregisterModal(modalId)
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
        val density = LocalDensity.current
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        val targetBlurPx = remember(density) {
            with(density) { Dimensions.ComponentSize.modalBlurRadius.roundToPx() }
        }

        val animatedBlurPx by animateIntAsState(
            targetValue = if (visibleState.targetState) targetBlurPx else 0,
            animationSpec = if (visibleState.targetState) {
                Motion.Spec.modalScrimEnterSpec()
            } else {
                Motion.Spec.modalScrimExitSpec()
            },
            label = "modal_blur_behind"
        )

        // Limpia el oscurecimiento estático del OS y aplica desenfoque por hardware en Android 12+ (API 31+)
        SideEffect {
            dialogWindow?.let { window ->
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(0f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window.setBackgroundBlurRadius(animatedBlurPx)
                    } catch (_: Exception) {
                        // Manejo defensivo en hardware o configuraciones que restrinjan blur
                    }
                }
            }
        }

        // Velo sutil para realce de bordes (18% en Android 12+ con blur, 50% clásico en Android 10/11; reforzado en destructivos)
        val targetScrimAlpha = remember(tone) {
            val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.18f else 0.50f
            if (tone == ModalTone.DESTRUCTIVE) base + 0.10f else base
        }

        val scrimAlpha by animateFloatAsState(
            targetValue = if (visibleState.targetState) targetScrimAlpha else 0.0f,
            animationSpec = if (visibleState.targetState) {
                Motion.Spec.modalScrimEnterSpec()
            } else {
                Motion.Spec.modalScrimExitSpec()
            },
            label = "modal_scrim_alpha"
        )

        CompositionLocalProvider(LocalModalDismissHandler provides dismissWithAnimation) {
            // Fondo traslúcido con desenfoque a pantalla completa con descarte al hacer clic afuera
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
                        animationSpec = Motion.Spec.navButtonCollapseScaleSpec()
                    ) + fadeOut(animationSpec = Motion.Spec.navButtonCollapseFadeSpec()),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val cardColor = when (tone) {
                            ModalTone.STANDARD -> MaterialTheme.colorScheme.surface
                            ModalTone.DESTRUCTIVE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.14f)
                                .compositeOver(MaterialTheme.colorScheme.surface)
                        }

                        val cardBorder = when (tone) {
                            ModalTone.STANDARD -> BorderStroke(
                                width = Dimensions.Stroke.thin,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                            )
                            ModalTone.DESTRUCTIVE -> BorderStroke(
                                width = Dimensions.Stroke.thin,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                            )
                        }

                        // Tarjeta modal del diálogo
                        Surface(
                            modifier = modifier
                                .safeDrawingPadding()
                                .padding(vertical = Dimensions.Spacing.xl)
                                .fillMaxWidth(0.86f)
                                .widthIn(
                                    min = Dimensions.ComponentSize.modalMinWidth,
                                    max = Dimensions.ComponentSize.modalMaxWidth
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {} // Intercepta clics dentro de la tarjeta para evitar descarte accidental
                                ),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.large),
                            color = cardColor,
                            border = cardBorder,
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
