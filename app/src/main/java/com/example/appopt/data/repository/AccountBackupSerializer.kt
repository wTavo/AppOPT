package com.example.appopt.data.repository

import com.example.appopt.data.local.AccountDao
import com.example.appopt.data.local.AccountEntity
import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.totp.Base32
import com.example.appopt.security.CryptoManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.security.TransferCrypto
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Serializador y deserializador seguro para operaciones de transferencia, exportación en lotes
 * y fusión de copias de seguridad de cuentas 2FA en la nube o local.
 *
 * Principios de diseño:
 * - Aislamiento de lógica de parsing JSON y sobre criptográfico (Directivas 8 y 9).
 * - Zeroización inmediata de claves Base32 y arreglos de bytes en memoria tras la serialización.
 * - Validación defensiva de esquemas y claves compuestas para prevención de duplicados.
 */
object AccountBackupSerializer {

    /**
     * Serializa una lista de entidades persistidas a formato JSON estándar para respaldo en Google Drive.
     *
     * @param entities Lista de entidades de cuenta en Room a serializar.
     * @param cryptoManager Gestor criptográfico para descifrar temporalmente los secretos antes del empaquetado.
     * @return Cadena JSON formateada con cabecera de versión y arreglo de cuentas.
     */
    fun serializeAccountsForBackup(
        entities: List<AccountEntity>,
        cryptoManager: CryptoManager
    ): String {
        val root = JSONObject().apply {
            put("version", SecurityConfig.CURRENT_BACKUP_VERSION)
            put("exportedAt", System.currentTimeMillis())
            val accountsArray = JSONArray()

            for (entity in entities) {
                var secretBytes: ByteArray? = null
                try {
                    secretBytes = cryptoManager.decrypt(entity.encryptedSecret, entity.iv)
                    val secretBase32 = Base32.encode(secretBytes)
                    val accountObj = JSONObject().apply {
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
                        put("updatedAt", entity.updatedAt)
                    }
                    accountsArray.put(accountObj)
                } finally {
                    secretBytes?.let { CryptoManager.zeroize(it) }
                }
            }
            put("accounts", accountsArray)
        }
        return root.toString()
    }

