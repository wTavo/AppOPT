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
     * Dispara una tarea única de sincronización en segundo plano con retardo de consolidación (*Debouncing*).
     *
     * Principio de consolidación:
     * - Si el usuario realiza múltiples modificaciones consecutivas, cada cambio cancela y reemplaza
     *   la tarea pendiente mediante [ExistingWorkPolicy.REPLACE], reiniciando el temporizador de [debounceSeconds].
     * - Solo cuando el usuario pasa [debounceSeconds] sin realizar modificaciones, se ejecuta una única subida consolidada.
     *
     * @param context Contexto de la aplicación.
     * @param debounceSeconds Retardo en segundos antes de iniciar la subida (por defecto 30 segundos).
     */
    fun triggerReactiveSync(
        context: Context,
        debounceSeconds: Long = DEFAULT_DEBOUNCE_SECONDS
    ) {
        val prefsManager = com.example.appopt.data.local.PreferencesManager(context)
        if (!prefsManager.isGoogleDriveConnected() || !prefsManager.isAutoSyncEnabled()) {
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
