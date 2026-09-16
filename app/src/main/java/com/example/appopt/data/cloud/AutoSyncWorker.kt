package com.example.appopt.data.cloud

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.appopt.AuthenticatorApp
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
 * - Si no hubo modificaciones reales, finaliza en 0 ms con 0 peticiones de subida redundantes ([CloudVaultSyncManager.KEY_SYNC_PERFORMED] = false).
 * - Si existen cambios legítimos, solicita el token de forma silenciosa, cifra con AES-256-GCM y actualiza el respaldo ([CloudVaultSyncManager.KEY_SYNC_PERFORMED] = true).
 */
class AutoSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefsManager = AuthenticatorApp.instance.preferencesManager
        val isForceManualSync = inputData.getBoolean(CloudVaultSyncManager.KEY_FORCE_MANUAL_SYNC, false)

        // 1. Validar que la bóveda esté conectada, inicializada y contenga la clave custodiada
        if (!prefsManager.isCloudVaultInitialized() || !CloudVaultKeyStore.hasVaultKey(applicationContext)) {
            return@withContext Result.success(
                workDataOf(CloudVaultSyncManager.KEY_SYNC_PERFORMED to false)
            )
        }

        // 2. Si es sincronización automática en segundo plano, validar que esté habilitada
        if (!isForceManualSync && !prefsManager.isAutoSyncEnabled()) {
            return@withContext Result.success(
                workDataOf(CloudVaultSyncManager.KEY_SYNC_PERFORMED to false)
            )
        }

        val repository = AuthenticatorApp.instance.accountRepository

        try {
            // 3. Obtener token OAuth2 (en memoria o solicitar a Google Identity Services)
            val token = GoogleDriveManager.currentAccessToken ?: run {
                val authClient = GoogleDriveManager.getAuthorizationClient(applicationContext)
                val authResult = Tasks.await(authClient.authorize(GoogleDriveManager.getAuthorizationRequest()))
                if (authResult.hasResolution() || authResult.accessToken == null) {
                    SyncNotificationHelper.showSyncFailureNotification(applicationContext)
                    return@withContext Result.failure()
                }
                authResult.accessToken!!.also { GoogleDriveManager.currentAccessToken = it }
            }

            // 4. Si es auto-sincronización en segundo plano, comparar huella digital para evitar subidas redundantes
            val accounts = repository.getAccounts().first()
            val currentVaultHash = CloudVaultSyncManager.computeAccountsSignature(accounts)
            val lastSyncedVaultHash = prefsManager.getLastSyncedVaultHash()

            if (!isForceManualSync && currentVaultHash == lastSyncedVaultHash) {
                // El contenido de las credenciales es idéntico: 0 subidas necesarias
                return@withContext Result.success(
                    workDataOf(CloudVaultSyncManager.KEY_SYNC_PERFORMED to false)
                )
            }

            // 5. Ejecutar la canalización de subida unificada
            val uploadResult = ManualSyncManager.syncNow(applicationContext, token)

            if (uploadResult.isSuccess) {
                Result.success(
                    workDataOf(CloudVaultSyncManager.KEY_SYNC_PERFORMED to true)
                )
            } else {
                Result.failure()
            }
        } catch (_: Exception) {
            SyncNotificationHelper.showSyncFailureNotification(applicationContext)
            Result.failure()
        }
    }
}
