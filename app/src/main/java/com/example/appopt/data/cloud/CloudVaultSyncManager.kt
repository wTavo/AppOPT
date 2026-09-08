package com.example.appopt.data.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.appopt.domain.model.TotpAccount
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Frecuencia configurable de la copia de seguridad automática en Google Drive.
 *
 * @property intervalMinutes Intervalo en minutos entre ejecuciones periódicas (0 para desactivada).
 */
enum class SyncFrequency(val intervalMinutes: Long) {
    MINUTES_15(15L),
    HOURLY(60L),
    DAILY(1440L),
    WEEKLY(10080L),
    MONTHLY(43200L),
    OFF(0L);

    companion object {
        /**
         * Retorna la frecuencia a partir de su nombre o [DAILY] por defecto.
         */
        fun fromName(name: String?): SyncFrequency {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DAILY
        }
    }
}

/**
 * Gestor centralizado de sincronización periódica en la nube y cálculo de huellas criptográficas SHA-256.
 *
 * Responsabilidades:
 * - Calcula la huella digital SHA-256 del contenido exportado para evitar peticiones redundantes.
 * - Orquesta las tareas periódicas en segundo plano con [WorkManager] respetando restricciones de red, batería y frecuencia.
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
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(payload.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
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
     * Programa o cancela la tarea periódica de sincronización en segundo plano con [WorkManager].
     *
     * @param context Contexto de la aplicación.
     * @param frequency Frecuencia configurada ([SyncFrequency.DAILY], [SyncFrequency.WEEKLY], [SyncFrequency.MONTHLY] o [SyncFrequency.OFF]).
     * @param allowMobileData Indica si la sincronización tiene permiso para usar datos móviles (4G/5G).
     */
    fun schedulePeriodicSync(
        context: Context,
        frequency: SyncFrequency,
        allowMobileData: Boolean
    ) {
        val workManager = WorkManager.getInstance(context)

        if (frequency == SyncFrequency.OFF) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }

        val networkType = if (allowMobileData) {
            NetworkType.CONNECTED
        } else {
            NetworkType.UNMETERED
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<AutoSyncWorker>(frequency.intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    const val REACTIVE_WORK_NAME = "appopt_cloud_vault_reactive_sync"
    const val DEFAULT_DEBOUNCE_SECONDS = 30L

    /**
     * Clave del resultado devuelto por [AutoSyncWorker] indicando si se realizó una subida efectiva a Google Drive.
     */
    const val KEY_SYNC_PERFORMED = "key_sync_performed"

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
        if (!prefsManager.isGoogleDriveConnected() || !prefsManager.isAutoSyncEnabled()) {
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
