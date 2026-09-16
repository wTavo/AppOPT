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
 * Motor desacoplado para la resolución de conflictos, deduplicación por hash SHA-256
 * y fusión determinística de cuentas 2FA en la base de datos local.
 *
 * Principios de diseño:
 * - Aislamiento estricto de la lógica de fusión respecto a la serialización JSON (Directivas 8 y 9).
 * - Zeroización inmediata de claves Base32 y arreglos de bytes en memoria tras su uso.
 * - Soporte para resolución de conflictos basada en marcas de tiempo (`updatedAt`) y deduplicación inteligente.
 */
object AccountMergeEngine {

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

            for (entity in currentEntities) {
                try {
                    val secretBytes = cryptoManager.decrypt(entity.encryptedSecret, entity.iv)
                    val hash = secretBytes.sha256Hex()
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
                    val remoteHash = secretBytes.sha256Hex()
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
                                counter = maxOf(existingEntity.counter, remoteCounter),
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
                                counter = maxOf(existingEntity.counter, remoteCounter),
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
        val incomingHash = secretBytes.sha256Hex()

        var existingEntity: AccountEntity? = null
        for (entity in currentEntities) {
            try {
                val decBytes = cryptoManager.decrypt(entity.encryptedSecret, entity.iv)
                val hash = decBytes.sha256Hex()
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
                counter = maxOf(existingEntity.counter, counter),
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
            counter > existingEntity.counter
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
                counter = maxOf(existingEntity.counter, counter),
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

    /**
     * Parsea un payload JSON estructurado para previsualizar las cuentas antes de su importación,
     * detectando si alguna de ellas ya existe previamente en la base de datos local.
     *
     * @param jsonString Cadena JSON con arreglo de cuentas ("a" o "accounts").
     * @param accountDao DAO de Room para consultar las cuentas existentes.
     * @param cryptoManager Gestor criptográfico para calcular hashes de secretos locales.
     * @return Lista de modelos [ParsedAccountPreview].
     */
    suspend fun parseAccountsForPreview(
        jsonString: String,
        accountDao: AccountDao,
        cryptoManager: CryptoManager
    ): List<ParsedAccountPreview> {
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

        val currentEntities = accountDao.getAllAccountsSync()
        val localHashes = mutableSetOf<String>()

        for (entity in currentEntities) {
            if (!entity.isDeleted) {
                try {
                    val decBytes = cryptoManager.decrypt(entity.encryptedSecret, entity.iv)
                    val hash = decBytes.sha256Hex()
                    CryptoManager.zeroize(decBytes)
                    localHashes.add(hash)
                } catch (_: Exception) {
                    // Ignorar entidades corruptas aisladas
                }
            }
        }

        val list = mutableListOf<ParsedAccountPreview>()
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

            val incomingHash = secretBytes.sha256Hex()
            val isAlreadyInVault = incomingHash in localHashes

            val remoteId = when {
                item.has("id") -> item.optString("id", UUID.randomUUID().toString())
                else -> UUID.randomUUID().toString()
            }.ifBlank { UUID.randomUUID().toString() }

            val remoteIssuer = when {
                item.has("issuer") -> item.optString("issuer", "Cuenta")
                item.has("i") -> item.optString("i", "Cuenta")
                else -> "Cuenta"
            }.trim().ifBlank { "Cuenta" }

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

            list.add(
                ParsedAccountPreview(
                    id = remoteId,
                    issuer = remoteIssuer,
                    accountName = remoteAccountName,
                    algorithm = OtpAlgorithm.fromString(remoteAlgorithm),
                    digits = remoteDigits,
                    period = remotePeriod,
                    type = OtpType.fromString(remoteType),
                    counter = remoteCounter,
                    isFavorite = remoteIsFavorite,
                    isAlreadyInVault = isAlreadyInVault,
                    secretBytes = secretBytes
                )
            )
        }
        return list
    }
}