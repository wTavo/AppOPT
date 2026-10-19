package com.example.appopt.data.repository

import com.example.appopt.data.local.AccountDao
import com.example.appopt.data.local.AccountEntity
import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.domain.totp.Base32
import com.example.appopt.security.CryptoManager
import com.example.appopt.security.sha256Hex
import org.json.JSONObject
import java.util.UUID

/**
 * Motor desacoplado para la resolución de conflictos, deduplicación por hash SHA-256 en lote ($O(M)$)
 * y fusión determinística de cuentas 2FA en la base de datos local.
 *
 * Principios de diseño:
 * - Aislamiento estricto de la lógica de fusión respecto a la serialización JSON (Directivas 8 y 9).
 * - Pre-cálculo por lotes de huellas SHA-256 locales para evitar operaciones cuadráticas $O(N \times M)$ sobre el Android Keystore.
 * - Zeroización inmediata de claves Base32 y arreglos de bytes en memoria tras su uso.
 */
object AccountMergeEngine {

    /**
     * Estructura intermedia unificada para candidatos de fusión en lote.
     */
    private class MergeCandidate(
        val id: String,
        val secretBytes: ByteArray,
        val issuer: String,
        val accountName: String,
        val algorithm: OtpAlgorithm,
        val digits: Int,
        val period: Int,
        val type: OtpType,
        val counter: Long,
        val isFavorite: Boolean,
        val orderIndex: Int,
        val updatedAt: Long
    )

    /**
     * Parsea un payload JSON (formato estándar "accounts" o compacto "a") a una lista de [MergeCandidate].
     */
    private fun parseJsonItems(jsonString: String): List<MergeCandidate> {
        val trimmed = jsonString.trim()
        if (trimmed.isBlank()) return emptyList()

        val root = try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            return emptyList()
        }

        val accountsArray = when {
            root.has("accounts") -> root.getJSONArray("accounts")
            root.has("a") -> root.getJSONArray("a")
            else -> return emptyList()
        }

