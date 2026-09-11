package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.data.cloud.DriveBackupInfo
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.DateTimeFormatter

/**
 * Diálogo modal de advertencia preventiva cuando existe un respaldo previo en la nube y se intenta sobrescribir.
 *
 * @param backupInfo Metadatos de la copia de seguridad existente en Google Drive.
 * @param formattedLastSync Marca de tiempo de sincronización formateada alternativa.
 * @param onConfirmOverwrite Callback invocado al confirmar la sobrescritura del respaldo.
 * @param onRestoreInstead Callback invocado si el usuario prefiere restaurar la copia existente.
 * @param onDismiss Callback invocado para cancelar y cerrar el modal.
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

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        icon = {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(Dimensions.IconSize.hero)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.settings_drive_overwrite_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.settings_drive_overwrite_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
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
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            appHaptics.click()
                            onRestoreInstead()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.xs),
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_drive_overwrite_restore_btn),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = {
                            appHaptics.click()
                            onConfirmOverwrite()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        contentPadding = PaddingValues(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.xs),
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_drive_overwrite_confirm_btn),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            appHaptics.click()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(
                            text = stringResource(R.string.action_close),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        dismissButton = null,
        modifier = modifier
    )
}
