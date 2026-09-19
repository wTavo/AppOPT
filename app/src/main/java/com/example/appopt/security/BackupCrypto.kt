package com.example.appopt.security

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
    )

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
            if (javaClass != other?.javaClass) return false
            other as EncryptedBackupResult
            if (!envelopeBytes.contentEquals(other.envelopeBytes)) return false
            if (!vaultKey.contentEquals(other.vaultKey)) return false
            if (mainSlot != other.mainSlot) return false
            if (emergencySlot != other.emergencySlot) return false
            return true
        }

        override fun hashCode(): Int {
            var result = envelopeBytes.contentHashCode()
            result = 31 * result + vaultKey.contentHashCode()
            result = 31 * result + mainSlot.hashCode()
            result = 31 * result + (emergencySlot?.hashCode() ?: 0)
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
            if (javaClass != other?.javaClass) return false
            other as DecryptedBackupPayload
            if (plainJson != other.plainJson) return false
            if (!vaultKey.contentEquals(other.vaultKey)) return false
            if (mainSlot != other.mainSlot) return false
            if (emergencySlot != other.emergencySlot) return false
            return true
        }

        override fun hashCode(): Int {
            var result = plainJson.hashCode()
            result = 31 * result + vaultKey.contentHashCode()
            result = 31 * result + mainSlot.hashCode()
            result = 31 * result + (emergencySlot?.hashCode() ?: 0)
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
        val vaultSecretKey = SecretKeySpec(vaultKeyBytes, "AES")

        val contentCipher = Cipher.getInstance("AES/GCM/NoPadding")
        contentCipher.init(Cipher.ENCRYPT_MODE, vaultSecretKey)
        val contentIv = contentCipher.iv

        val plainBytes = plainJson.toByteArray(Charsets.UTF_8)
        val ciphertext = contentCipher.doFinal(plainBytes)
        CryptoManager.zeroize(plainBytes)

        val mainSlot = wrapVaultKey(vaultKeyBytes, primaryPassword)

        val emergencySlot = if (emergencyMnemonic != null && emergencyMnemonic.isNotEmpty()) {
            wrapVaultKey(vaultKeyBytes, emergencyMnemonic)
        } else {
            null
        }

        val base64Encoder = Base64.getEncoder()
        val contentIvB64 = base64Encoder.encodeToString(contentIv)
        val cipherB64 = base64Encoder.encodeToString(ciphertext)
        val now = System.currentTimeMillis()

        val emergencySlotJson = if (emergencySlot != null) {
            ",\"emergencySlot\":{\"salt\":\"${emergencySlot.saltB64}\",\"iv\":\"${emergencySlot.ivB64}\",\"key\":\"${emergencySlot.wrappedKeyB64}\"}"
        } else {
            ""
        }

        val envelopeString = "{\"version\":$CURRENT_BACKUP_VERSION,\"iv\":\"$contentIvB64\",\"ciphertext\":\"$cipherB64\"," +
                "\"mainSlot\":{\"salt\":\"${mainSlot.saltB64}\",\"iv\":\"${mainSlot.ivB64}\",\"key\":\"${mainSlot.wrappedKeyB64}\"}" +
                emergencySlotJson +
                ",\"createdAt\":$now}"

        return EncryptedBackupResult(
            envelopeBytes = envelopeString.toByteArray(Charsets.UTF_8),
            vaultKey = vaultKeyBytes,
            mainSlot = mainSlot,
            emergencySlot = emergencySlot
        )
    }

    /**
     * Cifra una cadena JSON en formato de sobre v2 retornando el arreglo binario.
     *
     * @param plainJson Texto JSON con los datos exportados.
     * @param password Contraseña maestra en arreglo [CharArray].
     * @return Arreglo de bytes binarios del archivo de respaldo cifrado en formato sobre seguro v2.
     */
    fun encryptBackup(plainJson: String, password: CharArray): ByteArray {
        val result = encryptBackupResult(plainJson, password, null)
        CryptoManager.zeroize(result.vaultKey)
        return result.envelopeBytes
    }

    /**
     * Cifra una cadena JSON con un esquema de doble ranura v2 ([primaryPassword] + [emergencyMnemonic]).
     *
     * @param plainJson Texto JSON con los datos de las cuentas a exportar.
     * @param primaryPassword Contraseña maestra o clave de 64 dígitos en [CharArray].
     * @param emergencyMnemonic Frase mnemónica de 12 palabras normalizada en [CharArray].
     * @return Arreglo de bytes binarios del archivo de respaldo cifrado en formato sobre seguro v2.
     */
    fun encryptDualBackup(
        plainJson: String,
        primaryPassword: CharArray,
        emergencyMnemonic: CharArray
    ): ByteArray {
        val result = encryptBackupResult(plainJson, primaryPassword, emergencyMnemonic)
        CryptoManager.zeroize(result.vaultKey)
        return result.envelopeBytes
    }

    /**
     * Cifra un nuevo respaldo reutilizando una [vaultKey] custodiada y preservando las ranuras existentes.
     *
     * Permite que la sincronización automática en segundo plano genere respaldos con datos frescos
     * manteniendo intactas las ranuras de contraseña y mnemónico sin requerir interacción del usuario.
     *
     * @param plainJson Texto JSON actualizado con las cuentas.
     * @param vaultKey Clave simétrica de 256 bits custodiada.
     * @param mainSlot Ranura principal existente envuelta con la contraseña del usuario.
     * @param emergencySlot Ranura de emergencia existente opcional.
     * @return Arreglo de bytes binarios del archivo de respaldo cifrado en formato sobre seguro v2.
     */
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
        val contentIvB64 = base64Encoder.encodeToString(contentIv)
        val cipherB64 = base64Encoder.encodeToString(ciphertext)
        val now = System.currentTimeMillis()

        val emergencySlotJson = if (emergencySlot != null) {
            ",\"emergencySlot\":{\"salt\":\"${emergencySlot.saltB64}\",\"iv\":\"${emergencySlot.ivB64}\",\"key\":\"${emergencySlot.wrappedKeyB64}\"}"
        } else {
            ""
        }

        val envelopeString = "{\"version\":$CURRENT_BACKUP_VERSION,\"iv\":\"$contentIvB64\",\"ciphertext\":\"$cipherB64\"," +
                "\"mainSlot\":{\"salt\":\"${mainSlot.saltB64}\",\"iv\":\"${mainSlot.ivB64}\",\"key\":\"${mainSlot.wrappedKeyB64}\"}" +
                emergencySlotJson +
                ",\"createdAt\":$now}"

        return envelopeString.toByteArray(Charsets.UTF_8)
    }

    /**
     * Descifra un archivo de respaldo versión 2 con recuperación detallada de la [VaultKey] y ranuras.
     *
     * @param backupBytes Contenido en bytes del archivo de respaldo.
     * @param password Contraseña, clave de 64 dígitos o frase de 12 palabras en [CharArray].
     * @return [Result] con [DecryptedBackupPayload] en caso de éxito, o error si la clave es incorrecta.
     */
    fun decryptBackupDetailed(backupBytes: ByteArray, password: CharArray): Result<DecryptedBackupPayload> {
        return runCatching {
            val jsonString = String(backupBytes, Charsets.UTF_8).trim()

            val versionMatch = "\"version\"\\s*:\\s*(\\d+)".toRegex().find(jsonString)
                ?: throw IllegalArgumentException("Formato de respaldo no válido: falta versión")
            val version = versionMatch.groupValues[1].toInt()

            if (version != CURRENT_BACKUP_VERSION) {
                throw IllegalArgumentException("Versión de respaldo no compatible ($version). Se requiere versión $CURRENT_BACKUP_VERSION.")
            }

            val base64Decoder = Base64.getDecoder()

            val contentIvMatch = "\"iv\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonString)
                ?: throw IllegalArgumentException("Formato v2 no válido: falta iv")
            val cipherMatch = "\"ciphertext\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonString)
                ?: throw IllegalArgumentException("Formato v2 no válido: falta ciphertext")

            val contentIv = base64Decoder.decode(contentIvMatch.groupValues[1])
            val ciphertext = base64Decoder.decode(cipherMatch.groupValues[1])

            val mainSlotMatch = "\"mainSlot\"\\s*:\\s*\\{\\s*\"salt\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"iv\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"key\"\\s*:\\s*\"([^\"]+)\"\\s*\\}".toRegex().find(jsonString)
                ?: throw IllegalArgumentException("Formato v2 no válido: falta mainSlot")
            val emergencySlotMatch = "\"emergencySlot\"\\s*:\\s*\\{\\s*\"salt\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"iv\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"key\"\\s*:\\s*\"([^\"]+)\"\\s*\\}".toRegex().find(jsonString)

            val mainSlot = WrappedSlot(
                saltB64 = mainSlotMatch.groupValues[1],
                ivB64 = mainSlotMatch.groupValues[2],
                wrappedKeyB64 = mainSlotMatch.groupValues[3]
            )

            val emergencySlot = emergencySlotMatch?.let {
                WrappedSlot(
                    saltB64 = it.groupValues[1],
                    ivB64 = it.groupValues[2],
                    wrappedKeyB64 = it.groupValues[3]
                )
            }

            var recoveredVaultKey: ByteArray?

            // 1. Intentar abrir Main Slot
            val mainSalt = base64Decoder.decode(mainSlot.saltB64)
            val mainIv = base64Decoder.decode(mainSlot.ivB64)
            val mainWrappedKey = base64Decoder.decode(mainSlot.wrappedKeyB64)
            recoveredVaultKey = unwrapVaultKey(mainSalt, mainIv, mainWrappedKey, password)

            // 2. Si falló el Main Slot, intentar abrir Emergency Slot
            if (recoveredVaultKey == null && emergencySlot != null) {
                val emSalt = base64Decoder.decode(emergencySlot.saltB64)
                val emIv = base64Decoder.decode(emergencySlot.ivB64)
                val emWrappedKey = base64Decoder.decode(emergencySlot.wrappedKeyB64)
                recoveredVaultKey = unwrapVaultKey(emSalt, emIv, emWrappedKey, password)
            }

            if (recoveredVaultKey == null) {
                throw IllegalArgumentException("Clave de descifrado incorrecta")
            }

            // 3. Descifrar el ciphertext principal con la Vault Key recuperada
            val vaultSecretKey = SecretKeySpec(recoveredVaultKey, "AES")
            val contentCipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, contentIv)
            contentCipher.init(Cipher.DECRYPT_MODE, vaultSecretKey, spec)

            val plainBytes = contentCipher.doFinal(ciphertext)
            val resultJson = String(plainBytes, Charsets.UTF_8)
            CryptoManager.zeroize(plainBytes)

            DecryptedBackupPayload(
                plainJson = resultJson,
                vaultKey = recoveredVaultKey,
                mainSlot = mainSlot,
                emergencySlot = emergencySlot
            )
        }
    }

    /**
     * Descifra un archivo de respaldo versión 2 y retorna el JSON descifrado en caso de éxito.
     *
     * @param backupBytes Contenido en bytes del archivo de respaldo.
     * @param password Contraseña, clave de 64 dígitos o frase de 12 palabras en [CharArray].
     * @return [Result] con el texto JSON descifrado en caso de éxito, o error si las credenciales son incorrectas.
     */
    fun decryptBackup(backupBytes: ByteArray, password: CharArray): Result<String> {
        return decryptBackupDetailed(backupBytes, password).map { payload ->
            val json = payload.plainJson
            CryptoManager.zeroize(payload.vaultKey)
            json
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

    private fun unwrapVaultKey(salt: ByteArray, iv: ByteArray, wrappedKey: ByteArray, secret: CharArray): ByteArray? {
        return try {
            val keySpec = PBEKeySpec(secret, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val derivedKeyBytes = factory.generateSecret(keySpec).encoded
            keySpec.clearPassword()

            try {
                val wrapSecretKey = SecretKeySpec(derivedKeyBytes, "AES")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, wrapSecretKey, spec)
                cipher.doFinal(wrappedKey)
            } finally {
                CryptoManager.zeroize(derivedKeyBytes)
            }
        } catch (_: Exception) {
            null
        }
    }
}
