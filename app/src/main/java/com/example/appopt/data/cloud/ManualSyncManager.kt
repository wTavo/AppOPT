package com.example.appopt.data.cloud

import android.content.Context
import com.example.appopt.AuthenticatorApp
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
 * - Garantiza Cero Conocimiento (*Zero Trust*) sobrescribiendo claves en memoria ([CharArray.fill]).
 * - Emite notificaciones de sistema y actualiza el estado de persistencia de forma idempotente y atómica.
 */
object ManualSyncManager {

    private val syncMutex = Mutex()

    /**
     * Ejecuta una sincronización manual inmediata de la bóveda local hacia Google Drive utilizando
     * la clave de bóveda custodiada en Android Keystore ([CloudVaultKeyStore]).
     *
     * @param context Contexto de la aplicación.
     * @param accessToken Token OAuth2 activo de Google Identity Services.
     * @return [Result] con éxito o error de la operación.
     */
    suspend fun syncNow(
        context: Context,
        accessToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val session = CloudVaultKeyStore.getVaultKeyAndSlots(context)
            ?: return@withContext Result.failure(
                IllegalStateException("No hay clave de bóveda custodiada para sincronización automática")
            )
        uploadVaultSnapshot(
            context = context,
            accessToken = accessToken,
            existingSession = session
        )
    }