        val result = mutableListOf<MergeCandidate>()
        for (i in 0 until accountsArray.length()) {
            val item = accountsArray.getJSONObject(i)
            val rawSecret = when {
                item.has("secret") -> item.getString("secret")
                item.has("s") -> item.getString("s")
                else -> continue
            }
            val secretBytes = try {
                Base32.decode(Base32.sanitize(rawSecret))
            } catch (_: Exception) {
                continue
            }

            val remoteId = item.optString("id", "").trim()
            val remoteIssuer = (if (item.has("issuer")) item.optString("issuer", "Cuenta") else item.optString("i", "Cuenta"))
                .trim().ifBlank { "Cuenta" }
            val remoteAccountName = (if (item.has("accountName")) item.optString("accountName", "") else item.optString("a", "")).trim()
            val remoteAlgorithm = if (item.has("algorithm")) item.optString("algorithm", "SHA1") else item.optString("alg", "SHA1")
            val remoteDigits = if (item.has("digits")) item.optInt("digits", 6) else item.optInt("d", 6)
            val remotePeriod = if (item.has("period")) item.optInt("period", 30) else item.optInt("p", 30)
            val remoteType = if (item.has("type")) item.optString("type", "TOTP") else item.optString("t", "TOTP")
            val remoteCounter = if (item.has("counter")) item.optLong("counter", 0L) else item.optLong("c", 0L)

            result.add(
                MergeCandidate(
                    id = remoteId,
                    secretBytes = secretBytes,
                    issuer = remoteIssuer,
                    accountName = remoteAccountName,
                    algorithm = OtpAlgorithm.fromString(remoteAlgorithm),
                    digits = remoteDigits,
                    period = remotePeriod,
                    type = OtpType.fromString(remoteType),
                    counter = remoteCounter,
                    isFavorite = item.optBoolean("isFavorite", false),
                    orderIndex = item.optInt("orderIndex", 0),
                    updatedAt = item.optLong("updatedAt", 0L)
                )
            )
        }
        return result
    }

    /**
     * Construye en una sola pasada ($O(M)$) el índice de entidades locales por huella SHA-256 de su secreto.
     */
    private fun buildLocalSecretHashMap(
        entities: List<AccountEntity>,
        cryptoManager: CryptoManager
    ): MutableMap<String, AccountEntity> {
        val localBySecretHash = mutableMapOf<String, AccountEntity>()
        for (entity in entities) {
            try {
                val secretBytes = cryptoManager.decrypt(entity.encryptedSecret, entity.iv)
                val hash = secretBytes.sha256Hex()
                CryptoManager.zeroize(secretBytes)
                localBySecretHash[hash] = entity
            } catch (_: Exception) {
                // Ignorar entidades corruptas aisladas
            }
        }
        return localBySecretHash
    }

    /**
     * Ejecuta la fusión en lote de una lista de candidatos contra la base de datos local precalculando
     * los hashes de secretos existentes una única vez ($O(M)$) y limpiando todos los secretos en memoria al finalizar.
     */
    private suspend fun executeBatchMerge(
        candidates: List<MergeCandidate>,
        accountDao: AccountDao,
        cryptoManager: CryptoManager,
        onInvalidateCache: (String) -> Unit,
        matchById: Boolean
    ): Int {
        if (candidates.isEmpty()) return 0

        val currentEntities = accountDao.getAllAccountsSync()
        val localById = currentEntities.associateBy { it.id }.toMutableMap()
        val localBySecretHash = buildLocalSecretHashMap(currentEntities, cryptoManager)
        var changesCount = 0

        for (candidate in candidates) {
            try {
                val remoteHash = candidate.secretBytes.sha256Hex()
                val existingEntity = (if (matchById && candidate.id.isNotBlank()) localById[candidate.id] else null)
                    ?: localBySecretHash[remoteHash]

                val trimmedIssuer = candidate.issuer.trim().ifBlank { "Servicio" }
                val trimmedAccountName = candidate.accountName.trim()
                val effectiveTime = if (candidate.updatedAt > 0L) candidate.updatedAt else System.currentTimeMillis()

                if (existingEntity == null) {
                    val payload = cryptoManager.encrypt(candidate.secretBytes)
                    val newId = candidate.id.ifBlank { UUID.randomUUID().toString() }
                    val newEntity = AccountEntity(
                        id = newId,
                        issuer = trimmedIssuer,
                        accountName = trimmedAccountName,
                        encryptedSecret = payload.ciphertext,
                        iv = payload.iv,
                        algorithm = candidate.algorithm.name,
                        digits = candidate.digits,
                        period = candidate.period,
                        type = candidate.type.name,
                        counter = candidate.counter,
                        isFavorite = candidate.isFavorite,
                        orderIndex = candidate.orderIndex,
                        isDeleted = false,
                        deletedAt = null,
                        createdAt = effectiveTime,
                        updatedAt = effectiveTime
                    )
                    accountDao.insertAccount(newEntity)
                    localById[newId] = newEntity
                    localBySecretHash[remoteHash] = newEntity
                    changesCount++
                } else if (existingEntity.isDeleted ||
                    (candidate.updatedAt > existingEntity.updatedAt) ||
                    existingEntity.issuer != trimmedIssuer ||
                    existingEntity.accountName != trimmedAccountName ||
                    existingEntity.digits != candidate.digits ||
                    existingEntity.period != candidate.period ||
                    existingEntity.algorithm != candidate.algorithm.name ||
                    existingEntity.type != candidate.type.name ||
                    candidate.counter > existingEntity.counter
                ) {
                    val payload = cryptoManager.encrypt(candidate.secretBytes)
                    val updatedEntity = existingEntity.copy(
                        issuer = trimmedIssuer,
                        accountName = trimmedAccountName,
                        encryptedSecret = payload.ciphertext,
                        iv = payload.iv,
                        algorithm = candidate.algorithm.name,
                        digits = candidate.digits,
                        period = candidate.period,
                        type = candidate.type.name,
                        counter = maxOf(existingEntity.counter, candidate.counter),
                        isDeleted = false,
                        deletedAt = null,
                        updatedAt = effectiveTime
                    )
                    accountDao.updateAccount(updatedEntity)
                    localById[existingEntity.id] = updatedEntity
                    localBySecretHash[remoteHash] = updatedEntity
                    onInvalidateCache(existingEntity.id)
                    changesCount++
                }
            } finally {
                CryptoManager.zeroize(candidate.secretBytes)
            }
        }
        return changesCount
    }

    /**
     * Fusiona de forma no destructiva las cuentas provenientes de una copia remota de Google Drive con la base de datos local.
     */
    suspend fun mergeRemoteBackup(
        remoteBackupJson: String,
        accountDao: AccountDao,
        cryptoManager: CryptoManager,
        onInvalidateCache: (String) -> Unit
    ): Result<Int> = runCatching {
        val candidates = parseJsonItems(remoteBackupJson)
        executeBatchMerge(
            candidates = candidates,
            accountDao = accountDao,
            cryptoManager = cryptoManager,
            onInvalidateCache = onInvalidateCache,
            matchById = true
        )
    }

    /**
     * Fusiona en un solo lote ($O(M)$) una lista de cuentas seleccionadas desde la previsualización,
     * sobrescribiendo con ceros todos los secretos en memoria al finalizar.
     */
    suspend fun mergeSelectedAccounts(
        accounts: List<ParsedAccountPreview>,
        accountDao: AccountDao,
        cryptoManager: CryptoManager,
        onInvalidateCache: (String) -> Unit
    ): Int {
        val candidates = accounts.map { preview ->
            MergeCandidate(
                id = preview.id,
                secretBytes = preview.secretBytes,
                issuer = preview.issuer,
                accountName = preview.accountName,
                algorithm = preview.algorithm,
                digits = preview.digits,
                period = preview.period,
                type = preview.type,
                counter = preview.counter,
                isFavorite = preview.isFavorite,
                orderIndex = 0,
                updatedAt = 0L
            )
        }
        return executeBatchMerge(
            candidates = candidates,
            accountDao = accountDao,
            cryptoManager = cryptoManager,
            onInvalidateCache = onInvalidateCache,
            matchById = false
        )
    }

    /**
     * Guarda o fusiona un servicio individual verificando si ya existe en la base de datos por hash de clave secreta.
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
        onInvalidateCache: (String) -> Unit,
        preferredId: String = "",
        isFavorite: Boolean = false,
        orderIndex: Int = 0
    ): Int {
        val candidate = MergeCandidate(
            id = preferredId,
            secretBytes = secretBytes,
            issuer = issuer,
            accountName = accountName,
            algorithm = algorithm,
            digits = digits,
            period = period,
            type = type,
            counter = counter,
            isFavorite = isFavorite,
            orderIndex = orderIndex,
            updatedAt = 0L
        )
        return executeBatchMerge(
            candidates = listOf(candidate),
            accountDao = accountDao,
            cryptoManager = cryptoManager,
            onInvalidateCache = onInvalidateCache,
            matchById = false
        )
    }

    /**
     * Parsea un payload JSON estructurado para previsualizar las cuentas antes de su importación,
     * detectando si alguna de ellas ya existe previamente en la base de datos local.
     */
    suspend fun parseAccountsForPreview(
        jsonString: String,
        accountDao: AccountDao,
        cryptoManager: CryptoManager
    ): List<ParsedAccountPreview> {
        val parsedList = parseJsonItems(jsonString)
        if (parsedList.isEmpty()) return emptyList()

        val activeEntities = accountDao.getAllAccountsSync().filter { !it.isDeleted }
        val localHashes = buildLocalSecretHashMap(activeEntities, cryptoManager).keys

        return parsedList.map { item ->
            val incomingHash = item.secretBytes.sha256Hex()
            ParsedAccountPreview(
                id = item.id.ifBlank { UUID.randomUUID().toString() },
                issuer = item.issuer,
                accountName = item.accountName,
                algorithm = item.algorithm,
                digits = item.digits,
                period = item.period,
                type = item.type,
                counter = item.counter,
                isFavorite = item.isFavorite,
                isAlreadyInVault = incomingHash in localHashes,
                secretBytes = item.secretBytes
            )
        }
    }
}