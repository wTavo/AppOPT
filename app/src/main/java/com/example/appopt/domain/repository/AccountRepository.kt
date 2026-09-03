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
     * Emite la lista de cuentas con sus códigos OTP calculados para el instante [currentTimeMillis].
     */
    fun getAccountsWithCodes(currentTimeMillis: Long): Flow<List<AccountWithCode>>

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
     * Genera un código OTP individual para una cuenta específica.
     */
    suspend fun generateOtpForAccount(id: String, currentTimeMillis: Long): String?

    /**
     * Exporta todas las cuentas de la bóveda en un archivo cifrado con contraseña mediante PBKDF2 y AES-GCM.
     *
     * @param password Contraseña maestra para proteger el archivo de respaldo.
     * @return Arreglo de bytes del archivo cifrado.
     */
    suspend fun exportVault(password: CharArray): ByteArray

    /**
     * Descifra e importa las cuentas contenidas en un archivo de respaldo.
     *
     * @param backupBytes Contenido del archivo de respaldo.
     * @param password Contraseña de descifrado.
     * @return [Result] con el número de cuentas importadas exitosamente.
     */
    suspend fun importVault(backupBytes: ByteArray, password: CharArray): Result<Int>

    /**
     * Exporta las cuentas de la bóveda para migración y transferencia por código QR.
     *
     * @param selectedAccountIds Conjunto opcional de identificadores de cuentas a exportar. Si es null, exporta todas.
     * @return Cadena formateada para codificarse en un código QR de migración.
     */
    suspend fun exportAccountsForTransfer(selectedAccountIds: Set<String>? = null): String

    /**
     * Importa una o múltiples cuentas a partir de los datos escaneados de un código QR de transferencia.
     *
     * @param transferPayload Cadena de texto obtenida del código QR.
     * @return [Result] con la cantidad de cuentas importadas exitosamente.
     */
    suspend fun importAccountsFromTransfer(transferPayload: String): Result<Int>
}