    /**
     * Crea o sobrescribe la copia de seguridad remota con protección de contraseña personalizada y mnemónico de emergencia.
     *
     * @param context Contexto de la aplicación.
     * @param accessToken Token OAuth2 activo de Google Identity Services.
     * @param secretKeyPass Contraseña o clave de 64 dígitos en [CharArray].
     * @param emergencyMnemonic Frase mnemónica de 12 palabras opcional en [CharArray].
     * @return [Result] con éxito o error de la operación.
     */
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
                existingSession = null
            )
        } finally {
            secretKeyPass.fill('0')
            emergencyMnemonic?.fill('0')
        }
    }

    /**
     * Canalización unificada de subida a Google Drive con bloqueo de concurrencia y cancelación defensiva.
     *
     * Extrae las credenciales, computa la huella SHA-256, transmite a Google Drive,
     * actualiza la marca de tiempo de última copia en [com.example.appopt.data.local.PreferencesManager],
     * guarda la sesión de clave en [CloudVaultKeyStore] y emite la notificación del sistema correspondiente.
     *
     * @param context Contexto de la aplicación.
     * @param accessToken Token OAuth2 activo.
     * @param secretKeyPass Clave o contraseña de cifrado opcional si se deriva una nueva clave.
     * @param emergencyMnemonic Frase mnemónica de emergencia opcional.
     * @param existingSession Sesión existente con vaultKey y slots para re-cifrado en segundo plano.
     * @return [Result] con el resultado de la subida.
     */
    suspend fun uploadVaultSnapshot(
        context: Context,
        accessToken: String,
        secretKeyPass: CharArray? = null,
        emergencyMnemonic: CharArray? = null,
        existingSession: CloudVaultKeyStore.VaultKeySession? = null
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
                val uploadResult = GoogleDriveManager.uploadBackup(
                    accessToken = accessToken,
                    rawBackupJson = payload,
                    secretKeyPass = secretKeyPass,
                    emergencyMnemonic = emergencyMnemonic,
                    existingSession = existingSession,
                    deviceId = deviceId
                )

                if (uploadResult.isSuccess) {
                    val session = uploadResult.getOrThrow()
                    CloudVaultKeyStore.saveVaultKeyAndSlots(context.applicationContext, session)
                    val now = System.currentTimeMillis()
                    prefsManager.setLastSyncTimestamp(now)
                    prefsManager.setLastSyncedVaultHash(currentVaultHash)
                    GoogleDriveManager.currentAccessToken = accessToken
                    SyncNotificationHelper.showSyncSuccessNotification(context.applicationContext)
                    Result.success(Unit)
                } else {
                    SyncNotificationHelper.showSyncFailureNotification(context.applicationContext)
                    val error = uploadResult.exceptionOrNull() ?: IllegalStateException("Error al subir copia de seguridad")
                    Result.failure(error)
                }
            } catch (e: Exception) {
                SyncNotificationHelper.showSyncFailureNotification(context.applicationContext)
                Result.failure(e)
            }
        }
    }

    /**
     * Consulta el historial completo de copias de seguridad disponibles en Google Drive (hasta 3 versiones).
     *
     * @param accessToken Token OAuth2 activo.
     * @return [Result] con la lista de [DriveBackupItem] ordenadas por fecha descendente.
     */
    suspend fun fetchBackupHistory(
        accessToken: String
    ): Result<List<DriveBackupItem>> = withContext(Dispatchers.IO) {
        GoogleDriveManager.fetchAllBackups(accessToken)
    }

    /**
     * Descarga y restaura una versión específica de copia de seguridad desde Google Drive.
     *
     * Al descifrar exitosamente el sobre criptográfico V2, custodia la sesión de clave ([CloudVaultKeyStore.VaultKeySession])
     * en Android Keystore para habilitar sincronizaciones automáticas posteriores sin requerir volver a solicitar la clave.
     *
     * @param accessToken Token OAuth2 activo.
     * @param fileId Identificador del archivo en Google Drive.
     * @param secretKeyPass Contraseña o clave de descifrado en [CharArray].
     * @return [Result] con el conteo de cuentas restauradas.
     */
    suspend fun restoreSpecificBackup(
        accessToken: String,
        fileId: String,
        secretKeyPass: CharArray
    ): Result<Int> = withContext(Dispatchers.IO) {
        val repository = AuthenticatorApp.instance.accountRepository
        val prefsManager = AuthenticatorApp.instance.preferencesManager

        try {
            val downloadResult = GoogleDriveManager.downloadBackupDetailedById(accessToken, fileId, secretKeyPass)
            if (downloadResult.isFailure) {
                return@withContext Result.failure(downloadResult.exceptionOrNull() ?: IllegalStateException("Error al descargar respaldo"))
            }

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
                    return@withContext Result.failure(mergeResult.exceptionOrNull() ?: importResult.exceptionOrNull() ?: IllegalStateException("Error al importar cuentas"))
                }
            }

            CloudVaultKeyStore.saveVaultKeyAndSlots(AuthenticatorApp.instance.applicationContext, downloaded.session)
            prefsManager.setGoogleDriveConnected(true)
            GoogleDriveManager.currentAccessToken = accessToken

            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            secretKeyPass.fill('0')
        }
    }

    /**
     * Descarga y restaura la copia de seguridad más reciente desde Google Drive.
     *
     * Al descifrar exitosamente el sobre criptográfico V2, custodia la sesión de clave ([CloudVaultKeyStore.VaultKeySession])
     * en Android Keystore para habilitar sincronizaciones automáticas posteriores sin requerir volver a solicitar la clave.
     *
     * @param accessToken Token OAuth2 activo.
     * @param secretKeyPass Contraseña o clave de descifrado en [CharArray].
     * @return [Result] con el conteo de cuentas restauradas.
     */
    suspend fun restoreFromBackup(
        accessToken: String,
        secretKeyPass: CharArray
    ): Result<Int> = withContext(Dispatchers.IO) {
        val repository = AuthenticatorApp.instance.accountRepository
        val prefsManager = AuthenticatorApp.instance.preferencesManager

        try {
            val downloadResult = GoogleDriveManager.downloadBackupDetailed(accessToken, secretKeyPass)
            if (downloadResult.isFailure) {
                return@withContext Result.failure(downloadResult.exceptionOrNull() ?: IllegalStateException("Error al descargar respaldo"))
            }

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
                    return@withContext Result.failure(mergeResult.exceptionOrNull() ?: importResult.exceptionOrNull() ?: IllegalStateException("Error al importar cuentas"))
                }
            }

            CloudVaultKeyStore.saveVaultKeyAndSlots(AuthenticatorApp.instance.applicationContext, downloaded.session)
            prefsManager.setGoogleDriveConnected(true)
            GoogleDriveManager.currentAccessToken = accessToken

            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            secretKeyPass.fill('0')
        }
    }

    /**
     * Elimina una versión específica de copia de seguridad en Google Drive tras validar la contraseña de descifrado.
     *
     * @param accessToken Token OAuth2 activo.
     * @param fileId Identificador del archivo a eliminar.
     * @param secretKeyPass Contraseña o clave de descifrado en [CharArray].
     * @return [Result] con éxito o fallo de la eliminación.
     */
    suspend fun deleteSpecificBackupWithAuth(
        accessToken: String,
        fileId: String,
        secretKeyPass: CharArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val verifyResult = GoogleDriveManager.downloadBackupById(accessToken, fileId, secretKeyPass)
            if (verifyResult.isFailure) {
                return@withContext Result.failure(verifyResult.exceptionOrNull() ?: IllegalStateException("Clave incorrecta"))
            }
            deleteSpecificBackup(accessToken, fileId)
        } finally {
            secretKeyPass.fill('0')
        }
    }

    /**
     * Elimina una versión específica de copia de seguridad en Google Drive.
     *
     * @param accessToken Token OAuth2 activo.
     * @param fileId Identificador del archivo a eliminar.
     * @return [Result] con éxito o fallo de la eliminación.
     */
    suspend fun deleteSpecificBackup(
        accessToken: String,
        fileId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val prefsManager = AuthenticatorApp.instance.preferencesManager
        val deleteResult = GoogleDriveManager.deleteBackupById(accessToken, fileId)
        if (deleteResult.isSuccess) {
            // Refrescar metadatos locales si se eliminó el respaldo principal
            val remaining = GoogleDriveManager.fetchAllBackups(accessToken).getOrNull().orEmpty()
            if (remaining.isEmpty()) {
                prefsManager.setLastSyncTimestamp(0L)
                prefsManager.setLastSyncedVaultHash("")
            } else {
                prefsManager.setLastSyncTimestamp(remaining.first().modifiedTimeMillis)
            }
            Result.success(Unit)
        } else {
            Result.failure(deleteResult.exceptionOrNull() ?: IllegalStateException("Error al eliminar la copia de seguridad"))
        }
    }
}
