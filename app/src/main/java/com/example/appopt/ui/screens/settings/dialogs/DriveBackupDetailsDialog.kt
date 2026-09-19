package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.appopt.data.cloud.CloudVaultKeyStore
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.security.MnemonicManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.components.ModalTone
import com.example.appopt.ui.screens.settings.dialogs.components.DriveDetailsDecryptStep
import com.example.appopt.ui.screens.settings.dialogs.components.DriveDetailsDeleteStep
import com.example.appopt.ui.screens.settings.dialogs.components.DriveDetailsHistoryStep
import com.example.appopt.ui.screens.settings.dialogs.components.DriveDetailsSelectAccountsStep
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private enum class DriveDetailsSubState {
    HISTORY,
    RESTORE_DECRYPT,
    RESTORE_SELECT_ACCOUNTS,
    DELETE_SINGLE
}

/**
 * Diálogo modal para la visualización del historial de versiones de respaldo en Google Drive,
 * restauración granular in-situ y borrado autenticado (Directivas 7, 14, 22, 23 y 29).
 *
 * @param backupItems Lista de elementos de respaldo detectados en Google Drive.
 * @param isLoading Indica si la consulta remota de versiones está en ejecución.
 * @param onRestoreBackup Callback ejecutado al confirmar la restauración de una versión con su clave de descifrado.
 * @param onDeleteSpecificBackup Callback ejecutado al eliminar una versión específica con autenticación.
 * @param onDismiss Callback ejecutado para cerrar el diálogo modal.
 * @param modifier Modificador de diseño Compose opcional.
 * @param lastFetchTimestamp Marca de tiempo de la última consulta de versiones para control de enfriamiento (cooldown).
 * @param lastSyncTimestamp Marca de tiempo de la última sincronización local confirmada.
 * @param lastSyncedHash Firma hash de la última copia de seguridad local confirmada.
 * @param hasUnsyncedChanges Indica si la bóveda local difiere de la versión en la nube.
 * @param onForceRefresh Callback para forzar la sincronización y refresco del historial.
 */
