package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.CloudVaultKeyStore
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.security.MnemonicManager
import com.example.appopt.ui.components.AccountImportSelectionList
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.screens.settings.dialogs.components.DriveBackupDecryptForm
import com.example.appopt.ui.screens.settings.dialogs.components.DriveDetailsSelectAccountsStep
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics
import kotlinx.coroutines.launch

private enum class DriveDecryptStep {
    LOADING_UNENCRYPTED,
    DECRYPT,
    SELECT_ACCOUNTS
}

/**
 * Diálogo modal para el descifrado y restauración granular del respaldo más reciente en Google Drive (Directivas 7, 14, 22 y 23).
 *
 * @param onRestore Callback ejecutado con la clave de descifrado ingresada en [CharArray].
 * @param onDismiss Callback ejecutado para descartar y cerrar el diálogo.
 * @param modifier Modificador de diseño Compose opcional.
 * @param fileId Identificador único del archivo de respaldo en Google Drive si se conoce.
 * @param backupDateMillis Marca de tiempo de la copia de seguridad remota.
 * @param deviceName Nombre del dispositivo emisor del respaldo.
 * @param isActual Indica si la copia remota coincide exactamente con la versión local actual.
 * @param isEncrypted Indica si la copia remota está protegida con cifrado de extremo a extremo.
 */
