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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appopt.R
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.repository.AccountWithCode
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import kotlinx.coroutines.delay

/**
 * Modal / Popup para visualizar o editar la información de una cuenta.
 *
 * Características de diseño:
 * - En modo visualización muestra limpiamente el nombre del servicio y la cuenta (sin campos de texto).
 * - Los dígitos aparecen abajo junto con el contador/temporizador circular de rotación TOTP.
 * - Con un simple toque sobre los dígitos se copia el código al portapapeles.
 * - Iconos en la esquina superior derecha: Lápiz para alternar al modo edición y Basurero para eliminar.
 * - En modo edición permite modificar el nombre del servicio y la cuenta/usuario.
 *
 * @param accountWithCode Cuenta seleccionada con su código activo.
 * @param onDismiss Callback para cerrar el modal.
 * @param onCopyCode Callback para copiar el código al portapapeles.
 * @param onUpdateAccount Callback para guardar cambios en el nombre del servicio o cuenta.
 * @param onDeleteAccount Callback para eliminar la cuenta de la bóveda.
 */
@Composable
fun AccountDetailsDialog(
    accountWithCode: AccountWithCode,
    onDismiss: () -> Unit,
    onCopyCode: (String) -> Unit,
    onUpdateAccount: (id: String, issuer: String, accountName: String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val account = accountWithCode.account

    var isEditMode by remember { mutableStateOf(false) }
    var editedIssuer by remember(account.id) { mutableStateOf(account.issuer) }
    var editedAccountName by remember(account.id) { mutableStateOf(account.accountName) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(account.issuer, account.accountName) {
        editedIssuer = account.issuer
        editedAccountName = account.accountName
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isEditMode) stringResource(R.string.account_modal_edit_title) else stringResource(R.string.account_modal_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Iconos de acción arriba a la derecha: Lápiz y Basurero
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isEditMode = !isEditMode },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Filled.Close else Icons.Filled.Edit,
                            contentDescription = if (isEditMode) stringResource(R.string.action_cancel_edit) else stringResource(R.string.action_edit),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = UrgentRed
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isEditMode) {
                    // --- MODO VISUALIZACIÓN: Tipografía limpia sin campos de texto ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = account.issuer.ifEmpty { stringResource(R.string.home_default_issuer) },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (account.accountName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = account.accountName,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Dígitos grandes abajo con el contador circular integrado
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onCopyCode(accountWithCode.code)
                                copied = true
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formattedCode,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 2.sp
                                )

                                AnimatedVisibility(
                                    visible = copied,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Row {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = stringResource(R.string.action_copied),
                                            tint = SafeGreen,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            if (account.type == OtpType.TOTP) {
                                CircularTimeProgress(
                                    remainingSeconds = accountWithCode.remainingSeconds,
                                    progress = accountWithCode.progress
                                )
                            }
                        }
                    }
                } else {
                    // --- MODO EDICIÓN: Campos de texto para modificar nombre del servicio y cuenta ---
                    OutlinedTextField(
                        value = editedIssuer,
                        onValueChange = { editedIssuer = it },
                        label = { Text(stringResource(R.string.account_modal_issuer_label)) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.Business, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editedAccountName,
                        onValueChange = { editedAccountName = it },
                        label = { Text(stringResource(R.string.account_modal_name_label)) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.PersonOutline, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (editedIssuer.isNotBlank()) {
                                onUpdateAccount(account.id, editedIssuer, editedAccountName)
                                isEditMode = false
                            }
                        },
                        enabled = editedIssuer.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_save_changes))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.account_modal_close_button))
            }
        },
        modifier = modifier
    )

    // Modal de confirmación para eliminar
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.home_delete_dialog_title)) },
            text = { Text(stringResource(R.string.home_delete_dialog_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount(account.id)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UrgentRed)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
