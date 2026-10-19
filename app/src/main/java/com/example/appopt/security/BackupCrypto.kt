package com.example.appopt.security

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gestor criptográfico para la creación y lectura de copias de seguridad cifradas (*Encrypted Backups*).
 *
 * Principio de diseño de seguridad (Versión 2 unificada con sobre criptográfico):
 * - Cifra el contenido del respaldo con una clave aleatoria simétrica [VaultKey] de 256 bits mediante **AES-256-GCM**.
 * - Envuelve la [VaultKey] en una ranura principal protegida por la contraseña del usuario ([mainSlot])
 *   y opcionalmente en una ranura de emergencia protegida por la frase mnemónica BIP-39 ([emergencySlot]).
 * - Deriva las claves de envoltura mediante **PBKDF2 con HMAC-SHA256** (100.000 iteraciones + Salt CSPRNG de 16 bytes).
 */
object BackupCrypto {

    private const val PBKDF2_ITERATIONS = SecurityConfig.PBKDF2_BACKUP_ITERATIONS
    private const val KEY_LENGTH_BITS = SecurityConfig.BACKUP_KEY_SIZE_BITS
    private const val SALT_LENGTH_BYTES = 16
    private const val CURRENT_BACKUP_VERSION = SecurityConfig.CURRENT_BACKUP_VERSION

    private val secureRandom = SecureRandom()

    /**
     * Datos serializados de una ranura de envoltura de clave (*Key Slot*).
     *
     * @property saltB64 Sal criptográfica aleatoria en Base64.
     * @property ivB64 Vector de inicialización AES-GCM en Base64.
     * @property wrappedKeyB64 [VaultKey] envuelta y cifrada en Base64.
     */
    data class WrappedSlot(
        val saltB64: String,
        val ivB64: String,
        val wrappedKeyB64: String
    ) {
        /** Convierte la ranura a objeto JSON estructurado. */
        fun toJsonObject(): JSONObject = JSONObject().apply {
            put("salt", saltB64)
            put("iv", ivB64)
            put("key", wrappedKeyB64)
        }

        companion object {
            /** Reconstruye una ranura desde un objeto JSON validando sus campos obligatorios. */
            fun fromJsonObject(json: JSONObject): WrappedSlot {
                val salt = json.optString("salt", "")
                val iv = json.optString("iv", "")
                val key = json.optString("key", "")
                require(salt.isNotBlank() && iv.isNotBlank() && key.isNotBlank()) {
                    "Formato de ranura criptográfica incompleto"
                }
                return WrappedSlot(saltB64 = salt, ivB64 = iv, wrappedKeyB64 = key)
            }
        }
    }

