package com.example.appopt.ui.screens.settings.dialogs.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Paso 2 del diálogo de exportación: Visualización de códigos QR cifrados, conmutador de PIN efímero,
 * paginación por lotes y barra de progreso de expiración temporal.
 *
 * @param transferQrBitmaps Lista de mapas de bits de códigos QR por lotes generados.
 * @param currentQrIndex Índice del código QR que se está visualizando en pantalla.
 * @param onSelectQrIndex Callback al avanzar o retroceder de lote.
 * @param transferPin PIN efímero de 6 dígitos para descifrado.
 * @param isPinVisible Estado de visualización conmutable (true: muestra el PIN en grande, false: muestra el QR).
 * @param onTogglePinVisibility Callback al alternar la visualización del PIN / QR.
 * @param exportedCount Número total de servicios que forman parte de la transferencia.
 * @param secondsRemaining Segundos restantes antes de la expiración del código.
 * @param totalSessionDuration Duración total asignada a la sesión de transferencia en segundos.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun ExportQrCarouselStep(
    transferQrBitmaps: List<Bitmap>,
    currentQrIndex: Int,
    onSelectQrIndex: (Int) -> Unit,
    transferPin: String,
    isPinVisible: Boolean,
    onTogglePinVisibility: () -> Unit,
    exportedCount: Int,
    secondsRemaining: Int,
    totalSessionDuration: Int,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
    ) {
        // Paginación por lotes (si hay más de 1 código QR)
        if (transferQrBitmaps.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        if (currentQrIndex > 0) {
                            haptics.click()
                            onSelectQrIndex(currentQrIndex - 1)
                        }
                    },
                    enabled = currentQrIndex > 0
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_transfer_prev_code)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(
                            R.string.settings_transfer_page_indicator,
                            currentQrIndex + 1,
                            transferQrBitmaps.size
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = stringResource(
                            R.string.settings_transfer_selected_services_count,
                            exportedCount
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        if (currentQrIndex < transferQrBitmaps.size - 1) {
                            haptics.click()
                            onSelectQrIndex(currentQrIndex + 1)
                        }
                    },
                    enabled = currentQrIndex < transferQrBitmaps.size - 1
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.settings_transfer_next_code)
                    )
                }
            }
        } else if (exportedCount > 0) {
            Text(
                text = stringResource(
                    R.string.settings_transfer_selected_services_count,
                    exportedCount
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        val currentBitmap = transferQrBitmaps.getOrNull(currentQrIndex)
        val imageBitmap = androidx.compose.runtime.remember(currentBitmap) { currentBitmap?.asImageBitmap() }
        val formattedPin = androidx.compose.runtime.remember(transferPin) {
            when (transferPin.length) {
                8 -> "${transferPin.substring(0, 4)} - ${transferPin.substring(4)}"
                6 -> "${transferPin.substring(0, 3)} ${transferPin.substring(3)}"
                else -> transferPin
            }
        }

        if (imageBitmap != null) {
            Crossfade(
                targetState = isPinVisible,
                animationSpec = tween(
                    durationMillis = Motion.Duration.FAST,
                    easing = Motion.EasingCurve.Standard
                ),
                label = "qrPinDisplayCrossfade"
            ) { showingPin ->
                if (showingPin) {
                    // Superficie conmutable: Muestra el PIN dentro del mismo cuadro del QR
                    Surface(
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier
                            .size(Dimensions.ComponentSize.qrCodeDisplay)
                            .padding(Dimensions.Spacing.xs)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(Dimensions.Spacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Dimensions.IconSize.hero)
                            )
                            Spacer(modifier = Modifier.height(Dimensions.Spacing.sm))
                            Text(
                                text = stringResource(R.string.settings_transfer_pin_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                            Text(
                                text = formattedPin,
                                style = MaterialTheme.typography.headlineLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(Dimensions.Spacing.sm))
                            Text(
                                text = stringResource(R.string.settings_transfer_pin_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Superficie conmutable: Muestra el código QR nítido
                    Surface(
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        color = Color.White,
                        modifier = Modifier
                            .size(Dimensions.ComponentSize.qrCodeDisplay)
                            .padding(Dimensions.Spacing.xs)
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(Dimensions.Spacing.xs)
                        )
                    }
                }
            }

            // Barra y etiqueta de tiempo restante proporcional
            val progress = secondsRemaining.toFloat() / maxOf(1, totalSessionDuration).toFloat()
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimensions.Spacing.xs),
                color = if (secondsRemaining <= 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )

            val expirationLabel = stringResource(R.string.settings_transfer_expires_in, secondsRemaining)

            Text(
                text = expirationLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (secondsRemaining <= 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Botón de alternancia entre Código QR y PIN
            if (transferPin.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        haptics.click()
                        onTogglePinVisibility()
                    },
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                ) {
                    AnimatedContent(
                        targetState = isPinVisible,
                        transitionSpec = {
                            val direction = if (targetState) 1 else -1
                            (slideInVertically(
                                animationSpec = tween(
                                    durationMillis = Motion.Duration.FAST,
                                    easing = Motion.EasingCurve.Standard
                                )
                            ) { height -> direction * height / 2 } + fadeIn(
                                animationSpec = tween(
                                    durationMillis = Motion.Duration.FAST,
                                    easing = Motion.EasingCurve.Standard
                                )
                            )) togetherWith
                                (slideOutVertically(
                                    animationSpec = tween(
                                        durationMillis = Motion.Duration.FAST,
                                        easing = Motion.EasingCurve.Standard
                                    )
                                ) { height -> -direction * height / 2 } + fadeOut(
                                    animationSpec = tween(
                                        durationMillis = Motion.Duration.FAST,
                                        easing = Motion.EasingCurve.Standard
                                    )
                                ))
                        },
                        label = "qrPinButtonTransition"
                    ) { showingPin ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (showingPin) Icons.Default.QrCodeScanner else Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.IconSize.small)
                            )
                            Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                            Text(
                                text = if (showingPin) {
                                    stringResource(R.string.settings_transfer_view_qr)
                                } else {
                                    stringResource(R.string.settings_transfer_view_pin)
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = stringResource(R.string.settings_export_qr_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = Dimensions.Spacing.md)
            )
        }
    }
}
