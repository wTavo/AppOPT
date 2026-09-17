package com.example.appopt.ui.screens.home.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Menú contextual flotante (*Speed Dial Overlay*) que se despliega directamente sobre el botón `(+)` del dock inferior.
 *
 * Características de diseño e interacción:
 * - Scrim translúcido de fondo para descartar tocando en cualquier área libre.
 * - Animación de expansión y colapso focalizada verticalmente desde el botón `(+)`.
 * - Tarjeta flotante con esquinas redondeadas ([Dimensions.CornerRadius.large]) y sombra modal elevada.
 * - Acciones rápidas para escanear código QR o introducir clave manual con respuesta háptica ([rememberAppHaptics]).
 *
 * @param isOpen Indica si el menú overlay se encuentra expandido y visible.
 * @param onDismiss Callback invocado al pulsar el fondo o cancelar para replegar el overlay.
 * @param onScanQr Callback invocado al seleccionar la acción de escanear código QR.
 * @param onAddManual Callback invocado al seleccionar la acción de ingresar clave manual.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun AddAccountSpeedDialOverlay(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onScanQr: () -> Unit,
    onAddManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()

    val transition = updateTransition(targetState = isOpen, label = "speed_dial_transition")

    val scrimAlpha by transition.animateFloat(
        transitionSpec = {
            tween(durationMillis = Motion.Duration.FAST, easing = Motion.EasingCurve.Standard)
        },
        label = "speed_dial_scrim_alpha"
    ) { open ->
        if (open) 0.40f else 0.0f
    }

    val cardScale by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(durationMillis = Motion.Duration.MEDIUM, easing = Motion.EasingCurve.Emphasized)
            } else {
                tween(durationMillis = Motion.Duration.FAST, easing = Motion.EasingCurve.Standard)
            }
        },
        label = "speed_dial_card_scale"
    ) { open ->
        if (open) 1.0f else Motion.Scale.MODAL_COLLAPSE_SCALE
    }

    val cardAlpha by transition.animateFloat(
        transitionSpec = {
            tween(durationMillis = Motion.Duration.FAST, easing = Motion.EasingCurve.Standard)
        },
        label = "speed_dial_card_alpha"
    ) { open ->
        if (open) 1.0f else 0.0f
    }

    if (isOpen || transition.currentState) {
        // 1. Capa Scrim translúcida de fondo para descartar (detrás del dock inferior, GPU alpha)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
                .graphicsLayer { alpha = scrimAlpha }
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        appHaptics.click()
                        onDismiss()
                    }
                )
        )

        // 2. Tarjeta flotante Speed Dial situada inmediatamente sobre el botón (+) del dock (GPU scale & alpha)
        Box(
            modifier = modifier
                .fillMaxSize()
                .zIndex(25f)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    alpha = cardAlpha
                    transformOrigin = TransformOrigin(pivotFractionX = 0.50f, pivotFractionY = 1.0f)
                }
                .navigationBarsPadding()
                .padding(bottom = Dimensions.ComponentSize.heroFab + Dimensions.Spacing.xl + Dimensions.Spacing.sm),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(Dimensions.CornerRadius.large),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = Dimensions.Elevation.cardDragging,
                shadowElevation = Dimensions.Elevation.modal,
                border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier
                    .widthIn(min = Dimensions.ComponentSize.speedDialMinWidth, max = Dimensions.ComponentSize.speedDialMaxWidth)
                    .padding(horizontal = Dimensions.Spacing.lg)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = Dimensions.Spacing.xs)
                ) {
                    // Opción 1: Escanear código QR
                    SpeedDialItem(
                        icon = Icons.Filled.QrCodeScanner,
                        text = stringResource(R.string.home_scan_qr_option),
                        onClick = {
                            appHaptics.click()
                            onScanQr()
                        }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                        thickness = Dimensions.Stroke.thin,
                        modifier = Modifier.padding(horizontal = Dimensions.Spacing.md)
                    )

                    // Opción 2: Ingresar clave manual
                    SpeedDialItem(
                        icon = Icons.Filled.Keyboard,
                        text = stringResource(R.string.home_add_manual_option),
                        onClick = {
                            appHaptics.click()
                            onAddManual()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Elemento interactivo individual dentro del menú contextual flotante Speed Dial.
 *
 * @param icon Icono descriptivo de la acción.
 * @param text Etiqueta de texto de la opción.
 * @param onClick Callback invocado al pulsar el elemento.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
private fun SpeedDialItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        Surface(
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(Dimensions.IconSize.medium)
                )
            }
        }

        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
