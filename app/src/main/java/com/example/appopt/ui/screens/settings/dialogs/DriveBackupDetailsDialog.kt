package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions

/**
 * Diálogo modal para visualizar los detalles de la copia de seguridad en Google Drive y gestionar su eliminación permanente.
 *
 * Implementa una máquina de estado unificada con navegación defensiva para el paso de confirmación de borrado.
 *
 * @param formattedLastSync Marca de tiempo relativa o absoluta de la última copia de seguridad.
 * @param isLoading Indica si hay una operación asíncrona en curso.
 * @param onDeleteConfirmed Callback invocado al confirmar la eliminación de la copia remota.
 * @param onDismiss Callback invocado para cerrar el diálogo.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveBackupDetailsDialog(
    formattedLastSync: String?,
    isLoading: Boolean,
    onDeleteConfirmed: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isConfirmingDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (isConfirmingDelete) {
                isConfirmingDelete = false
            } else {
                onDismiss()
            }
        },
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        title = {
            Text(
                text = if (isConfirmingDelete) {
                    stringResource(R.string.settings_drive_delete_confirm_title)
                } else {
                    stringResource(R.string.settings_drive_details_title)
                },
                style = MaterialTheme.typography.titleLarge,
                color = if (isConfirmingDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            if (!isConfirmingDelete) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                ) {
                    Text(
                        text = stringResource(R.string.settings_drive_details_date, formattedLastSync ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_drive_details_encryption),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { isConfirmingDelete = true },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.IconSize.small)
                        )
                        Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                        Text(
                            text = stringResource(R.string.settings_drive_delete_button),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.settings_drive_delete_confirm_msg),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            if (isConfirmingDelete) {
                Button(
                    onClick = {
                        isConfirmingDelete = false
                        onDeleteConfirmed()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Text(
                        text = stringResource(R.string.settings_drive_delete_confirm_btn),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        dismissButton = {
            if (isConfirmingDelete) {
                TextButton(onClick = { isConfirmingDelete = false }) {
                    Text(
                        text = stringResource(R.string.settings_drive_details_back),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        modifier = modifier
    )
}
