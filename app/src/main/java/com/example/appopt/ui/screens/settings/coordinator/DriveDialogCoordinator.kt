package com.example.appopt.ui.screens.settings.coordinator

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.screens.settings.SettingsUiState
import com.example.appopt.ui.screens.settings.SettingsViewModel
import com.example.appopt.util.DriveErrorMessageResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Estado y orquestador centralizado de diálogos y llamadas autenticadas con Google Drive (Directivas 8, 14 y 15).
 */
class DriveDialogCoordinator(
    private val context: Context,
    private val viewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
    private val onRequestAuth: ((String) -> Unit) -> Unit
) {
    var showExportDialog by mutableStateOf(false)
    var showBackupDetailsDialog by mutableStateOf(false)
    var isHistoryLoadingSynchronous by mutableStateOf(false)
    var showDisconnectConfirmDialog by mutableStateOf(false)
    var showDriveProtectDialog by mutableStateOf(false)
    var showCreateBackupConfirmDialog by mutableStateOf(false)
    var showDriveDecryptDialog by mutableStateOf(false)
    var showOverwriteWarningDialog by mutableStateOf(false)
    var driveAccessToken by mutableStateOf<String?>(null)

    /** Cierra defensivamente todos los modales cuando la bóveda se bloquea. */
    fun closeAllDialogs() {
        showExportDialog = false
        showBackupDetailsDialog = false
        isHistoryLoadingSynchronous = false
        showDisconnectConfirmDialog = false
        showDriveProtectDialog = false
        showCreateBackupConfirmDialog = false
        showDriveDecryptDialog = false
        showOverwriteWarningDialog = false
    }

    /** Ejecuta una acción que requiere token OAuth2 de Google Drive, solicitándolo si es necesario. */
    fun executeWithAuth(action: (String) -> Unit) {
        val token = driveAccessToken ?: GoogleDriveManager.currentAccessToken
        if (token == null) {
            onRequestAuth { freshToken ->
                driveAccessToken = freshToken
                GoogleDriveManager.currentAccessToken = freshToken
                action(freshToken)
            }
        } else {
            action(token)
        }
    }

    /** Abre el historial de respaldos en la nube, cargando el estado actualizado desde Google Drive aplicando caché TTL. */
    fun openBackupDetails(uiState: SettingsUiState) {
        val now = System.currentTimeMillis()
        val lastFetch = AuthenticatorApp.instance.preferencesManager.getLastBackupHistoryFetchTimestamp()
        val isCacheFresh = (now - lastFetch < SecurityConfig.BACKUP_HISTORY_CACHE_TTL_MILLIS) && uiState.backupHistoryList.isNotEmpty()
        if (!isCacheFresh) {
            isHistoryLoadingSynchronous = true
            viewModel.startBackupHistoryLoading()
        }
        showBackupDetailsDialog = true
        executeWithAuth { token ->
            viewModel.fetchBackupHistoryIfNeeded(
                token = token,
                onAuthExpired = {
                    driveAccessToken = null
                    GoogleDriveManager.currentAccessToken = null
                    onRequestAuth { freshToken ->
                        driveAccessToken = freshToken
                        GoogleDriveManager.currentAccessToken = freshToken
                        viewModel.fetchBackupHistoryIfNeeded(
                            token = freshToken,
                            onAuthExpired = {},
                            onFinished = { isHistoryLoadingSynchronous = false }
                        )
                    }
                },
                onFinished = {
                    isHistoryLoadingSynchronous = false
                }
            )
        }
    }

    /** Desvincula Google Drive y muestra mensaje de confirmación. */
    fun confirmDisconnect() {
        showDisconnectConfirmDialog = false
        driveAccessToken = null
        viewModel.disconnectGoogleDrive(context)
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.settings_drive_disconnected_success))
        }
    }

    /** Crea un respaldo protegido con cifrado E2EE. */
    fun protectAndSync(primaryPassChars: CharArray, emergencyMnemonicChars: CharArray) {
        showDriveProtectDialog = false
        executeWithAuth { token ->
            viewModel.createProtectedBackup(context, token, primaryPassChars, emergencyMnemonicChars) { result ->
                scope.launch {
                    result.onSuccess {
                        snackbarHostState.showSnackbar(context.getString(R.string.settings_drive_sync_success))
                    }.onFailure { error ->
                        snackbarHostState.showSnackbar(DriveErrorMessageResolver.resolve(context, error))
                    }
                }
            }
        }
    }

    /** Crea un respaldo en la nube utilizando la clave maestra existente en el hardware seguro del dispositivo. */
    fun createBackupWithExistingKey() {
        showCreateBackupConfirmDialog = false
        executeWithAuth { token ->
            viewModel.executeManualSync(context, token)
        }
    }

    /** Descifra y restaura la copia más reciente. */
    fun restoreDriveDecrypt(passChars: CharArray) {
        showDriveDecryptDialog = false
        fun executeRestore(token: String) {
            val passCharsCopy = passChars.clone()
            viewModel.restoreFromBackup(token, passCharsCopy) { result ->
                result.onSuccess { count ->
                    passChars.fill('0')
                    scope.launch {
                        val message = if (count > 0) {
                            context.getString(R.string.settings_drive_restore_success, count)
                        } else {
                            context.getString(R.string.settings_drive_restore_up_to_date)
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                }.onFailure { error ->
                    val isAuthExpired = error.message?.contains("401", ignoreCase = true) == true ||
                            error.message?.contains("403", ignoreCase = true) == true
                    if (isAuthExpired) {
                        driveAccessToken = null
                        GoogleDriveManager.currentAccessToken = null
                        onRequestAuth { freshToken ->
                            driveAccessToken = freshToken
                            executeRestore(freshToken)
                        }
                    } else {
                        passChars.fill('0')
                        scope.launch {
                            snackbarHostState.showSnackbar(DriveErrorMessageResolver.resolve(context, error))
                        }
                    }
                }
            }
        }
        executeWithAuth { executeRestore(it) }
    }

    /** Fuerza la recarga del historial de versiones. */
    fun forceRefreshHistory() {
        executeWithAuth { token ->
            viewModel.forceRefreshBackupHistory(
                token = token,
                onAuthExpired = {
                    driveAccessToken = null
                    GoogleDriveManager.currentAccessToken = null
                    onRequestAuth { freshToken ->
                        driveAccessToken = freshToken
                        viewModel.forceRefreshBackupHistory(
                            token = freshToken,
                            onAuthExpired = {}
                        )
                    }
                }
            )
        }
    }

    /** Restaura una versión histórica específica. */
    fun restoreBackupHistoryItem(item: DriveBackupItem, passChars: CharArray) {
        showBackupDetailsDialog = false
        fun executeRestore(token: String) {
            val passCharsCopy = passChars.clone()
            viewModel.restoreSpecificBackup(token, item.fileId, passCharsCopy) { result ->
                result.onSuccess { count ->
                    passChars.fill('0')
                    scope.launch {
                        val message = if (count > 0) {
                            context.getString(R.string.settings_drive_restore_success, count)
                        } else {
                            context.getString(R.string.settings_drive_restore_up_to_date)
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                }.onFailure { error ->
                    val isAuthExpired = error.message?.contains("401", ignoreCase = true) == true ||
                            error.message?.contains("403", ignoreCase = true) == true
                    if (isAuthExpired) {
                        driveAccessToken = null
                        GoogleDriveManager.currentAccessToken = null
                        onRequestAuth { freshToken ->
                            driveAccessToken = freshToken
                            executeRestore(freshToken)
                        }
                    } else {
                        passChars.fill('0')
                        scope.launch {
                            snackbarHostState.showSnackbar(DriveErrorMessageResolver.resolve(context, error))
                        }
                    }
                }
            }
        }
        executeWithAuth { executeRestore(it) }
    }

    /** Elimina una versión de respaldo específica. */
    fun deleteBackupHistoryItem(item: DriveBackupItem, passChars: CharArray) {
        executeWithAuth { token ->
            viewModel.deleteSpecificBackup(token, item.fileId, passChars) { success ->
                scope.launch {
                    val msg = if (success) {
                        context.getString(R.string.settings_drive_delete_single_success)
                    } else {
                        context.getString(R.string.settings_drive_decrypt_error)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }
}

/**
 * Recuerda y mantiene una instancia estable de [DriveDialogCoordinator] en el árbol de composición.
 */
@Composable
fun rememberDriveDialogCoordinator(
    context: Context,
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    onRequestAuth: ((String) -> Unit) -> Unit
): DriveDialogCoordinator {
    val scope = rememberCoroutineScope()
    return remember(context, viewModel, snackbarHostState) {
        DriveDialogCoordinator(context, viewModel, scope, snackbarHostState, onRequestAuth)
    }
}
