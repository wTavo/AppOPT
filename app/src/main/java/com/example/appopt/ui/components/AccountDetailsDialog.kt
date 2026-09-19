package com.example.appopt.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.appopt.domain.repository.AccountWithCode
import com.example.appopt.ui.components.details.AccountDetailsDeleteStep
import com.example.appopt.ui.components.details.AccountDetailsEditStep
import com.example.appopt.ui.components.details.AccountDetailsViewStep
import com.example.appopt.ui.theme.Motion

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
 * - Incluye confirmación de eliminación unificada dentro del mismo modal sin superponer ventanas secundarias (Directivas 14 y 29).
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
    var isEditMode by remember { mutableStateOf(false) }
    var editedIssuer by remember { mutableStateOf(account.issuer) }
    var editedAccountName by remember { mutableStateOf(account.accountName) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                    AccountDetailsViewStep(
                        accountWithCode = accountWithCode,
                        onEditClick = { isEditMode = true },
                        onDeleteClick = { showDeleteConfirm = true },
                        onCopyCode = onCopyCode,
                        onDismiss = onDismiss
                    )
                }

                AccountDetailsSubState.EDIT -> {
                    AccountDetailsEditStep(
                        editedIssuer = editedIssuer,
                        onIssuerChange = { editedIssuer = it },
                        editedAccountName = editedAccountName,
                        onAccountNameChange = { editedAccountName = it },
                        isFormValid = isFormValid,
                        onSaveChanges = {
                            onUpdateAccount(account.id, editedIssuer.trim(), editedAccountName.trim())
                            isEditMode = false
                        },
                        onCancelEdit = {
                            editedIssuer = account.issuer
                            editedAccountName = account.accountName
                            isEditMode = false
                        }
                    )
                }

                AccountDetailsSubState.DELETE_CONFIRM -> {
                    AccountDetailsDeleteStep(
                        onConfirmDelete = {
                            showDeleteConfirm = false
                            onDeleteAccount(account.id)
                            onDismiss()
                        },
                        onDismiss = { showDeleteConfirm = false }
                    )
                }
            }
        }
    }
}
