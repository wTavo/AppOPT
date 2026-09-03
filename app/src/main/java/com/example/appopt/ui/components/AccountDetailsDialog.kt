package com.example.appopt.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.appopt.R
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.repository.AccountWithCode
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.rememberAppHaptics
import kotlinx.coroutines.delay

/**
 * Modal / Popup para visualizar o editar la información de una cuenta con escala tipográfica estandarizada y animaciones centralizadas.
 *
 * Características de diseño:
 * - En modo visualización muestra limpiamente el nombre del servicio y la cuenta usando la escala tipográfica de la app.
 * - Los dígitos aparecen abajo junto con el contador/temporizador circular de rotación TOTP.
 * - En modo edición permite modificar el nombre del servicio y el nombre de la cuenta con validación instantánea.
 * - Incluye confirmación de eliminación unificada dentro del mismo modal sin superponer ventanas secundarias.
 *
 * @param accountWithCode Contenedor con la entidad de cuenta, código activo y progreso temporal.
 * @param onDismiss Callback para cerrar el diálogo modal.
 * @param onUpdateAccount Callback para persistir los cambios de emisor y nombre de cuenta.
 * @param onDeleteAccount Callback para eliminar permanentemente la cuenta de la bóveda.
 * @param onCopyCode Callback invocado al pulsar sobre el código para copiarlo al portapapeles.
 * @param modifier Modificador de layout.
 */
@Composable
fun AccountDetailsDialog(
    accountWithCode: AccountWithCode,
    onDismiss: () -> Unit,
    onUpdateAccount: (String, String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val account = accountWithCode.account
    val appHaptics = rememberAppHaptics()
    var isEditMode by remember { mutableStateOf(false) }
    var editedIssuer by remember { mutableStateOf(account.issuer) }
    var editedAccountName by remember { mutableStateOf(account.accountName) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(Motion.Duration.FeedbackToast.toLong())
            copied = false
        }
    }

    // Formato visual de los dígitos
    val formattedCode = remember(accountWithCode.code) {
        val raw = accountWithCode.code
        when (raw.length) {
            6 -> "${raw.substring(0, 3)} ${raw.substring(3)}"
            8 -> "${raw.substring(0, 4)} ${raw.substring(4)}"
            else -> raw
        }
    }

    // Comprueba si hubo alguna modificación real en los campos editados
    val hasChanges = remember(editedIssuer, editedAccountName, account.issuer, account.accountName) {
        editedIssuer.trim() != account.issuer.trim() || editedAccountName.trim() != account.accountName.trim()
    }
    val isFormValid = editedIssuer.isNotBlank() && hasChanges

    AlertDialog(
        onDismissRequest = {
            if (showDeleteConfirm) {
                showDeleteConfirm = false
            } else if (isEditMode) {
                isEditMode = false
            } else {
                onDismiss()
            }
        },
        title = {
            if (showDeleteConfirm) {
                Text(
                    text = stringResource(R.string.home_delete_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isEditMode) stringResource(R.string.account_modal_edit_title) else stringResource(R.string.account_modal_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Iconos de acción arriba a la derecha: Lápiz y Basurero
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                appHaptics.click()
                                isEditMode = !isEditMode
                            },
                            modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                        ) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Filled.Close else Icons.Filled.Edit,
                                contentDescription = if (isEditMode) stringResource(R.string.action_cancel_edit) else stringResource(R.string.action_edit),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                appHaptics.click()
                                showDeleteConfirm = true
                            },
                            modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = UrgentRed
                            )
                        }
                    }
                }
            }
        },
        text = {
            if (showDeleteConfirm) {
                Text(
                    text = stringResource(R.string.home_delete_dialog_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.lg)
                ) {
                    if (!isEditMode) {
                        // --- MODO VISUALIZACIÓN: Tipografía limpia con Avatar de Marca ---
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Dimensions.Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ServiceBrandAvatar(
                                issuer = account.issuer,
                                size = Dimensions.IconSize.hero
                            )

                            Spacer(modifier = Modifier.width(Dimensions.Spacing.md))

                            Column {
                                Text(
                                    text = account.issuer.ifEmpty { stringResource(R.string.home_default_issuer) },
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (account.accountName.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                                    Text(
                                        text = account.accountName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Dígitos grandes abajo con el contador circular integrado
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
                                .clickable {
                                    appHaptics.copy()
                                    onCopyCode(accountWithCode.code)
                                    copied = true
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.large)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimensions.Spacing.lg, horizontal = Dimensions.Spacing.lg),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = formattedCode,
                                        style = MaterialTheme.typography.displayMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    AnimatedVisibility(
                                        visible = copied,
                                        enter = fadeIn(animationSpec = Motion.Spec.quickFadeSpec()),
                                        exit = fadeOut(animationSpec = Motion.Spec.quickFadeSpec())
                                    ) {
                                        Row {
                                            Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = stringResource(R.string.action_copied),
                                                tint = SafeGreen,
                                                modifier = Modifier.size(Dimensions.IconSize.large)
                                            )
                                        }
                                    }
                                }

                                if (account.type == OtpType.TOTP) {
                                    CircularTimeProgress(
                                        period = account.period
                                    )
                                }
                            }
                        }
                    } else {
                        // --- MODO EDICIÓN: Campos de texto para modificar nombre del servicio y cuenta ---
                        OutlinedTextField(
                            value = editedIssuer,
                            onValueChange = { editedIssuer = it },
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
                            onValueChange = { editedAccountName = it },
                            label = { Text(stringResource(R.string.account_modal_name_label), style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Filled.PersonOutline, contentDescription = null)
                            },
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Botón animado reutilizable de Guardar cambios
                        AppAnimatedButton(
                            text = stringResource(R.string.action_save_changes),
                            enabled = isFormValid,
                            onClick = {
                                onUpdateAccount(account.id, editedIssuer.trim(), editedAccountName.trim())
                                true
                            },
                            onActionConfirmed = {
                                isEditMode = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (showDeleteConfirm) {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount(account.id)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    colors = ButtonDefaults.buttonColors(containerColor = UrgentRed)
                ) {
                    Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        dismissButton = {
            if (showDeleteConfirm) {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.settings_drive_details_back), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.account_modal_close_button), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        modifier = modifier
    )
}
