package com.example.appopt.ui.components.details

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
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.theme.Dimensions

/**
 * Paso modal para confirmar el traslado a la papelera de reciclaje de una cuenta (Directivas 14, 22 y 29).
 *
 * @param onConfirmDelete Callback para confirmar y trasladar a la papelera.
 * @param onDismiss Callback para cancelar la confirmación y volver.
 */
@Composable
fun AccountDetailsDeleteStep(
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.Spacing.lg)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        // Cabecera fija de eliminación
        Text(
            text = stringResource(R.string.home_delete_dialog_title),
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
            Text(
                text = stringResource(R.string.account_details_delete_to_trash_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Pie fijo con botones de acción destructiva
        AppDialogActionButtons(
            dismissText = stringResource(R.string.settings_drive_details_back),
            onDismiss = onDismiss,
            confirmText = stringResource(R.string.account_details_move_to_trash_btn),
            onConfirm = onConfirmDelete,
            isDestructive = true
        )
    }
}