    /**
     * Resultado de una operación de cifrado de respaldo nuevo.
     *
     * @property envelopeBytes Bytes del sobre JSON cifrado versión 2 listo para subir a Google Drive.
     * @property vaultKey Clave simétrica de 256 bits generada en memoria (debe ser limpiada tras su uso).
     * @property mainSlot Ranura principal envuelta con la contraseña maestra.
     * @property emergencySlot Ranura secundaria envuelta con el mnemónico de emergencia opcional.
     */
    data class EncryptedBackupResult(
        val envelopeBytes: ByteArray,
        val vaultKey: ByteArray,
        val mainSlot: WrappedSlot,
        val emergencySlot: WrappedSlot?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncryptedBackupResult) return false
            return envelopeBytes.contentEquals(other.envelopeBytes) &&
                vaultKey.contentEquals(other.vaultKey) &&
                mainSlot == other.mainSlot &&
                emergencySlot == other.emergencySlot
        }

        override fun hashCode(): Int {
            var result = envelopeBytes.contentHashCode()
            result = 31 * result + vaultKey.contentHashCode()
            result = 31 * result + mainSlot.hashCode()
            result = 31 * result + emergencySlot.hashCode()
            return result
        }
    }

    /**
     * Contenido descifrado y componentes de sesión recuperados de un sobre de respaldo.
     *
     * @property plainJson Texto plano JSON con los datos de las cuentas.
     * @property vaultKey Clave de bóveda simétrica de 256 bits recuperada (debe ser custodiada o limpiada).
     * @property mainSlot Ranura principal presente en el sobre.
     * @property emergencySlot Ranura de emergencia presente en el sobre, si aplica.
     */
    data class DecryptedBackupPayload(
        val plainJson: String,
        val vaultKey: ByteArray,
        val mainSlot: WrappedSlot,
        val emergencySlot: WrappedSlot?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DecryptedBackupPayload) return false
            return plainJson == other.plainJson &&
                vaultKey.contentEquals(other.vaultKey) &&
                mainSlot == other.mainSlot &&
                emergencySlot == other.emergencySlot
        }

        override fun hashCode(): Int {
            var result = plainJson.hashCode()
            result = 31 * result + vaultKey.contentHashCode()
            result = 31 * result + mainSlot.hashCode()
            result = 31 * result + emergencySlot.hashCode()
            return result
        }
    }

    /**
     * Cifra una cadena JSON generando un sobre versión 2 con la contraseña principal y mnemónico opcional.
     *
     * @param plainJson Texto JSON con los datos de las cuentas a exportar.
     * @param primaryPassword Contraseña maestra o clave de 64 dígitos en [CharArray].
     * @param emergencyMnemonic Frase mnemónica de 12 palabras normalizada opcional en [CharArray].
     * @return [EncryptedBackupResult] con el sobre binario v2 y los componentes de sesión.
     */
    fun encryptBackupResult(
        plainJson: String,
        primaryPassword: CharArray,
        emergencyMnemonic: CharArray? = null
    ): EncryptedBackupResult {
        val vaultKeyBytes = ByteArray(KEY_LENGTH_BITS / 8)
        secureRandom.nextBytes(vaultKeyBytes)
        val mainSlot = wrapVaultKey(vaultKeyBytes, primaryPassword)
        val emergencySlot = if (emergencyMnemonic != null && emergencyMnemonic.isNotEmpty()) {
            wrapVaultKey(vaultKeyBytes, emergencyMnemonic)
        } else {
            null
        }
        val envelopeBytes = encryptWithExistingKey(plainJson, vaultKeyBytes, mainSlot, emergencySlot)
        return EncryptedBackupResult(envelopeBytes, vaultKeyBytes, mainSlot, emergencySlot)
    }

    /** Cifra una cadena JSON en formato de sobre v2 retornando el arreglo binario. */
    fun encryptBackup(plainJson: String, password: CharArray): ByteArray {
        val result = encryptBackupResult(plainJson, password, null)
        CryptoManager.zeroize(result.vaultKey)
        return result.envelopeBytes
    }

    /** Cifra una cadena JSON con un esquema de doble ranura v2 ([primaryPassword] + [emergencyMnemonic]). */
    fun encryptDualBackup(
        plainJson: String,
        primaryPassword: CharArray,
        emergencyMnemonic: CharArray
    ): ByteArray {
        val result = encryptBackupResult(plainJson, primaryPassword, emergencyMnemonic)
        CryptoManager.zeroize(result.vaultKey)
        return result.envelopeBytes
    }

    /** Cifra un nuevo respaldo reutilizando una [vaultKey] custodiada y preservando las ranuras existentes. */
    fun encryptWithExistingKey(
        plainJson: String,
        vaultKey: ByteArray,
        mainSlot: WrappedSlot,
        emergencySlot: WrappedSlot? = null
    ): ByteArray {
        val vaultSecretKey = SecretKeySpec(vaultKey, "AES")
        val contentCipher = Cipher.getInstance("AES/GCM/NoPadding")
        contentCipher.init(Cipher.ENCRYPT_MODE, vaultSecretKey)
        val contentIv = contentCipher.iv

        val plainBytes = plainJson.toByteArray(Charsets.UTF_8)
        val ciphertext = contentCipher.doFinal(plainBytes)
        CryptoManager.zeroize(plainBytes)

        val base64Encoder = Base64.getEncoder()
        val envelopeJson = JSONObject().apply {
            put("version", CURRENT_BACKUP_VERSION)
            put("iv", base64Encoder.encodeToString(contentIv))
            put("ciphertext", base64Encoder.encodeToString(ciphertext))
            put("mainSlot", mainSlot.toJsonObject())
            if (emergencySlot != null) put("emergencySlot", emergencySlot.toJsonObject())
            put("createdAt", System.currentTimeMillis())
        }
        return envelopeJson.toString().toByteArray(Charsets.UTF_8)
    }

    /** Descifra un archivo de respaldo versión 2 con recuperación detallada de la [VaultKey] y ranuras. */
    fun decryptBackupDetailed(backupBytes: ByteArray, password: CharArray): Result<DecryptedBackupPayload> {
        return runCatching {
            val root = JSONObject(String(backupBytes, Charsets.UTF_8).trim())
            val version = root.optInt("version", -1)
            require(version == CURRENT_BACKUP_VERSION) {
                "Versión de respaldo no compatible ($version). Se requiere versión $CURRENT_BACKUP_VERSION."
            }

            val base64Decoder = Base64.getDecoder()
            val ivB64 = root.optString("iv", "")
            val cipherB64 = root.optString("ciphertext", "")
            require(ivB64.isNotBlank() && cipherB64.isNotBlank()) { "Formato v2 no válido: falta iv o ciphertext" }

            val contentIv = base64Decoder.decode(ivB64)
            val ciphertext = base64Decoder.decode(cipherB64)

            val mainSlotObj = root.optJSONObject("mainSlot")
                ?: throw IllegalArgumentException("Formato v2 no válido: falta mainSlot")
            val mainSlot = WrappedSlot.fromJsonObject(mainSlotObj)
            val emergencySlot = root.optJSONObject("emergencySlot")?.let { WrappedSlot.fromJsonObject(it) }

            var recoveredVaultKey = unwrapSlot(mainSlot, password, base64Decoder)
            if (recoveredVaultKey == null && emergencySlot != null) {
                recoveredVaultKey = unwrapSlot(emergencySlot, password, base64Decoder)
            }
            val validVaultKey = recoveredVaultKey ?: throw IllegalArgumentException("Clave de descifrado incorrecta")

            val vaultSecretKey = SecretKeySpec(validVaultKey, "AES")
            val contentCipher = Cipher.getInstance("AES/GCM/NoPadding")
            contentCipher.init(
                Cipher.DECRYPT_MODE,
                vaultSecretKey,
                GCMParameterSpec(SecurityConfig.AES_GCM_TAG_LENGTH_BITS, contentIv)
            )

            val plainBytes = contentCipher.doFinal(ciphertext)
            val resultJson = String(plainBytes, Charsets.UTF_8)
            CryptoManager.zeroize(plainBytes)

            DecryptedBackupPayload(
                plainJson = resultJson,
                vaultKey = validVaultKey,
                mainSlot = mainSlot,
                emergencySlot = emergencySlot
            )
        }
    }

    /** Descifra un archivo de respaldo versión 2 y retorna el JSON descifrado en caso de éxito. */
    fun decryptBackup(backupBytes: ByteArray, password: CharArray): Result<String> {
        return decryptBackupDetailed(backupBytes, password).map { payload ->
            val json = payload.plainJson
            CryptoManager.zeroize(payload.vaultKey)
            json
        }
    }

    /** Crea un sobre de respaldo estándar versión 2 sin cifrado de extremo a extremo. */
    fun createUnencryptedBackup(plainJson: String): ByteArray {
        val now = System.currentTimeMillis()
        val envelopeString = "{\"version\":$CURRENT_BACKUP_VERSION,\"isEncrypted\":false,\"payload\":$plainJson,\"createdAt\":$now}"
        return envelopeString.toByteArray(Charsets.UTF_8)
    }

    /** Determina de forma determinística si un archivo de respaldo cuenta con cifrado criptográfico. */
    fun isBackupEncrypted(backupBytes: ByteArray): Boolean {
        return try {
            val root = JSONObject(String(backupBytes, Charsets.UTF_8).trim())
            if (root.has("isEncrypted") && !root.optBoolean("isEncrypted", true)) {
                false
            } else {
                root.has("ciphertext") && root.has("mainSlot")
            }
        } catch (_: Exception) {
            true
        }
    }

    /** Lee y extrae el JSON plano de un archivo de respaldo sin cifrado de extremo a extremo. */
    fun readUnencryptedBackup(backupBytes: ByteArray): Result<String> {
        return runCatching {
            val jsonString = String(backupBytes, Charsets.UTF_8).trim()
            val root = JSONObject(jsonString)
            if (root.has("payload")) {
                val marker = "\"payload\":"
                val start = jsonString.indexOf(marker)
                val end = jsonString.lastIndexOf(",\"createdAt\":")
                if (start != -1 && end > start + marker.length) {
                    return@runCatching jsonString.substring(start + marker.length, end).trim()
                }
                root.optJSONObject("payload")?.let { return@runCatching it.toString() }
                root.optJSONArray("payload")?.let { return@runCatching it.toString() }
            }
            jsonString
        }
    }

    private fun wrapVaultKey(vaultKeyBytes: ByteArray, secret: CharArray): WrappedSlot {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)

        val keySpec = PBEKeySpec(secret, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derivedKeyBytes = factory.generateSecret(keySpec).encoded
        keySpec.clearPassword()

        val wrapSecretKey = SecretKeySpec(derivedKeyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrapSecretKey)
        val iv = cipher.iv

        val wrappedKey = cipher.doFinal(vaultKeyBytes)
        CryptoManager.zeroize(derivedKeyBytes)

        val base64Encoder = Base64.getEncoder()
        return WrappedSlot(
            saltB64 = base64Encoder.encodeToString(salt),
            ivB64 = base64Encoder.encodeToString(iv),
            wrappedKeyB64 = base64Encoder.encodeToString(wrappedKey)
        )
    }

    private fun unwrapSlot(slot: WrappedSlot, secret: CharArray, decoder: Base64.Decoder): ByteArray? {
        return try {
            val salt = decoder.decode(slot.saltB64)
            val iv = decoder.decode(slot.ivB64)
            val wrappedKey = decoder.decode(slot.wrappedKeyB64)
            val keySpec = PBEKeySpec(secret, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val derivedKeyBytes = factory.generateSecret(keySpec).encoded
            keySpec.clearPassword()

            try {
                val wrapSecretKey = SecretKeySpec(derivedKeyBytes, "AES")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    wrapSecretKey,
                    GCMParameterSpec(SecurityConfig.AES_GCM_TAG_LENGTH_BITS, iv)
                )
                cipher.doFinal(wrappedKey)
            } finally {
                CryptoManager.zeroize(derivedKeyBytes)
            }
        } catch (_: Exception) {
            null
        }
    }
}
