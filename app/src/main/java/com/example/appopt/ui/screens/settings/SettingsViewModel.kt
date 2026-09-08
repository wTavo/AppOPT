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
 * Estado inmutable de la pantalla de Ajustes y sincronización en la nube.
 *
 * @param accounts Lista de cuentas OTP activas en la bóveda local.
 * @param isDriveConnected Indica si la cuenta de Google Drive está vinculada.
 * @param isDriveLoading Indica si hay una operación de red o sincronización en curso iniciada localmente.
 * @param isCheckingDriveBackup Indica si se está verificando el estado de copias remotas al iniciar.
 * @param driveBackupExists Indica si existe al menos una copia de seguridad en Google Drive.
 * @param driveBackupInfo Información de metadatos de la copia más reciente.
 * @param backupHistoryList Lista de versiones históricas en Google Drive ordenadas por fecha descendente.
 * @param lastSyncTimestamp Marca de tiempo Unix de la última sincronización confirmada.
 * @param lastSyncedHash Huella digital SHA-256 de las cuentas en el momento de la última sincronización.
 * @param hasUnsyncedChanges Indica si hay cambios locales pendientes de sincronizar con la nube.
 * @param isFpsOverlayEnabled Indica si la superposición diagnóstica de FPS está activa.
 * @param isAutoSyncEnabled Indica si la sincronización periódica en segundo plano está activada.
 * @param isSyncMobileDataAllowed Indica si se permite la sincronización a través de datos móviles.
 * @param isFetchingBackupHistory Indica si se está consultando el historial de versiones en la nube.
 * @param isAutoSyncRunning Indica si hay un worker de WorkManager ejecutando sincronización reactiva o periódica.
 */
