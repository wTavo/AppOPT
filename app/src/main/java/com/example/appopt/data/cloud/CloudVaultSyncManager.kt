package com.example.appopt.data.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.security.sha256Hex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import com.example.appopt.security.SecurityConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Estado reactivo unificado de las tareas en segundo plano de sincronización en la nube.
 *
 * @property isSyncRunning Indica si una tarea reactiva, manual o periódica está ejecutándose activamente.
 * @property isSyncPending Indica si hay una tarea en cola esperando el retardo de consolidación (*debounce*).
 * @property hasSucceededWithUpload Indica si la última tarea completada realizó una subida efectiva a Google Drive.
 * @property hasFailed Indica si la última tarea en segundo plano finalizó en estado de error.
 */
data class WorkManagerSyncStatus(
    val isSyncRunning: Boolean = false,
    val isSyncPending: Boolean = false,
    val hasSucceededWithUpload: Boolean = false,
    val hasFailed: Boolean = false
)

/**
 * Gestor centralizado de sincronización periódica en la nube y cálculo de huellas criptográficas SHA-256.
 *
 * Responsabilidades:
 * - Calcula la huella digital SHA-256 del contenido exportado para evitar peticiones redundantes.
 * - Orquesta las tareas periódicas en segundo plano con [WorkManager] respetando restricciones de red y batería.
 */
object CloudVaultSyncManager {

    const val PERIODIC_WORK_NAME = "appopt_cloud_vault_periodic_sync"

    /**
     * Calcula la huella digital SHA-256 matemática del contenido de la bóveda.
     *
     * @param payload Cadena serializada JSON con los servicios a evaluar.
     * @return Cadena hexadecimal de 64 caracteres representativa del contenido exacto.
     */
    fun computeVaultHash(payload: String): String {
        return payload.toByteArray(StandardCharsets.UTF_8).sha256Hex()
    }

    /**
     * Calcula la firma hash determinística de una lista de cuentas en memoria.
     *
     * Evalúa exclusivamente los datos sustanciales de las credenciales 2FA
     * (identificador, emisor, nombre de cuenta, algoritmo, dígitos, periodo, tipo y contador),
     * omitiendo metadatos volátiles o de presentación local como [TotpAccount.orderIndex],
     * [TotpAccount.updatedAt] o [TotpAccount.isFavorite] para evitar falsos positivos de sincronización al reordenar tarjetas.
     *
     * @param accounts Lista de cuentas registradas en la bóveda.
     * @return Huella SHA-256 representativa del conjunto de credenciales.
     */
    fun computeAccountsSignature(accounts: List<TotpAccount>): String {
        val raw = accounts.sortedBy { it.id }.joinToString("|") {
            "${it.id}:${it.accountName}:${it.issuer}:${it.period}:${it.digits}:${it.algorithm.name}:${it.type.name}:${it.counter}"
        }
        return computeVaultHash(raw)
    }

    /**
     * Cancela todas las tareas programadas de sincronización en segundo plano con [WorkManager].
     *
     * @param context Contexto de la aplicación.
     */
    fun cancelAllSync(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(REACTIVE_WORK_NAME)
    }

    const val REACTIVE_WORK_NAME = "appopt_cloud_vault_reactive_sync"
    const val DEFAULT_DEBOUNCE_SECONDS = SecurityConfig.REACTIVE_SYNC_DEBOUNCE_SECONDS

    /**
     * Clave del resultado devuelto por [AutoSyncWorker] indicando si se realizó una subida efectiva a Google Drive.
     */
    const val KEY_SYNC_PERFORMED = "key_sync_performed"

    /**
     * Clave de entrada enviada a [AutoSyncWorker] para indicar una sincronización manual forzada por el usuario.
     */
    const val KEY_FORCE_MANUAL_SYNC = "key_force_manual_sync"

