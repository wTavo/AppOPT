package com.example.appopt.data.repository

import com.example.appopt.data.local.AccountDao
import com.example.appopt.data.local.AccountEntity
import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.domain.totp.Base32
import com.example.appopt.security.CryptoManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.security.TransferCrypto
import org.json.JSONArray
import org.json.JSONObject

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
        val targetBatches = maxOf(SecurityConfig.TRANSFER_QR_MIN_BATCH_COUNT, (entities.size + SecurityConfig.TRANSFER_QR_BATCH_SIZE - 1) / SecurityConfig.TRANSFER_QR_BATCH_SIZE)
        val durationSeconds = SecurityConfig.calculateTransferExpirationSeconds(targetBatches)
        return TransferCrypto.encryptTransferPayloadInChunks(
            accountsJson = plainJson,
            pin = pin,
            durationSeconds = durationSeconds,
            targetChunkCount = targetBatches
        )
    }


    /**
     * Fusiona de forma no destructiva las cuentas provenientes de una copia remota de Google Drive con la base de datos local.
     * Delega la ejecución en [AccountMergeEngine].
     */
    suspend fun mergeRemoteBackup(
        remoteBackupJson: String,
        accountDao: AccountDao,
        cryptoManager: CryptoManager,
        onInvalidateCache: (String) -> Unit
    ): Result<Int> = AccountMergeEngine.mergeRemoteBackup(
        remoteBackupJson,
        accountDao,
        cryptoManager,
        onInvalidateCache
    )

    /**
     * Guarda o fusiona un servicio individual verificando si ya existe en la base de datos por hash de clave secreta.
     * Delega la ejecución en [AccountMergeEngine].
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
    ): Int = AccountMergeEngine.mergeSingleAccount(
        issuer,
        accountName,
        secretBytes,
        algorithm,
        digits,
        period,
        type,
        counter,
        accountDao,
        cryptoManager,
        onInvalidateCache
    )

    /**
     * Parsea un payload JSON estructurado para previsualizar las cuentas antes de su importación.
     * Delega la ejecución en [AccountMergeEngine].
     */
    suspend fun parseAccountsForPreview(
        jsonString: String,
        accountDao: AccountDao,
        cryptoManager: CryptoManager
    ): List<ParsedAccountPreview> = AccountMergeEngine.parseAccountsForPreview(
        jsonString,
        accountDao,
        cryptoManager
    )
}
