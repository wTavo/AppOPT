package com.example.appopt.data.cloud

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.appopt.AuthenticatorApp
import com.example.appopt.data.local.PreferencesManager
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker en segundo plano ejecutado periódicamente por Android WorkManager.
 *
 * Principios de diseño y optimización:
 * - Calcula la huella SHA-256 de la bóveda local y la compara con la última copia en la nube.
 * - Si no hubo modificaciones reales, finaliza en 0 ms con 0 peticiones de red.
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
            // 2. Extraer los datos actuales de la bóveda
            val payload = repository.exportAccountsForTransfer()
            if (payload.isBlank()) {
                return@withContext Result.success()
            }

            // 3. Comparar huella digital SHA-256 para evitar subidas innecesarias
            val currentVaultHash = CloudVaultSyncManager.computeVaultHash(payload)
            val lastSyncedVaultHash = prefsManager.getLastSyncedVaultHash()

            if (currentVaultHash == lastSyncedVaultHash) {
                // El contenido es idéntico: 0 peticiones a Google Drive
                return@withContext Result.success()
            }

            // 4. Solicitar token OAuth2 silencioso a Google Identity Services
            val authClient = GoogleDriveManager.getAuthorizationClient(applicationContext)
            val authResult = Tasks.await(authClient.authorize(GoogleDriveManager.getAuthorizationRequest()))

            if (authResult.hasResolution() || authResult.accessToken == null) {
                return@withContext Result.retry()
            }

            val token = authResult.accessToken!!
            val autoSyncKey = "AppOPT_AutoSync_Vault_E2EE_v1".toCharArray()

            try {
                // 5. Cifrado y subida a Google Drive con AES-256-GCM
                val uploadResult = GoogleDriveManager.uploadBackup(token, payload, autoSyncKey)

                if (uploadResult.isSuccess) {
                    val now = System.currentTimeMillis()
                    prefsManager.setLastSyncTimestamp(now)
                    prefsManager.setLastSyncedVaultHash(currentVaultHash)
                    Result.success()
                } else {
                    Result.retry()
                }
            } finally {
                autoSyncKey.fill('0')
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
