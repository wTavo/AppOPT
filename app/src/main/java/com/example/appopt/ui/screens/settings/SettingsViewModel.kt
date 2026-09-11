package com.example.appopt.ui.screens.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.appopt.AuthenticatorApp
import com.example.appopt.data.cloud.CloudVaultSyncManager
import com.example.appopt.data.cloud.DriveBackupInfo
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.data.cloud.ManualSyncManager
import com.example.appopt.data.cloud.SyncFrequency
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.security.SecurityConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel para gestionar de forma centralizada la lógica de configuración, diagnóstico,
 * transferencia por QR y sincronización cifrada E2EE con Google Drive.
 *
 * Principio de diseño:
 * - Desacopla 100% la lógica de negocio y las llamadas asíncronas de la vista Compose (Directiva 8 y 13).
 * - Modela el estado en un único flujo reactivo inmutable [uiState] (Directiva 21).
 * - Implementa caché TTL en memoria para el historial de versiones (Directiva 6).
 * - Confinamiento estricto de hilos: operaciones de red y base de datos en [Dispatchers.IO] (Directiva 20).
 */
class SettingsViewModel : ViewModel() {

    private val repository = AuthenticatorApp.instance.accountRepository
    private val prefsManager = AuthenticatorApp.instance.preferencesManager
    private val appInstance = AuthenticatorApp.instance

    private val _internalState = MutableStateFlow(
        run {
            val cachedHistory = prefsManager.getCachedBackupHistory()
            val mostRecent = cachedHistory.firstOrNull()
            SettingsUiState(
                isFpsOverlayEnabled = prefsManager.isFpsOverlayEnabled(),
                isAutoSyncEnabled = prefsManager.isAutoSyncEnabled(),
                isSyncMobileDataAllowed = prefsManager.isSyncMobileDataAllowed(),
                backupHistoryList = cachedHistory,
                driveBackupExists = cachedHistory.isNotEmpty(),
                driveBackupInfo = mostRecent?.let { m ->
                    DriveBackupInfo(m.fileId, m.modifiedTimeMillis, m.deviceName)
                }
            )
        }
    )

