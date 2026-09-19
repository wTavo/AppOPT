package com.example.appopt.ui.components.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Paso modal para la edición del nombre de emisor y cuenta (Directivas 14 y 29).
 *
 * @param editedIssuer Nombre del emisor editado.
 * @param onIssuerChange Callback al escribir en el campo de emisor.
 * @param editedAccountName Nombre de cuenta editado.
 * @param onAccountNameChange Callback al escribir en el campo de cuenta.
 * @param isFormValid Indica si hay cambios válidos para guardar.
 * @param onSaveChanges Callback para persistir los cambios.
 * @param onCancelEdit Callback para revertir y volver al modo de visualización.
 */
@Composable
fun AccountDetailsEditStep(
    editedIssuer: String,
    onIssuerChange: (String) -> Unit,
    editedAccountName: String,
    onAccountNameChange: (String) -> Unit,
    isFormValid: Boolean,
    onSaveChanges: () -> Unit,
    onCancelEdit: () -> Unit
) {
    val appHaptics = rememberAppHaptics()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.Spacing.lg)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        // Cabecera fija de Edición
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.account_modal_edit_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                onClick = {
                    appHaptics.click()
                    onCancelEdit()
                },
                modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_cancel_edit),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Cuerpo central scrolleable aislado con campos de texto
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            OutlinedTextField(
                value = editedIssuer,
                onValueChange = onIssuerChange,
                label = { Text(stringResource(R.string.account_modal_issuer_label), style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Business, contentDescription = null)
                },
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = editedAccountName,
                onValueChange = onAccountNameChange,
                label = { Text(stringResource(R.string.account_modal_name_label), style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.PersonOutline, contentDescription = null)
                },
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Pie fijo con Botones Volver + Guardar cambios
        AppDialogActionButtons(
            dismissText = stringResource(R.string.settings_drive_details_back),
            onDismiss = onCancelEdit,
            confirmText = stringResource(R.string.action_save_changes),
            onConfirm = onSaveChanges,
            confirmEnabled = isFormValid
        )
    }
}
