package com.example.appopt.ui.screens.scan.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.security.TransferQrChunk
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.screens.scan.QrScannerMode
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion

/**
 * Sub-pantalla principal del escáner de códigos QR con visor de cámara bajo demanda,
 * progreso de fragmentos de transferencia y badges de confirmación animada.
 *
 * @param title Título principal de la cabecera.
 * @param mode Modo de operación activo ([QrScannerMode.SINGLE_ACCOUNT] o [QrScannerMode.TRANSFER_MIGRATION]).
 * @param isCameraActive Indica si la cámara se encuentra encendida y transmitiendo.
 * @param isProcessingBarcode Indica si hay un análisis de código en curso para bloquear lecturas concurrentes.
 * @param sessionChunks Mapa reactivo de fragmentos capturados durante la sesión de transferencia.
 * @param totalExpectedChunks Total de fragmentos requeridos para completar la transferencia.
 * @param duplicateChunkIndex Índice del fragmento duplicado si se re-escanea uno existente, o `null`.
 * @param capturedChunkAnimationIndex Índice del fragmento que dispara la animación central de captura exitosa, o `null`.
 * @param onCameraActiveChange Callback para alternar el encendido o apagado de la cámara.
 * @param onBarcodeScanned Callback invocado al capturar una cadena de código QR.
 * @param onClose Callback para cerrar el diálogo modal.
 * @param modifier Modificador de diseño Compose.
 */
@Composable
fun QrScanCameraStep(
    title: String,
    mode: QrScannerMode,
    isCameraActive: Boolean,
    isProcessingBarcode: Boolean,
    sessionChunks: Map<Int, TransferQrChunk>,
    totalExpectedChunks: Int,
    duplicateChunkIndex: Int?,
    capturedChunkAnimationIndex: Int?,
    onCameraActiveChange: (Boolean) -> Unit,
    onBarcodeScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimensions.Spacing.lg)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Cabecera fija
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )

        // 2. Cuerpo central scrolleable aislado
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CameraXBarcodeScannerView(
                isProcessingBarcode = isProcessingBarcode,
                isCameraActive = isCameraActive,
                onCameraActiveChange = onCameraActiveChange,
                onBarcodeScanned = onBarcodeScanned,
                overlayContent = {
                    if (mode == QrScannerMode.TRANSFER_MIGRATION) {
                        QrCaptureSuccessBadge(capturedChunkIndex = capturedChunkAnimationIndex)
                    }
                }
            )

            if (mode == QrScannerMode.TRANSFER_MIGRATION && totalExpectedChunks > 1 && sessionChunks.size < totalExpectedChunks) {
                Surface(
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(
                        width = Dimensions.Stroke.thin,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        // Cantidad de códigos QR que lleva capturados
                        Text(
                            text = stringResource(
                                R.string.scan_transfer_chunk_count_header,
                                sessionChunks.size,
                                totalExpectedChunks
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        // Fila de pastillas (Pills) con el estado individual de cada fragmento
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                Dimensions.Spacing.sm,
                                Alignment.CenterHorizontally
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (chunkIndex in 1..totalExpectedChunks) {
                                val isScanned = sessionChunks.containsKey(chunkIndex)
                                val pillContainerColor = if (isScanned) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                }
                                val pillContentColor = if (isScanned) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }

                                Surface(
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                                    color = pillContainerColor,
                                    border = if (!isScanned) {
                                        BorderStroke(
                                            width = Dimensions.Stroke.thin,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    } else null
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = Dimensions.Spacing.sm,
                                            vertical = Dimensions.Spacing.xs
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                                    ) {
                                        Icon(
                                            imageVector = if (isScanned) {
                                                Icons.Default.CheckCircle
                                            } else {
                                                Icons.Default.HourglassEmpty
                                            },
                                            contentDescription = null,
                                            tint = pillContentColor,
                                            modifier = Modifier.size(Dimensions.IconSize.small)
                                        )
                                        Text(
                                            text = stringResource(R.string.scan_transfer_chunk_pill_label, chunkIndex),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isScanned) FontWeight.Bold else FontWeight.Normal,
                                            color = pillContentColor
                                        )
                                    }
                                }
                            }
                        }

                        val isDuplicate = duplicateChunkIndex != null
                        if (isDuplicate) {
                            Text(
                                text = stringResource(
                                    R.string.scan_transfer_chunk_duplicate_title,
                                    duplicateChunkIndex
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        val statusDesc = if (isDuplicate) {
                            stringResource(R.string.scan_transfer_chunk_duplicate_desc)
                        } else {
                            stringResource(R.string.scan_transfer_chunk_captured_desc)
                        }

                        Text(
                            text = statusDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Text(
                    text = if (mode == QrScannerMode.TRANSFER_MIGRATION) {
                        stringResource(R.string.scan_import_hint)
                    } else {
                        stringResource(R.string.scan_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 3. Pie fijo de acciones
        AppDialogActionButtons(
            onDismiss = onClose,
            dismissText = stringResource(R.string.action_close)
        )
    }
}

/**
 * Insignia animada central proyectada sobre el visor de cámara al capturar exitosamente un código QR.
 *
 * Emerge con animación de rebote elástico suave y desaparece automáticamente tras confirmar la captura.
 *
 * @param capturedChunkIndex Índice numérico del código QR capturado, o `null` si no hay animación activa.
 * @param modifier Modificador de diseño Compose.
 */
@Composable
private fun BoxScope.QrCaptureSuccessBadge(
    capturedChunkIndex: Int?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = capturedChunkIndex != null,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = Motion.Duration.FAST,
                easing = Motion.EasingCurve.Standard
            )
        ) + scaleIn(
            initialScale = 0.75f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = Motion.Duration.FAST,
                easing = Motion.EasingCurve.Standard
            )
        ) + scaleOut(
            targetScale = 0.85f,
            animationSpec = tween(
                durationMillis = Motion.Duration.FAST,
                easing = Motion.EasingCurve.Standard
            )
        ),
        modifier = modifier.align(Alignment.Center)
    ) {
        Surface(
            shape = RoundedCornerShape(Dimensions.CornerRadius.large),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            border = BorderStroke(
                width = Dimensions.Stroke.regular,
                color = MaterialTheme.colorScheme.primary
            ),
            shadowElevation = Dimensions.Elevation.cardDragging,
            modifier = Modifier.padding(Dimensions.Spacing.md)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs),
                modifier = Modifier.padding(
                    horizontal = Dimensions.Spacing.lg,
                    vertical = Dimensions.Spacing.md
                )
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(Dimensions.IconSize.hero)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(Dimensions.IconSize.large)
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.scan_transfer_center_captured_badge,
                        capturedChunkIndex ?: 1
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
