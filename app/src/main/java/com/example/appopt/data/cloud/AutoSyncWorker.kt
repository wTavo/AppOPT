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
        val prefsManager = AuthenticatorApp.instance.preferencesManager

        // 1. Validar si la sincronización automática está habilitada y la cuenta conectada
        if (!prefsManager.isGoogleDriveConnected() || !prefsManager.isAutoSyncEnabled()) {
            return@withContext Result.success()
        }

        val repository = AuthenticatorApp.instance.accountRepository

        try {
            // 2. Obtener token OAuth2 (en memoria o solicitar a Google Identity Services)
            val token = GoogleDriveManager.currentAccessToken ?: run {
                val authClient = GoogleDriveManager.getAuthorizationClient(applicationContext)
                val authResult = Tasks.await(authClient.authorize(GoogleDriveManager.getAuthorizationRequest()))
                if (authResult.hasResolution() || authResult.accessToken == null) {
                    SyncNotificationHelper.showSyncFailureNotification(applicationContext)
                    return@withContext Result.failure()
                }
                authResult.accessToken!!.also { GoogleDriveManager.currentAccessToken = it }
            }

            // 3. Comparar huella digital SHA-256 para evitar subidas redundantes
            val accounts = repository.getAccounts().first()
            val currentVaultHash = CloudVaultSyncManager.computeAccountsSignature(accounts)
            val lastSyncedVaultHash = prefsManager.getLastSyncedVaultHash()

            if (currentVaultHash == lastSyncedVaultHash) {
                // El contenido de las credenciales es idéntico: 0 subidas necesarias
                return@withContext Result.success()
            }

            // 4. Ejecutar la canalización de subida unificada
            val uploadResult = ManualSyncManager.syncNow(applicationContext, token)

            if (uploadResult.isSuccess) {
                Result.success()
            } else {
                Result.failure()
            }
        } catch (_: Exception) {
            SyncNotificationHelper.showSyncFailureNotification(applicationContext)
            Result.failure()
        }
    }
}