@Composable
fun DriveDecryptDialog(
    onRestore: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    fileId: String? = null,
    backupDateMillis: Long? = null,
    deviceName: String? = null,
    isActual: Boolean = false,
    isEncrypted: Boolean = true
) {
    val decryptErrorText = stringResource(R.string.settings_drive_decrypt_error)
    val appHaptics = rememberAppHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { AuthenticatorApp.instance.accountRepository }

    var currentStep by remember(isEncrypted) {
        mutableStateOf(if (isEncrypted) DriveDecryptStep.DECRYPT else DriveDecryptStep.LOADING_UNENCRYPTED)
    }
    var restoreSecretText by remember { mutableStateOf("") }
    var isRestoreSecretVisible by remember { mutableStateOf(false) }
    var isDecrypting by remember { mutableStateOf(!isEncrypted) }
    var decryptErrorMessage by remember { mutableStateOf<String?>(null) }
    var downloadedSession by remember { mutableStateOf<CloudVaultKeyStore.VaultKeySession?>(null) }

    var parsedAccounts by remember { mutableStateOf<List<ParsedAccountPreview>>(emptyList()) }
    var selectedAccountIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(isEncrypted, fileId) {
        if (!isEncrypted) {
            isDecrypting = true
            decryptErrorMessage = null
            try {
                val token = GoogleDriveManager.currentAccessToken.orEmpty()
                val downloadResult = if (token.isNotBlank() && !fileId.isNullOrBlank()) {
                    GoogleDriveManager.downloadBackupDetailedById(token, fileId, null)
                } else if (token.isNotBlank()) {
                    GoogleDriveManager.downloadBackupDetailed(token, null)
                } else {
                    null
                }
                if (downloadResult != null && downloadResult.isSuccess) {
                    val detailed = downloadResult.getOrThrow()
                    downloadedSession = null
                    val jsonString = detailed.plainJson
                    val previews = repository.parseAccountsForPreview(jsonString)
                    if (previews.isNotEmpty()) {
                        parsedAccounts = previews
                        selectedAccountIds = previews.filter { !it.isAlreadyInVault }.map { it.id }.toSet()
                        appHaptics.success()
                        currentStep = DriveDecryptStep.SELECT_ACCOUNTS
                    } else {
                        decryptErrorMessage = decryptErrorText
                        appHaptics.error()
                    }
                } else {
                    decryptErrorMessage = decryptErrorText
                    appHaptics.error()
                }
            } catch (_: Exception) {
                decryptErrorMessage = decryptErrorText
                appHaptics.error()
            } finally {
                isDecrypting = false
            }
        }
    }

    AppModalDialog(
        onDismissRequest = {
            if (!isDecrypting) onDismiss()
        },
        onBackStep = {
            if (isDecrypting) {
                return@AppModalDialog true
            }
            if (isEncrypted && currentStep == DriveDecryptStep.SELECT_ACCOUNTS) {
                isDecrypting = false
                currentStep = DriveDecryptStep.DECRYPT
                true
            } else {
                false
            }
        },
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = { Motion.Spec.dialogStepContentTransform() },
            label = "DriveDecryptStepTransition"
        ) { step ->
            when (step) {
                DriveDecryptStep.LOADING_UNENCRYPTED -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg)
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        // Cabecera fija
                        Text(
                            text = stringResource(R.string.drive_restore_selection_title),
                            style = MaterialTheme.typography.titleLarge
                        )

                        // Cuerpo central scrolleable aislado
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isDecrypting) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Dimensions.Spacing.xl),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(Dimensions.IconSize.large)
                                    )
                                }
                            } else if (decryptErrorMessage != null) {
                                Text(
                                    text = decryptErrorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(vertical = Dimensions.Spacing.md)
                                )
                            }
                        }

                        // Pie fijo de acciones
                        AppDialogActionButtons(
                            dismissText = stringResource(R.string.action_close),
                            onDismiss = {
                                if (!isDecrypting) onDismiss()
                            }
                        )
                    }
                }
                DriveDecryptStep.DECRYPT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg)
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        // Cabecera fija
                        Text(
                            text = stringResource(R.string.settings_drive_decrypt_title),
                            style = MaterialTheme.typography.titleLarge
                        )

                        // Cuerpo central scrolleable aislado
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                        ) {

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
                            onRestoreSecretChange = {
                                restoreSecretText = it
                                decryptErrorMessage = null
                            },
                            isRestoreSecretVisible = isRestoreSecretVisible,
                            onToggleSecretVisibility = { isRestoreSecretVisible = !isRestoreSecretVisible }
                        )

                        androidx.compose.animation.AnimatedVisibility(
                            visible = decryptErrorMessage != null,
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                        ) {
                            if (decryptErrorMessage != null) {
                                Text(
                                    text = decryptErrorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        }

                        // Pie fijo de acciones
                        AppDialogActionButtons(
                            dismissText = stringResource(R.string.action_close),
                            onDismiss = {
                                if (!isDecrypting) onDismiss()
                            },
                            confirmText = stringResource(R.string.settings_drive_decrypt_and_restore),
                            onConfirm = {
                                val token = GoogleDriveManager.currentAccessToken.orEmpty()
                                val normalizedSecret = if (restoreSecretText.contains(" ")) {
                                    MnemonicManager.normalizePhrase(restoreSecretText)
                                } else {
                                    restoreSecretText.trim()
                                }
                                val passChars = normalizedSecret.toCharArray()
                                isDecrypting = true
                                decryptErrorMessage = null

                                scope.launch {
                                    try {
                                        val downloadResult = if (token.isNotBlank() && !fileId.isNullOrBlank()) {
                                            GoogleDriveManager.downloadBackupDetailedById(token, fileId, passChars)
                                        } else if (token.isNotBlank()) {
                                            GoogleDriveManager.downloadBackupDetailed(token, passChars)
                                        } else {
                                            null
                                        }

                                        if (downloadResult != null && downloadResult.isSuccess) {
                                            val detailed = downloadResult.getOrThrow()
                                            downloadedSession = detailed.session
                                            val jsonString = detailed.plainJson
                                            val previews = repository.parseAccountsForPreview(jsonString)
                                            if (previews.isNotEmpty()) {
                                                parsedAccounts = previews
                                                selectedAccountIds = previews.filter { !it.isAlreadyInVault }.map { it.id }.toSet()
                                                appHaptics.success()
                                                currentStep = DriveDecryptStep.SELECT_ACCOUNTS
                                            } else {
                                                decryptErrorMessage = decryptErrorText
                                                appHaptics.error()
                                                isDecrypting = false
                                            }
                                        } else {
                                            onRestore(passChars)
                                            onDismiss()
                                            isDecrypting = false
                                        }
                                    } catch (_: Exception) {
                                        decryptErrorMessage = decryptErrorText
                                        appHaptics.error()
                                        isDecrypting = false
                                    } finally {
                                        passChars.fill('0')
                                    }
                                }
                            },
                            confirmEnabled = restoreSecretText.isNotBlank() && !isDecrypting,
                            isLoading = isDecrypting
                        )
                    }
                }

                DriveDecryptStep.SELECT_ACCOUNTS -> {
                    DriveDetailsSelectAccountsStep(
                        parsedAccounts = parsedAccounts,
                        selectedAccountIds = selectedAccountIds,
                        onToggleAccount = { id ->
                            selectedAccountIds = if (id in selectedAccountIds) {
                                selectedAccountIds - id
                            } else {
                                selectedAccountIds + id
                            }
                        },
                        onSelectAll = {
                            selectedAccountIds = parsedAccounts.map { it.id }.toSet()
                        },
                        onDeselectAll = {
                            selectedAccountIds = emptySet()
                        },
                        onConfirmImport = {
                            val accountsToImport = parsedAccounts.filter { it.id in selectedAccountIds }
                            scope.launch {
                                repository.importSelectedAccounts(accountsToImport)
                                val prefsManager = AuthenticatorApp.instance.preferencesManager
                                prefsManager.setGoogleDriveConnected(true)
                                val session = downloadedSession
                                if (session != null) {
                                    CloudVaultKeyStore.saveVaultKeyAndSlots(context.applicationContext, session)
                                    prefsManager.setDriveBackupEncrypted(true)
                                } else if (!isEncrypted) {
                                    prefsManager.setDriveBackupEncrypted(false)
                                }
                                if (backupDateMillis != null && backupDateMillis > 0L) {
                                    prefsManager.setLastSyncTimestamp(backupDateMillis)
                                }
                            }
                            true
                        },
                        onActionConfirmed = {
                            onDismiss()
                        },
                        onBack = {
                            isDecrypting = false
                            if (isEncrypted) {
                                currentStep = DriveDecryptStep.DECRYPT
                            } else {
                                onDismiss()
                            }
                        },
                        backText = stringResource(if (isEncrypted) R.string.settings_drive_details_back else R.string.action_close)
                    )
                }
            }
        }
    }
}