@Composable
fun DriveBackupDetailsDialog(
    backupItems: List<DriveBackupItem>,
    isLoading: Boolean,
    onRestoreBackup: (DriveBackupItem, CharArray) -> Unit,
    onDeleteSpecificBackup: (DriveBackupItem, CharArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    lastFetchTimestamp: Long = 0L,
    lastSyncTimestamp: Long = 0L,
    lastSyncedHash: String = "",
    hasUnsyncedChanges: Boolean = false,
    onForceRefresh: () -> Unit = {}
) {
    val context = LocalContext.current
    val appHaptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val repository = remember { AuthenticatorApp.instance.accountRepository }
    val decryptErrorText = stringResource(R.string.settings_drive_decrypt_error)

    var pendingRestoreBackup by remember { mutableStateOf<DriveBackupItem?>(null) }
    var restoreSecretText by remember { mutableStateOf("") }
    var isRestoreSecretVisible by remember { mutableStateOf(false) }
    var isDecryptingBackup by remember { mutableStateOf(false) }
    var decryptErrorMessage by remember { mutableStateOf<String?>(null) }
    var downloadedSession by remember { mutableStateOf<CloudVaultKeyStore.VaultKeySession?>(null) }

    var parsedAccounts by remember { mutableStateOf<List<ParsedAccountPreview>>(emptyList()) }
    var selectedAccountIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var pendingDeleteBackup by remember { mutableStateOf<DriveBackupItem?>(null) }
    var deleteSecretText by remember { mutableStateOf("") }
    var isDeleteSecretVisible by remember { mutableStateOf(false) }

    var currentTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lastFetchTimestamp) {
        val initialElapsed = (System.currentTimeMillis() - lastFetchTimestamp).coerceAtLeast(0L)
        val initialRemaining = (SecurityConfig.BACKUP_HISTORY_CACHE_TTL_MILLIS - initialElapsed).coerceAtLeast(0L)
        if (initialRemaining > 0L) {
            while (isActive) {
                val now = System.currentTimeMillis()
                currentTick = now
                val elapsed = (now - lastFetchTimestamp).coerceAtLeast(0L)
                val remaining = (SecurityConfig.BACKUP_HISTORY_CACHE_TTL_MILLIS - elapsed).coerceAtLeast(0L)
                if (remaining <= 0L) {
                    break
                }
                delay(1_000L.milliseconds)
            }
        }
    }

    val elapsed = (currentTick - lastFetchTimestamp).coerceAtLeast(0L)
    val remainingMillis = (SecurityConfig.BACKUP_HISTORY_CACHE_TTL_MILLIS - elapsed).coerceAtLeast(0L)
    val secondsRemaining = (remainingMillis + 999L) / 1000L
    val isCooldownActive = secondsRemaining > 0L

    var currentSubState by remember { mutableStateOf(DriveDetailsSubState.HISTORY) }

    AppModalDialog(
        onDismissRequest = onDismiss,
        onBackStep = {
            when (currentSubState) {
                DriveDetailsSubState.RESTORE_SELECT_ACCOUNTS -> {
                    currentSubState = DriveDetailsSubState.RESTORE_DECRYPT
                    true
                }
                DriveDetailsSubState.RESTORE_DECRYPT, DriveDetailsSubState.DELETE_SINGLE -> {
                    restoreSecretText = ""
                    deleteSecretText = ""
                    decryptErrorMessage = null
                    currentSubState = DriveDetailsSubState.HISTORY
                    true
                }
                else -> false
            }
        },
        tone = if (currentSubState == DriveDetailsSubState.DELETE_SINGLE) ModalTone.DESTRUCTIVE else ModalTone.STANDARD,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = currentSubState,
            transitionSpec = { Motion.Spec.dialogStepContentTransform() },
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxWidth(),
            label = "driveDetailsSubStateTransition"
        ) { subState ->
            when (subState) {
                DriveDetailsSubState.HISTORY -> {
                    DriveDetailsHistoryStep(
                        backupItems = backupItems,
                        isLoading = isLoading,
                        lastSyncTimestamp = lastSyncTimestamp,
                        lastSyncedHash = lastSyncedHash,
                        hasUnsyncedChanges = hasUnsyncedChanges,
                        isCooldownActive = isCooldownActive,
                        secondsRemaining = secondsRemaining,
                        onRestoreSelected = { item ->
                            pendingRestoreBackup = item
                            restoreSecretText = ""
                            currentSubState = DriveDetailsSubState.RESTORE_DECRYPT
                        },
                        onDeleteSelected = { item ->
                            pendingDeleteBackup = item
                            deleteSecretText = ""
                            currentSubState = DriveDetailsSubState.DELETE_SINGLE
                        },
                        onForceRefresh = onForceRefresh,
                        onDismiss = onDismiss
                    )
                }

                DriveDetailsSubState.RESTORE_DECRYPT -> {
                    DriveDetailsDecryptStep(
                        targetBackup = pendingRestoreBackup,
                        secretText = restoreSecretText,
                        onSecretTextChange = {
                            restoreSecretText = it
                            decryptErrorMessage = null
                        },
                        isSecretVisible = isRestoreSecretVisible,
                        onToggleSecretVisibility = { isRestoreSecretVisible = !isRestoreSecretVisible },
                        decryptErrorMessage = decryptErrorMessage,
                        isDecrypting = isDecryptingBackup,
                        onConfirm = {
                            val targetItem = pendingRestoreBackup ?: return@DriveDetailsDecryptStep
                            val token = GoogleDriveManager.currentAccessToken.orEmpty()
                            val normalizedSecret = if (restoreSecretText.contains(" ")) {
                                MnemonicManager.normalizePhrase(restoreSecretText)
                            } else {
                                restoreSecretText.trim()
                            }
                            val passChars = normalizedSecret.toCharArray()
                            isDecryptingBackup = true
                            decryptErrorMessage = null

                            scope.launch {
                                try {
                                    val downloadResult = if (token.isNotBlank() && targetItem.fileId.isNotBlank()) {
                                        GoogleDriveManager.downloadBackupDetailedById(token, targetItem.fileId, passChars)
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
                                            selectedAccountIds = previews.map { it.id }.toSet()
                                            appHaptics.success()
                                            currentSubState = DriveDetailsSubState.RESTORE_SELECT_ACCOUNTS
                                        } else {
                                            decryptErrorMessage = decryptErrorText
                                            appHaptics.error()
                                        }
                                    } else {
                                        onRestoreBackup(targetItem, passChars)
                                        pendingRestoreBackup = null
                                        restoreSecretText = ""
                                        currentSubState = DriveDetailsSubState.HISTORY
                                    }
                                } catch (_: Exception) {
                                    decryptErrorMessage = decryptErrorText
                                    appHaptics.error()
                                } finally {
                                    passChars.fill('0')
                                    isDecryptingBackup = false
                                }
                            }
                        },
                        onBack = {
                            restoreSecretText = ""
                            decryptErrorMessage = null
                            currentSubState = DriveDetailsSubState.HISTORY
                        }
                    )
                }

                DriveDetailsSubState.RESTORE_SELECT_ACCOUNTS -> {
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
                                downloadedSession?.let { session ->
                                    CloudVaultKeyStore.saveVaultKeyAndSlots(context.applicationContext, session)
                                    val prefsManager = AuthenticatorApp.instance.preferencesManager
                                    prefsManager.setGoogleDriveConnected(true)
                                    pendingRestoreBackup?.let { prefsManager.setLastSyncTimestamp(it.modifiedTimeMillis) }
                                }
                            }
                            true
                        },
                        onActionConfirmed = {
                            pendingRestoreBackup = null
                            restoreSecretText = ""
                            onDismiss()
                        },
                        onBack = {
                            currentSubState = DriveDetailsSubState.RESTORE_DECRYPT
                        }
                    )
                }

                DriveDetailsSubState.DELETE_SINGLE -> {
                    DriveDetailsDeleteStep(
                        targetBackup = pendingDeleteBackup,
                        deleteSecretText = deleteSecretText,
                        onDeleteSecretTextChange = { deleteSecretText = it },
                        isSecretVisible = isDeleteSecretVisible,
                        onToggleSecretVisibility = { isDeleteSecretVisible = !isDeleteSecretVisible },
                        onConfirmDelete = {
                            val targetItem = pendingDeleteBackup
                            if (targetItem != null) {
                                val normalizedSecret = if (deleteSecretText.contains(" ")) {
                                    MnemonicManager.normalizePhrase(deleteSecretText)
                                } else {
                                    deleteSecretText.trim()
                                }
                                val passChars = normalizedSecret.toCharArray()
                                onDeleteSpecificBackup(targetItem, passChars)
                                pendingDeleteBackup = null
                                deleteSecretText = ""
                                currentSubState = DriveDetailsSubState.HISTORY
                            }
                        },
                        onBack = {
                            deleteSecretText = ""
                            currentSubState = DriveDetailsSubState.HISTORY
                        }
                    )
                }
            }
        }
    }
}
