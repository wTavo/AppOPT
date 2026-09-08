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
import com.example.appopt.security.CryptoManager
import com.example.appopt.security.TransferCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Implementación del repositorio de cuentas 2FA.
 *
 * Principio central de seguridad:
 * - Orquesta el descifrado bajo demanda: el secreto TOTP se descifra únicamente en el instante del cálculo del código.
 * - Una vez generado el código numérico, el arreglo de bytes descifrado se limpia de inmediato de la memoria RAM con ceros ([CryptoManager.zeroize]).
 * - En la base de datos solo se almacenan bytes cifrados con AES-256-GCM y su respectivo vector de inicialización (IV).
 */
class AccountRepositoryImpl(
    private val accountDao: AccountDao,
    private val cryptoManager: CryptoManager
) : AccountRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val accountsFlow: SharedFlow<List<TotpAccount>> = accountDao.getAllAccounts()
        .map { list -> list.map { it.toDomain() } }
        .shareIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    private val deletedAccountsFlow: SharedFlow<List<TotpAccount>> = accountDao.getDeletedAccounts()
        .map { list -> list.map { it.toDomain() } }
        .shareIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    private data class CachedOtp(
        val step: Long,
        val counter: Long,
        val code: String
    )

    private val otpCodeCache = java.util.concurrent.ConcurrentHashMap<String, CachedOtp>()

    init {
        // Motor de Precarga en Caliente: Precalcula los códigos en segundo plano al arrancar la app
        repositoryScope.launch {
            accountsFlow.collect { accounts ->
                if (accounts.isNotEmpty()) {
                    computeAccountsWithCodes(accounts, System.currentTimeMillis())
                }
            }
        }
        // Purga automática de registros en papelera que superen los 30 días de retención
        repositoryScope.launch {
            purgeExpiredTrash()
        }
    }

    /**
     * Limpia de forma segura la caché de códigos OTP en memoria RAM al bloquear la bóveda.
     */
    override fun clearMemoryCache() {
        otpCodeCache.clear()
    }

    /**
     * Obtiene la lista de cuentas como modelos de dominio sin exponer secretos (pre-cargada con replay=1).
     */
    override fun getAccounts(): Flow<List<TotpAccount>> = accountsFlow

    /**
     * Obtiene el flujo reactivo de cuentas en la papelera de reciclaje temporal.
     */
    override fun getDeletedAccounts(): Flow<List<TotpAccount>> = deletedAccountsFlow

    /**
     * Calcula sincrónicamente los códigos OTP para una lista de cuentas en memoria en el instante [currentTimeMillis],
     * utilizando una caché de pasos de tiempo (RFC 6238) y ejecución en segundo plano para evitar bloqueos en el hilo UI.
     */
    override suspend fun computeAccountsWithCodes(
        accounts: List<TotpAccount>,
        currentTimeMillis: Long
    ): List<AccountWithCode> = withContext(Dispatchers.Default) {
        val needsDecryption = accounts.any { domainAccount ->
            if (domainAccount.type == OtpType.TOTP) {
                val step = currentTimeMillis / 1000L / domainAccount.period
                val cached = otpCodeCache[domainAccount.id]
                cached == null || cached.step != step
            } else {
                otpCodeCache[domainAccount.id] == null
            }
        }

        val entitiesMap = if (needsDecryption) {
            accountDao.getAllAccountsSync().associateBy { it.id }
        } else {
            emptyMap()
        }

        accounts.map { domainAccount ->
            val code = if (domainAccount.type == OtpType.TOTP) {
                val step = currentTimeMillis / 1000L / domainAccount.period
                val cached = otpCodeCache[domainAccount.id]
                if (cached != null && cached.step == step) {
                    cached.code
                } else {
                    val entity = entitiesMap[domainAccount.id]
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
                        } catch (_: Exception) {
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
                    val entity = entitiesMap[domainAccount.id]
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
                        } catch (_: Exception) {
                            "------"
                        } finally {
                            secretBytes?.let { CryptoManager.zeroize(it) }
                        }
                    } else {
                        "------"
                    }
                }
            }

            AccountWithCode(
                account = domainAccount,
                code = code
            )
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
        com.example.appopt.data.cloud.CloudVaultSyncManager.triggerReactiveSync(com.example.appopt.AuthenticatorApp.instance)
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
        com.example.appopt.data.cloud.CloudVaultSyncManager.triggerReactiveSync(com.example.appopt.AuthenticatorApp.instance)
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
        com.example.appopt.data.cloud.CloudVaultSyncManager.triggerReactiveSync(com.example.appopt.AuthenticatorApp.instance)
    }

    /**
     * Actualiza el orden secuencial de una lista de cuentas persistiendo los nuevos índices.
     */
    override suspend fun reorderAccounts(orderedIds: List<String>) {
        accountDao.updateAccountsOrder(orderedIds)
    }

    /**
     * Elimina una cuenta trasladándola a la papelera de reciclaje temporal (30 días).
     */
    override suspend fun deleteAccount(id: String) {
        moveToTrash(id)
    }

    /**
     * Traslada una cuenta a la papelera de reciclaje temporal por 30 días.
     */
    override suspend fun moveToTrash(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            otpCodeCache.remove(id)
            val now = System.currentTimeMillis()
            accountDao.moveToTrash(id, now)
            com.example.appopt.data.cloud.CloudVaultSyncManager.triggerReactiveSync(com.example.appopt.AuthenticatorApp.instance)
        }
    }

    /**
     * Restaura una cuenta desde la papelera de reciclaje a la bóveda activa.
     */
    override suspend fun restoreFromTrash(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            accountDao.restoreFromTrash(id, now)
            com.example.appopt.data.cloud.CloudVaultSyncManager.triggerReactiveSync(com.example.appopt.AuthenticatorApp.instance)
        }
    }

    /**
     * Elimina permanentemente una cuenta de la base de datos de forma irreversible.
     */
    override suspend fun permanentlyDelete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            otpCodeCache.remove(id)
            accountDao.deleteAccountById(id)
            com.example.appopt.data.cloud.CloudVaultSyncManager.triggerReactiveSync(com.example.appopt.AuthenticatorApp.instance)
        }
    }

    /**
     * Vacía completamente la papelera de reciclaje.
     */
    override suspend fun emptyTrash(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val count = accountDao.emptyTrash()
            if (count > 0) {
                com.example.appopt.data.cloud.CloudVaultSyncManager.triggerReactiveSync(com.example.appopt.AuthenticatorApp.instance)
            }
            count
        }
    }

    /**
     * Purga automáticamente las cuentas cuya estancia en papelera supere los 30 días.
     */
    override suspend fun purgeExpiredTrash(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val threshold = System.currentTimeMillis() - com.example.appopt.security.SecurityConfig.TRASH_RETENTION_MILLIS
            accountDao.purgeExpiredTrash(threshold)
        }
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
        com.example.appopt.data.cloud.CloudVaultSyncManager.triggerReactiveSync(com.example.appopt.AuthenticatorApp.instance)
    }

    /**
     * Exporta las cuentas de la bóveda para migración y transferencia por código QR o copia en la nube.
     *
     * Si se suministra un [pin], el resultado es cifrado mediante AES-256-GCM y PBKDF2 con prefijo "appopt-transfer:".
     * Si no se suministra un [pin], genera el payload JSON estándar "appopt-migration".
     */
    override suspend fun exportAccountsForTransfer(
        selectedAccountIds: Set<String>?,
        pin: CharArray?
    ): String {
        val allEntities = accountDao.getAllAccounts().first()
        val entities = if (selectedAccountIds != null) {
            allEntities.filter { it.id in selectedAccountIds }
        } else {
            allEntities
        }

        val jsonArray = JSONArray()
        for (entity in entities) {
            var secretBytes: ByteArray? = null
            try {
                secretBytes = cryptoManager.decrypt(entity.encryptedSecret, entity.iv)
                val secretBase32 = Base32.encode(secretBytes)
                val item = JSONObject().apply {
                    put("i", entity.issuer)
                    if (entity.accountName.isNotBlank()) {
                        put("a", entity.accountName)
                    }
                    put("s", secretBase32)
                    if (entity.algorithm != "SHA1") {
                        put("alg", entity.algorithm)
                    }
                    if (entity.digits != 6) {
                        put("d", entity.digits)
                    }
                    if (entity.period != 30) {
                        put("p", entity.period)
                    }
                    if (entity.type != "TOTP") {
                        put("t", entity.type)
                    }
                    if (entity.counter > 0) {
                        put("c", entity.counter)
                    }
                }
                jsonArray.put(item)
            } finally {
                secretBytes?.let { CryptoManager.zeroize(it) }
            }
        }

        val rootObject = JSONObject().apply {
            put("v", 1)
            put("a", jsonArray)
        }

        val plainJson = rootObject.toString()
        return if (pin != null) {
            TransferCrypto.encryptTransferPayload(plainJson, pin)
        } else {
            plainJson
        }
    }

    /**
     * Importa una o múltiples cuentas a partir de los datos escaneados de un código QR de transferencia.
     *
     * Soporta:
     * 1. URIs estándar otpauth://
     * 2. Transferencias cifradas con prefijo "appopt-transfer:" descifradas mediante [pin].
     * 3. Payloads JSON estructurados (formato compacto optimizado "a" y formato legado "accounts").
     */
    override suspend fun importAccountsFromTransfer(
        transferPayload: String,
        pin: CharArray?
    ): Result<Int> {
        val trimmed = transferPayload.trim()

        // 1. Caso payload cifrado con PIN
        val effectivePayload = if (trimmed.startsWith(TransferCrypto.QR_TRANSFER_PREFIX, ignoreCase = true)) {
            if (pin == null) {
                return Result.failure(TransferCrypto.InvalidPinException("Se requiere PIN para descifrar esta transferencia"))
            }
            val decryptResult = TransferCrypto.decryptTransferPayload(trimmed, pin)
            if (decryptResult.isFailure) {
                return Result.failure(decryptResult.exceptionOrNull() ?: TransferCrypto.InvalidPinException())
            }
            decryptResult.getOrThrow()
        } else {
            trimmed
        }

        val effectiveTrimmed = effectivePayload.trim()

        // 2. Caso URI individual estándar otpauth://
        if (effectiveTrimmed.startsWith("otpauth://", ignoreCase = true)) {
            val parseResult = OtpUriParser.parse(effectiveTrimmed)
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

        // 3. Caso JSON estructurado multi-cuenta (formato compacto o legado)
        return runCatching {
            val root = JSONObject(effectiveTrimmed)
            val accountsArray = when {
                root.has("a") -> root.getJSONArray("a")
                root.has("accounts") -> root.getJSONArray("accounts")
                else -> throw IllegalArgumentException("Estructura de cuentas no válida")
            }

            var count = 0
            for (i in 0 until accountsArray.length()) {
                val item = accountsArray.getJSONObject(i)
                val rawSecret = when {
                    item.has("s") -> item.getString("s")
                    item.has("secret") -> item.getString("secret")
                    else -> throw IllegalArgumentException("Falta clave secreta")
                }
                val secretBytes = Base32.decode(Base32.sanitize(rawSecret))

                val issuer = when {
                    item.has("i") -> item.getString("i")
                    item.has("issuer") -> item.getString("issuer")
                    else -> "Cuenta"
                }

                val accountName = when {
                    item.has("a") -> item.getString("a")
                    item.has("accountName") -> item.getString("accountName")
                    else -> ""
                }

                val algorithmStr = when {
                    item.has("alg") -> item.getString("alg")
                    item.has("algorithm") -> item.getString("algorithm")
                    else -> "SHA1"
                }

                val digits = when {
                    item.has("d") -> item.getInt("d")
                    item.has("digits") -> item.getInt("digits")
                    else -> 6
                }

                val period = when {
                    item.has("p") -> item.getInt("p")
                    item.has("period") -> item.getInt("period")
                    else -> 30
                }

                val typeStr = when {
                    item.has("t") -> item.getString("t")
                    item.has("type") -> item.getString("type")
                    else -> "TOTP"
                }

                val counter = when {
                    item.has("c") -> item.getLong("c")
                    item.has("counter") -> item.getLong("counter")
                    else -> 0L
                }

                try {
                    saveAccount(
                        issuer = issuer,
                        accountName = accountName,
                        secretBytes = secretBytes,
                        algorithm = OtpAlgorithm.fromString(algorithmStr),
                        digits = digits,
                        period = period,
                        type = OtpType.fromString(typeStr),
                        counter = counter
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
     * Fusiona de forma no destructiva las cuentas provenientes de una copia remota de Google Drive con la base de datos local.
     */
    override suspend fun mergeAccountsFromRemote(remoteBackupJson: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = remoteBackupJson.trim()
            if (trimmed.isBlank()) return@runCatching 0

            val root = JSONObject(trimmed)
            if (!root.has("accounts")) return@runCatching 0
            val accountsArray = root.getJSONArray("accounts")
            if (accountsArray.length() == 0) return@runCatching 0

            val currentEntities = accountDao.getAllAccountsSync()
            val localById = currentEntities.associateBy { it.id }
            val localByCompositeKey = currentEntities.associateBy {
                "${it.issuer.lowercase().trim()}:${it.accountName.lowercase().trim()}"
            }

            var changesCount = 0

            for (i in 0 until accountsArray.length()) {
                val item = accountsArray.getJSONObject(i)
                val remoteId = item.optString("id", "")
                val remoteIssuer = item.optString("issuer", "Cuenta").trim()
                val remoteAccountName = item.optString("accountName", "Usuario").trim()
                val remoteUpdatedAt = item.optLong("updatedAt", 0L)
                val rawSecret = item.getString("secret")
                val remoteAlgorithm = OtpAlgorithm.fromString(item.optString("algorithm", "SHA1"))
                val remoteDigits = item.optInt("digits", 6)
                val remotePeriod = item.optInt("period", 30)
                val remoteType = OtpType.fromString(item.optString("type", "TOTP"))
                val remoteCounter = item.optLong("counter", 0L)

                val compositeKey = "${remoteIssuer.lowercase()}:${remoteAccountName.lowercase()}"
                val existingEntity = (if (remoteId.isNotBlank()) localById[remoteId] else null) ?: localByCompositeKey[compositeKey]

                if (existingEntity == null) {
                    val secretBytes = Base32.decode(Base32.sanitize(rawSecret))
                    try {
                        val payload = cryptoManager.encrypt(secretBytes)
                        val newId = if (remoteId.isNotBlank()) remoteId else UUID.randomUUID().toString()
                        val now = if (remoteUpdatedAt > 0L) remoteUpdatedAt else System.currentTimeMillis()
                        val newEntity = AccountEntity(
                            id = newId,
                            issuer = remoteIssuer,
                            accountName = remoteAccountName,
                            encryptedSecret = payload.ciphertext,
                            iv = payload.iv,
                            algorithm = remoteAlgorithm.name,
                            digits = remoteDigits,
                            period = remotePeriod,
                            type = remoteType.name,
                            counter = remoteCounter,
                            createdAt = now,
                            updatedAt = now
                        )
                        accountDao.insertAccount(newEntity)
                        changesCount++
                    } finally {
                        CryptoManager.zeroize(secretBytes)
                    }
                } else {
                    if (remoteUpdatedAt > existingEntity.updatedAt) {
                        val secretBytes = Base32.decode(Base32.sanitize(rawSecret))
                        try {
                            val payload = cryptoManager.encrypt(secretBytes)
                            val updatedEntity = existingEntity.copy(
                                issuer = remoteIssuer,
                                accountName = remoteAccountName,
                                encryptedSecret = payload.ciphertext,
                                iv = payload.iv,
                                algorithm = remoteAlgorithm.name,
                                digits = remoteDigits,
                                period = remotePeriod,
                                type = remoteType.name,
                                counter = remoteCounter,
                                updatedAt = remoteUpdatedAt
                            )
                            accountDao.updateAccount(updatedEntity)
                            otpCodeCache.remove(existingEntity.id)
                            changesCount++
                        } finally {
                            CryptoManager.zeroize(secretBytes)
                        }
                    }
                }
            }

            changesCount
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
            isDeleted = isDeleted,
            deletedAt = deletedAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
