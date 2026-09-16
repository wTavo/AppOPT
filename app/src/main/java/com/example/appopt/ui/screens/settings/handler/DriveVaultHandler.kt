package com.example.appopt.ui.screens.settings.handler

import android.content.Context
import com.example.appopt.AuthenticatorApp
import com.example.appopt.data.cloud.CloudVaultKeyStore
import com.example.appopt.data.cloud.CloudVaultSyncManager
import com.example.appopt.data.cloud.DriveBackupInfo
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.data.cloud.ManualSyncManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.screens.settings.SettingsUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestor especializado para la orquestación operativa de copias de seguridad en Google Drive (Directivas 8 y 18).
 *
 * Desacopla la lógica de red, transacciones de archivos, consultas con caché TTL y mutaciones de [SettingsUiState].
 */
class DriveVaultHandler(
    private val scope: CoroutineScope,
    private val internalState: MutableStateFlow<SettingsUiState>
) {
    private val prefsManager = AuthenticatorApp.instance.preferencesManager

    /**
     * Desvincula la cuenta de Google Drive, limpiando tokens, cachés y cancelando tareas programadas.
     *
     * @param context Contexto de la aplicación.
     */
    fun disconnectGoogleDrive(context: Context) {
        GoogleDriveManager.clearSession()
        prefsManager.setLastBackupHistoryFetchTimestamp(0L)
        prefsManager.setCachedBackupHistory(emptyList())
        internalState.update {
            it.copy(
                driveBackupExists = false,
                driveBackupInfo = null,
                backupHistoryList = emptyList()
            )
        }
        prefsManager.setGoogleDriveConnected(false)
        prefsManager.setLastSyncTimestamp(0L)
        prefsManager.setLastSyncedVaultHash("")
        CloudVaultKeyStore.clear(context)
        CloudVaultSyncManager.cancelAllSync(context)
    }

    /**
     * Consulta el historial de versiones aplicando caché TTL para evitar saturación de red.
     *
     * @param token Token de acceso de Google Drive.
     * @param onAuthExpired Callback invocado si el token ha expirado.
     * @param onFinished Callback opcional invocado al finalizar la carga.
     */
    fun fetchBackupHistoryIfNeeded(
        token: String,
        onAuthExpired: () -> Unit,
        onFinished: (() -> Unit)? = null
    ) {
        val now = System.currentTimeMillis()
        val lastFetch = prefsManager.getLastBackupHistoryFetchTimestamp()
        val hasCachedItems = internalState.value.backupHistoryList.isNotEmpty()
        val isCacheFresh = (now - lastFetch < SecurityConfig.BACKUP_HISTORY_CACHE_TTL_MILLIS) && hasCachedItems

        if (isCacheFresh) {
            internalState.update { it.copy(isFetchingBackupHistory = false) }
            onFinished?.invoke()
            return
        }

        internalState.update { it.copy(isFetchingBackupHistory = true) }

        scope.launch(Dispatchers.IO) {
            try {
                val historyResult = ManualSyncManager.fetchBackupHistory(token)
                if (historyResult.isSuccess) {
                    val items = historyResult.getOrNull().orEmpty()
                    prefsManager.setLastBackupHistoryFetchTimestamp(System.currentTimeMillis())
                    prefsManager.setCachedBackupHistory(items)
                    val mostRecent = items.firstOrNull()
                    internalState.update {
                        it.copy(
                            backupHistoryList = items,
                            driveBackupExists = items.isNotEmpty(),
                            driveBackupInfo = mostRecent?.let { m ->
                                DriveBackupInfo(m.fileId, m.modifiedTimeMillis, m.deviceName)
                            }
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onAuthExpired()
                    }
                }
            } finally {
                internalState.update { it.copy(isFetchingBackupHistory = false) }
                withContext(Dispatchers.Main) {
                    onFinished?.invoke()
                }
            }
        }
    }

    /**
     * Fuerza la actualización inmediata del historial de versiones desde Google Drive.
     *
     * @param token Token de acceso de Google Drive.
     * @param onAuthExpired Callback invocado si el token ha expirado.
     * @param onFinished Callback opcional invocado al finalizar la carga.
     */
    fun forceRefreshBackupHistory(
        token: String,
        onAuthExpired: () -> Unit,
        onFinished: (() -> Unit)? = null
    ) {
        internalState.update { it.copy(isFetchingBackupHistory = true, isRefreshingBackupHistory = true) }
        scope.launch(Dispatchers.IO) {
            try {
                val historyResult = ManualSyncManager.fetchBackupHistory(token)
                if (historyResult.isSuccess) {
                    val items = historyResult.getOrNull().orEmpty()
                    prefsManager.setLastBackupHistoryFetchTimestamp(System.currentTimeMillis())
                    prefsManager.setCachedBackupHistory(items)
                    val mostRecent = items.firstOrNull()
                    internalState.update {
                        it.copy(
                            backupHistoryList = items,
                            driveBackupExists = items.isNotEmpty(),
                            driveBackupInfo = mostRecent?.let { m ->
                                DriveBackupInfo(m.fileId, m.modifiedTimeMillis, m.deviceName)
                            }
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onAuthExpired()
                    }
                }
            } finally {
                internalState.update { it.copy(isFetchingBackupHistory = false, isRefreshingBackupHistory = false) }
                withContext(Dispatchers.Main) {
                    onFinished?.invoke()
                }
            }
        }
    }

    /**
     * Refresca la lista de versiones en segundo plano tras una mutación exitosa.
     */
    fun refreshBackupHistory(token: String) {
        scope.launch(Dispatchers.IO) {
            val historyResult = ManualSyncManager.fetchBackupHistory(token)
            if (historyResult.isSuccess) {
                val items = historyResult.getOrNull().orEmpty()
                prefsManager.setLastBackupHistoryFetchTimestamp(System.currentTimeMillis())
                prefsManager.setCachedBackupHistory(items)
                val mostRecent = items.firstOrNull()
                internalState.update {
                    it.copy(
                        backupHistoryList = items,
                        driveBackupExists = items.isNotEmpty(),
                        driveBackupInfo = mostRecent?.let { m ->
                            DriveBackupInfo(m.fileId, m.modifiedTimeMillis, m.deviceName)
                        }
                    )
                }
            }
        }
    }

    /**
     * Elimina una versión de respaldo específica en Google Drive.
     *
     * @param token Token de acceso de Google Drive.
     * @param fileId Identificador único del archivo en Drive.
     * @param passChars Contraseña o frase de descifrado requerida.
     * @param onComplete Callback con el resultado booleano.
     */
    fun deleteSpecificBackup(
        token: String,
        fileId: String,
        passChars: CharArray,
        onComplete: (Boolean) -> Unit
    ) {
        scope.launch {
            internalState.update { it.copy(isFetchingBackupHistory = true, isRefreshingBackupHistory = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    ManualSyncManager.deleteSpecificBackupWithAuth(token, fileId, passChars)
                }
                if (result.isSuccess) {
                    val updated = internalState.value.backupHistoryList.filterNot { it.fileId == fileId }
                    prefsManager.setLastBackupHistoryFetchTimestamp(System.currentTimeMillis())
                    prefsManager.setCachedBackupHistory(updated)
                    val mostRecent = updated.firstOrNull()
                    internalState.update {
                        it.copy(
                            backupHistoryList = updated,
                            driveBackupExists = updated.isNotEmpty(),
                            driveBackupInfo = mostRecent?.let { m ->
                                DriveBackupInfo(m.fileId, m.modifiedTimeMillis, m.deviceName)
                            }
                        )
                    }
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            } finally {
                passChars.fill('0')
                internalState.update { it.copy(isFetchingBackupHistory = false, isRefreshingBackupHistory = false) }
            }
        }
    }

    /**
     * Crea una copia de seguridad protegida con contraseña y frase mnemónica.
     *
     * @param context Contexto de la aplicación.
     * @param token Token de acceso de Google Drive.
     * @param primaryPass Caracteres de la contraseña maestra.
     * @param emergencyMnemonic Caracteres de la frase de 12 palabras opcional.
     * @param onComplete Callback con el resultado de la operación.
     */
    fun createProtectedBackup(
        context: Context,
        token: String,
        primaryPass: CharArray,
        emergencyMnemonic: CharArray?,
        onComplete: (Result<Unit>) -> Unit
    ) {
        scope.launch {
            internalState.update { it.copy(isDriveLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    ManualSyncManager.createProtectedBackup(
                        context = context,
                        accessToken = token,
                        secretKeyPass = primaryPass,
                        emergencyMnemonic = emergencyMnemonic
                    )
                }
                if (result.isSuccess) {
                    internalState.update { it.copy(driveBackupExists = true) }
                    refreshBackupHistory(token)
                }
                onComplete(result)
            } finally {
                internalState.update { it.copy(isDriveLoading = false) }
            }
        }
    }

    /**
     * Descifra y restaura la copia de seguridad más reciente desde Google Drive.
     *
     * @param token Token de acceso de Google Drive.
     * @param passChars Caracteres de descifrado.
     * @param onComplete Callback con el [Result] de la restauración.
     */
    fun restoreFromBackup(
        token: String,
        passChars: CharArray,
        onComplete: (Result<Int>) -> Unit
    ) {
        scope.launch {
            internalState.update { it.copy(isDriveLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    ManualSyncManager.restoreFromBackup(token, passChars)
                }
                if (result.isSuccess) {
                    prefsManager.setLastBackupHistoryFetchTimestamp(0L)
                    refreshBackupHistory(token)
                }
                onComplete(result)
            } finally {
                internalState.update { it.copy(isDriveLoading = false) }
            }
        }
    }

    /**
     * Descifra y restaura una versión histórica específica de Google Drive.
     *
     * @param token Token de acceso de Google Drive.
     * @param fileId Identificador del archivo en Drive.
     * @param passChars Caracteres de descifrado.
     * @param onComplete Callback con el [Result] de la restauración.
     */
    fun restoreSpecificBackup(
        token: String,
        fileId: String,
        passChars: CharArray,
        onComplete: (Result<Int>) -> Unit
    ) {
        scope.launch {
            internalState.update { it.copy(isDriveLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    ManualSyncManager.restoreSpecificBackup(token, fileId, passChars)
                }
                if (result.isSuccess) {
                    prefsManager.setLastBackupHistoryFetchTimestamp(0L)
                    refreshBackupHistory(token)
                }
                onComplete(result)
            } finally {
                internalState.update { it.copy(isDriveLoading = false) }
            }
        }
    }
}
