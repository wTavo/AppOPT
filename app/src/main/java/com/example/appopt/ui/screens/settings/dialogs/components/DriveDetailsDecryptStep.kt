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
 * Paso modal para ingresar la contraseña o frase mnemónica para descifrar un respaldo seleccionado (Directivas 14 y 29).
 *
 * @param targetBackup Respaldo seleccionado para descifrar.
 * @param secretText Texto actual de la clave o contraseña ingresada.
 * @param onSecretTextChange Callback al modificar la clave.
 * @param isSecretVisible Indica si el texto de la clave está visible.
 * @param onToggleSecretVisibility Callback para alternar la visibilidad de la clave.
 * @param decryptErrorMessage Mensaje de error de descifrado opcional.
 * @param isDecrypting Indica si la operación asíncrona de descifrado está en curso.
 * @param onConfirm Callback para ejecutar el descifrado y restauración.
 * @param onBack Callback para regresar al historial.
 */
@Composable
fun DriveDetailsDecryptStep(
    targetBackup: DriveBackupItem?,
    secretText: String,
    onSecretTextChange: (String) -> Unit,
    isSecretVisible: Boolean,
    onToggleSecretVisibility: () -> Unit,
    decryptErrorMessage: String?,
    isDecrypting: Boolean,
    onConfirm: () -> Unit,
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
            text = stringResource(R.string.settings_drive_decrypt_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
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
                DriveBackupDecryptForm(
                    targetBackup = targetBackup,
                    isActual = false,
                    restoreSecretText = secretText,
                    onRestoreSecretChange = onSecretTextChange,
                    isRestoreSecretVisible = isSecretVisible,
                    onToggleSecretVisibility = onToggleSecretVisibility,
                    hintText = stringResource(R.string.settings_drive_decrypt_hint)
                )
            }

            if (decryptErrorMessage != null) {
                Text(
                    text = decryptErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // Pie fijo de acciones
        AppDialogActionButtons(
            confirmText = stringResource(R.string.settings_drive_decrypt_and_restore),
            onConfirm = onConfirm,
            dismissText = stringResource(R.string.settings_drive_details_back),
            onDismiss = onBack,
            confirmEnabled = secretText.isNotBlank() && !isDecrypting
        )
    }
}
