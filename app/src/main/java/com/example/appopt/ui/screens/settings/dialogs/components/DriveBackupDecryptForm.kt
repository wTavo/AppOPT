package com.example.appopt.ui.screens.settings.dialogs.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.appopt.R
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.util.DateTimeFormatter

/**
 * Sub-componente para el formulario de descifrado y restauración in-situ de una versión de respaldo.
 *
 * Muestra los metadatos de la copia a restaurar (dispositivo y fecha) junto con el campo de texto
 * para ingresar la clave o frase de descifrado.
 *
 * @param targetBackup Versión de respaldo que se va a restaurar.
 * @param restoreSecretText Texto actual de la contraseña o frase mnemónica ingresada.
 * @param onRestoreSecretChange Callback para actualizar el texto del secreto.
 * @param isRestoreSecretVisible Indica si el texto del campo es visible u oculto.
 * @param onToggleSecretVisibility Callback para alternar la visibilidad de la contraseña.
 * @param modifier Modificador de diseño Compose opcional.
 * @param isActual Indica si la versión corresponde a la copia activa del dispositivo.
 * @param hintText Texto de ayuda o instrucción que se muestra debajo del campo.
 */
@Composable
fun DriveBackupDecryptForm(
    targetBackup: DriveBackupItem?,
    restoreSecretText: String,
    onRestoreSecretChange: (String) -> Unit,
    isRestoreSecretVisible: Boolean,
    onToggleSecretVisibility: () -> Unit,
    modifier: Modifier = Modifier,
    isActual: Boolean = false,
    hintText: String = stringResource(R.string.settings_drive_decrypt_hint)
) {
    val context = LocalContext.current
    val formattedDate = remember(targetBackup?.modifiedTimeMillis) {
        targetBackup?.modifiedTimeMillis?.let { DateTimeFormatter.formatRelativeSyncTime(context, it) } ?: ""
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        if (targetBackup != null) {
            Surface(
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(
                    Dimensions.Stroke.thin,
                    if (isActual) SafeGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
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
                                color = (if (isActual) SafeGreen else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isActual) Icons.Filled.CloudDone else Icons.Filled.Restore,
                            contentDescription = null,
                            tint = if (isActual) SafeGreen else MaterialTheme.colorScheme.primary,
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
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            if (isActual) {
                                Surface(
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                                    color = SafeGreen.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_drive_version_actual_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SafeGreen,
                                        maxLines = 1,
                                        modifier = Modifier.padding(
                                            horizontal = Dimensions.Spacing.xs,
                                            vertical = Dimensions.Spacing.xs / 2
                                        )
                                    )
                                }
                            }
                        }

                        if (targetBackup.deviceName.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.settings_drive_decrypt_device, targetBackup.deviceName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = hintText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = restoreSecretText,
            onValueChange = onRestoreSecretChange,
            label = { Text(stringResource(R.string.settings_drive_decrypt_input_label)) },
            visualTransformation = if (isRestoreSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleSecretVisibility) {
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
}
