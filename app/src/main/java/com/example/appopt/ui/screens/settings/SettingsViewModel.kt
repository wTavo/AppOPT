package com.example.appopt.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appopt.AuthenticatorApp
import com.example.appopt.data.cloud.CloudVaultSyncManager
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.ui.screens.settings.handler.DriveVaultHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
 * - Modela el estado en un único flujo reactivo inmutable [uiState] (Directiva 19).
 * - Confinamiento estricto de hilos: operaciones de red y base de datos en [Dispatchers.IO] (Directiva 18).
 * - Delega operaciones de Google Drive a [DriveVaultHandler] para alta cohesión y modularidad.
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
                isDriveBackupEncrypted = prefsManager.isDriveBackupEncrypted(),
                backupHistoryList = cachedHistory,
                driveBackupExists = cachedHistory.isNotEmpty(),
                driveBackupInfo = mostRecent
            )
        }
    )

    private val driveVaultHandler = DriveVaultHandler(viewModelScope, _internalState)

    /** Estado reactivo unificado de la pantalla de Ajustes. */
    val uiState: StateFlow<SettingsUiState> = combine(
        _internalState,
        repository.getAccounts(),
        prefsManager.isGoogleDriveConnectedFlow,
        prefsManager.isDriveBackupEncryptedFlow,
        combine(
            prefsManager.lastSyncTimestampFlow,
            prefsManager.lastSyncedVaultHashFlow,
            prefsManager.lastBackupHistoryFetchTimestampFlow
        ) { lastSync, lastHash, lastFetch ->
            Triple(lastSync, lastHash.orEmpty(), lastFetch)
        }
    ) { internal, accounts, isConnected, isEncrypted, (lastSync, safeLastHash, lastFetch) ->
        val isSynced = CloudVaultSyncManager.isVaultSyncedWithCloud(isConnected, lastSync, safeLastHash, accounts)
        val hasChanges = isConnected && lastSync > 0L && safeLastHash.isNotEmpty() && !isSynced

        internal.copy(
            accounts = accounts,
            isDriveConnected = isConnected,
            isDriveBackupEncrypted = isEncrypted,
            lastSyncTimestamp = lastSync,
            lastSyncedHash = safeLastHash,
            hasUnsyncedChanges = hasChanges,
            lastHistoryFetchTimestamp = lastFetch
        )
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = _internalState.value.copy(
            isDriveConnected = prefsManager.isGoogleDriveConnectedFlow.value,
            isDriveBackupEncrypted = prefsManager.isDriveBackupEncryptedFlow.value,
            lastSyncTimestamp = prefsManager.lastSyncTimestampFlow.value,
            lastSyncedHash = prefsManager.lastSyncedVaultHashFlow.value.orEmpty(),
            lastHistoryFetchTimestamp = prefsManager.lastBackupHistoryFetchTimestampFlow.value
        )
    )

    init {
        observeWorkManagerSync()
    }

    /**
     * Observa en tiempo real las tareas en segundo plano de sincronización reactiva y periódica.
     */
    private fun observeWorkManagerSync() {
        viewModelScope.launch {
            CloudVaultSyncManager.observeWorkManagerSyncStatus(appInstance)
                .collect { status ->
                    _internalState.update { current ->
                        current.copy(
                            isAutoSyncRunning = status.isSyncRunning,
                            driveBackupExists = if (status.hasSucceededWithUpload) true else current.driveBackupExists
                        )
                    }
                }
        }
    }

    /** Configura el estado de la superposición de FPS. */
    fun setFpsOverlayEnabled(enabled: Boolean) {
        prefsManager.setFpsOverlayEnabled(enabled)
        _internalState.update { it.copy(isFpsOverlayEnabled = enabled) }
    }

    /** Activa o desactiva la sincronización periódica en segundo plano. */
    fun setAutoSyncEnabled(enabled: Boolean, context: Context) {
        prefsManager.setAutoSyncEnabled(enabled)
        _internalState.update { it.copy(isAutoSyncEnabled = enabled) }
        if (enabled) {
            CloudVaultSyncManager.triggerReactiveSync(context, 0L)
        }
    }

    /** Configura si la sincronización automática permite datos móviles. */
    fun setSyncMobileDataAllowed(allowed: Boolean, context: Context) {
        prefsManager.setSyncMobileDataAllowed(allowed)
        _internalState.update { it.copy(isSyncMobileDataAllowed = allowed) }
        if (uiState.value.isAutoSyncEnabled) {
            CloudVaultSyncManager.triggerReactiveSync(context, 0L)
        }
    }

    /** Configura si las copias de seguridad en Google Drive se generan con cifrado E2EE. */
    fun setDriveBackupEncrypted(enabled: Boolean) {
        prefsManager.setDriveBackupEncrypted(enabled)
        _internalState.update { it.copy(isDriveBackupEncrypted = enabled) }
    }

    /** Desvincula la cuenta de Google Drive. */
    fun disconnectGoogleDrive(context: Context) {
        driveVaultHandler.disconnectGoogleDrive(context)
    }

    /** Notifica que la autorización OAuth2 fue exitosa y consulta de forma asíncrona si existen respaldos en la nube. */
    fun onGoogleDriveConnected(token: String) {
        prefsManager.setGoogleDriveConnected(true)
        GoogleDriveManager.currentAccessToken = token
        _internalState.update { it.copy(isDriveLoading = false) }
        fetchBackupHistoryIfNeeded(
            token = token,
            onAuthExpired = {
                // Token expirado defensivo
            }
        )
    }

    /** Ejecuta una sincronización manual inmediata con la nube. */
    fun executeManualSync(context: Context, token: String? = null) {
        if (token != null) {
            GoogleDriveManager.currentAccessToken = token
        }
        CloudVaultSyncManager.syncImmediately(context)
    }

    /** Activa el estado de carga del historial de versiones en la UI. */
    fun startBackupHistoryLoading() {
        _internalState.update { it.copy(isFetchingBackupHistory = true) }
    }

    /** Consulta el historial de versiones aplicando caché TTL. */
    fun fetchBackupHistoryIfNeeded(
        token: String,
        onAuthExpired: () -> Unit,
        onFinished: (() -> Unit)? = null
    ) {
        driveVaultHandler.fetchBackupHistoryIfNeeded(token, onAuthExpired, onFinished)
    }

    /** Fuerza la actualización inmediata del historial de versiones desde Google Drive. */
    fun forceRefreshBackupHistory(
        token: String,
        onAuthExpired: () -> Unit,
        onFinished: (() -> Unit)? = null
    ) {
        driveVaultHandler.forceRefreshBackupHistory(token, onAuthExpired, onFinished)
    }

    /** Elimina una versión de respaldo específica en Google Drive. */
    fun deleteSpecificBackup(
        token: String,
        fileId: String,
        passChars: CharArray? = null,
        onComplete: (Boolean) -> Unit
    ) {
        driveVaultHandler.deleteSpecificBackup(token, fileId, passChars, onComplete)
    }

    /** Crea una copia de seguridad protegida con contraseña y frase mnemónica. */
    fun createProtectedBackup(
        context: Context,
        token: String,
        primaryPass: CharArray,
        emergencyMnemonic: CharArray?,
        onComplete: (Result<Unit>) -> Unit
    ) {
        driveVaultHandler.createProtectedBackup(context, token, primaryPass, emergencyMnemonic, onComplete)
    }

    /** Descifra y restaura la copia de seguridad más reciente desde Google Drive. */
    fun restoreFromBackup(
        token: String,
        passChars: CharArray? = null,
        onComplete: (Result<Int>) -> Unit
    ) {
        driveVaultHandler.restoreFromBackup(token, passChars, onComplete)
    }

    /** Descifra y restaura una versión histórica específica de Google Drive. */
    fun restoreSpecificBackup(
        token: String,
        fileId: String,
        passChars: CharArray? = null,
        onComplete: (Result<Int>) -> Unit
    ) {
        driveVaultHandler.restoreSpecificBackup(token, fileId, passChars, onComplete)
    }

    /** Exporta las cuentas seleccionadas divididas en lotes cifrados para multi-QR. */
    suspend fun exportAccountsInBatches(
        selectedIds: Set<String>,
        pin: CharArray
    ): List<String> = withContext(Dispatchers.IO) {
        repository.exportAccountsInBatches(selectedIds, pin)
    }

    /** Elimina localmente las cuentas que fueron exportadas. */
    fun deleteExportedAccounts(ids: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id -> repository.deleteAccount(id) }
        }
    }
}
