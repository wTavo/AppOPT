package com.example.appopt.ui.components

import java.util.UUID
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.ui.graphics.graphicsLayer
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
     * Incorpora un fondo sutilmente teñido de advertencia, velo de advertencia y controles en esquema de error,
     * conservando el borde perimetral neutro estándar para máxima sobriedad y consistencia visual.
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

    val modalId = remember {
        UUID.randomUUID().toString().also { id ->
            ModalOverlayController.registerModal(id)
        }
    }

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
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        // Limpia el oscurecimiento estático del OS de forma síncrona antes del primer fotograma
        dialogWindow?.let { window ->
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0f)
        }
        SideEffect {
            dialogWindow?.let { window ->
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(0f)
            }
        }

        val transition = updateTransition(visibleState, label = "modal_dialog_transition")

        // Velo sutil para realce de bordes (18% en Android 12+ con blur, 50% clásico en Android 10/11)
        val targetScrimAlpha = remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.18f else 0.50f
        }

        val scrimAlpha by transition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    Motion.Spec.modalScrimEnterSpec()
                } else {
                    Motion.Spec.modalScrimExitSpec()
                }
            },
            label = "modal_scrim_alpha"
        ) { isVisible ->
            if (isVisible) targetScrimAlpha else 0.0f
        }

        // Animación de escala GPU: nace y muere en 0.0f (MODAL_COLLAPSE_SCALE) anclada al botón emisor
        val modalScale by transition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    Motion.Spec.navButtonExpandScaleSpec()
                } else {
                    Motion.Spec.navButtonCollapseScaleSpec()
                }
            },
            label = "modal_scale"
        ) { isVisible ->
            if (isVisible) 1.0f else Motion.Scale.MODAL_COLLAPSE_SCALE
        }

        // Animación de desvanecimiento GPU: rápida al entrar y sincronizada al final del colapso al salir
        val modalAlpha by transition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    Motion.Spec.quickFadeSpec()
                } else {
                    Motion.Spec.navButtonCollapseFadeSpec()
                }
            },
            label = "modal_alpha"
        ) { isVisible ->
            if (isVisible) 1.0f else 0.0f
        }

        CompositionLocalProvider(LocalModalDismissHandler provides dismissWithAnimation) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = handleDismissOrBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Velo oscurecido de fondo (scrim) animado en capa GPU independiente sin afectar la opacidad de la tarjeta
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = scrimAlpha }
                        .background(Color.Black)
                )

                // Capa modal animada al 100% en fase de Draw por hardware GPU (RenderNode):
                // La escala (desde 0.20f) y el desvanecimiento se ejecutan por transformaciones de matriz
                // en hardware nativo con transformOrigin exacto del botón emisor, sin recomposiciones ni relayouts.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = modalScale
                            scaleY = modalScale
                            alpha = modalAlpha
                            this.transformOrigin = transformOrigin
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val cardColor = when (tone) {
                        ModalTone.STANDARD -> MaterialTheme.colorScheme.surface
                        ModalTone.DESTRUCTIVE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.14f)
                            .compositeOver(MaterialTheme.colorScheme.surface)
                    }

                    val cardBorder = BorderStroke(
                        width = Dimensions.Stroke.thin,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                    )

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
