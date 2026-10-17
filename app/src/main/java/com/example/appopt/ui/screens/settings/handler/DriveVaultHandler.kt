package com.example.appopt.ui.screens.settings.handler

import android.content.Context
import com.example.appopt.AuthenticatorApp
import com.example.appopt.data.cloud.CloudVaultSyncManager
import com.example.appopt.data.cloud.DriveBackupItem
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
 * Gestor especializado para la orquestación operativa de copias de seguridad en Google Drive (Directivas 8, 18 y 33).
 *
 * Desacopla la lógica de red, transacciones de archivos, consultas con caché TTL y mutaciones de [SettingsUiState].
 */
class DriveVaultHandler(
    private val scope: CoroutineScope,
    private val internalState: MutableStateFlow<SettingsUiState>
) {
    private val prefsManager = AuthenticatorApp.instance.preferencesManager

    /**
     * Desvincula la cuenta de Google Drive, desactivando la copia automática ([com.example.appopt.data.local.PreferencesManager.setAutoSyncEnabled])
     * y preservando el estado de paridad previo si la bóveda se encontraba 100% sincronizada al momento
     * de desvincular.
     *
     * @param context Contexto de la aplicación.
     * @param currentAccounts Lista actual de cuentas activas en la bóveda local.
     * @param hasUnsyncedChanges Indica si existían cambios pendientes de sincronizar al momento de desvincular.
     */
    fun disconnectGoogleDrive(
        context: Context,
        currentAccounts: List<com.example.appopt.domain.model.TotpAccount> = emptyList(),
        hasUnsyncedChanges: Boolean = false
    ) {
        val currentConnectedEmail = prefsManager.getConnectedDriveEmail()
        if (!currentConnectedEmail.isNullOrBlank() && prefsManager.getLastKnownDriveEmail().isNullOrBlank()) {
            prefsManager.setLastKnownDriveEmail(currentConnectedEmail)
        }

        val lastActiveSync = prefsManager.getLastSyncTimestamp()
        val lastActiveHash = prefsManager.getLastSyncedVaultHash().orEmpty()
        val currentHash = if (currentAccounts.isNotEmpty()) {
            CloudVaultSyncManager.computeAccountsSignature(currentAccounts)
        } else {
            ""
        }
        val effectiveSyncedHash = lastActiveHash.ifEmpty { currentHash }
        val wasFullySyncedBeforeDisconnect = lastActiveSync > 0L &&
                !hasUnsyncedChanges &&
                effectiveSyncedHash.isNotEmpty() &&
                (lastActiveHash.isEmpty() || currentHash.isEmpty() || currentHash == lastActiveHash)

        if (wasFullySyncedBeforeDisconnect) {
            prefsManager.setLastKnownSyncedVaultHash(effectiveSyncedHash)
            prefsManager.setVaultModifiedWhileDisconnected(false)
        } else if (lastActiveSync > 0L) {
            prefsManager.setLastKnownSyncedVaultHash(lastActiveHash.ifEmpty { null })
            prefsManager.setVaultModifiedWhileDisconnected(true)
        }

        GoogleDriveManager.signOut(context)
        prefsManager.setConnectedDriveEmail(null)
        prefsManager.setLastBackupHistoryFetchTimestamp(0L)
        prefsManager.setCachedBackupHistory(emptyList())
        internalState.update {
            it.copy(
                driveBackupExists = false,
                driveBackupInfo = null,
                backupHistoryList = emptyList(),
                isAutoSyncEnabled = false
            )
        }
        prefsManager.setGoogleDriveConnected(false)
        prefsManager.setLastSyncTimestamp(0L)
        prefsManager.setLastSyncedVaultHash("")
        prefsManager.setAutoSyncEnabled(false)
        CloudVaultSyncManager.cancelAllSync(context)
    }

    /**
     * Reconcilia de forma asíncrona el estado de vinculación al iniciar el ViewModel si la cuenta ya está
     * conectada y existe historial en caché pendiente de verificar paridad.
     */
    fun reconcileCachedStateOnInit() {
        if (prefsManager.isGoogleDriveConnected() && prefsManager.getLastSyncTimestamp() == 0L) {
            val mostRecent = internalState.value.driveBackupInfo
            if (mostRecent != null) {
                scope.launch(Dispatchers.IO) {
                    relinkAutoSyncIfEligible(mostRecent)
                }
            }
        }
    }

    /**
     * Evalúa bajo el principio de Cero Divergencia si es seguro re-vincular automáticamente el estado
     * de sincronización y activar la copia automática al reconectar una cuenta de Google.
     *
     * Invariantes obligatorias (TODAS deben cumplirse):
     * 1. Existe un respaldo remoto ([mostRecent] != null) y la sesión actual aún no está inicializada (`lastSyncTimestamp == 0L`).
     * 2. La cuenta conectada coincide exactamente con la última cuenta vinculada (`connectedDriveEmail == lastKnownDriveEmail`).
     * 3. La bóveda local NO sufrió ninguna modificación (alta, edición, borrado o importación) mientras estuvo sin cuenta vinculada.
     * 4. El dispositivo custodia la clave de cifrado válida para ese respaldo (`!mostRecent.isEncrypted || CloudVaultKeyStore.hasVaultKey`).
     * 5. El respaldo más reciente proviene de este mismo dispositivo (`mostRecent.deviceId == installationId`).
     * 6. La huella criptográfica SHA-256 actual de las cuentas locales coincide bit a bit con la huella del respaldo en la nube (`currentLocalHash == expectedRemoteHash`).
     */
    private suspend fun relinkAutoSyncIfEligible(mostRecent: DriveBackupItem?) {
        ManualSyncManager.relinkAutoSyncIfEligible(mostRecent)
        internalState.update { it.copy(isAutoSyncEnabled = prefsManager.isAutoSyncEnabled()) }
    }

    /**
     * Completa de forma atómica la vinculación de la cuenta de Google Drive: mantiene el estado de carga
     * activo en el botón de conexión mientras consulta el historial en la nube y evalúa [relinkAutoSyncIfEligible],
     * activando `isGoogleDriveConnected = true` únicamente cuando el estado definitivo está listo para mostrarse
     * sin parpadeos ni transiciones intermedias falsas.
     *
     * @param token Token OAuth2 válido recién autorizado.
     * @param onComplete Callback invocado en el hilo principal al concluir la vinculación.
     */
    fun connectAndVerifyBackupHistory(
        token: String,
        onComplete: () -> Unit = {}
    ) {
        internalState.update { it.copy(isDriveLoading = true, isFetchingBackupHistory = true) }
        scope.launch(Dispatchers.IO) {
            val result = ManualSyncManager.connectAndVerifyDrive(token)
            val items = result.getOrNull().orEmpty()
            val mostRecent = items.firstOrNull()
            internalState.update {
                it.copy(
                    backupHistoryList = items,
                    driveBackupExists = items.isNotEmpty(),
                    driveBackupInfo = mostRecent,
                    isDriveLoading = false,
                    isFetchingBackupHistory = false,
                    isAutoSyncEnabled = prefsManager.isAutoSyncEnabled()
                )
            }
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
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
        val isCacheFresh = (now - lastFetch < SecurityConfig.BACKUP_CACHE_TTL_MILLIS) && hasCachedItems

        if (isCacheFresh) {
            scope.launch(Dispatchers.IO) {
                relinkAutoSyncIfEligible(internalState.value.driveBackupInfo)
                internalState.update { it.copy(isFetchingBackupHistory = false) }
                withContext(Dispatchers.Main) {
                    onFinished?.invoke()
                }
            }
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
                    relinkAutoSyncIfEligible(mostRecent)
                    internalState.update {
                        it.copy(
                            backupHistoryList = items,
                            driveBackupExists = items.isNotEmpty(),
                            driveBackupInfo = mostRecent
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
                GoogleDriveManager.clearDownloadCache()
                val historyResult = ManualSyncManager.fetchBackupHistory(token)
                if (historyResult.isSuccess) {
                    val items = historyResult.getOrNull().orEmpty()
                    prefsManager.setLastBackupHistoryFetchTimestamp(System.currentTimeMillis())
                    prefsManager.setCachedBackupHistory(items)
                    val mostRecent = items.firstOrNull()
                    relinkAutoSyncIfEligible(mostRecent)
                    internalState.update {
                        it.copy(
                            backupHistoryList = items,
                            driveBackupExists = items.isNotEmpty(),
                            driveBackupInfo = mostRecent
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
                relinkAutoSyncIfEligible(mostRecent)
                internalState.update {
                    it.copy(
                        backupHistoryList = items,
                        driveBackupExists = items.isNotEmpty(),
                        driveBackupInfo = mostRecent
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
     * @param passChars Contraseña o frase de descifrado requerida (opcional si no está cifrado).
     * @param onComplete Callback con el resultado booleano.
     */
    fun deleteSpecificBackup(
        token: String,
        fileId: String,
        passChars: CharArray? = null,
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
                            driveBackupInfo = mostRecent
                        )
                    }
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            } finally {
                passChars?.fill('0')
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
     * @param passChars Caracteres de descifrado (opcional si no está cifrado).
     * @param onComplete Callback con el [Result] de la restauración.
     */
    fun restoreFromBackup(
        token: String,
        passChars: CharArray? = null,
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
                passChars?.fill('0')
                internalState.update { it.copy(isDriveLoading = false) }
            }
        }
    }

    /**
     * Descifra y restaura una versión histórica específica de Google Drive.
     *
     * @param token Token de acceso de Google Drive.
     * @param fileId Identificador del archivo en Drive.
     * @param passChars Caracteres de descifrado (opcional si no está cifrado).
     * @param onComplete Callback con el [Result] de la restauración.
     */
    fun restoreSpecificBackup(
        token: String,
        fileId: String,
        passChars: CharArray? = null,
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
                passChars?.fill('0')
                internalState.update { it.copy(isDriveLoading = false) }
            }
        }
    }
}
