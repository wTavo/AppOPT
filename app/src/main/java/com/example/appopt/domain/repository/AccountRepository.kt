package com.example.appopt.domain.repository

import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.model.TotpAccount
import kotlinx.coroutines.flow.Flow

/**
 * Contenedor de presentación que asocia una cuenta [TotpAccount] con su código OTP activo.
 *
 * Nota de rendimiento: Los campos de tiempo (segundos restantes, progreso) se eliminaron
 * intencionalmente de este modelo para evitar que el StateFlow emita nuevos valores cada segundo,
 * lo que causaba la recomposición de todas las tarjetas visibles durante el scroll.
 * [CircularTimeProgress] calcula el tiempo de forma autónoma internamente.
 *
 * @property account Datos descriptivos de la cuenta.
 * @property code Código numérico OTP activo (o valor de espera).
 */
data class AccountWithCode(
    val account: TotpAccount,
    val code: String
)

/**
 * Contrato del Repositorio de Dominio para la gestión segura de cuentas 2FA.
 */
interface AccountRepository {
    /**
     * Emite la lista de cuentas almacenadas en la base de datos local.
     */
    fun getAccounts(): Flow<List<TotpAccount>>

    /**
     * Calcula sincrónicamente los códigos OTP para una lista de cuentas en memoria en el instante [currentTimeMillis],
     * utilizando una caché de pasos de tiempo para evitar descifrados de hardware redundantes.
     */
    suspend fun computeAccountsWithCodes(accounts: List<TotpAccount>, currentTimeMillis: Long): List<AccountWithCode>

    /**
     * Limpia de forma segura cualquier caché volátil de códigos OTP en memoria RAM.
     */
    fun clearMemoryCache()

    /**
     * Busca una cuenta por su ID.
     */
    suspend fun getAccountById(id: String): TotpAccount?

    /**
     * Cifra el secreto proporcionado y guarda la cuenta de forma persistente.
     */
    suspend fun saveAccount(
        issuer: String,
        accountName: String,
        secretBytes: ByteArray,
        algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
        digits: Int = 6,
        period: Int = 30,
        type: OtpType = OtpType.TOTP,
        counter: Long = 0L,
        isFavorite: Boolean = false
    ): TotpAccount

    /**
     * Actualiza el nombre del servicio (emisor) y el nombre de cuenta/usuario.
     */
    suspend fun updateAccount(id: String, issuer: String, accountName: String)

    /**
     * Alterna la marca de favorito de una cuenta.
     */
    suspend fun toggleFavorite(id: String)

    /**
     * Actualiza el orden secuencial de una lista de identificadores de cuentas.
     *
     * @param orderedIds Lista de IDs en su nuevo orden de visualización.
     */
    suspend fun reorderAccounts(orderedIds: List<String>)

    /**
     * Elimina permanentemente una cuenta de la bóveda local.
     */
    suspend fun deleteAccount(id: String)

    /**
     * Incrementa el contador de un token HOTP.
     */
    suspend fun incrementHotpCounter(id: String)

    /**
     * Exporta las cuentas de la bóveda para migración y transferencia por código QR.
     *
     * @param selectedAccountIds Conjunto opcional de identificadores de cuentas a exportar. Si es null, exporta todas.
     * @param pin PIN opcional de 6 dígitos en [CharArray] para cifrar el payload con AES-256-GCM.
     * @return Cadena formateada para codificarse en un código QR de migración.
     */
    suspend fun exportAccountsForTransfer(
        selectedAccountIds: Set<String>? = null,
        pin: CharArray? = null
    ): String

    /**
     * Importa una o múltiples cuentas a partir de los datos escaneados de un código QR de transferencia.
     *
     * @param transferPayload Cadena de texto obtenida del código QR.
     * @param pin PIN opcional de 6 dígitos en [CharArray] para descifrar transferencias protegidas.
     * @return [Result] con la cantidad de cuentas importadas exitosamente.
     */
    suspend fun importAccountsFromTransfer(
        transferPayload: String,
        pin: CharArray? = null
    ): Result<Int>

    /**
     * Fusiona de forma no destructiva las cuentas provenientes de una copia remota de Google Drive con la base de datos local.
     *
     * Principio de resolución determinística de conflictos (Multi-Device Deterministic Merge):
     * - Cuentas remotas no presentes en local se incorporan automáticamente.
     * - Cuentas coincidentes se actualizan preservando la versión con [TotpAccount.updatedAt] más reciente.
     * - Cuentas locales no presentes en el respaldo remoto se mantienen intactas para posterior subida.
     *
     * @param remoteBackupJson Cadena JSON con el respaldo descargado y descifrado de Google Drive.
     * @return [Result] con el número de cuentas insertadas o actualizadas.
     */
    suspend fun mergeAccountsFromRemote(remoteBackupJson: String): Result<Int>

    /**
     * Emite el listado reactivo de cuentas que se encuentran actualmente en la papelera de reciclaje temporal.
     */
    fun getDeletedAccounts(): Flow<List<TotpAccount>>

    /**
     * Traslada una cuenta a la papelera de reciclaje temporal por 30 días.
     *
     * @param id Identificador UUID de la cuenta.
     */
    suspend fun moveToTrash(id: String): Result<Unit>

    /**
     * Restaura una cuenta desde la papelera de reciclaje a la bóveda activa de cuentas.
     *
     * @param id Identificador UUID de la cuenta.
     */
    suspend fun restoreFromTrash(id: String): Result<Unit>

    /**
     * Elimina físicamente una cuenta de forma definitiva e irreversible.
     *
     * @param id Identificador UUID de la cuenta a purgar.
     */
    suspend fun permanentlyDelete(id: String): Result<Unit>

    /**
     * Vacía completamente la papelera de reciclaje.
     *
     * @return [Result] con la cantidad de cuentas purgadas.
     */
    suspend fun emptyTrash(): Result<Int>

    /**
     * Purga automáticamente las cuentas cuya estancia en papelera supere los 30 días de retención.
     *
     * @return [Result] con la cantidad de cuentas purgadas.
     */
    suspend fun purgeExpiredTrash(): Result<Int>
}