@Immutable
data class SettingsUiState(
    val accounts: List<TotpAccount> = emptyList(),
    val isDriveConnected: Boolean = false,
    val isDriveLoading: Boolean = false,
    val isCheckingDriveBackup: Boolean = false,
    val driveBackupExists: Boolean = false,
    val driveBackupInfo: DriveBackupInfo? = null,
    val backupHistoryList: List<DriveBackupItem> = emptyList(),
    val lastSyncTimestamp: Long = 0L,
    val lastSyncedHash: String = "",
    val hasUnsyncedChanges: Boolean = false,
    val isFpsOverlayEnabled: Boolean = false,
    val isAutoSyncEnabled: Boolean = false,
    val isSyncMobileDataAllowed: Boolean = false,
    val isFetchingBackupHistory: Boolean = false,
    val isAutoSyncRunning: Boolean = false
) {
    /** Indica si cualquier proceso de sincronización local, global o en segundo plano está en curso. */
    val isSyncActive: Boolean
        get() = isDriveLoading || isAutoSyncRunning

    /** Marca de tiempo efectiva para mostrar la última sincronización relativa. */
    val effectiveLastSyncTimestamp: Long
        get() = if (lastSyncTimestamp > 0L) {
            lastSyncTimestamp
        } else {
            backupHistoryList.firstOrNull()?.modifiedTimeMillis
                ?: driveBackupInfo?.modifiedTimeMillis
                ?: 0L
        }
}

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
        SettingsUiState(
            isFpsOverlayEnabled = prefsManager.isFpsOverlayEnabled(),
            isAutoSyncEnabled = prefsManager.isAutoSyncEnabled(),
            isSyncMobileDataAllowed = prefsManager.isSyncMobileDataAllowed()
        )
    )

    /** Marca de tiempo de la última consulta exitosa de historial de versiones para throttling. */
    private var lastBackupHistoryFetchTimestamp = 0L

    /** Estado reactivo unificado de la pantalla de Ajustes. */
    val uiState: StateFlow<SettingsUiState> = combine(
        _internalState,
        repository.getAccounts(),
        prefsManager.isGoogleDriveConnectedFlow,
        prefsManager.lastSyncTimestampFlow,
        prefsManager.lastSyncedVaultHashFlow
    ) { internal, accounts, isConnected, lastSync, lastHash ->
        val safeLastHash = lastHash.orEmpty()
        val currentVaultHash = CloudVaultSyncManager.computeAccountsSignature(accounts)
        val hasChanges = if (!isConnected || (lastSync == 0L && internal.driveBackupInfo == null)) {
            false
        } else {
            safeLastHash.isNotEmpty() && currentVaultHash != safeLastHash
        }

        internal.copy(
            accounts = accounts,
            isDriveConnected = isConnected,
            lastSyncTimestamp = lastSync,
            lastSyncedHash = safeLastHash,
            hasUnsyncedChanges = hasChanges
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
                reactiveList.any { it.state == WorkInfo.State.RUNNING } ||
                        periodicList.any { it.state == WorkInfo.State.RUNNING }
            }.collect { isRunning ->
                _internalState.update { it.copy(isAutoSyncRunning = isRunning) }
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
        lastBackupHistoryFetchTimestamp = 0L
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
     * Notifica que la autorización OAuth2 fue exitosa y consulta el estado del respaldo.
     *
     * @param token Token OAuth2 de Google Drive.
     */
    fun onGoogleDriveConnected(token: String) {
        prefsManager.setGoogleDriveConnected(true)
        GoogleDriveManager.currentAccessToken = token
        _internalState.update { it.copy(isDriveLoading = false) }
        refreshBackupHistory(token)
    }

    /**
     * Verifica de forma asíncrona la existencia del respaldo remoto en Google Drive al entrar a la pantalla.
     *
     * @param token Token de acceso OAuth2 vigente.
     */
    fun checkRemoteBackupOnStartup(token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _internalState.update { it.copy(isCheckingDriveBackup = true) }
            try {
                val historyResult = ManualSyncManager.fetchBackupHistory(token)
                if (historyResult.isSuccess) {
                    val items = historyResult.getOrNull().orEmpty()
                    lastBackupHistoryFetchTimestamp = System.currentTimeMillis()
                    val mostRecent = items.firstOrNull()
                    val accounts = repository.getAccounts().first()
                    val currentVaultHash = CloudVaultSyncManager.computeAccountsSignature(accounts)

                    _internalState.update {
                        it.copy(
                            backupHistoryList = items,
                            driveBackupExists = items.isNotEmpty(),
                            driveBackupInfo = mostRecent?.let { m ->
                                DriveBackupInfo(m.fileId, m.modifiedTimeMillis, m.deviceName)
                            }
                        )
                    }

                    if (accounts.isNotEmpty() && prefsManager.getLastSyncTimestamp() == 0L && mostRecent != null) {
                        prefsManager.setLastSyncTimestamp(mostRecent.modifiedTimeMillis)
                        prefsManager.setLastSyncedVaultHash(currentVaultHash)
                    }
                }
            } finally {
                _internalState.update { it.copy(isCheckingDriveBackup = false) }
            }
        }
    }

    /**
     * Ejecuta una sincronización manual inmediata con la nube.
     *
     * @param context Contexto de la aplicación.
     * @param token Token de acceso de Google Drive.
     * @param onComplete Callback con el resultado booleano de éxito o fallo.
     */
    fun executeManualSync(context: Context, token: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _internalState.update { it.copy(isDriveLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    ManualSyncManager.syncNow(context, token)
                }
                if (result.isSuccess) {
                    _internalState.update { it.copy(driveBackupExists = true) }
                    refreshBackupHistory(token)
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            } finally {
                _internalState.update { it.copy(isDriveLoading = false) }
            }
        }
    }

    /**
     * Consulta el historial de versiones aplicando caché TTL (20 segundos) para evitar saturación de red.
     *
     * @param token Token de acceso de Google Drive.
     * @param forceRefresh Verdadero si se desea omitir la caché y consultar obligatoriamente.
     * @param onAuthExpired Callback invocado si el token ha expirado y requiere reautenticación silenciosa.
     */
    fun fetchBackupHistoryIfNeeded(
        token: String,
        forceRefresh: Boolean = false,
        onAuthExpired: () -> Unit
    ) {
        val now = System.currentTimeMillis()
        val isCacheStale = forceRefresh ||
                _internalState.value.backupHistoryList.isEmpty() ||
                (now - lastBackupHistoryFetchTimestamp > SecurityConfig.BACKUP_HISTORY_CACHE_TTL_MILLIS)

        if (!isCacheStale) return

        viewModelScope.launch(Dispatchers.IO) {
            _internalState.update { it.copy(isFetchingBackupHistory = true) }
            try {
                val historyResult = ManualSyncManager.fetchBackupHistory(token)
                if (historyResult.isSuccess) {
                    val items = historyResult.getOrNull().orEmpty()
                    lastBackupHistoryFetchTimestamp = System.currentTimeMillis()
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
            }
        }
    }

    /**
     * Refresca la lista de versiones en segundo plano.
     */
    private fun refreshBackupHistory(token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val historyResult = ManualSyncManager.fetchBackupHistory(token)
            if (historyResult.isSuccess) {
                val items = historyResult.getOrNull().orEmpty()
                lastBackupHistoryFetchTimestamp = System.currentTimeMillis()
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
     * Elimina una versión de respaldo específica en Google Drive.
     *
     * @param token Token de acceso de Google Drive.
     * @param fileId Identificador único del archivo en Drive.
     * @param onComplete Callback con el resultado booleano.
     */
    fun deleteSpecificBackup(token: String, fileId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _internalState.update { it.copy(isFetchingBackupHistory = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    ManualSyncManager.deleteSpecificBackup(token, fileId)
                }
                if (result.isSuccess) {
                    val updated = _internalState.value.backupHistoryList.filterNot { it.fileId == fileId }
                    lastBackupHistoryFetchTimestamp = System.currentTimeMillis()
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
                _internalState.update { it.copy(isFetchingBackupHistory = false) }
            }
        }
    }

    /**
     * Elimina la totalidad de copias de seguridad de la aplicación en Google Drive.
     *
     * @param token Token de acceso de Google Drive.
     * @param onComplete Callback con el resultado booleano.
     */
    fun deleteAllBackups(token: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _internalState.update { it.copy(isFetchingBackupHistory = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    ManualSyncManager.deleteAllBackups(token)
                }
                if (result.isSuccess) {
                    lastBackupHistoryFetchTimestamp = 0L
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
                _internalState.update { it.copy(isFetchingBackupHistory = false) }
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
        onComplete: (Boolean) -> Unit
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
                    if (uiState.value.isAutoSyncEnabled) {
                        CloudVaultSyncManager.triggerReactiveSync(context, 0L)
                    }
                    onComplete(true)
                } else {
                    onComplete(false)
                }
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
                    lastBackupHistoryFetchTimestamp = 0L
                    if (uiState.value.isAutoSyncEnabled) {
                        CloudVaultSyncManager.triggerReactiveSync(context, 0L)
                    }
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
                    lastBackupHistoryFetchTimestamp = 0L
                    if (uiState.value.isAutoSyncEnabled) {
                        CloudVaultSyncManager.triggerReactiveSync(context, 0L)
                    }
                }
                onComplete(result)
            } finally {
                _internalState.update { it.copy(isDriveLoading = false) }
            }
        }
    }

    /**
     * Exporta las cuentas seleccionadas en formato JSON estructurado para transferencia offline.
     *
     * @param selectedIds Conjunto de identificadores de cuentas a exportar.
     * @return Cadena JSON con el payload de transferencia.
     */
    suspend fun exportAccounts(selectedIds: Set<String>): String = withContext(Dispatchers.IO) {
        repository.exportAccountsForTransfer(selectedIds)
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
