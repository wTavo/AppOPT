package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.data.cloud.DriveBackupInfo
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.components.ModalTone
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.DateTimeFormatter

/**
 * Diálogo modal de advertencia preventiva cuando existe un respaldo previo en la nube y se intenta sobrescribir.
 *
 * Cumple estrictamente con:
 * - Directiva 14: Estructura tripartita inmutable (Cabecera fija, cuerpo central scrolleable aislado y pie fijo).
 * - Directiva 14: Tono destructivo [ModalTone.DESTRUCTIVE] con fondo y borde diferenciados.
 * - Directiva 14: Barra unificada de acciones mediante [AppDialogActionButtons] («Cerrar» a la izquierda y acción destructiva a la derecha).
 * - Directiva 22: Idempotencia y confirmación visual animada.
 * - Directiva 23: Simetría de controles y altura estándar de 50.dp.
 *
 * @param backupInfo Metadatos de la copia de seguridad existente en Google Drive.
 * @param formattedLastSync Marca de tiempo de sincronización formateada alternativa.
 * @param onConfirmOverwrite Callback invocado al confirmar la sobrescritura del respaldo.
 * @param onRestoreInstead Callback invocado si el usuario prefiere restaurar la copia existente.
 * @param onDismiss Callback invocado para cerrar el modal sin realizar cambios.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveOverwriteWarningDialog(
    backupInfo: DriveBackupInfo?,
    formattedLastSync: String?,
    onConfirmOverwrite: () -> Unit,
    onRestoreInstead: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()
    val backupDateFormatted = remember(backupInfo, formattedLastSync) {
        backupInfo?.modifiedTimeMillis?.let {
            DateTimeFormatter.formatAbsoluteDateTime(it)
        } ?: (formattedLastSync ?: "")
    }
    val defaultDeviceName = stringResource(R.string.drive_default_device_name)
    val deviceName = backupInfo?.deviceName ?: defaultDeviceName

    AppModalDialog(
        onDismissRequest = onDismiss,
        tone = ModalTone.DESTRUCTIVE,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.lg)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            // 1. Cabecera fija superior
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(Dimensions.IconSize.large)
                )

                Text(
                    text = stringResource(R.string.settings_drive_overwrite_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            // 2. Cuerpo central scrolleable aislado
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
            ) {
                Text(
                    text = stringResource(R.string.settings_drive_overwrite_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Tarjeta con metadatos de la copia existente
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    border = BorderStroke(
                        width = Dimensions.Stroke.thin,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(Dimensions.Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_drive_overwrite_date, backupDateFormatted),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_drive_overwrite_device, deviceName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Tarjeta interactiva para la opción alternativa de restauración
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    border = BorderStroke(
                        width = Dimensions.Stroke.thin,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(Dimensions.Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.settings_drive_overwrite_restore_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = {
                                appHaptics.click()
                                onRestoreInstead()
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                            contentPadding = PaddingValues(
                                horizontal = Dimensions.Spacing.md,
                                vertical = Dimensions.Spacing.xs
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Dimensions.ComponentHeight.buttonCompact)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_drive_overwrite_restore_btn),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 3. Pie fijo inferior de acciones simétricas con botón Cerrar a la izquierda
            AppDialogActionButtons(
                dismissText = stringResource(R.string.action_close),
                onDismiss = onDismiss,
                confirmText = stringResource(R.string.settings_drive_overwrite_confirm_btn),
                onConfirm = onConfirmOverwrite,
                isDestructive = true
            )
        }
    }
}
