package com.example.appopt.data.cloud

import android.content.Context
import com.example.appopt.AuthenticatorApp
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.domain.repository.AccountRepository
import com.example.appopt.util.SyncNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Gestor centralizado y dedicado para la ejecución de sincronizaciones manuales y operaciones de respaldo en la nube.
 *
 * Responsabilidades:
 * - Unifica la lógica de exportación, cálculo de firmas SHA-256 y actualización de marcas de tiempo.
 * - Custodia la clave simétrica de la bóveda ([CloudVaultKeyStore]) y preserva las ranuras de descifrado.
 * - Garantiza Cero Conocimiento (*Zero Trust*) sobrescribiendo claves en memoria ([CharArray.fill] y [ParsedAccountPreview.zeroizeAll]).
 */
object ManualSyncManager {

    private val syncMutex = Mutex()

    /**
     * Ejecuta una sincronización manual inmediata de la bóveda local hacia Google Drive utilizando
     * la clave de bóveda custodiada en Android Keystore ([CloudVaultKeyStore]).
     */
    suspend fun syncNow(context: Context, accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        val prefsManager = AuthenticatorApp.instance.preferencesManager
        val isEncrypted = prefsManager.isDriveBackupEncrypted()
        val session = if (isEncrypted) {
            CloudVaultKeyStore.getVaultKeyAndSlots(context)
                ?: return@withContext Result.failure(
                    IllegalStateException("No hay clave de bóveda custodiada para sincronización automática")
                )
        } else {
            null
        }
        uploadVaultSnapshot(context = context, accessToken = accessToken, existingSession = session, isEncrypted = isEncrypted)
    }

