package com.example.appopt.ui.screens.settings.dialogs.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.components.ModalTone
import com.example.appopt.ui.theme.Dimensions

/**
 * Diálogo modal de advertencia y confirmación para desactivar el cifrado de extremo a extremo (E2EE) en Google Drive.
 *
 * Directivas de diseño:
 * - Emplea [AppModalDialog] con tono destructivo / advertencia ([ModalTone.DESTRUCTIVE]) y estructura tripartita inmutable (Directiva 14).
 * - Botones simétricos 50/50 mediante [AppDialogActionButtons] (Directiva 23).
 * - Textos centralizados en español estándar y en Sentence case (Directiva 2).
 *
 * @param onConfirm Callback invocado al confirmar la desactivación del cifrado.
 * @param onDismiss Callback para cancelar o descartar el diálogo modal.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveDisableE2eeConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppModalDialog(
        onDismissRequest = onDismiss,
        tone = ModalTone.DESTRUCTIVE,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            // 1. Cabecera fija superior
            Text(
                text = stringResource(R.string.settings_drive_disable_e2ee_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )

            // 2. Cuerpo central scrolleable aislado
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
            ) {
                Text(
                    text = stringResource(R.string.settings_drive_disable_e2ee_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 3. Pie fijo inferior de acciones
            AppDialogActionButtons(
                confirmText = stringResource(R.string.settings_drive_disable_e2ee_confirm_btn),
                onConfirm = onConfirm,
                dismissText = stringResource(R.string.settings_drive_details_back),
                onDismiss = onDismiss,
                isDestructive = true
            )
        }
    }
}
