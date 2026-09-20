package com.example.appopt.ui.screens.settings.dialogs.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.theme.Dimensions

/**
 * Diálogo modal de confirmación transparente para la creación de una copia de seguridad estándar
 * en Google Drive sin cifrado de extremo a extremo (E2EE).
 *
 * Directivas de diseño:
 * - Emplea [AppModalDialog] con estructura tripartita inmutable (Directiva 14).
 * - Botones simétricos 50/50 mediante [AppDialogActionButtons] (Directiva 23).
 * - Textos centralizados en español estándar y Sentence case (Directiva 2).
 * - Modularidad y límite de extensión estricto (Directiva 29).
 * - Principio de Cero Confianza / Zero-Trust (Directiva 9).
 *
 * @param onConfirm Callback al confirmar la creación de la copia estándar en la nube.
 * @param onEnableE2ee Callback para alternar al flujo de protección con contraseña E2EE.
 * @param onDismiss Callback para descartar o volver del diálogo modal.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveCreateStandardBackupConfirmDialog(
    onConfirm: () -> Unit,
    onEnableE2ee: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppModalDialog(
        onDismissRequest = onDismiss,
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
                text = stringResource(R.string.settings_drive_create_standard_confirm_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
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
                    text = stringResource(R.string.settings_drive_create_standard_confirm_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(
                    onClick = onEnableE2ee,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = stringResource(R.string.settings_drive_create_standard_enable_e2ee_action),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 3. Pie fijo inferior de acciones simétricas
            AppDialogActionButtons(
                confirmText = stringResource(R.string.settings_drive_create_confirm_action),
                onConfirm = onConfirm,
                dismissText = stringResource(R.string.settings_drive_details_back),
                onDismiss = onDismiss
            )
        }
    }
}