    /** Crea o sobrescribe la copia de seguridad remota con protección de contraseña personalizada y mnemónico. */
    suspend fun createProtectedBackup(
        context: Context,
        accessToken: String,
        secretKeyPass: CharArray,
        emergencyMnemonic: CharArray? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            uploadVaultSnapshot(
                context = context,
                accessToken = accessToken,
                secretKeyPass = secretKeyPass,
                emergencyMnemonic = emergencyMnemonic,
                existingSession = null,
                isEncrypted = true
            )
        } finally {
            secretKeyPass.fill('0')
            emergencyMnemonic?.fill('0')
        }
    }

    /** Canalización unificada de subida a Google Drive con bloqueo de concurrencia y cancelación defensiva. */
    suspend fun uploadVaultSnapshot(
        context: Context,
        accessToken: String,
        secretKeyPass: CharArray? = null,
        emergencyMnemonic: CharArray? = null,
        existingSession: CloudVaultKeyStore.VaultKeySession? = null,
        isEncrypted: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val repository = AuthenticatorApp.instance.accountRepository
            val prefsManager = AuthenticatorApp.instance.preferencesManager
            try {
                SyncNotificationHelper.showSyncProgressNotification(context.applicationContext)
                val accounts = repository.getAccounts().first()
                val payload = repository.exportAccountsForTransfer()
                val currentVaultHash = CloudVaultSyncManager.computeAccountsSignature(accounts)
                val deviceId = prefsManager.getInstallationId()
                val wasUninitialized = prefsManager.getLastSyncTimestamp() == 0L

                val uploadResult = GoogleDriveManager.uploadBackup(
                    accessToken = accessToken,
                    rawBackupJson = payload,
                    secretKeyPass = secretKeyPass,
                    emergencyMnemonic = emergencyMnemonic,
                    existingSession = existingSession,
                    deviceId = deviceId,
                    isEncrypted = isEncrypted,
                    vaultHash = currentVaultHash
                )

                if (uploadResult.isSuccess) {
                    uploadResult.getOrThrow()?.let {
                        CloudVaultKeyStore.saveVaultKeyAndSlots(context.applicationContext, it)
                    }
                    prefsManager.getConnectedDriveEmail()?.takeIf { it.isNotBlank() }?.let {
                        prefsManager.setLastKnownDriveEmail(it)
                    }
                    prefsManager.setLastSyncTimestamp(System.currentTimeMillis())
                    prefsManager.setLastSyncedVaultHash(currentVaultHash)
                    prefsManager.setLastKnownSyncedVaultHash(currentVaultHash)
                    prefsManager.setVaultModifiedWhileDisconnected(false)
                    prefsManager.setDriveBackupEncrypted(isEncrypted)
                    if (wasUninitialized) prefsManager.setAutoSyncEnabled(true)
                    GoogleDriveManager.currentAccessToken = accessToken
                    SyncNotificationHelper.showSyncSuccessNotification(context.applicationContext)
                    Result.success(Unit)
                } else {
                    SyncNotificationHelper.showSyncFailureNotification(context.applicationContext)
                    Result.failure(uploadResult.exceptionOrNull() ?: IllegalStateException("Error al subir copia de seguridad"))
                }
            } catch (e: Exception) {
                SyncNotificationHelper.showSyncFailureNotification(context.applicationContext)
                Result.failure(e)
            }
        }
    }

    /** Consulta el historial completo de copias de seguridad disponibles en Google Drive (hasta 3 versiones). */
    suspend fun fetchBackupHistory(accessToken: String): Result<List<DriveBackupItem>> = withContext(Dispatchers.IO) {
        GoogleDriveManager.fetchAllBackups(accessToken)
    }

    /**
     * Consolida la sesión de clave y actualiza de forma determinística los hashes y banderas de sincronización
     * tras una restauración exitosa desde Google Drive.
     */
    suspend fun finalizeRestoreSyncState(
        accessToken: String?,
        session: CloudVaultKeyStore.VaultKeySession?,
        isEncrypted: Boolean = session != null
    ) {
        val app = AuthenticatorApp.instance
        val prefsManager = app.preferencesManager
        if (session != null) {
            CloudVaultKeyStore.saveVaultKeyAndSlots(app.applicationContext, session)
            prefsManager.setDriveBackupEncrypted(true)
        } else {
            prefsManager.setDriveBackupEncrypted(isEncrypted)
        }
        val updatedAccounts = app.accountRepository.getAccounts().first()
        val currentVaultHash = CloudVaultSyncManager.computeAccountsSignature(updatedAccounts)
        prefsManager.getConnectedDriveEmail()?.takeIf { it.isNotBlank() }?.let {
            prefsManager.setLastKnownDriveEmail(it)
        }
        prefsManager.setGoogleDriveConnected(true)
        prefsManager.setLastSyncTimestamp(System.currentTimeMillis())
        prefsManager.setLastSyncedVaultHash(currentVaultHash)
        prefsManager.setLastKnownSyncedVaultHash(currentVaultHash)
        prefsManager.setVaultModifiedWhileDisconnected(false)
        prefsManager.setAutoSyncEnabled(true)
        CloudVaultSyncManager.cancelAllSync(app.applicationContext)
        if (!accessToken.isNullOrBlank()) GoogleDriveManager.currentAccessToken = accessToken
    }

    /**
     * Ejecuta de forma atómica la importación granular de cuentas seleccionadas desde un respaldo de Google Drive,
     * garantizando la zeroización del 100% de las previsualizaciones en RAM y la reconciliación de hashes.
     */
    suspend fun completeGranularRestore(
        context: Context,
        repository: AccountRepository = AuthenticatorApp.instance.accountRepository,
        allParsedAccounts: List<ParsedAccountPreview>,
        selectedAccountIds: Set<String>,
        session: CloudVaultKeyStore.VaultKeySession?,
        isEncrypted: Boolean = session != null,
        remoteVaultHash: String = "",
        backupModifiedTimeMillis: Long = 0L
    ) = withContext(Dispatchers.IO) {
        val prefsManager = AuthenticatorApp.instance.preferencesManager
        val accountsToImport = allParsedAccounts.filter { it.id in selectedAccountIds }
        try {
            repository.importSelectedAccounts(accountsToImport)
        } finally {
            ParsedAccountPreview.zeroizeAll(allParsedAccounts)
        }

        prefsManager.setGoogleDriveConnected(true)
        if (session != null) {
            CloudVaultKeyStore.saveVaultKeyAndSlots(context.applicationContext, session)
            prefsManager.setDriveBackupEncrypted(true)
        } else {
            prefsManager.setDriveBackupEncrypted(isEncrypted)
        }

        val updatedAccounts = repository.getAccounts().first()
        val currentVaultHash = CloudVaultSyncManager.computeAccountsSignature(updatedAccounts)
        val isFullRestore = remoteVaultHash.isNotEmpty() && selectedAccountIds.size == allParsedAccounts.size
        val effectiveSyncedHash = if (isFullRestore) remoteVaultHash else currentVaultHash

        prefsManager.getConnectedDriveEmail()?.takeIf { it.isNotBlank() }?.let {
            prefsManager.setLastKnownDriveEmail(it)
        }
        if (backupModifiedTimeMillis > 0L) {
            prefsManager.setLastSyncTimestamp(backupModifiedTimeMillis)
        } else if (prefsManager.getLastSyncTimestamp() == 0L) {
            prefsManager.setLastSyncTimestamp(System.currentTimeMillis())
        }
        prefsManager.setLastSyncedVaultHash(effectiveSyncedHash)
        prefsManager.setLastKnownSyncedVaultHash(effectiveSyncedHash)
        prefsManager.setVaultModifiedWhileDisconnected(false)
        prefsManager.setAutoSyncEnabled(true)

        if (!isFullRestore && remoteVaultHash.isNotEmpty() && currentVaultHash != remoteVaultHash) {
            CloudVaultSyncManager.triggerReactiveSync(context.applicationContext)
        } else {
            CloudVaultSyncManager.cancelAllSync(context.applicationContext)
        }
    }

    private suspend fun applyDownloadedBackup(
        accessToken: String,
        downloadResult: Result<DownloadedBackupResult>
    ): Result<Int> {
        if (downloadResult.isFailure) {
            return Result.failure(downloadResult.exceptionOrNull() ?: IllegalStateException("Error al descargar respaldo"))
        }
        val repository = AuthenticatorApp.instance.accountRepository
        val downloaded = downloadResult.getOrThrow()
        val jsonPayload = downloaded.plainJson
        val mergeResult = repository.mergeAccountsFromRemote(jsonPayload)
        val count = if (mergeResult.isSuccess) {
            mergeResult.getOrThrow()
        } else {
            val importResult = repository.importAccountsFromTransfer(jsonPayload)
            if (importResult.isSuccess) {
                importResult.getOrThrow()
            } else {
                return Result.failure(
                    mergeResult.exceptionOrNull()
                        ?: importResult.exceptionOrNull()
                        ?: IllegalStateException("Error al importar cuentas")
                )
            }
        }
        finalizeRestoreSyncState(accessToken = accessToken, session = downloaded.session)
        return Result.success(count)
    }

    /** Descarga y restaura una versión específica de copia de seguridad desde Google Drive. */
    suspend fun restoreSpecificBackup(
        accessToken: String,
        fileId: String,
        secretKeyPass: CharArray? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            applyDownloadedBackup(accessToken, GoogleDriveManager.downloadBackupDetailedById(accessToken, fileId, secretKeyPass))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            secretKeyPass?.fill('0')
        }
    }

    /** Descarga y restaura la copia de seguridad más reciente desde Google Drive. */
    suspend fun restoreFromBackup(
        accessToken: String,
        secretKeyPass: CharArray? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            applyDownloadedBackup(accessToken, GoogleDriveManager.downloadBackupDetailed(accessToken, secretKeyPass))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            secretKeyPass?.fill('0')
        }
    }

    /** Elimina una versión específica de copia de seguridad tras validar la contraseña de descifrado. */
    suspend fun deleteSpecificBackupWithAuth(
        accessToken: String,
        fileId: String,
        secretKeyPass: CharArray? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val verifyResult = GoogleDriveManager.downloadBackupById(accessToken, fileId, secretKeyPass)
            if (verifyResult.isFailure) {
                return@withContext Result.failure(verifyResult.exceptionOrNull() ?: IllegalStateException("Clave incorrecta"))
            }
            deleteSpecificBackup(accessToken, fileId)
        } finally {
            secretKeyPass?.fill('0')
        }
    }

    /** Elimina una versión específica de copia de seguridad en Google Drive. */
    suspend fun deleteSpecificBackup(accessToken: String, fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val prefsManager = AuthenticatorApp.instance.preferencesManager
        val deleteResult = GoogleDriveManager.deleteBackupById(accessToken, fileId)
        if (deleteResult.isSuccess) {
            val remaining = GoogleDriveManager.fetchAllBackups(accessToken).getOrNull().orEmpty()
            if (remaining.isEmpty()) {
                prefsManager.setLastSyncTimestamp(0L)
                prefsManager.setLastSyncedVaultHash("")
                prefsManager.setLastKnownSyncedVaultHash("")
                prefsManager.setAutoSyncEnabled(false)
            } else {
                prefsManager.setLastSyncTimestamp(remaining.first().modifiedTimeMillis)
            }
            Result.success(Unit)
        } else {
            Result.failure(deleteResult.exceptionOrNull() ?: IllegalStateException("Error al eliminar la copia de seguridad"))
        }
    }

    /**
     * Evalúa bajo el principio de Cero Divergencia si es seguro re-vincular automáticamente el estado
     * de sincronización y activar la copia automática al reconectar una cuenta de Google.
     */
    suspend fun relinkAutoSyncIfEligible(mostRecent: DriveBackupItem?) {
        val prefsManager = AuthenticatorApp.instance.preferencesManager
        if (prefsManager.getLastSyncTimestamp() > 0L) return
        if (mostRecent == null) {
            prefsManager.setAutoSyncEnabled(false)
            return
        }
        val appContext = AuthenticatorApp.instance
        val connectedEmail = prefsManager.getConnectedDriveEmail()
        val lastKnownEmail = prefsManager.getLastKnownDriveEmail()
        val isSameAccount = (connectedEmail.isNullOrBlank() && lastKnownEmail.isNullOrBlank()) ||
            (!connectedEmail.isNullOrBlank() && !lastKnownEmail.isNullOrBlank() &&
                connectedEmail.equals(lastKnownEmail, ignoreCase = true))

        val wasModifiedOffline = prefsManager.wasVaultModifiedWhileDisconnected()
        val hasValidKey = !mostRecent.isEncrypted || CloudVaultKeyStore.hasVaultKey(appContext)
        val isSameDeviceOrigin = mostRecent.deviceId.isEmpty() || mostRecent.deviceId == prefsManager.getInstallationId()
        val expectedRemoteHash = mostRecent.vaultHash.ifEmpty { prefsManager.getLastKnownSyncedVaultHash().orEmpty() }

        val currentAccounts = appContext.accountRepository.getAccounts().first()
        val currentLocalHash = if (currentAccounts.isNotEmpty()) {
            CloudVaultSyncManager.computeAccountsSignature(currentAccounts)
        } else {
            ""
        }

        val hasExactParity = if (mostRecent.vaultHash.isNotEmpty()) {
            currentAccounts.isNotEmpty() && currentLocalHash == mostRecent.vaultHash
        } else {
            !wasModifiedOffline && currentAccounts.isNotEmpty() &&
                expectedRemoteHash.isNotEmpty() && currentLocalHash == expectedRemoteHash
        }

        if (isSameAccount && hasValidKey && isSameDeviceOrigin && hasExactParity) {
            if (!connectedEmail.isNullOrBlank()) prefsManager.setLastKnownDriveEmail(connectedEmail)
            prefsManager.setLastSyncTimestamp(mostRecent.modifiedTimeMillis)
            prefsManager.setLastSyncedVaultHash(currentLocalHash)
            prefsManager.setLastKnownSyncedVaultHash(currentLocalHash)
            prefsManager.setVaultModifiedWhileDisconnected(false)
            prefsManager.setDriveBackupEncrypted(mostRecent.isEncrypted)
            prefsManager.setAutoSyncEnabled(true)
        } else {
            prefsManager.setAutoSyncEnabled(false)
        }
    }

    /** Completa de forma atómica y centralizada la vinculación de la cuenta de Google Drive en toda la aplicación. */
    suspend fun connectAndVerifyDrive(token: String): Result<List<DriveBackupItem>> = withContext(Dispatchers.IO) {
        val prefsManager = AuthenticatorApp.instance.preferencesManager
        GoogleDriveManager.currentAccessToken = token
        try {
            val historyResult = fetchBackupHistory(token)
            val items = if (historyResult.isSuccess) {
                val list = historyResult.getOrNull().orEmpty()
                prefsManager.setLastBackupHistoryFetchTimestamp(System.currentTimeMillis())
                prefsManager.setCachedBackupHistory(list)
                relinkAutoSyncIfEligible(list.firstOrNull())
                list
            } else {
                relinkAutoSyncIfEligible(null)
                emptyList()
            }
            prefsManager.setGoogleDriveConnected(true)
            Result.success(items)
        } catch (e: Exception) {
            relinkAutoSyncIfEligible(null)
            prefsManager.setGoogleDriveConnected(true)
            Result.failure(e)
        }
    }
}