    /** Estado reactivo unificado de la pantalla de Ajustes. */
    val uiState: StateFlow<SettingsUiState> = combine(
        _internalState,
        repository.getAccounts(),
        prefsManager.isGoogleDriveConnectedFlow,
        combine(
            prefsManager.lastSyncTimestampFlow,
            prefsManager.lastSyncedVaultHashFlow,
            prefsManager.lastBackupHistoryFetchTimestampFlow
        ) { lastSync, lastHash, lastFetch ->
            Triple(lastSync, lastHash.orEmpty(), lastFetch)
        }
    ) { internal, accounts, isConnected, (lastSync, safeLastHash, lastFetch) ->
        val currentVaultHash = CloudVaultSyncManager.computeAccountsSignature(accounts)
        val hasChanges = if (!isConnected || lastSync == 0L || safeLastHash.isEmpty()) {
            false
        } else {
            currentVaultHash != safeLastHash
        }

        internal.copy(
            accounts = accounts,
            isDriveConnected = isConnected,
            lastSyncTimestamp = lastSync,
            lastSyncedHash = safeLastHash,
            hasUnsyncedChanges = hasChanges,
            lastHistoryFetchTimestamp = lastFetch
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    init {
        observeWorkManagerSync()
    }

    /**
     * Observa en tiempo real las tareas en segundo plano de sincronización reactiva y periódica.
     */
    private fun observeWorkManagerSync() {
        viewModelScope.launch {
            val workManager = WorkManager.getInstance(appInstance)
            combine(
                workManager.getWorkInfosForUniqueWorkFlow(CloudVaultSyncManager.REACTIVE_WORK_NAME),
                workManager.getWorkInfosForUniqueWorkFlow(CloudVaultSyncManager.PERIODIC_WORK_NAME)
            ) { reactiveList, periodicList ->
                val isRunning = reactiveList.any { it.state == WorkInfo.State.RUNNING } ||
                        periodicList.any { it.state == WorkInfo.State.RUNNING }
                val hasSucceeded = reactiveList.any {
                    it.state == WorkInfo.State.SUCCEEDED && it.outputData.getBoolean(CloudVaultSyncManager.KEY_SYNC_PERFORMED, false)
                }
                Pair(isRunning, hasSucceeded)
            }.collect { (isRunning, hasSucceeded) ->
                _internalState.update { current ->
                    current.copy(
                        isAutoSyncRunning = isRunning,
                        driveBackupExists = if (hasSucceeded) true else current.driveBackupExists
                    )
                }
            }
        }
    }

    /**
     * Configura el estado de la superposición de fotogramas por segundo (FPS).
     *
     * @param enabled Verdadero para mostrar el overlay, falso para ocultarlo.
     */
    fun setFpsOverlayEnabled(enabled: Boolean) {
        prefsManager.setFpsOverlayEnabled(enabled)
        _internalState.update { it.copy(isFpsOverlayEnabled = enabled) }
    }

    /**
     * Activa o desactiva la sincronización periódica en segundo plano.
     *
     * @param enabled Verdadero para programar la sincronización, falso para desactivarla.
     * @param context Contexto de la aplicación para programar WorkManager.
     */
    fun setAutoSyncEnabled(enabled: Boolean, context: Context) {
        prefsManager.setAutoSyncEnabled(enabled)
        _internalState.update { it.copy(isAutoSyncEnabled = enabled) }
        if (enabled) {
            CloudVaultSyncManager.triggerReactiveSync(context, 0L)
        }
    }

    /**
     * Configura si la sincronización automática tiene permitido ejecutarse mediante datos móviles.
     *
     * @param allowed Verdadero si se permiten datos móviles, falso si solo Wi-Fi.
     * @param context Contexto de la aplicación.
     */
    fun setSyncMobileDataAllowed(allowed: Boolean, context: Context) {
        prefsManager.setSyncMobileDataAllowed(allowed)
        _internalState.update { it.copy(isSyncMobileDataAllowed = allowed) }
        if (uiState.value.isAutoSyncEnabled) {
            CloudVaultSyncManager.triggerReactiveSync(context, 0L)
        }
    }

    /**
     * Desvincula la cuenta de Google Drive, limpiando tokens, cachés y cancelando tareas programadas.
     *
     * @param context Contexto de la aplicación.
     */
    fun disconnectGoogleDrive(context: Context) {
        GoogleDriveManager.clearSession()
        prefsManager.setLastBackupHistoryFetchTimestamp(0L)
        prefsManager.setCachedBackupHistory(emptyList())
        _internalState.update {
            it.copy(
                driveBackupExists = false,
                driveBackupInfo = null,
                backupHistoryList = emptyList()
            )
        }
        prefsManager.setGoogleDriveConnected(false)
        prefsManager.setLastSyncTimestamp(0L)
        prefsManager.setLastSyncedVaultHash("")
        CloudVaultSyncManager.schedulePeriodicSync(context, SyncFrequency.OFF, false)
    }

    /**
     * Notifica que la autorización OAuth2 fue exitosa.
     *
     * @param token Token OAuth2 de Google Drive.
     */
    fun onGoogleDriveConnected(token: String) {
        prefsManager.setGoogleDriveConnected(true)
        GoogleDriveManager.currentAccessToken = token
        _internalState.update { it.copy(isDriveLoading = false) }
    }

    /**
     * Ejecuta una sincronización manual inmediata con la nube a través del motor unificado de [WorkManager].
     *
     * @param context Contexto de la aplicación.
     * @param token Token de acceso de Google Drive opcional para cachear en memoria.
     */
    fun executeManualSync(context: Context, token: String? = null) {
        if (token != null) {
            GoogleDriveManager.currentAccessToken = token
        }
        CloudVaultSyncManager.syncImmediately(context)
    }

    /**
     * Activa de forma inmediata y síncrona el estado de carga del historial de versiones en la UI.
     */
    fun startBackupHistoryLoading() {
        _internalState.update { it.copy(isFetchingBackupHistory = true) }
    }

    /**
     * Consulta el historial de versiones aplicando caché TTL (20 segundos) para evitar saturación de red.
     * Solo realiza la petición si la caché en memoria expiró o está vacía.
     *
     * @param token Token de acceso de Google Drive.
     * @param onAuthExpired Callback invocado si el token ha expirado y requiere reautenticación silenciosa.
     * @param onFinished Callback opcional invocado al finalizar la carga (éxito o fallo).
     */
    fun fetchBackupHistoryIfNeeded(
        token: String,
        onAuthExpired: () -> Unit,
        onFinished: (() -> Unit)? = null
    ) {
        val now = System.currentTimeMillis()
        val lastFetch = prefsManager.getLastBackupHistoryFetchTimestamp()
        val hasCachedItems = _internalState.value.backupHistoryList.isNotEmpty()
        val isCacheFresh = (now - lastFetch < SecurityConfig.BACKUP_HISTORY_CACHE_TTL_MILLIS) && hasCachedItems

        if (isCacheFresh) {
            _internalState.update { it.copy(isFetchingBackupHistory = false) }
            onFinished?.invoke()
            return
        }

        _internalState.update { it.copy(isFetchingBackupHistory = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val historyResult = ManualSyncManager.fetchBackupHistory(token)
                if (historyResult.isSuccess) {
                    val items = historyResult.getOrNull().orEmpty()
                    prefsManager.setLastBackupHistoryFetchTimestamp(System.currentTimeMillis())
                    prefsManager.setCachedBackupHistory(items)
                    val mostRecent = items.firstOrNull()
                    _internalState.update {
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
                _internalState.update { it.copy(isFetchingBackupHistory = false) }
                withContext(Dispatchers.Main) {
                    onFinished?.invoke()
                }
            }
        }
    }

    /**
     * Fuerza la actualización inmediata del historial de versiones desde Google Drive,
     * omitiendo el tiempo de enfriamiento, mostrando la animación de carga y reiniciando el temporizador.
     *
     * @param token Token de acceso de Google Drive.
     * @param onAuthExpired Callback invocado si el token ha expirado.
     */
    fun forceRefreshBackupHistory(
        token: String,
        onAuthExpired: () -> Unit
    ) {
        _internalState.update { it.copy(isFetchingBackupHistory = true, isRefreshingBackupHistory = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val historyResult = ManualSyncManager.fetchBackupHistory(token)
                if (historyResult.isSuccess) {
                    val items = historyResult.getOrNull().orEmpty()
                    prefsManager.setLastBackupHistoryFetchTimestamp(System.currentTimeMillis())
                    prefsManager.setCachedBackupHistory(items)
                    val mostRecent = items.firstOrNull()
                    _internalState.update {
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
                _internalState.update { it.copy(isFetchingBackupHistory = false, isRefreshingBackupHistory = false) }
            }
        }
    }

    /**
     * Refresca la lista de versiones en segundo plano tras una mutación.
     */
    private fun refreshBackupHistory(token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val historyResult = ManualSyncManager.fetchBackupHistory(token)
            if (historyResult.isSuccess) {
                val items = historyResult.getOrNull().orEmpty()
                prefsManager.setLastBackupHistoryFetchTimestamp(System.currentTimeMillis())
                prefsManager.setCachedBackupHistory(items)
                val mostRecent = items.firstOrNull()
                _internalState.update {
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
     * Elimina una versión de respaldo específica en Google Drive tras validar la autenticación criptográfica.
     *
     * @param token Token de acceso de Google Drive.
     * @param fileId Identificador único del archivo en Drive.
     * @param passChars Contraseña o frase de descifrado requerida para autorizar la eliminación.
     * @param onComplete Callback con el resultado booleano.
     */
    fun deleteSpecificBackup(
        token: String,
        fileId: String,
        passChars: CharArray,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _internalState.update { it.copy(isFetchingBackupHistory = true, isRefreshingBackupHistory = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    ManualSyncManager.deleteSpecificBackupWithAuth(token, fileId, passChars)
                }
                if (result.isSuccess) {
                    val updated = _internalState.value.backupHistoryList.filterNot { it.fileId == fileId }
                    prefsManager.setLastBackupHistoryFetchTimestamp(System.currentTimeMillis())
                    prefsManager.setCachedBackupHistory(updated)
                    val mostRecent = updated.firstOrNull()
                    _internalState.update {
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
                _internalState.update { it.copy(isFetchingBackupHistory = false, isRefreshingBackupHistory = false) }
            }
        }
    }

    /**
     * Elimina la totalidad de copias de seguridad de la aplicación en Google Drive tras validar la autenticación criptográfica.
     *
     * @param token Token de acceso de Google Drive.
     * @param passChars Contraseña o frase de descifrado requerida para autorizar la eliminación.
     * @param onComplete Callback con el resultado booleano.
     */
    fun deleteAllBackups(
        token: String,
        passChars: CharArray,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _internalState.update { it.copy(isFetchingBackupHistory = true, isRefreshingBackupHistory = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    ManualSyncManager.deleteAllBackupsWithAuth(token, passChars)
                }
                if (result.isSuccess) {
                    prefsManager.setLastBackupHistoryFetchTimestamp(0L)
                    prefsManager.setCachedBackupHistory(emptyList())
                    _internalState.update {
                        it.copy(
                            backupHistoryList = emptyList(),
                            driveBackupExists = false,
                            driveBackupInfo = null
                        )
                    }
                    prefsManager.setLastSyncTimestamp(0L)
                    prefsManager.setLastSyncedVaultHash("")
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            } finally {
                passChars.fill('0')
                _internalState.update { it.copy(isFetchingBackupHistory = false, isRefreshingBackupHistory = false) }
            }
        }
    }

    /**
     * Crea una copia de seguridad protegida con contraseña y frase mnemónica opcional.
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
        viewModelScope.launch {
            _internalState.update { it.copy(isDriveLoading = true) }
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
                    _internalState.update { it.copy(driveBackupExists = true) }
                    refreshBackupHistory(token)
                }
                onComplete(result)
            } finally {
                _internalState.update { it.copy(isDriveLoading = false) }
            }
        }
    }

    /**
     * Descifra y restaura la copia de seguridad más reciente desde Google Drive.
     *
     * @param context Contexto de la aplicación.
     * @param token Token de acceso de Google Drive.
     * @param passChars Caracteres de descifrado.
     * @param onComplete Callback con el [Result] que contiene el conteo de cuentas restauradas.
     */
    fun restoreFromBackup(
        context: Context,
        token: String,
        passChars: CharArray,
        onComplete: (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            _internalState.update { it.copy(isDriveLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    ManualSyncManager.restoreFromBackup(context, token, passChars)
                }
                if (result.isSuccess) {
                    prefsManager.setLastBackupHistoryFetchTimestamp(0L)
                    refreshBackupHistory(token)
                }
                onComplete(result)
            } finally {
                _internalState.update { it.copy(isDriveLoading = false) }
            }
        }
    }

    /**
     * Descifra y restaura una versión histórica específica de Google Drive.
     *
     * @param context Contexto de la aplicación.
     * @param token Token de acceso de Google Drive.
     * @param fileId Identificador del archivo en Drive.
     * @param passChars Caracteres de descifrado.
     * @param onComplete Callback con el [Result] de la restauración.
     */
    fun restoreSpecificBackup(
        context: Context,
        token: String,
        fileId: String,
        passChars: CharArray,
        onComplete: (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            _internalState.update { it.copy(isDriveLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    ManualSyncManager.restoreSpecificBackup(context, token, fileId, passChars)
                }
                if (result.isSuccess) {
                    prefsManager.setLastBackupHistoryFetchTimestamp(0L)
                    refreshBackupHistory(token)
                }
                onComplete(result)
            } finally {
                _internalState.update { it.copy(isDriveLoading = false) }
            }
        }
    }

    /**
     * Exporta las cuentas seleccionadas para transferencia offline con cifrado opcional mediante PIN.
     *
     * @param selectedIds Conjunto de identificadores de cuentas a exportar.
     * @param pin PIN opcional en [CharArray] para cifrar el payload con AES-256-GCM.
     * @return Cadena formateada para código QR con el payload de transferencia.
     */
    suspend fun exportAccounts(
        selectedIds: Set<String>,
        pin: CharArray? = null
    ): String = withContext(Dispatchers.IO) {
        repository.exportAccountsForTransfer(selectedIds, pin)
    }

    /**
     * Exporta las cuentas seleccionadas divididas en lotes cifrados para transferencia multi-QR.
     *
     * @param selectedIds Conjunto de identificadores de cuentas a exportar.
     * @param pin PIN de 6 dígitos en [CharArray] para cifrar cada lote con AES-256-GCM.
     * @return Lista de cadenas cifradas correspondientes a cada código QR.
     */
    suspend fun exportAccountsInBatches(
        selectedIds: Set<String>,
        pin: CharArray
    ): List<String> = withContext(Dispatchers.IO) {
        repository.exportAccountsInBatches(selectedIds, pin)
    }

    /**
     * Elimina localmente las cuentas que fueron exportadas tras una transferencia completada.
     *
     * @param ids Identificadores de las cuentas a eliminar.
     */
    fun deleteExportedAccounts(ids: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id -> repository.deleteAccount(id) }
        }
    }
}
