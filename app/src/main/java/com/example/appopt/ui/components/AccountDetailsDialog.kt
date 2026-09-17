package com.example.appopt.ui.components
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
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
import com.example.appopt.R
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.repository.AccountWithCode
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.rememberAppHaptics
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Sub-estados de visualización y edición del diálogo de detalles de cuenta.
 */
private enum class AccountDetailsSubState {
    VIEW,
    EDIT,
    DELETE_CONFIRM
}

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
            delay(Motion.Duration.FEEDBACK_TOAST.toLong().milliseconds)
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

    val currentDialogState = when {
        showDeleteConfirm -> AccountDetailsSubState.DELETE_CONFIRM
        isEditMode -> AccountDetailsSubState.EDIT
        else -> AccountDetailsSubState.VIEW
    }

    AppModalDialog(
        onDismissRequest = onDismiss,
        onBackStep = {
            if (showDeleteConfirm) {
                showDeleteConfirm = false
                true
            } else if (isEditMode) {
                isEditMode = false
                true
            } else {
                false
            }
        },
        tone = if (showDeleteConfirm) ModalTone.DESTRUCTIVE else ModalTone.STANDARD,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = currentDialogState,
            transitionSpec = { Motion.Spec.dialogStepContentTransform() },
            contentAlignment = Alignment.TopCenter,
            label = "accountDetailsStepTransition",
            modifier = Modifier.fillMaxWidth()
        ) { subState ->
                                when (subState) {
                    AccountDetailsSubState.VIEW -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimensions.Spacing.lg)
                                .imePadding(),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                        ) {
                            // Cabecera fija con Título e Iconos de acción (Lápiz y Basurero)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.account_modal_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            appHaptics.click()
                                            isEditMode = true
                                        },
                                        modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = stringResource(R.string.action_edit),
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

                            // Cuerpo central scrolleable aislado
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                            ) {
                                // Avatar de Marca y Nombre de Cuenta
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

                                    Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))

                                    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)) {
                                        Text(
                                            text = account.issuer.ifEmpty { stringResource(R.string.home_default_issuer) },
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if (account.accountName.isNotBlank()) {
                                            Text(
                                                text = account.accountName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Tarjeta de Dígitos OTP grandes con contador circular
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.large)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
                                            .clickable {
                                                appHaptics.copy()
                                                onCopyCode(accountWithCode.code)
                                                copied = true
                                            }
                                            .padding(vertical = Dimensions.Spacing.lg, horizontal = Dimensions.Spacing.lg),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                                        ) {
                                            Text(
                                                text = formattedCode,
                                                style = MaterialTheme.typography.displayMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                softWrap = false
                                            )

                                            AnimatedVisibility(
                                                visible = copied,
                                                enter = fadeIn(animationSpec = Motion.Spec.quickFadeSpec()),
                                                exit = fadeOut(animationSpec = Motion.Spec.quickFadeSpec())
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = stringResource(R.string.action_copied),
                                                    tint = SafeGreen,
                                                    modifier = Modifier.size(Dimensions.IconSize.large)
                                                )
                                            }
                                        }

                                        if (account.type == OtpType.TOTP) {
                                            CircularTimeProgress(
                                                period = account.period
                                            )
                                        }
                                    }
                                }
                            }

                            // Pie fijo con Botón Cerrar
                            AppDialogActionButtons(
                                dismissText = stringResource(R.string.account_modal_close_button),
                                onDismiss = onDismiss
                            )
                        }
                    }
                    AccountDetailsSubState.EDIT -> {
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
                                        isEditMode = false
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
                            }

                            // Pie fijo con Botones Volver + Guardar cambios
                            AppDialogActionButtons(
                                dismissText = stringResource(R.string.settings_drive_details_back),
                                onDismiss = {
                                    editedIssuer = account.issuer
                                    editedAccountName = account.accountName
                                    isEditMode = false
                                },
                                confirmText = stringResource(R.string.action_save_changes),
                                onConfirm = {
                                    onUpdateAccount(account.id, editedIssuer.trim(), editedAccountName.trim())
                                    isEditMode = false
                                },
                                confirmEnabled = isFormValid
                            )
                        }
                    }
                    AccountDetailsSubState.DELETE_CONFIRM -> {
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
                                onDismiss = { showDeleteConfirm = false },
                                confirmText = stringResource(R.string.account_details_move_to_trash_btn),
                                onConfirm = {
                                    showDeleteConfirm = false
                                    onDeleteAccount(account.id)
                                    onDismiss()
                                },
                                isDestructive = true
                            )
                        }
                }
            }
        }
    }
}
