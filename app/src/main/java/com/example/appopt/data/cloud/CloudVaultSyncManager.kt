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

/**
 * Frecuencia configurable de la copia de seguridad automática en Google Drive.
 *
 * @property intervalDays Intervalo de días entre ejecuciones periódicas (0 para desactivada).
 */
enum class SyncFrequency(val intervalDays: Long) {
    DAILY(1L),
    WEEKLY(7L),
    MONTHLY(30L),
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

    const val PeriodicWorkName = "appopt_cloud_vault_periodic_sync"

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
            workManager.cancelUniqueWork(PeriodicWorkName)
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

        val syncRequest = PeriodicWorkRequestBuilder<AutoSyncWorker>(frequency.intervalDays, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PeriodicWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }
}
