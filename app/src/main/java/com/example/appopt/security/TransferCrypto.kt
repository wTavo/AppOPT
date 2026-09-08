package com.example.appopt.security

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gestor criptográfico para la transferencia segura y ultra-compacta de servicios entre dispositivos mediante códigos QR cifrados.
 *
 * Optimizaciones y seguridad:
 * - Compresión Deflate de alta densidad previa al cifrado para reducir el tamaño del payload en más de un 80%.
 * - Cifrado simétrico autenticado **AES-256-GCM** (Tag de 128 bits e IV único de 12 bytes).
 * - Derivación de clave mediante **PBKDF2 con HMAC-SHA256** (10.000 iteraciones + Salt CSPRNG de 16 bytes) a partir del PIN de 6 dígitos.
 * - Formato de sobre binario directo: [Versión (1B)][Salt (16B)][IV (12B)][Ciphertext + Tag], codificado en Base64 seguro para URL.
 * - Ventana de expiración temporal estricta de 90 segundos con tolerancia de 15s para desajustes de reloj.
 */
object TransferCrypto {

    /**
     * Prefijo canónico que identifica un código QR de transferencia cifrado de AppOPT.
     */
    const val QR_TRANSFER_PREFIX = "appopt-transfer:"

    private const val HEADER_VERSION_BYTES = 1
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val HEADER_OFFSET_SALT = HEADER_VERSION_BYTES
    private const val HEADER_OFFSET_IV = HEADER_OFFSET_SALT + SALT_LENGTH_BYTES
    private const val HEADER_OFFSET_CIPHERTEXT = HEADER_OFFSET_IV + IV_LENGTH_BYTES

    /**
     * Margen de tolerancia de desfase de reloj en milisegundos entre dispositivos (15 segundos).
     */
    private const val CLOCK_SKEW_TOLERANCE_MILLIS = 15_000L

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
     * Comprime un arreglo de bytes utilizando el algoritmo Deflate (nivel máximo).
     */
    private fun compress(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val outputStream = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        deflater.end()
        return outputStream.toByteArray()
    }

    /**
     * Descomprime un arreglo de bytes comprimido con Deflate.
     */
    private fun decompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val outputStream = ByteArrayOutputStream(data.size * 2)
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && inflater.needsInput()) break
            outputStream.write(buffer, 0, count)
        }
        inflater.end()
        return outputStream.toByteArray()
    }

    /**
     * Cifra el contenido JSON de las cuentas a transferir en un sobre binario ultra-compacto listo para código QR.
     *
     * @param accountsJson Texto JSON con las cuentas a exportar.
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

        // Estructura interna directa: <expiresAt>|<accountsJson>
        val innerPayloadString = "$expiresAt|$accountsJson"
        val rawPlainBytes = innerPayloadString.toByteArray(Charsets.UTF_8)
        val compressedPlainBytes = compress(rawPlainBytes)
        CryptoManager.zeroize(rawPlainBytes)

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
            val ciphertext = cipher.doFinal(compressedPlainBytes)

            // Sobre binario compacto: [Versión (1B)][Salt (16B)][IV (12B)][Ciphertext + Tag]
            val binaryEnvelope = ByteArray(HEADER_OFFSET_CIPHERTEXT + ciphertext.size)
            binaryEnvelope[0] = SecurityConfig.TRANSFER_QR_VERSION.toByte()
            System.arraycopy(salt, 0, binaryEnvelope, HEADER_OFFSET_SALT, SALT_LENGTH_BYTES)
            System.arraycopy(iv, 0, binaryEnvelope, HEADER_OFFSET_IV, IV_LENGTH_BYTES)
            System.arraycopy(ciphertext, 0, binaryEnvelope, HEADER_OFFSET_CIPHERTEXT, ciphertext.size)

            val base64Envelope = Base64.getUrlEncoder().withoutPadding().encodeToString(binaryEnvelope)
            return "$QR_TRANSFER_PREFIX$base64Envelope"
        } finally {
            CryptoManager.zeroize(compressedPlainBytes)
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
        val rawEncoded = if (trimmed.startsWith(QR_TRANSFER_PREFIX, ignoreCase = true)) {
            trimmed.substring(QR_TRANSFER_PREFIX.length).trim()
        } else {
            trimmed
        }

        return runCatching {
            val binaryEnvelope = try {
                Base64.getUrlDecoder().decode(rawEncoded)
            } catch (_: Exception) {
                Base64.getDecoder().decode(rawEncoded)
            }

            if (binaryEnvelope.size <= HEADER_OFFSET_CIPHERTEXT) {
                throw IllegalArgumentException("Tamaño de sobre de transferencia inválido")
            }

            val salt = binaryEnvelope.copyOfRange(HEADER_OFFSET_SALT, HEADER_OFFSET_IV)
            val iv = binaryEnvelope.copyOfRange(HEADER_OFFSET_IV, HEADER_OFFSET_CIPHERTEXT)
            val ciphertext = binaryEnvelope.copyOfRange(HEADER_OFFSET_CIPHERTEXT, binaryEnvelope.size)

            var derivedKeyBytes: ByteArray? = null
            var decryptedCompressedBytes: ByteArray? = null

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
                    decryptedCompressedBytes = cipher.doFinal(ciphertext)
                } catch (_: Exception) {
                    throw InvalidPinException()
                }

                val decompressedBytes = try {
                    decompress(decryptedCompressedBytes)
                } catch (_: Exception) {
                    decryptedCompressedBytes
                }

                val decryptedPayloadString = String(decompressedBytes, Charsets.UTF_8)
                CryptoManager.zeroize(decompressedBytes)

                val separatorIndex = decryptedPayloadString.indexOf('|')
                if (separatorIndex < 0) {
                    throw IllegalArgumentException("Estructura de sobre inválida")
                }

                val expiresAtStr = decryptedPayloadString.substring(0, separatorIndex)
                val expiresAt = expiresAtStr.toLongOrNull() ?: 0L
                val now = System.currentTimeMillis()

                if (expiresAt > 0 && now > (expiresAt + CLOCK_SKEW_TOLERANCE_MILLIS)) {
                    throw ExpiredTransferException()
                }

                decryptedPayloadString.substring(separatorIndex + 1)
            } finally {
                derivedKeyBytes?.let { CryptoManager.zeroize(it) }
                decryptedCompressedBytes?.let { CryptoManager.zeroize(it) }
            }
        }
    }
}
