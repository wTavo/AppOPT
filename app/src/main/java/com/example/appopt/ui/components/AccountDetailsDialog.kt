package com.example.appopt.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.SharedBatchKeyStore
import com.example.appopt.data.cloud.SharedServicesCloudClient
import com.example.appopt.domain.model.AccountWithCode
import com.example.appopt.ui.components.details.AccountDetailsDeleteStep
import com.example.appopt.ui.components.details.AccountDetailsEditStep
import com.example.appopt.ui.components.details.AccountDetailsShareStep
import com.example.appopt.ui.components.details.AccountDetailsViewStep
import com.example.appopt.ui.screens.settings.coordinator.rememberGoogleOAuthRequester
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Sub-estados de visualización, edición, compartición y eliminación del diálogo de detalles de cuenta.
 */
private enum class AccountDetailsSubState {
    VIEW,
    EDIT,
    SHARE,
    DELETE_CONFIRM
}

/**
 * Modal / Popup para visualizar, editar, compartir sin exponer secretos o eliminar una cuenta (Directivas 14, 29 y 31).
 *
 * Características de diseño:
 * - En modo visualización muestra limpiamente el nombre del servicio y la cuenta usando la escala tipográfica de la app.
 * - Los dígitos aparecen abajo junto con el contador/temporizador circular de rotación TOTP.
 * - En modo edición permite modificar el nombre del servicio y el nombre de la cuenta con validación instantánea.
 * - En modo compartición permite otorgar acceso en línea o por lote temporal a otro usuario mediante invitación protegida
 *   (exigiendo cuenta de Google vinculada y conexión a internet) cerrando el modal automáticamente tras confirmar con éxito.
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appHaptics = rememberAppHaptics()
    val account = accountWithCode.account

    val isDriveConnected by AuthenticatorApp.instance.preferencesManager
        .isGoogleDriveConnectedFlow
        .collectAsState(initial = true)

    val connectedUserEmail = remember(isDriveConnected) {
        AuthenticatorApp.instance.preferencesManager.getConnectedDriveEmail()
            ?: AuthenticatorApp.instance.preferencesManager.getLastKnownDriveEmail()
    }

    var isEditMode by remember { mutableStateOf(false) }
    var isShareMode by remember { mutableStateOf(false) }
    var editedIssuer by remember { mutableStateOf(account.issuer) }
    var editedAccountName by remember { mutableStateOf(account.accountName) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var shareErrorMessageResId by remember { mutableStateOf<Int?>(null) }
    val shareErrorMessage = shareErrorMessageResId?.let { stringResource(it) }

    var onTokenErrorAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val requestOAuthToken = rememberGoogleOAuthRequester(
        onAuthError = {
            appHaptics.error()
            shareErrorMessageResId = R.string.share_error_not_signed_in
            onTokenErrorAction?.invoke()
            onTokenErrorAction = null
        }
    )

    val hasChanges = remember(editedIssuer, editedAccountName, account.issuer, account.accountName) {
        editedIssuer.trim() != account.issuer.trim() || editedAccountName.trim() != account.accountName.trim()
    }
    val isFormValid = editedIssuer.isNotBlank() && hasChanges

    val currentDialogState = when {
        showDeleteConfirm -> AccountDetailsSubState.DELETE_CONFIRM
        isShareMode -> AccountDetailsSubState.SHARE
        isEditMode -> AccountDetailsSubState.EDIT
        else -> AccountDetailsSubState.VIEW
    }

    LaunchedEffect(isShareMode) {
        if (isShareMode) {
            shareErrorMessageResId = null
        }
    }

    AppModalDialog(
        onDismissRequest = onDismiss,
        onBackStep = {
            when {
                showDeleteConfirm -> {
                    showDeleteConfirm = false
                    true
                }
                isShareMode -> {
                    shareErrorMessageResId = null
                    isShareMode = false
                    true
                }
                isEditMode -> {
                    isEditMode = false
                    true
                }
                else -> false
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
                        onShareClick = {
                            shareErrorMessageResId = null
                            isShareMode = true
                        },
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

                AccountDetailsSubState.SHARE -> {
                    AccountDetailsShareStep(
                        isDriveConnected = isDriveConnected,
                        connectedUserEmail = connectedUserEmail,
                        errorMessage = shareErrorMessage,
                        onShareService = { guestEmail, accessMode, batchDuration ->
                            if (!isDriveConnected) return@AccountDetailsShareStep false
                            if (!connectedUserEmail.isNullOrBlank() && guestEmail.trim().equals(connectedUserEmail.trim(), ignoreCase = true)) {
                                appHaptics.error()
                                shareErrorMessageResId = R.string.share_error_self_email
                                return@AccountDetailsShareStep false
                            }
                            if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
                                appHaptics.error()
                                shareErrorMessageResId = R.string.share_error_no_internet
                                return@AccountDetailsShareStep false
                            }
                            shareErrorMessageResId = null

                            suspendCancellableCoroutine { continuation ->
                                onTokenErrorAction = {
                                    if (continuation.isActive) continuation.resume(false)
                                }
                                continuation.invokeOnCancellation {
                                    onTokenErrorAction = null
                                }
                                requestOAuthToken { token ->
                                    onTokenErrorAction = null
                                    coroutineScope.launch {
                                        val result = SharedServicesCloudClient.shareService(
                                            accessToken = token,
                                            account = account,
                                            guestEmail = guestEmail,
                                            accessMode = accessMode,
                                            batchDuration = batchDuration
                                        )
                                        result.onSuccess { newGrant ->
                                            val cached = SharedBatchKeyStore.getCachedOutgoingGrants(context)
                                            val updated = cached.filterNot { it.grantId == newGrant.grantId } + newGrant
                                            SharedBatchKeyStore.saveCachedOutgoingGrants(context, updated)
                                            appHaptics.success()
                                            if (continuation.isActive) continuation.resume(true)
                                        }.onFailure {
                                            appHaptics.error()
                                            shareErrorMessageResId = if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
                                                R.string.share_error_no_internet
                                            } else {
                                                R.string.share_error_generic
                                            }
                                            if (continuation.isActive) continuation.resume(false)
                                        }
                                    }
                                }
                            }
                        },
                        onShareSuccess = onDismiss,
                        onBack = {
                            shareErrorMessageResId = null
                            isShareMode = false
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
