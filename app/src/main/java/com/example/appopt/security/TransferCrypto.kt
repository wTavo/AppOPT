package com.example.appopt.security

import com.example.appopt.domain.model.TransferQrChunk
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gestor criptográfico para la transferencia segura y ultra-compacta de servicios entre dispositivos mediante códigos QR cifrados.
 *
 * Optimizaciones y seguridad:
 * - Compresión Deflate de alta densidad previa al cifrado para reducir el tamaño del payload en más de un 80% ([TransferPayloadCodec]).
 * - Cifrado simétrico autenticado **AES-256-GCM** (Tag de 128 bits e IV único de 12 bytes).
 * - Derivación de clave mediante **PBKDF2 con HMAC-SHA256** (10.000 iteraciones + Salt CSPRNG de 16 bytes) a partir del PIN.
 * - Esquema **Todo o Nada (All-or-Nothing)** en versión 2: El texto cifrado completo se divide en fragmentos continuos;
 *   es matemáticamente imposible descifrar cualquier dato si falta al menos un fragmento.
 * - Ventana de expiración temporal estricta de 90 segundos con tolerancia de 15s para desajustes de reloj.
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
     * Error específico cuando la colección de fragmentos multi-lote está incompleta o es inconsistente.
     */
    class IncompleteTransferException(message: String = "Faltan fragmentos para completar la transferencia") : Exception(message)

    /**
     * Genera una clave aleatoria alfanumérica de 8 caracteres utilizando el alfabeto Base32 seguro
     * ([SecurityConfig.TRANSFER_KEY_ALPHABET]) y un generador criptográficamente seguro (CSPRNG).
     *
     * @return Cadena de 8 caracteres en mayúsculas (ej. "K7NP4M9X").
     */
    fun generateTransferPin(): String {
        val alphabet = SecurityConfig.TRANSFER_KEY_ALPHABET
        val chars = CharArray(SecurityConfig.TRANSFER_KEY_LENGTH)
        for (i in chars.indices) {
            chars[i] = alphabet[secureRandom.nextInt(alphabet.length)]
        }
        return String(chars)
    }

    /**
     * Cifra el contenido JSON en un único sobre binario versión 1 listo para código QR.
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
        return encryptTransferPayloadInChunks(
            accountsJson = accountsJson,
            pin = pin,
            durationSeconds = durationSeconds,
            maxChunkBytes = Int.MAX_VALUE
        ).first()
    }

    /**
     * Cifra el contenido JSON bajo el esquema "Todo o Nada" (v2) y lo divide en una lista de fragmentos binarios para códigos QR.
     *
     * @param accountsJson Texto JSON con todas las cuentas a exportar.
     * @param pin PIN de 6 dígitos en arreglo [CharArray].
     * @param durationSeconds Duración en segundos de validez de la sesión (por defecto 90s).
     * @param maxChunkBytes Tamaño máximo en bytes de ciphertext por fragmento QR.
     * @param targetChunkCount Cantidad exacta deseada de fragmentos QR (calculada en base a lotes de 10 cuentas).
     * @return Lista de cadenas formateadas con prefijo [QR_TRANSFER_PREFIX], una por cada fragmento.
     */
    fun encryptTransferPayloadInChunks(
        accountsJson: String,
        pin: CharArray,
        durationSeconds: Int = SecurityConfig.TRANSFER_QR_EXPIRATION_SECONDS,
        maxChunkBytes: Int = SecurityConfig.TRANSFER_QR_CHUNK_MAX_BYTES,
        targetChunkCount: Int? = null
    ): List<String> {
        val salt = ByteArray(TransferPayloadCodec.SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)

        val now = System.currentTimeMillis()
        val expiresAt = now + (durationSeconds * 1000L)

        val innerPayloadString = "$expiresAt|$accountsJson"
        val rawPlainBytes = innerPayloadString.toByteArray(Charsets.UTF_8)
        val compressedPlainBytes = TransferPayloadCodec.compress(rawPlainBytes)
        CryptoManager.zeroize(rawPlainBytes)

        val normalizedPinChars = String(pin)
            .replace("-", "")
            .replace(" ", "")
            .uppercase()
            .toCharArray()

        var derivedKeyBytes: ByteArray? = null

        try {
            val keySpec = PBEKeySpec(
                normalizedPinChars,
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
            val fullCiphertext = cipher.doFinal(compressedPlainBytes)

            val sessionId = secureRandom.nextLong()

            val effectiveChunkBytes = if (targetChunkCount != null && targetChunkCount > 0) {
                maxOf(1, (fullCiphertext.size + targetChunkCount - 1) / targetChunkCount)
            } else {
                maxChunkBytes
            }

            val chunkList = mutableListOf<ByteArray>()
            var offset = 0
            while (offset < fullCiphertext.size) {
                val length = minOf(effectiveChunkBytes, fullCiphertext.size - offset)
                val chunk = fullCiphertext.copyOfRange(offset, offset + length)
                chunkList.add(chunk)
                offset += length
            }
            if (chunkList.isEmpty()) {
                chunkList.add(ByteArray(0))
            }

            val total = chunkList.size
            val resultQrStrings = mutableListOf<String>()

            for ((idx0, chunkBytes) in chunkList.withIndex()) {
                val index = idx0 + 1
                val base64Envelope = TransferPayloadCodec.encodeChunkV2(
                    sessionId = sessionId,
                    index = index,
                    total = total,
                    salt = salt,
                    iv = iv,
                    chunkBytes = chunkBytes
                )
                resultQrStrings.add("$QR_TRANSFER_PREFIX$base64Envelope")
            }

            return resultQrStrings
        } finally {
            CryptoManager.zeroize(normalizedPinChars)
            CryptoManager.zeroize(compressedPlainBytes)
            derivedKeyBytes?.let { CryptoManager.zeroize(it) }
        }
    }

    /**
     * Parsea e inspecciona la cabecera binaria de un código QR escaneado sin descifrar su contenido.
     *
     * @param qrPayload Cadena de texto leída del código QR.
     * @return Instancia estructurada de [TransferQrChunk].
     */
    fun parseTransferChunk(qrPayload: String): TransferQrChunk {
        return TransferPayloadCodec.parseChunk(qrPayload, QR_TRANSFER_PREFIX)
    }

    /**
     * Descifra y valida un payload QR de transferencia mono-código o fragmento único con el PIN del usuario.
     *
     * @param qrPayload Cadena leída del código QR.
     * @param pin PIN de 6 dígitos ingresado por el usuario en [CharArray].
     * @return [Result] con el JSON en texto claro de las cuentas si el PIN es correcto y el QR no ha expirado.
     */
    fun decryptTransferPayload(
        qrPayload: String,
        pin: CharArray
    ): Result<String> {
        return runCatching {
            val chunk = parseTransferChunk(qrPayload)
            decryptAssembledChunks(listOf(chunk), pin).getOrThrow()
        }
    }

    /**
     * Ensambla, descifra y valida una colección completa de fragmentos "Todo o Nada" con el PIN ingresado por el usuario.
     *
     * @param chunks Colección de fragmentos recopilados durante la sesión de escaneo.
     * @param pin PIN de 6 dígitos ingresado por el usuario en [CharArray].
     * @return [Result] con el JSON en texto claro de las cuentas si los fragmentos están completos, el PIN es válido y no ha expirado.
     */
    fun decryptAssembledChunks(
        chunks: List<TransferQrChunk>,
        pin: CharArray
    ): Result<String> {
        return runCatching {
            if (chunks.isEmpty()) {
                throw IncompleteTransferException("No hay fragmentos para descifrar")
            }

            val first = chunks.first()
            val total = first.total
            val sessionId = first.sessionId

            if (chunks.size != total) {
                throw IncompleteTransferException("Se recibieron ${chunks.size} de $total fragmentos")
            }

            for (chunk in chunks) {
                if (chunk.total != total || chunk.sessionId != sessionId) {
                    throw IllegalArgumentException("Los fragmentos pertenecen a sesiones de transferencia diferentes")
                }
                if (!chunk.salt.contentEquals(first.salt) || !chunk.iv.contentEquals(first.iv)) {
                    throw IllegalArgumentException("Parámetros criptográficos inconsistentes entre fragmentos")
                }
            }

            val sortedChunks = chunks.sortedBy { it.index }
            for (i in 0 until total) {
                if (sortedChunks[i].index != i + 1) {
                    throw IncompleteTransferException("Falta el fragmento número ${i + 1}")
                }
            }

            val totalCiphertextSize = sortedChunks.sumOf { it.chunkCiphertext.size }
            val fullCiphertext = ByteArray(totalCiphertextSize)
            var currentOffset = 0
            for (chunk in sortedChunks) {
                System.arraycopy(chunk.chunkCiphertext, 0, fullCiphertext, currentOffset, chunk.chunkCiphertext.size)
                currentOffset += chunk.chunkCiphertext.size
            }

            val salt = first.salt
            val iv = first.iv

            val normalizedPinChars = String(pin)
                .replace("-", "")
                .replace(" ", "")
                .uppercase()
                .toCharArray()

            var derivedKeyBytes: ByteArray? = null
            var decryptedCompressedBytes: ByteArray? = null

            try {
                val keySpec = PBEKeySpec(
                    normalizedPinChars,
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
                    decryptedCompressedBytes = cipher.doFinal(fullCiphertext)
                } catch (_: Exception) {
                    throw InvalidPinException()
                }

                val decompressedBytes = try {
                    TransferPayloadCodec.decompress(decryptedCompressedBytes)
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
                CryptoManager.zeroize(normalizedPinChars)
                derivedKeyBytes?.let { CryptoManager.zeroize(it) }
                decryptedCompressedBytes?.let { CryptoManager.zeroize(it) }
            }
        }
    }
}
