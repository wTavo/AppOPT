package com.example.appopt.ui.screens.settings.dialogs.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.theme.Dimensions

/**
 * Paso modal destructivo para confirmar la eliminación autenticada de una versión específica de respaldo (Directivas 14, 22 y 29).
 *
 * @param targetBackup Respaldo seleccionado para eliminación.
 * @param deleteSecretText Clave o contraseña ingresada para autorizar el borrado.
 * @param onDeleteSecretTextChange Callback al modificar la clave.
 * @param isSecretVisible Indica si la clave está en texto claro.
 * @param onToggleSecretVisibility Callback para alternar visibilidad.
 * @param onConfirmDelete Callback para ejecutar el borrado autenticado.
 * @param onBack Callback para regresar al historial sin borrar.
 */
@Composable
fun DriveDetailsDeleteStep(
    targetBackup: DriveBackupItem?,
    deleteSecretText: String,
    onDeleteSecretTextChange: (String) -> Unit,
    isSecretVisible: Boolean,
    onToggleSecretVisibility: () -> Unit,
    onConfirmDelete: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.Spacing.lg)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        // Cabecera fija
        Text(
            text = stringResource(R.string.settings_drive_delete_version_confirm_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error
        )

        // Cuerpo central scrolleable aislado
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            if (targetBackup != null) {
                if (targetBackup.isEncrypted) {
                    DriveBackupDecryptForm(
                        targetBackup = targetBackup,
                        isActual = false,
                        restoreSecretText = deleteSecretText,
                        onRestoreSecretChange = onDeleteSecretTextChange,
                        isRestoreSecretVisible = isSecretVisible,
                        onToggleSecretVisibility = onToggleSecretVisibility,
                        hintText = stringResource(R.string.settings_drive_delete_version_auth_hint)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_drive_delete_version_auth_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Pie fijo de acciones
        AppDialogActionButtons(
            confirmText = stringResource(R.string.action_delete),
            onConfirm = onConfirmDelete,
            dismissText = stringResource(R.string.settings_drive_details_back),
            onDismiss = onBack,
            confirmEnabled = if (targetBackup?.isEncrypted == false) true else deleteSecretText.isNotBlank(),
            isDestructive = true
        )
    }
}