    /**
     * Serializa una lista de cuentas para transferencia y opcionalmente las cifra con PIN.
     *
     * @param entities Lista de entidades de cuenta en Room a transferir.
     * @param cryptoManager Gestor criptográfico para descifrar temporalmente los secretos en memoria.
     * @param pin PIN opcional para cifrar el payload con AES-256-GCM.
     * @return Cadena serializada (plana o cifrada con prefijo [TransferCrypto.QR_TRANSFER_PREFIX]).
     */
    fun serializeAccountsForTransfer(
        entities: List<AccountEntity>,
        cryptoManager: CryptoManager,
        pin: CharArray?
    ): String {
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
     * Serializa y cifra una lista de entidades en fragmentos multi-QR bajo el protocolo "Todo o Nada" (v2).
     *
     * @param entities Lista de entidades a exportar.
     * @param cryptoManager Gestor criptográfico para descifrar secretos en RAM.
     * @param pin Clave de autenticación en [CharArray] para el derivador PBKDF2/AES-GCM.
     * @return Lista de fragmentos cifrados formateados con prefijo URI.
     */
    fun serializeAndEncryptChunks(
        entities: List<AccountEntity>,
        cryptoManager: CryptoManager,
        pin: CharArray
    ): List<String> {
        if (entities.isEmpty()) return emptyList()

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
        val targetBatches = maxOf(1, (entities.size + SecurityConfig.TRANSFER_QR_BATCH_SIZE - 1) / SecurityConfig.TRANSFER_QR_BATCH_SIZE)
        val durationSeconds = SecurityConfig.calculateTransferExpirationSeconds(targetBatches)
        return TransferCrypto.encryptTransferPayloadInChunks(
            accountsJson = plainJson,
            pin = pin,
            durationSeconds = durationSeconds,
            targetChunkCount = targetBatches
        )
    }

    /**
     * Parsea un payload JSON estructurado multi-cuenta e inserta cada cuenta mediante el callback [onSaveAccount].
     *
     * @param jsonString Cadena JSON con arreglo "a" o "accounts".
     * @param onSaveAccount Función de guardado atómico persistente.
     * @return [Result] con el conteo de cuentas guardadas exitosamente.
     */
    suspend fun parseAndSaveAccountsJson(
        jsonString: String,
        onSaveAccount: suspend (
            issuer: String,
            accountName: String,
            secretBytes: ByteArray,
            algorithm: OtpAlgorithm,
            digits: Int,
            period: Int,
            type: OtpType,
            counter: Long
        ) -> Unit
    ): Result<Int> {
        return runCatching {
            val root = JSONObject(jsonString)
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
                    onSaveAccount(
                        issuer,
                        accountName,
                        secretBytes,
                        OtpAlgorithm.fromString(algorithmStr),
                        digits,
                        period,
                        OtpType.fromString(typeStr),
                        counter
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
     *
     * @param remoteBackupJson Cadena JSON del archivo de respaldo remoto.
     * @param accountDao DAO de Room para operaciones locales.
     * @param cryptoManager Gestor criptográfico para cifrado con AES-256-GCM.
     * @param onInvalidateCache Callback opcional para limpiar entradas modificadas de la caché en memoria.
     * @return [Result] con el número de cuentas insertadas o actualizadas.
     */
    suspend fun mergeRemoteBackup(
        remoteBackupJson: String,
        accountDao: AccountDao,
        cryptoManager: CryptoManager,
        onInvalidateCache: (String) -> Unit
    ): Result<Int> {
        return runCatching {
            val trimmed = remoteBackupJson.trim()
            if (trimmed.isBlank()) return@runCatching 0

            val root = JSONObject(trimmed)
            val accountsArray = when {
                root.has("accounts") -> root.getJSONArray("accounts")
                root.has("a") -> root.getJSONArray("a")
                else -> return@runCatching 0
            }
            if (accountsArray.length() == 0) return@runCatching 0

            val currentEntities = accountDao.getAllAccountsSync()
            val localById = currentEntities.associateBy { it.id }.toMutableMap()
            val localBySecretHash = mutableMapOf<String, AccountEntity>()
            val sha256 = MessageDigest.getInstance("SHA-256")

            for (entity in currentEntities) {
                try {
                    val secretBytes = cryptoManager.decrypt(entity.encryptedSecret, entity.iv)
                    val hash = sha256.digest(secretBytes).joinToString("") { "%02x".format(it) }
                    CryptoManager.zeroize(secretBytes)
                    localBySecretHash[hash] = entity
                } catch (_: Exception) {
                    // Ignorar fallas aisladas en entidades corruptas
                }
            }

            var changesCount = 0

            for (i in 0 until accountsArray.length()) {
                val item = accountsArray.getJSONObject(i)
                val remoteId = when {
                    item.has("id") -> item.optString("id", "")
                    else -> ""
                }.trim()

                val rawSecret = when {
                    item.has("secret") -> item.getString("secret")
                    item.has("s") -> item.getString("s")
                    else -> continue
                }

                val remoteIssuer = when {
                    item.has("issuer") -> item.optString("issuer", "Cuenta")
                    item.has("i") -> item.optString("i", "Cuenta")
                    else -> "Cuenta"
                }.trim()

                val remoteAccountName = when {
                    item.has("accountName") -> item.optString("accountName", "")
                    item.has("a") -> item.optString("a", "")
                    else -> ""
                }.trim()

                val remoteAlgorithm = when {
                    item.has("algorithm") -> item.optString("algorithm", "SHA1")
                    item.has("alg") -> item.optString("alg", "SHA1")
                    else -> "SHA1"
                }

                val remoteDigits = when {
                    item.has("digits") -> item.optInt("digits", 6)
                    item.has("d") -> item.optInt("d", 6)
                    else -> 6
                }

                val remotePeriod = when {
                    item.has("period") -> item.optInt("period", 30)
                    item.has("p") -> item.optInt("p", 30)
                    else -> 30
                }

                val remoteType = when {
                    item.has("type") -> item.optString("type", "TOTP")
                    item.has("t") -> item.optString("t", "TOTP")
                    else -> "TOTP"
                }

                val remoteCounter = when {
                    item.has("counter") -> item.optLong("counter", 0L)
                    item.has("c") -> item.optLong("c", 0L)
                    else -> 0L
                }

                val remoteIsFavorite = item.optBoolean("isFavorite", false)
                val remoteOrderIndex = item.optInt("orderIndex", 0)
                val remoteUpdatedAt = item.optLong("updatedAt", 0L)

                val sanitizedSecret = Base32.sanitize(rawSecret)
                val secretBytes = Base32.decode(sanitizedSecret)
                try {
                    val remoteHash = sha256.digest(secretBytes).joinToString("") { "%02x".format(it) }
                    val existingEntity = (if (remoteId.isNotBlank()) localById[remoteId] else null) ?: localBySecretHash[remoteHash]

                    if (existingEntity == null) {
                        val payload = cryptoManager.encrypt(secretBytes)
                        val newId = if (remoteId.isNotBlank()) remoteId else UUID.randomUUID().toString()
                        val now = if (remoteUpdatedAt > 0L) remoteUpdatedAt else System.currentTimeMillis()
                        val newEntity = AccountEntity(
                            id = newId,
                            issuer = remoteIssuer,
                            accountName = remoteAccountName,
                            encryptedSecret = payload.ciphertext,
                            iv = payload.iv,
                            algorithm = OtpAlgorithm.fromString(remoteAlgorithm).name,
                            digits = remoteDigits,
                            period = remotePeriod,
                            type = OtpType.fromString(remoteType).name,
                            counter = remoteCounter,
                            isFavorite = remoteIsFavorite,
                            orderIndex = remoteOrderIndex,
                            isDeleted = false,
                            deletedAt = null,
                            createdAt = now,
                            updatedAt = now
                        )
                        accountDao.insertAccount(newEntity)
                        localById[newId] = newEntity
                        localBySecretHash[remoteHash] = newEntity
                        changesCount++
                    } else {
                        if (existingEntity.isDeleted) {
                            val payload = cryptoManager.encrypt(secretBytes)
                            val restoredEntity = existingEntity.copy(
                                issuer = remoteIssuer,
                                accountName = remoteAccountName,
                                encryptedSecret = payload.ciphertext,
                                iv = payload.iv,
                                algorithm = OtpAlgorithm.fromString(remoteAlgorithm).name,
                                digits = remoteDigits,
                                period = remotePeriod,
                                type = OtpType.fromString(remoteType).name,
                                counter = remoteCounter,
                                isDeleted = false,
                                deletedAt = null,
                                updatedAt = if (remoteUpdatedAt > 0L) remoteUpdatedAt else System.currentTimeMillis()
                            )
                            accountDao.updateAccount(restoredEntity)
                            localById[existingEntity.id] = restoredEntity
                            localBySecretHash[remoteHash] = restoredEntity
                            onInvalidateCache(existingEntity.id)
                            changesCount++
                        } else if (remoteUpdatedAt > existingEntity.updatedAt ||
                            remoteIssuer != existingEntity.issuer ||
                            remoteAccountName != existingEntity.accountName ||
                            remoteCounter > existingEntity.counter
                        ) {
                            val payload = cryptoManager.encrypt(secretBytes)
                            val updatedEntity = existingEntity.copy(
                                issuer = remoteIssuer,
                                accountName = remoteAccountName,
                                encryptedSecret = payload.ciphertext,
                                iv = payload.iv,
                                algorithm = OtpAlgorithm.fromString(remoteAlgorithm).name,
                                digits = remoteDigits,
                                period = remotePeriod,
                                type = OtpType.fromString(remoteType).name,
                                counter = remoteCounter,
                                updatedAt = if (remoteUpdatedAt > 0L) remoteUpdatedAt else System.currentTimeMillis()
                            )
                            accountDao.updateAccount(updatedEntity)
                            localById[existingEntity.id] = updatedEntity
                            localBySecretHash[remoteHash] = updatedEntity
                            onInvalidateCache(existingEntity.id)
                            changesCount++
                        }
                    }
                } finally {
                    CryptoManager.zeroize(secretBytes)
                }
            }

            if (changesCount > 0) {
                com.example.appopt.data.cloud.CloudVaultSyncManager.triggerReactiveSync(com.example.appopt.AuthenticatorApp.instance)
            }

            changesCount
        }
    }

    /**
     * Guarda o fusiona un servicio individual verificando si ya existe en la base de datos por hash de clave secreta.
     *
     * @param issuer Nombre del servicio o emisor.
     * @param accountName Nombre de usuario o cuenta.
     * @param secretBytes Clave secreta en bytes.
     * @param algorithm Algoritmo HMAC (SHA1, SHA256, SHA512).
     * @param digits Cantidad de dígitos generados.
     * @param period Período en segundos para TOTP.
     * @param type Tipo de OTP (TOTP / HOTP).
     * @param counter Contador para HOTP.
     * @param accountDao DAO de Room para operaciones locales.
     * @param cryptoManager Gestor criptográfico para cifrado AES-256-GCM.
     * @param onInvalidateCache Callback opcional para invalidar la caché en memoria.
     * @return 1 si se insertó, actualizó o restauró el servicio; 0 si ya existía de forma idéntica.
     */
    suspend fun mergeSingleAccount(
        issuer: String,
        accountName: String,
        secretBytes: ByteArray,
        algorithm: OtpAlgorithm,
        digits: Int,
        period: Int,
        type: OtpType,
        counter: Long,
        accountDao: AccountDao,
        cryptoManager: CryptoManager,
        onInvalidateCache: (String) -> Unit
    ): Int {
        val currentEntities = accountDao.getAllAccountsSync()
        val sha256 = MessageDigest.getInstance("SHA-256")
        val incomingHash = sha256.digest(secretBytes).joinToString("") { "%02x".format(it) }

        var existingEntity: AccountEntity? = null
        for (entity in currentEntities) {
            try {
                val decBytes = cryptoManager.decrypt(entity.encryptedSecret, entity.iv)
                val hash = sha256.digest(decBytes).joinToString("") { "%02x".format(it) }
                CryptoManager.zeroize(decBytes)
                if (hash == incomingHash) {
                    existingEntity = entity
                    break
                }
            } catch (_: Exception) {
                // Ignorar fallas aisladas en entidades corruptas
            }
        }

        val trimmedIssuer = issuer.trim().ifBlank { "Servicio" }
        val trimmedAccountName = accountName.trim()
        val now = System.currentTimeMillis()

        return if (existingEntity == null) {
            val payload = cryptoManager.encrypt(secretBytes)
            val newEntity = AccountEntity(
                id = UUID.randomUUID().toString(),
                issuer = trimmedIssuer,
                accountName = trimmedAccountName,
                encryptedSecret = payload.ciphertext,
                iv = payload.iv,
                algorithm = algorithm.name,
                digits = digits,
                period = period,
                type = type.name,
                counter = counter,
                isFavorite = false,
                orderIndex = 0,
                isDeleted = false,
                deletedAt = null,
                createdAt = now,
                updatedAt = now
            )
            accountDao.insertAccount(newEntity)
            com.example.appopt.data.cloud.CloudVaultSyncManager.triggerReactiveSync(com.example.appopt.AuthenticatorApp.instance)
            1
        } else if (existingEntity.isDeleted) {
            val payload = cryptoManager.encrypt(secretBytes)
            val restoredEntity = existingEntity.copy(
                issuer = trimmedIssuer,
                accountName = trimmedAccountName,
                encryptedSecret = payload.ciphertext,
                iv = payload.iv,
                algorithm = algorithm.name,
                digits = digits,
                period = period,
                type = type.name,
                counter = counter,
                isDeleted = false,
                deletedAt = null,
                updatedAt = now
            )
            accountDao.updateAccount(restoredEntity)
            onInvalidateCache(existingEntity.id)
            com.example.appopt.data.cloud.CloudVaultSyncManager.triggerReactiveSync(com.example.appopt.AuthenticatorApp.instance)
            1
        } else if (existingEntity.issuer != trimmedIssuer ||
            existingEntity.accountName != trimmedAccountName ||
            existingEntity.digits != digits ||
            existingEntity.period != period ||
            existingEntity.algorithm != algorithm.name ||
            existingEntity.type != type.name ||
            existingEntity.counter != counter
        ) {
            val payload = cryptoManager.encrypt(secretBytes)
            val updatedEntity = existingEntity.copy(
                issuer = trimmedIssuer,
                accountName = trimmedAccountName,
                encryptedSecret = payload.ciphertext,
                iv = payload.iv,
                algorithm = algorithm.name,
                digits = digits,
                period = period,
                type = type.name,
                counter = counter,
                updatedAt = now
            )
            accountDao.updateAccount(updatedEntity)
            onInvalidateCache(existingEntity.id)
            com.example.appopt.data.cloud.CloudVaultSyncManager.triggerReactiveSync(com.example.appopt.AuthenticatorApp.instance)
            1
        } else {
            0
        }
    }
}