    /**
     * Observa en tiempo real el estado de las tareas de WorkManager ([REACTIVE_WORK_NAME] y [PERIODIC_WORK_NAME]).
     *
     * @param context Contexto de la aplicación.
     * @return Flujo reactivo con el estado consolidado de ejecución de tareas en la nube.
     */
    fun observeWorkManagerSyncStatus(context: Context): Flow<WorkManagerSyncStatus> {
        val workManager = WorkManager.getInstance(context)
        return combine(
            workManager.getWorkInfosForUniqueWorkFlow(REACTIVE_WORK_NAME),
            workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK_NAME)
        ) { reactiveList, periodicList ->
            val isReactiveRunning = reactiveList.any { it.state == WorkInfo.State.RUNNING }
            val isPeriodicRunning = periodicList.any { it.state == WorkInfo.State.RUNNING }
            val isReactivePending = reactiveList.any { it.state == WorkInfo.State.ENQUEUED }
            val allInfos = reactiveList + periodicList

            val hasFailed = allInfos.any { it.state == WorkInfo.State.FAILED }
            val hasSucceededWithUpload = allInfos.any { info ->
                info.state == WorkInfo.State.SUCCEEDED &&
                    info.outputData.getBoolean(KEY_SYNC_PERFORMED, false)
            }

            WorkManagerSyncStatus(
                isSyncRunning = isReactiveRunning || isPeriodicRunning,
                isSyncPending = isReactivePending,
                hasSucceededWithUpload = hasSucceededWithUpload,
                hasFailed = hasFailed
            )
        }
    }

    /**
     * Evalúa si una lista de cuentas en memoria está completamente sincronizada con la nube
     * según las marcas de tiempo y la huella digital guardadas en preferencias.
     *
     * @param isConnected Indica si Google Drive está conectado.
     * @param lastSyncTimestamp Marca de tiempo de la última sincronización.
     * @param lastSyncedHash Huella digital de la última sincronización confirmada.
     * @param accounts Lista de cuentas locales en la bóveda.
     * @return True si la bóveda local coincide exactamente con la última versión en la nube.
     */
    fun isVaultSyncedWithCloud(
        isConnected: Boolean,
        lastSyncTimestamp: Long,
        lastSyncedHash: String?,
        accounts: List<TotpAccount>
    ): Boolean {
        if (!isConnected || lastSyncTimestamp <= 0L || lastSyncedHash.isNullOrEmpty() || accounts.isEmpty()) {
            return false
        }
        val currentVaultHash = computeAccountsSignature(accounts)
        return currentVaultHash == lastSyncedHash
    }

    /**
     * Calcula la firma hash determinística de una lista de entidades [AccountEntity] sin requerir descifrado de secretos.
     *
     * @param entities Lista de entidades activas en la base de datos Room.
     * @return Huella SHA-256 representativa del conjunto de credenciales.
     */
    fun computeEntitiesSignature(entities: List<com.example.appopt.data.local.AccountEntity>): String {
        val raw = entities.sortedBy { it.id }.joinToString("|") {
            "${it.id}:${it.accountName}:${it.issuer}:${it.period}:${it.digits}:${it.algorithm}:${it.type}:${it.counter}"
        }
        return computeVaultHash(raw)
    }

    /**
     * Encola una sincronización inmediata en la nube a través del motor unificado de [WorkManager] (retardo 0s).
     *
     * Principio de diseño:
     * - Cancela y reemplaza de inmediato cualquier tarea reactiva diferida de 30s que estuviera en cola.
     * - Ejecuta [AutoSyncWorker] de forma instantánea garantizando que la subida sobreviva
     *   al ciclo de vida de la pantalla si el usuario sale de la aplicación.
     *
     * @param context Contexto de la aplicación.
     */
    fun syncImmediately(context: Context) {
        val prefsManager = com.example.appopt.data.local.PreferencesManager(context)
        if (!prefsManager.isGoogleDriveConnected()) {
            return
        }

        val workManager = WorkManager.getInstance(context)
        val allowMobileData = prefsManager.isSyncMobileDataAllowed()
        val networkType = if (allowMobileData) {
            NetworkType.CONNECTED
        } else {
            NetworkType.UNMETERED
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val immediateRequest = OneTimeWorkRequestBuilder<AutoSyncWorker>()
            .setInputData(androidx.work.workDataOf(KEY_FORCE_MANUAL_SYNC to true))
            .setInitialDelay(0L, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            REACTIVE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            immediateRequest
        )
    }

    /**
     * Dispara una tarea única de sincronización en segundo plano con retardo de consolidación (*Debouncing*).
     *
     * Principio de consolidación y verificación de paridad:
     * - Si [debounceSeconds] es 0, delega inmediatamente a [syncImmediately].
     * - Si las credenciales locales son idénticas a la última versión subida a Google Drive
     *   (ej. el usuario envió una cuenta a la papelera y la restauró de inmediato), cancela
     *   cualquier tarea pendiente en cola para evitar subidas o animaciones redundantes.
     * - Si existen cambios reales, cancela y reemplaza la tarea pendiente mediante [ExistingWorkPolicy.REPLACE],
     *   reiniciando el temporizador de [debounceSeconds].
     * - Solo cuando el usuario pasa [debounceSeconds] sin realizar modificaciones, se ejecuta la subida consolidada.
     *
     * @param context Contexto de la aplicación.
     * @param debounceSeconds Retardo en segundos antes de iniciar la subida (por defecto 30 segundos, 0 para ejecución inmediata).
     */
    fun triggerReactiveSync(
        context: Context,
        debounceSeconds: Long = DEFAULT_DEBOUNCE_SECONDS
    ) {
        val prefsManager = com.example.appopt.data.local.PreferencesManager(context)
        val isDriveEncrypted = prefsManager.isDriveBackupEncrypted()
        val isReady = if (isDriveEncrypted) {
            prefsManager.isCloudVaultInitialized() && CloudVaultKeyStore.hasVaultKey(context)
        } else {
            prefsManager.isGoogleDriveConnected()
        }
        if (!isReady || !prefsManager.isAutoSyncEnabled()) {
            return
        }

        if (debounceSeconds == 0L) {
            syncImmediately(context)
            return
        }

        val workManager = WorkManager.getInstance(context)

        CoroutineScope(Dispatchers.IO).launch {
            val lastSyncedVaultHash = prefsManager.getLastSyncedVaultHash()
            val db = com.example.appopt.data.local.AppDatabase.getInstance(context)
            val entities = db.accountDao().getAllAccountsSync()
            val currentVaultHash = computeEntitiesSignature(entities)

            if (!lastSyncedVaultHash.isNullOrEmpty() && currentVaultHash == lastSyncedVaultHash) {
                // La bóveda regresó a paridad exacta con la nube (o no tiene cambios).
                // Cancelamos cualquier tarea pendiente en cola y evitamos sincronizaciones innecesarias.
                workManager.cancelUniqueWork(REACTIVE_WORK_NAME)
                return@launch
            }

            val allowMobileData = prefsManager.isSyncMobileDataAllowed()
            val networkType = if (allowMobileData) {
                NetworkType.CONNECTED
            } else {
                NetworkType.UNMETERED
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()

            val reactiveRequest = OneTimeWorkRequestBuilder<AutoSyncWorker>()
                .setInitialDelay(debounceSeconds, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniqueWork(
                REACTIVE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                reactiveRequest
            )
        }
    }
}
