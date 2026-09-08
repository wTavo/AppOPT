package com.example.appopt.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gestor criptográfico para la transferencia segura de servicios entre dispositivos mediante códigos QR cifrados.
 *
 * Características de seguridad:
 * - Cifrado simétrico autenticado **AES-256-GCM** que protege la confidencialidad e integridad del payload en el QR.
 * - Derivación de clave mediante **PBKDF2 con HMAC-SHA256** (10.000 iteraciones + Salt CSPRNG de 16 bytes) a partir de un PIN efímero de 6 dígitos.
 * - Ventana de expiración temporal estricta de 90 segundos para prevenir reutilizaciones tardías o capturas remotas.
 * - Limpieza determinista de memoria (*zeroize*) para todos los arreglos de caracteres y bytes con secretos.
 */
object TransferCrypto {

    /**
     * Prefijo canónico que identifica un código QR de transferencia cifrado de AppOPT.
     */
    const val QR_TRANSFER_PREFIX = "appopt-transfer:"

    /**
     * Margen de tolerancia de desfase de reloj en milisegundos entre dispositivos (15 segundos).
     */
    private const val CLOCK_SKEW_TOLERANCE_MILLIS = 15_000L

    private const val SALT_LENGTH_BYTES = 16
    private val secureRandom = SecureRandom()

    /**
     * Error específico cuando el PIN de transferencia es erróneo o los datos fueron alterados.
     */
    class InvalidPinException(message: String = "PIN de transferencia incorrecto") : Exception(message)

    /**
     * Error específico cuando el código QR superó la ventana de expiración permitida.
     */
    class ExpiredTransferException(message: String = "El código QR de transferencia ha expirado") : Exception(message)

    /**
     * Genera un PIN aleatorio numérico de 6 dígitos utilizando un generador criptográficamente seguro (CSPRNG).
     *
     * @return Cadena de 6 dígitos (ej. "482915").
     */
    fun generateTransferPin(): String {
        val number = secureRandom.nextInt(1_000_000)
        return String.format("%06d", number)
    }

    /**
     * Cifra el contenido JSON de las cuentas a transferir en un sobre seguro empaquetado para código QR.
     *
     * @param accountsJson Texto JSON en texto claro con las cuentas a exportar.
     * @param pin PIN de 6 dígitos en arreglo [CharArray].
     * @param durationSeconds Duración en segundos de validez del código QR (por defecto 90s).
     * @return Cadena formateada lista para codificarse en QR con prefijo [QR_TRANSFER_PREFIX].
     */
    fun encryptTransferPayload(
        accountsJson: String,
        pin: CharArray,
        durationSeconds: Int = SecurityConfig.TRANSFER_QR_EXPIRATION_SECONDS
    ): String {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)

        val now = System.currentTimeMillis()
        val expiresAt = now + (durationSeconds * 1000L)

        val encoder = Base64.getEncoder()
        val dataB64 = encoder.encodeToString(accountsJson.toByteArray(Charsets.UTF_8))
        val innerPayloadString = "{\"createdAt\":$now,\"expiresAt\":$expiresAt,\"data\":\"$dataB64\"}"

        val plainBytes = innerPayloadString.toByteArray(Charsets.UTF_8)
        var derivedKeyBytes: ByteArray? = null

        try {
            val keySpec = PBEKeySpec(
                pin,
                salt,
                SecurityConfig.TRANSFER_QR_PBKDF2_ITERATIONS,
                SecurityConfig.BACKUP_KEY_SIZE_BITS
            )
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            derivedKeyBytes = factory.generateSecret(keySpec).encoded
            keySpec.clearPassword()

            val secretKey = SecretKeySpec(derivedKeyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plainBytes)

            val saltB64 = encoder.encodeToString(salt)
            val ivB64 = encoder.encodeToString(iv)
            val cipherB64 = encoder.encodeToString(ciphertext)

            val envelope = "{\"v\":${SecurityConfig.TRANSFER_QR_VERSION},\"s\":\"$saltB64\",\"iv\":\"$ivB64\",\"c\":\"$cipherB64\"}"
            return "$QR_TRANSFER_PREFIX$envelope"
        } finally {
            CryptoManager.zeroize(plainBytes)
            derivedKeyBytes?.let { CryptoManager.zeroize(it) }
        }
    }

    /**
     * Descifra y valida un payload QR de transferencia con el PIN ingresado por el usuario.
     *
     * @param qrPayload Cadena leída del código QR.
     * @param pin PIN de 6 dígitos ingresado por el usuario en [CharArray].
     * @return [Result] con el JSON en texto claro de las cuentas si el PIN es correcto y el QR no ha expirado.
     */
    fun decryptTransferPayload(
        qrPayload: String,
        pin: CharArray
    ): Result<String> {
        val trimmed = qrPayload.trim()
        val jsonString = if (trimmed.startsWith(QR_TRANSFER_PREFIX, ignoreCase = true)) {
            trimmed.substring(QR_TRANSFER_PREFIX.length).trim()
        } else {
            trimmed
        }

        return runCatching {
            val saltMatch = "\"s\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonString)
                ?: throw IllegalArgumentException("Formato no válido: falta salt")
            val ivMatch = "\"iv\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonString)
                ?: throw IllegalArgumentException("Formato no válido: falta iv")
            val cipherMatch = "\"c\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonString)
                ?: throw IllegalArgumentException("Formato no válido: falta ciphertext")

            val decoder = Base64.getDecoder()
            val salt = decoder.decode(saltMatch.groupValues[1])
            val iv = decoder.decode(ivMatch.groupValues[1])
            val ciphertext = decoder.decode(cipherMatch.groupValues[1])

            var derivedKeyBytes: ByteArray? = null
            var decryptedBytes: ByteArray? = null

            try {
                val keySpec = PBEKeySpec(
                    pin,
                    salt,
                    SecurityConfig.TRANSFER_QR_PBKDF2_ITERATIONS,
                    SecurityConfig.BACKUP_KEY_SIZE_BITS
                )
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                derivedKeyBytes = factory.generateSecret(keySpec).encoded
                keySpec.clearPassword()

                val secretKey = SecretKeySpec(derivedKeyBytes, "AES")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(SecurityConfig.AES_GCM_TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

                try {
                    decryptedBytes = cipher.doFinal(ciphertext)
                } catch (_: Exception) {
                    throw InvalidPinException()
                }

                val decryptedJsonString = String(decryptedBytes, Charsets.UTF_8)

                val expiresAtMatch = "\"expiresAt\"\\s*:\\s*(-?\\d+)".toRegex().find(decryptedJsonString)
                val expiresAt = expiresAtMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val now = System.currentTimeMillis()

                if (expiresAt > 0 && now > (expiresAt + CLOCK_SKEW_TOLERANCE_MILLIS)) {
                    throw ExpiredTransferException()
                }

                val dataMatch = "\"data\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(decryptedJsonString)
                    ?: throw IllegalArgumentException("Formato no válido: falta data")

                val dataBytes = decoder.decode(dataMatch.groupValues[1])
                val originalAccountsJson = String(dataBytes, Charsets.UTF_8)
                originalAccountsJson
            } finally {
                derivedKeyBytes?.let { CryptoManager.zeroize(it) }
                decryptedBytes?.let { CryptoManager.zeroize(it) }
            }
        }
    }
}
