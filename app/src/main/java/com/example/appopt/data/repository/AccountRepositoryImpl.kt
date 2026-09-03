package com.example.appopt.data.repository

import com.example.appopt.data.local.AccountDao
import com.example.appopt.data.local.AccountEntity
import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.domain.repository.AccountRepository
import com.example.appopt.domain.repository.AccountWithCode
import com.example.appopt.domain.totp.Base32
import com.example.appopt.domain.totp.OtpUriParser
import com.example.appopt.domain.totp.TotpEngine
import com.example.appopt.security.BackupCrypto
import com.example.appopt.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Implementación del repositorio de cuentas 2FA.
 *
 * Principio central de seguridad:
 * - Orquesta el descifrado bajo demanda: el secreto TOTP se descifra únicamente en el instante del cálculo del código.
 * - Una vez generado el código numérico, el arreglo de bytes descifrado se limpia de inmediato de la memoria RAM con ceros ([CryptoManager.zeroize]).
 * - En la base de datos sólo se almacenan bytes cifrados con AES-256-GCM y su respectivo vector de inicialización (IV).
 */
class AccountRepositoryImpl(
    private val accountDao: AccountDao,
    private val cryptoManager: CryptoManager
) : AccountRepository {

    private data class CachedOtp(
        val step: Long,
        val counter: Long,
        val code: String
    )

    private val otpCodeCache = java.util.concurrent.ConcurrentHashMap<String, CachedOtp>()

    /**
     * Limpia de forma segura la caché de códigos OTP en memoria RAM al bloquear la bóveda.
     */
    override fun clearMemoryCache() {
        otpCodeCache.clear()
    }

    /**
     * Obtiene la lista de cuentas como modelos de dominio sin exponer secretos.
     */
    override fun getAccounts(): Flow<List<TotpAccount>> {
        return accountDao.getAllAccounts().map { list ->
            list.map { it.toDomain() }
        }
    }

    /**
     * Calcula sincrónicamente los códigos OTP para una lista de cuentas en memoria en el instante [currentTimeMillis],
     * utilizando una caché de pasos de tiempo (RFC 6238) para evitar descifrados de hardware redundantes en cada tick.
     */
    override suspend fun computeAccountsWithCodes(
        accounts: List<TotpAccount>,
        currentTimeMillis: Long
    ): List<AccountWithCode> {
        return accounts.map { domainAccount ->
            val code = if (domainAccount.type == OtpType.TOTP) {
                val step = currentTimeMillis / 1000L / domainAccount.period
                val cached = otpCodeCache[domainAccount.id]
                if (cached != null && cached.step == step) {
                    cached.code
                } else {
                    val entity = accountDao.getAccountById(domainAccount.id)
                    if (entity != null) {
                        var secretBytes: ByteArray? = null
                        try {
                            secretBytes = cryptoManager.decrypt(entity.encryptedSecret, entity.iv)
                            val computed = TotpEngine.generateTotp(
                                secretBytes = secretBytes,
                                timeMillis = currentTimeMillis,
                                periodSeconds = domainAccount.period,
                                digits = domainAccount.digits,
                                algorithm = domainAccount.algorithm
                            )
                            otpCodeCache[domainAccount.id] = CachedOtp(step, 0L, computed)
                            computed
                        } catch (e: Exception) {
                            "------"
                        } finally {
                            secretBytes?.let { CryptoManager.zeroize(it) }
                        }
                    } else {
                        "------"
                    }
                }
            } else {
                val cached = otpCodeCache[domainAccount.id]
                if (cached != null && cached.counter == domainAccount.counter) {
                    cached.code
                } else {
                    val entity = accountDao.getAccountById(domainAccount.id)
                    if (entity != null) {
                        var secretBytes: ByteArray? = null
                        try {
                            secretBytes = cryptoManager.decrypt(entity.encryptedSecret, entity.iv)
                            val computed = TotpEngine.generateHotp(
                                secretBytes = secretBytes,
                                counter = domainAccount.counter,
                                digits = domainAccount.digits,
                                algorithm = domainAccount.algorithm
                            )
                            otpCodeCache[domainAccount.id] = CachedOtp(0L, domainAccount.counter, computed)
                            computed
                        } catch (e: Exception) {
                            "------"
                        } finally {
                            secretBytes?.let { CryptoManager.zeroize(it) }
                        }
                    } else {
                        "------"
                    }
                }
            }

            val remainingSeconds = if (domainAccount.type == OtpType.TOTP) {
                TotpEngine.getRemainingSeconds(currentTimeMillis, domainAccount.period)
            } else 0

            val progress = if (domainAccount.type == OtpType.TOTP) {
                TotpEngine.getProgress(currentTimeMillis, domainAccount.period)
            } else 1.0f

            AccountWithCode(
                account = domainAccount,
                code = code,
                remainingSeconds = remainingSeconds,
                progress = progress
            )
        }
    }

    /**
     * Emite la lista de cuentas acompañadas de sus códigos OTP calculados en tiempo real para el timestamp dado.
     *
     * @param currentTimeMillis Instante temporal del reloj del sistema.
     * @return Flujo reactivo de [AccountWithCode].
     */
    override fun getAccountsWithCodes(currentTimeMillis: Long): Flow<List<AccountWithCode>> {
        return accountDao.getAllAccounts().map { list ->
            computeAccountsWithCodes(list.map { it.toDomain() }, currentTimeMillis)
        }
    }

    /**
     * Obtiene una cuenta por su identificador único.
     */
    override suspend fun getAccountById(id: String): TotpAccount? {
        return accountDao.getAccountById(id)?.toDomain()
    }

    /**
     * Cifra el secreto en memoria mediante AES-256-GCM y almacena la nueva cuenta en la base de datos local.
     */
    override suspend fun saveAccount(
        issuer: String,
        accountName: String,
        secretBytes: ByteArray,
        algorithm: OtpAlgorithm,
        digits: Int,
        period: Int,
        type: OtpType,
        counter: Long,
        isFavorite: Boolean
    ): TotpAccount {
        val payload = cryptoManager.encrypt(secretBytes)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val entity = AccountEntity(
            id = id,
            issuer = issuer.trim(),
            accountName = accountName.trim(),
            encryptedSecret = payload.ciphertext,
            iv = payload.iv,
            algorithm = algorithm.name,
            digits = digits,
            period = period,
            type = type.name,
            counter = counter,
            isFavorite = isFavorite,
            createdAt = now,
            updatedAt = now
        )

        accountDao.insertAccount(entity)
        return entity.toDomain()
    }

    /**
     * Actualiza el nombre del emisor/servicio y cuenta/usuario de una cuenta existente.
     */
    override suspend fun updateAccount(id: String, issuer: String, accountName: String) {
        otpCodeCache.remove(id)
        accountDao.updateMetadata(
            id = id,
            issuer = issuer.trim(),
            accountName = accountName.trim(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Alterna el estado de favorito de una cuenta.
     */
    override suspend fun toggleFavorite(id: String) {
        val account = accountDao.getAccountById(id) ?: return
        accountDao.updateAccount(
            account.copy(
                isFavorite = !account.isFavorite,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Actualiza el orden secuencial de una lista de cuentas persistiendo los nuevos índices.
     */
    override suspend fun reorderAccounts(orderedIds: List<String>) {
        accountDao.updateAccountsOrder(orderedIds)
    }

    /**
     * Elimina permanentemente una cuenta de la base de datos.
     */
    override suspend fun deleteAccount(id: String) {
        otpCodeCache.remove(id)
        accountDao.deleteAccountById(id)
    }

    /**
     * Incrementa el contador de un token HOTP.
     */
    override suspend fun incrementHotpCounter(id: String) {
        otpCodeCache.remove(id)
        val account = accountDao.getAccountById(id) ?: return
        accountDao.updateAccount(
            account.copy(
                counter = account.counter + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Genera un código OTP individual para una cuenta específica.
     */
    override suspend fun generateOtpForAccount(id: String, currentTimeMillis: Long): String? {
        val entity = accountDao.getAccountById(id) ?: return null
        var secretBytes: ByteArray? = null
        return try {
            secretBytes = cryptoManager.decrypt(entity.encryptedSecret, entity.iv)
            val algorithm = OtpAlgorithm.fromString(entity.algorithm)
            if (entity.type == OtpType.HOTP.name) {
                TotpEngine.generateHotp(
                    secretBytes = secretBytes,
                    counter = entity.counter,
                    digits = entity.digits,
                    algorithm = algorithm
                )
            } else {
                TotpEngine.generateTotp(
                    secretBytes = secretBytes,
                    timeMillis = currentTimeMillis,
                    periodSeconds = entity.period,
                    digits = entity.digits,
                    algorithm = algorithm
                )
            }
        } catch (e: Exception) {
            null
        } finally {
            secretBytes?.let { CryptoManager.zeroize(it) }
        }
    }

    /**
     * Exporta todas las cuentas en un archivo cifrado con contraseña mediante PBKDF2 y AES-256-GCM.
     */
    override suspend fun exportVault(password: CharArray): ByteArray {
        val entities = accountDao.getAllAccounts().first()
        val jsonArray = JSONArray()

        for (entity in entities) {
            var secretBytes: ByteArray? = null
            try {
                secretBytes = cryptoManager.decrypt(entity.encryptedSecret, entity.iv)
                val secretBase32 = Base32.encode(secretBytes)
                val item = JSONObject().apply {
                    put("id", entity.id)
                    put("issuer", entity.issuer)
                    put("accountName", entity.accountName)
                    put("secret", secretBase32)
                    put("algorithm", entity.algorithm)
                    put("digits", entity.digits)
                    put("period", entity.period)
                    put("type", entity.type)
                    put("counter", entity.counter)
                    put("isFavorite", entity.isFavorite)
                    put("orderIndex", entity.orderIndex)
                    put("createdAt", entity.createdAt)
                    put("updatedAt", entity.updatedAt)
                }
                jsonArray.put(item)
            } finally {
                secretBytes?.let { CryptoManager.zeroize(it) }
            }
        }

        val rootObject = JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("accounts", jsonArray)
        }

        return BackupCrypto.encryptBackup(rootObject.toString(), password)
    }

    /**
     * Descifra e importa las cuentas contenidas en un archivo de respaldo.
     */
    override suspend fun importVault(backupBytes: ByteArray, password: CharArray): Result<Int> {
        val decryptResult = BackupCrypto.decryptBackup(backupBytes, password)
        if (decryptResult.isFailure) {
            return Result.failure(decryptResult.exceptionOrNull() ?: Exception("Error al descifrar el respaldo"))
        }

        return runCatching {
            val jsonString = decryptResult.getOrThrow()
            val rootObject = JSONObject(jsonString)
            val jsonArray = rootObject.getJSONArray("accounts")

            var count = 0
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val rawSecret = item.getString("secret")
                val secretBytes = Base32.decode(Base32.sanitize(rawSecret))

                try {
                    val encryptedPayload = cryptoManager.encrypt(secretBytes)
                    val entity = AccountEntity(
                        id = item.optString("id", UUID.randomUUID().toString()),
                        issuer = item.optString("issuer", "Cuenta"),
                        accountName = item.optString("accountName", "Usuario"),
                        encryptedSecret = encryptedPayload.ciphertext,
                        iv = encryptedPayload.iv,
                        algorithm = item.optString("algorithm", "SHA1"),
                        digits = item.optInt("digits", 6),
                        period = item.optInt("period", 30),
                        type = item.optString("type", "TOTP"),
                        counter = item.optLong("counter", 0L),
                        isFavorite = item.optBoolean("isFavorite", false),
                        orderIndex = item.optInt("orderIndex", 0),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                    )
                    accountDao.insertAccount(entity)
                    count++
                } finally {
                    CryptoManager.zeroize(secretBytes)
                }
            }

            count
        }
    }

    /**
     * Exporta las cuentas de la bóveda para migración y transferencia por código QR.
     *
     * Si sólo hay 1 cuenta, genera el URI estándar otpauth://
     * Si hay múltiples cuentas, genera un payload JSON estructurado con el prefijo "appopt-migration:"
     * que asegura compatibilidad y decodificación eficiente en el código QR.
     */
    override suspend fun exportAccountsForTransfer(selectedAccountIds: Set<String>?): String {
        val allEntities = accountDao.getAllAccounts().first()
        val entities = if (selectedAccountIds != null) {
            allEntities.filter { it.id in selectedAccountIds }
        } else {
            allEntities
        }
        if (entities.isEmpty()) return ""

        val jsonArray = JSONArray()
        for (entity in entities) {
            var secretBytes: ByteArray? = null
            try {
                secretBytes = cryptoManager.decrypt(entity.encryptedSecret, entity.iv)
                val secretBase32 = Base32.encode(secretBytes)
                val item = JSONObject().apply {
                    put("issuer", entity.issuer)
                    put("accountName", entity.accountName)
                    put("secret", secretBase32)
                    put("algorithm", entity.algorithm)
                    put("digits", entity.digits)
                    put("period", entity.period)
                    put("type", entity.type)
                    put("counter", entity.counter)
                }
                jsonArray.put(item)
            } finally {
                secretBytes?.let { CryptoManager.zeroize(it) }
            }
        }

        val rootObject = JSONObject().apply {
            put("version", 1)
            put("type", "appopt-migration")
            put("accounts", jsonArray)
        }
        return rootObject.toString()
    }

    /**
     * Importa una o múltiples cuentas a partir de los datos escaneados de un código QR de transferencia.
     */
    override suspend fun importAccountsFromTransfer(transferPayload: String): Result<Int> {
        val trimmed = transferPayload.trim()
        // 1. Caso URI individual estándar otpauth://
        if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
            val parseResult = OtpUriParser.parse(trimmed)
            if (parseResult.isFailure) {
                return Result.failure(parseResult.exceptionOrNull() ?: Exception("QR OTP no válido"))
            }
            val data = parseResult.getOrThrow()
            saveAccount(
                issuer = data.issuer,
                accountName = data.accountName,
                secretBytes = data.secretBytes,
                algorithm = data.algorithm,
                digits = data.digits,
                period = data.period,
                type = data.type,
                counter = data.counter
            )
            return Result.success(1)
        }

        // 2. Caso JSON estructurado multi-cuenta
        return runCatching {
            val root = JSONObject(trimmed)
            val accountsArray = root.getJSONArray("accounts")
            var count = 0
            for (i in 0 until accountsArray.length()) {
                val item = accountsArray.getJSONObject(i)
                val rawSecret = item.getString("secret")
                val secretBytes = Base32.decode(Base32.sanitize(rawSecret))
                try {
                    saveAccount(
                        issuer = item.optString("issuer", "Cuenta"),
                        accountName = item.optString("accountName", "Usuario"),
                        secretBytes = secretBytes,
                        algorithm = OtpAlgorithm.fromString(item.optString("algorithm", "SHA1")),
                        digits = item.optInt("digits", 6),
                        period = item.optInt("period", 30),
                        type = OtpType.fromString(item.optString("type", "TOTP")),
                        counter = item.optLong("counter", 0L)
                    )
                    count++
                } finally {
                    CryptoManager.zeroize(secretBytes)
                }
            }
            count
        }
    }

    /**
     * Mapea una entidad persistida a un modelo de dominio.
     */
    private fun AccountEntity.toDomain(): TotpAccount {
        return TotpAccount(
            id = id,
            issuer = issuer,
            accountName = accountName,
            type = OtpType.fromString(type),
            algorithm = OtpAlgorithm.fromString(algorithm),
            digits = digits,
            period = period,
            counter = counter,
            isFavorite = isFavorite,
            orderIndex = orderIndex,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
