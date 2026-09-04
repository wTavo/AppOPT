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
 * Principio de diseño de seguridad (Sección 6 y 12 del Plan):
 * - Deriva una clave simétrica AES-256 a partir de la contraseña del usuario mediante **PBKDF2 con HMAC-SHA256** (100.000 iteraciones + Salt CSPRNG de 16 bytes).
 * - Cifra el contenido con **AES-256-GCM** garantizando confidencialidad e integridad del archivo de respaldo.
 */
object BackupCrypto {

    private const val PBKDF2_ITERATIONS = SecurityConfig.PBKDF2_BACKUP_ITERATIONS
    private const val KEY_LENGTH_BITS = SecurityConfig.BACKUP_KEY_SIZE_BITS
    private const val SALT_LENGTH_BYTES = 16
    private const val BACKUP_VERSION_V1 = 1
    private const val CURRENT_BACKUP_VERSION = 2

    private val secureRandom = SecureRandom()

    /**
     * Cifra una cadena JSON con un esquema de doble ranura (*Dual-Slot Key Wrapping*).
     *
     * Protege el contenido con una [Vault Key] aleatoria de 256 bits, la cual queda envuelta simultáneamente
     * por la clave principal elegida por el usuario y por la frase de emergencia de 12 palabras (BIP-39).
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
        // 1. Generar la clave maestra del Vault (256 bits)
        val vaultKeyBytes = ByteArray(KEY_LENGTH_BITS / 8)
        secureRandom.nextBytes(vaultKeyBytes)
        val vaultSecretKey = SecretKeySpec(vaultKeyBytes, "AES")

        // 2. Cifrar el contenido con la clave del Vault mediante AES-256-GCM
        val contentCipher = Cipher.getInstance("AES/GCM/NoPadding")
        contentCipher.init(Cipher.ENCRYPT_MODE, vaultSecretKey)
        val contentIv = contentCipher.iv

        val plainBytes = plainJson.toByteArray(Charsets.UTF_8)
        val ciphertext = contentCipher.doFinal(plainBytes)
        CryptoManager.zeroize(plainBytes)

        // 3. Envolver la Vault Key en la Ranura Principal (Slot 1)
        val mainSlot = wrapVaultKey(vaultKeyBytes, primaryPassword)

        // 4. Envolver la Vault Key en la Ranura de Emergencia (Slot 2)
        val emergencySlot = wrapVaultKey(vaultKeyBytes, emergencyMnemonic)

        // Limpiar clave del Vault en memoria
        CryptoManager.zeroize(vaultKeyBytes)

        val base64Encoder = Base64.getEncoder()
        val contentIvB64 = base64Encoder.encodeToString(contentIv)
        val cipherB64 = base64Encoder.encodeToString(ciphertext)
        val now = System.currentTimeMillis()

        val envelopeString = "{\"version\":$CURRENT_BACKUP_VERSION,\"iv\":\"$contentIvB64\",\"ciphertext\":\"$cipherB64\"," +
                "\"mainSlot\":{\"salt\":\"${mainSlot.saltB64}\",\"iv\":\"${mainSlot.ivB64}\",\"key\":\"${mainSlot.wrappedKeyB64}\"}," +
                "\"emergencySlot\":{\"salt\":\"${emergencySlot.saltB64}\",\"iv\":\"${emergencySlot.ivB64}\",\"key\":\"${emergencySlot.wrappedKeyB64}\"}," +
                "\"createdAt\":$now}"

        return envelopeString.toByteArray(Charsets.UTF_8)
    }

    /**
     * Cifra una cadena JSON con todas las cuentas utilizando una sola contraseña (formato v1 / exportaciones simples).
     *
     * @param plainJson Texto JSON con los datos exportados.
     * @param password Contraseña maestra en arreglo [CharArray] para permitir limpieza de memoria.
     * @return Arreglo de bytes binarios del archivo de respaldo cifrado en formato sobre seguro.
     */
    fun encryptBackup(plainJson: String, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)

        val keySpec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derivedKeyBytes = factory.generateSecret(keySpec).encoded
        keySpec.clearPassword()

        val secretKey = SecretKeySpec(derivedKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv

        val plainBytes = plainJson.toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(plainBytes)

        // Limpiar buffers de clave en memoria
        CryptoManager.zeroize(derivedKeyBytes)
        CryptoManager.zeroize(plainBytes)

        val base64Encoder = Base64.getEncoder()
        val saltB64 = base64Encoder.encodeToString(salt)
        val ivB64 = base64Encoder.encodeToString(iv)
        val cipherB64 = base64Encoder.encodeToString(ciphertext)
        val now = System.currentTimeMillis()

        val envelopeString = "{\"version\":$BACKUP_VERSION_V1,\"salt\":\"$saltB64\",\"iv\":\"$ivB64\",\"ciphertext\":\"$cipherB64\",\"createdAt\":$now}"

        return envelopeString.toByteArray(Charsets.UTF_8)
    }

