package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.appopt.R
import com.example.appopt.security.MnemonicManager
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.util.DateTimeFormatter

/**
 * Diálogo modal para ingresar la clave o frase de recuperación mnemónica y restaurar la bóveda desde Google Drive.
 *
 * Aplica el principio de Cero Confianza y sobreescritura segura de contraseñas tras su procesamiento.
 *
 * @param backupDateMillis Marca de tiempo de la copia de seguridad que se va a restaurar (opcional).
 * @param deviceName Nombre del dispositivo emisor del respaldo (opcional).
 * @param isMostRecent Indica si la copia seleccionada corresponde a la versión más reciente en la nube.
 * @param onRestore Callback invocado con los caracteres de descifrado ([CharArray]).
 * @param onDismiss Callback invocado para cerrar el modal.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveDecryptDialog(
    backupDateMillis: Long? = null,
    deviceName: String? = null,
    isMostRecent: Boolean = false,
    onRestore: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                if (backupDateMillis != null && backupDateMillis > 0L) {
                    val formattedDate = remember(backupDateMillis) {
                        DateTimeFormatter.formatRelativeSyncTime(context, backupDateMillis)
                    }

                    Surface(
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            Dimensions.Stroke.thin,
                            if (isMostRecent) SafeGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimensions.Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(Dimensions.IconSize.hero)
                                    .background(
                                        color = (if (isMostRecent) SafeGreen else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isMostRecent) Icons.Filled.CloudDone else Icons.Filled.Restore,
                                    contentDescription = null,
                                    tint = if (isMostRecent) SafeGreen else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(Dimensions.IconSize.small)
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                                ) {
                                    Text(
                                        text = formattedDate,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isMostRecent) {
                                        Surface(
                                            shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                                            color = SafeGreen.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.settings_drive_version_most_recent_badge),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SafeGreen,
                                                modifier = Modifier.padding(horizontal = Dimensions.Spacing.xs, vertical = Dimensions.Spacing.xs)
                                            )
                                        }
                                    }
                                }

                                if (!deviceName.isNullOrBlank()) {
                                    Text(
                                        text = stringResource(R.string.settings_drive_decrypt_device, deviceName),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

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
                    text = stringResource(R.string.action_close),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        modifier = modifier
    )
}
