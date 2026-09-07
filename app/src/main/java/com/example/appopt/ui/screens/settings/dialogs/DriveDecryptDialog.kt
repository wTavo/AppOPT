package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.appopt.R
import com.example.appopt.security.MnemonicManager
import com.example.appopt.ui.theme.Dimensions

/**
 * Diálogo modal para ingresar la clave o frase de recuperación mnemónica y restaurar la bóveda desde Google Drive.
 *
 * Aplica el principio de Cero Confianza y sobreescritura segura de contraseñas tras su procesamiento.
 *
 * @param onRestore Callback invocado con los caracteres de descifrado ([CharArray]).
 * @param onDismiss Callback invocado para cerrar el modal.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveDecryptDialog(
    onRestore: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var restoreSecretText by remember { mutableStateOf("") }
    var isRestoreSecretVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        title = {
            Text(
                text = stringResource(R.string.settings_drive_decrypt_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
            ) {
                Text(
                    text = stringResource(R.string.settings_drive_decrypt_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = restoreSecretText,
                    onValueChange = { restoreSecretText = it },
                    label = { Text(stringResource(R.string.settings_drive_decrypt_input_label)) },
                    visualTransformation = if (isRestoreSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isRestoreSecretVisible = !isRestoreSecretVisible }) {
                            Icon(
                                imageVector = if (isRestoreSecretVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            var isProcessing by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    if (!isProcessing) {
                        isProcessing = true
                        val normalizedSecret = if (restoreSecretText.contains(" ")) {
                            MnemonicManager.normalizePhrase(restoreSecretText)
                        } else {
                            restoreSecretText.trim()
                        }
                        val passChars = normalizedSecret.toCharArray()
                        try {
                            onRestore(passChars)
                        } finally {
                            passChars.fill('0')
                        }
                    }
                },
                enabled = restoreSecretText.isNotBlank() && !isProcessing,
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
            ) {
                Text(
                    text = stringResource(R.string.settings_drive_decrypt_and_restore),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        modifier = modifier
    )
}
