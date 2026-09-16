package com.example.appopt.ui.screens.settings.dialogs.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
 * @param isKeyVisible Indica si la clave criptográfica de 64 dígitos se muestra en texto claro.
 * @param onToggleKeyVisibility Callback para alternar la visibilidad de la clave de 64 dígitos.
 * @param copyCountdown Segundos restantes de retención en el portapapeles seguro (0 cuando no está copiado).
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
    isKeyVisible: Boolean,
    onToggleKeyVisibility: () -> Unit,
    copyCountdown: Int,
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Selector de Método Prominente y Elegante (Tarjetas de Selección Táctil)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
        ) {
            // Opción 0: Contraseña Maestra
            val isTab0 = selectedTab == 0
            Surface(
                onClick = { onTabSelected(0) },
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = if (isTab0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(
                    if (isTab0) Dimensions.Stroke.regular else Dimensions.Stroke.thin,
                    if (isTab0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(Dimensions.ComponentSize.methodSelectorCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Password,
                        contentDescription = null,
                        tint = if (isTab0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimensions.IconSize.medium)
                    )
                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                    Text(
                        text = stringResource(R.string.settings_drive_method_password),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        color = if (isTab0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Opción 1: Clave de 64 Dígitos
            val isTab1 = selectedTab == 1
            Surface(
                onClick = { onTabSelected(1) },
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = if (isTab1) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(
                    if (isTab1) Dimensions.Stroke.regular else Dimensions.Stroke.thin,
                    if (isTab1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(Dimensions.ComponentSize.methodSelectorCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = null,
                        tint = if (isTab1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimensions.IconSize.medium)
                    )
                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                    Text(
                        text = stringResource(R.string.settings_drive_method_key),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        color = if (isTab1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (selectedTab == 0) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)) {
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

                Text(
                    text = stringResource(R.string.settings_drive_password_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)) {
                // Tarjeta Criptográfica Premium para Clave de 64 Dígitos
                Surface(
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(Dimensions.Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_drive_key_label),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = onToggleKeyVisibility,
                                modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                            ) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isKeyVisible) {
                                    generated64Key.chunked(16).joinToString("\n")
                                } else {
                                    "••••••••••••••••\n••••••••••••••••\n••••••••••••••••\n••••••••••••••••"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(Dimensions.Spacing.md)
                            )
                        }

                        Text(
                            text = stringResource(R.string.settings_drive_key_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Botones Simétricos con Altura y Tipografía Idéntica
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onRegenerateKey,
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        contentPadding = PaddingValues(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.xs),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.IconSize.small)
                        )
                        Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                        Text(
                            text = stringResource(R.string.settings_drive_key_regenerate_short),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = onCopyKey,
                        enabled = copyCountdown <= 0,
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        contentPadding = PaddingValues(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.xs),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                    ) {
                        Icon(
                            imageVector = if (copyCountdown > 0) Icons.Filled.Timer else Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.IconSize.small)
                        )
                        Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                        Text(
                            text = if (copyCountdown > 0) {
                                stringResource(R.string.settings_drive_copied_countdown, copyCountdown)
                            } else {
                                stringResource(R.string.settings_drive_copy_key_short)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
