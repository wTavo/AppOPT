package com.example.appopt.data.cloud

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.appopt.AuthenticatorApp
import com.example.appopt.data.local.PreferencesManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.util.SyncNotificationHelper
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Worker en segundo plano ejecutado automáticamente por Android WorkManager.
 *
 * Principios de diseño y optimización:
 * - Descarga y fusiona de forma no destructiva cualquier cambio proveniente de otros dispositivos en Google Drive.
 * - Calcula la huella SHA-256 de la bóveda local consolidada y la compara con la última copia en la nube.
 * - Si no hubo modificaciones reales, finaliza en 0 ms con 0 peticiones de subida redundantes.
 * - Si existen cambios legítimos, solicita el token de forma silenciosa, cifra con AES-256-GCM y actualiza el respaldo.
 */
class AutoSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefsManager = PreferencesManager(applicationContext)

        // 1. Validar si la sincronización automática está habilitada y la cuenta conectada
        if (!prefsManager.isGoogleDriveConnected() || !prefsManager.isAutoSyncEnabled()) {
            return@withContext Result.success()
        }

        val repository = AuthenticatorApp.instance.accountRepository

        try {
            // 2. Solicitar token OAuth2 silencioso a Google Identity Services
            val authClient = GoogleDriveManager.getAuthorizationClient(applicationContext)
            val authResult = Tasks.await(authClient.authorize(GoogleDriveManager.getAuthorizationRequest()))

            if (authResult.hasResolution() || authResult.accessToken == null) {
                return@withContext Result.retry()
            }

            val token = authResult.accessToken!!
            val autoSyncKey = SecurityConfig.AUTO_SYNC_VAULT_KEY.toCharArray()

            try {
                // 3. Fusión remota previa (Descargar cambios de otros teléfonos si existen)
                val downloadResult = GoogleDriveManager.downloadBackup(token, autoSyncKey)
                if (downloadResult.isSuccess) {
                    val remotePayload = downloadResult.getOrNull()
                    if (!remotePayload.isNullOrBlank()) {
                        repository.mergeAccountsFromRemote(remotePayload)
                    }
                }

                // 4. Extraer cuentas locales consolidadas tras la posible fusión
                val accounts = repository.getAccounts().first()
                if (accounts.isEmpty()) {
                    return@withContext Result.success()
                }

                // 5. Comparar huella digital SHA-256 para evitar subidas redundantes
                val currentVaultHash = CloudVaultSyncManager.computeAccountsSignature(accounts)
                val lastSyncedVaultHash = prefsManager.getLastSyncedVaultHash()

                if (currentVaultHash == lastSyncedVaultHash && downloadResult.isSuccess) {
                    // El contenido de las credenciales es idéntico: 0 subidas necesarias
                    return@withContext Result.success()
                }

                val payload = repository.exportAccountsForTransfer()
                if (payload.isBlank()) {
                    return@withContext Result.success()
                }

                // 6. Cifrado y subida a Google Drive con AES-256-GCM
                SyncNotificationHelper.showSyncProgressNotification(applicationContext)
                val uploadResult = GoogleDriveManager.uploadBackup(token, payload, autoSyncKey)

                if (uploadResult.isSuccess) {
                    val now = System.currentTimeMillis()
                    prefsManager.setLastSyncTimestamp(now)
                    prefsManager.setLastSyncedVaultHash(currentVaultHash)
                    SyncNotificationHelper.showSyncSuccessNotification(applicationContext, accounts.size)
                    Result.success()
                } else {
                    SyncNotificationHelper.showSyncFailureNotification(applicationContext)
                    Result.retry()
                }
            } finally {
                autoSyncKey.fill('0')
            }
        } catch (_: Exception) {
            SyncNotificationHelper.showSyncFailureNotification(applicationContext)
            Result.retry()
        }
    }
}
