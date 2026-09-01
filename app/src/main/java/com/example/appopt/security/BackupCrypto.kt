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
 * - Toda contraseña y arreglo de clave intermedia se sobreescribe con ceros tras la operación.
 * - Implementación pura en Kotlin sin dependencias de stubs de plataforma para permitir portabilidad y tests.
 */
object BackupCrypto {

    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val CURRENT_BACKUP_VERSION = 1

    private val secureRandom = SecureRandom()

    /**
     * Cifra una cadena JSON con todas las cuentas utilizando la contraseña proporcionada por el usuario.
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

        // Formato JSON puro para el sobre de respaldo sin stubs
        val envelopeString = "{\"version\":$CURRENT_BACKUP_VERSION,\"salt\":\"$saltB64\",\"iv\":\"$ivB64\",\"ciphertext\":\"$cipherB64\",\"createdAt\":$now}"

        return envelopeString.toByteArray(Charsets.UTF_8)
    }

    /**
     * Descifra un archivo de respaldo y valida su versión e integridad criptográfica.
     *
     * @param backupBytes Contenido en bytes del archivo de respaldo.
     * @param password Contraseña de descifrado en [CharArray].
     * @return [Result] con el texto JSON descifrado en caso de éxito, o error si la contraseña es incorrecta / datos manipulados.
     */
    fun decryptBackup(backupBytes: ByteArray, password: CharArray): Result<String> {
        return runCatching {
            val jsonString = String(backupBytes, Charsets.UTF_8).trim()

            val versionMatch = "\"version\"\\s*:\\s*(\\d+)".toRegex().find(jsonString)
                ?: throw IllegalArgumentException("Formato de respaldo no válido: falta version")
            val version = versionMatch.groupValues[1].toInt()
            require(version == CURRENT_BACKUP_VERSION) { "Versión de respaldo no soportada: $version" }

            val saltMatch = "\"salt\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonString)
                ?: throw IllegalArgumentException("Formato de respaldo no válido: falta salt")
            val ivMatch = "\"iv\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonString)
                ?: throw IllegalArgumentException("Formato de respaldo no válido: falta iv")
            val cipherMatch = "\"ciphertext\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonString)
                ?: throw IllegalArgumentException("Formato de respaldo no válido: falta ciphertext")

            val base64Decoder = Base64.getDecoder()
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
    }
}
