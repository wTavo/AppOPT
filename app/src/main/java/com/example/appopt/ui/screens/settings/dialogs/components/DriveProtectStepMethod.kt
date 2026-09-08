package com.example.appopt.ui.screens.settings.dialogs.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions

/**
 * Sub-componente para el Paso 1 del diálogo de protección: Selección y configuración del método de protección principal.
 *
 * Permite al usuario alternar entre una contraseña maestra personalizada o una clave criptográfica de 64 dígitos generada aleatoriamente.
 *
 * @param selectedTab Índice de la pestaña seleccionada (0: Contraseña, 1: Clave de 64 dígitos).
 * @param onTabSelected Callback invocado al cambiar de pestaña.
 * @param masterPasswordText Texto de la contraseña maestra ingresada.
 * @param onMasterPasswordChange Callback para actualizar la contraseña maestra.
 * @param masterPasswordConfirmText Texto de confirmación de la contraseña maestra.
 * @param onMasterPasswordConfirmChange Callback para actualizar la confirmación de la contraseña.
 * @param isMasterPasswordVisible Indica si los campos de contraseña se muestran en texto plano.
 * @param onTogglePasswordVisibility Callback para alternar la visibilidad de las contraseñas.
 * @param generated64Key Clave hexadecimal de 64 caracteres generada.
 * @param onRegenerateKey Callback para generar una nueva clave aleatoria.
 * @param onCopyKey Callback para copiar la clave al portapapeles seguro.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveProtectStepMethod(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    masterPasswordText: String,
    onMasterPasswordChange: (String) -> Unit,
    masterPasswordConfirmText: String,
    onMasterPasswordConfirmChange: (String) -> Unit,
    isMasterPasswordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    generated64Key: String,
    onRegenerateKey: () -> Unit,
    onCopyKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        Text(
            text = stringResource(R.string.settings_drive_protect_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                text = { Text(stringResource(R.string.settings_drive_method_password), style = MaterialTheme.typography.labelSmall) },
                icon = { Icon(Icons.Filled.Password, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                text = { Text(stringResource(R.string.settings_drive_method_key), style = MaterialTheme.typography.labelSmall) },
                icon = { Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small)) }
            )
        }

        if (selectedTab == 0) {
            OutlinedTextField(
                value = masterPasswordText,
                onValueChange = onMasterPasswordChange,
                label = { Text(stringResource(R.string.settings_drive_password_label)) },
                visualTransformation = if (isMasterPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisibility) {
                        Icon(
                            imageVector = if (isMasterPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null
                        )
                    }
                },
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = masterPasswordConfirmText,
                onValueChange = onMasterPasswordConfirmChange,
                label = { Text(stringResource(R.string.settings_drive_password_confirm_label)) },
                visualTransformation = if (isMasterPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = masterPasswordConfirmText.isNotEmpty() && masterPasswordText != masterPasswordConfirmText,
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Surface(
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Dimensions.Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.settings_drive_key_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = generated64Key.chunked(16).joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onRegenerateKey) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.settings_drive_key_regenerate))
                        }
                        IconButton(onClick = onCopyKey) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.settings_drive_copy_60s))
                        }
                    }
                }
            }
        }
    }
}