    /**
     * Descifra un archivo de respaldo (soporta formatos v1 y v2 con doble ranura) y valida su integridad criptográfica.
     *
     * @param backupBytes Contenido en bytes del archivo de respaldo.
     * @param password Contraseña, clave de 64 dígitos o frase de 12 palabras en [CharArray].
     * @return [Result] con el texto JSON descifrado en caso de éxito, o error si las credenciales son incorrectas / datos manipulados.
     */
    fun decryptBackup(backupBytes: ByteArray, password: CharArray): Result<String> {
        return runCatching {
            val jsonString = String(backupBytes, Charsets.UTF_8).trim()

            val versionMatch = "\"version\"\\s*:\\s*(\\d+)".toRegex().find(jsonString)
                ?: throw IllegalArgumentException("Formato de respaldo no válido: falta version")
            val version = versionMatch.groupValues[1].toInt()

            val base64Decoder = Base64.getDecoder()

            when (version) {
                BACKUP_VERSION_V1 -> {
                    // Descifrado directo compatible con versión 1
                    val saltMatch = "\"salt\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonString)
                        ?: throw IllegalArgumentException("Formato v1 no válido: falta salt")
                    val ivMatch = "\"iv\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonString)
                        ?: throw IllegalArgumentException("Formato v1 no válido: falta iv")
                    val cipherMatch = "\"ciphertext\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonString)
                        ?: throw IllegalArgumentException("Formato v1 no válido: falta ciphertext")

                    val salt = base64Decoder.decode(saltMatch.groupValues[1])
                    val iv = base64Decoder.decode(ivMatch.groupValues[1])
                    val ciphertext = base64Decoder.decode(cipherMatch.groupValues[1])

                    val keySpec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
                    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    val derivedKeyBytes = factory.generateSecret(keySpec).encoded
                    keySpec.clearPassword()

                    val secretKey = SecretKeySpec(derivedKeyBytes, "AES")
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    val spec = GCMParameterSpec(128, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                    val plainBytes = cipher.doFinal(ciphertext)
                    CryptoManager.zeroize(derivedKeyBytes)

                    val resultJson = String(plainBytes, Charsets.UTF_8)
                    CryptoManager.zeroize(plainBytes)
                    resultJson
                }
                CURRENT_BACKUP_VERSION -> {
                    // Descifrado versión 2 con doble ranura (Main Slot o Emergency Slot)
                    val contentIvMatch = "\"iv\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonString)
                        ?: throw IllegalArgumentException("Formato v2 no válido: falta iv")
                    val cipherMatch = "\"ciphertext\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonString)
                        ?: throw IllegalArgumentException("Formato v2 no válido: falta ciphertext")

                    val contentIv = base64Decoder.decode(contentIvMatch.groupValues[1])
                    val ciphertext = base64Decoder.decode(cipherMatch.groupValues[1])

                    // Extraer los slots
                    val mainSlotMatch = "\"mainSlot\"\\s*:\\s*\\{\\s*\"salt\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"iv\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"key\"\\s*:\\s*\"([^\"]+)\"\\s*\\}".toRegex().find(jsonString)
                    val emergencySlotMatch = "\"emergencySlot\"\\s*:\\s*\\{\\s*\"salt\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"iv\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"key\"\\s*:\\s*\"([^\"]+)\"\\s*\\}".toRegex().find(jsonString)

                    var recoveredVaultKey: ByteArray? = null

                    // 1. Intentar abrir Main Slot
                    if (mainSlotMatch != null) {
                        val slotSalt = base64Decoder.decode(mainSlotMatch.groupValues[1])
                        val slotIv = base64Decoder.decode(mainSlotMatch.groupValues[2])
                        val slotWrappedKey = base64Decoder.decode(mainSlotMatch.groupValues[3])
                        recoveredVaultKey = unwrapVaultKey(slotSalt, slotIv, slotWrappedKey, password)
                    }

                    // 2. Si falló el Main Slot, intentar abrir Emergency Slot
                    if (recoveredVaultKey == null && emergencySlotMatch != null) {
                        val slotSalt = base64Decoder.decode(emergencySlotMatch.groupValues[1])
                        val slotIv = base64Decoder.decode(emergencySlotMatch.groupValues[2])
                        val slotWrappedKey = base64Decoder.decode(emergencySlotMatch.groupValues[3])
                        recoveredVaultKey = unwrapVaultKey(slotSalt, slotIv, slotWrappedKey, password)
                    }

                    if (recoveredVaultKey == null) {
                        throw IllegalArgumentException("No se pudo descifrar ninguna de las ranuras de seguridad con la clave proporcionada.")
                    }

                    // 3. Descifrar el ciphertext principal con la Vault Key recuperada
                    val vaultSecretKey = SecretKeySpec(recoveredVaultKey, "AES")
                    val contentCipher = Cipher.getInstance("AES/GCM/NoPadding")
                    val spec = GCMParameterSpec(128, contentIv)
                    contentCipher.init(Cipher.DECRYPT_MODE, vaultSecretKey, spec)

                    val plainBytes = contentCipher.doFinal(ciphertext)
                    CryptoManager.zeroize(recoveredVaultKey)

                    val resultJson = String(plainBytes, Charsets.UTF_8)
                    CryptoManager.zeroize(plainBytes)
                    resultJson
                }
                else -> throw IllegalArgumentException("Versión de respaldo no soportada: $version")
            }
        }
    }

    private data class WrappedSlot(val saltB64: String, val ivB64: String, val wrappedKeyB64: String)

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

            val wrapSecretKey = SecretKeySpec(derivedKeyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, wrapSecretKey, spec)

            val recoveredKey = cipher.doFinal(wrappedKey)
            CryptoManager.zeroize(derivedKeyBytes)
            recoveredKey
        } catch (_: Exception) {
            null
        }
    }
}
