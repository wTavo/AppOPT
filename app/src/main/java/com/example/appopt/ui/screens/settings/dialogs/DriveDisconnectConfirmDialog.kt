package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.components.AppDestructiveConfirmDialog

/**
 * Diálogo modal de confirmación destructiva para desvincular la cuenta de Google Drive.
 *
 * @param onConfirm Callback invocado al confirmar la desconexión de la cuenta.
 * @param onDismiss Callback invocado para cerrar el diálogo sin realizar cambios.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveDisconnectConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppDestructiveConfirmDialog(
        title = stringResource(R.string.settings_drive_disconnect_confirm_title),
        message = stringResource(R.string.settings_drive_disconnect_confirm_msg),
        confirmText = stringResource(R.string.settings_drive_disconnect_button),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        icon = Icons.Filled.CloudOff,
        modifier = modifier
    )
}
