package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.security.MnemonicManager
import com.example.appopt.ui.screens.settings.dialogs.components.DriveBackupDecryptForm
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Diálogo modal para solicitar la clave de descifrado (contraseña personalizada o frase BIP-39 de 12 palabras)
 * al restaurar una copia de seguridad seleccionada de Google Drive.
 *
 * @param backupDateMillis Marca de tiempo de la copia de seguridad que se va a restaurar (opcional).
 * @param deviceName Nombre del dispositivo emisor del respaldo (opcional).
 * @param isActual Indica si la copia seleccionada corresponde al estado actual de la bóveda local.
 * @param onRestore Callback invocado con los caracteres de descifrado ([CharArray]).
 * @param onDismiss Callback invocado para cerrar el modal.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveDecryptDialog(
    backupDateMillis: Long? = null,
    deviceName: String? = null,
    isActual: Boolean = false,
    onRestore: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()
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
            val targetBackup = remember(backupDateMillis, deviceName, isActual) {
                DriveBackupItem(
                    fileId = "",
                    fileName = "",
                    modifiedTimeMillis = backupDateMillis ?: 0L,
                    sizeBytes = 0L,
                    deviceName = deviceName.orEmpty(),
                    isMostRecent = isActual
                )
            }

            DriveBackupDecryptForm(
                targetBackup = targetBackup,
                isActual = isActual,
                restoreSecretText = restoreSecretText,
                onRestoreSecretChange = { restoreSecretText = it },
                isRestoreSecretVisible = isRestoreSecretVisible,
                onToggleSecretVisibility = { isRestoreSecretVisible = !isRestoreSecretVisible }
            )
        },
        confirmButton = {
            var isProcessing by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        appHaptics.click()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    modifier = Modifier
                        .fillMaxHeight()
                        .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                ) {
                    Text(
                        text = stringResource(R.string.action_close),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = {
                        if (!isProcessing) {
                            isProcessing = true
                            appHaptics.click()
                            val normalizedSecret = if (restoreSecretText.contains(" ")) {
                                MnemonicManager.normalizePhrase(restoreSecretText)
                            } else {
                                restoreSecretText.trim()
                            }
                            val passChars = normalizedSecret.toCharArray()
                            onRestore(passChars)
                            restoreSecretText = ""
                            isProcessing = false
                        }
                    },
                    enabled = restoreSecretText.isNotBlank() && !isProcessing,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.xs),
                    modifier = Modifier
                        .fillMaxHeight()
                        .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                ) {
                    Text(
                        text = stringResource(R.string.settings_drive_decrypt_and_restore),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        dismissButton = null,
        modifier = modifier
    )
}
