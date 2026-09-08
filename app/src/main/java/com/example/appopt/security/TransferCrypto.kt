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
 * Representa un fragmento o pieza de un sobre de transferencia QR cifrado bajo el esquema "Todo o Nada" o versión 1.
 *
 * @property version Versión del esquema del sobre ([SecurityConfig.TRANSFER_QR_VERSION] o [SecurityConfig.TRANSFER_QR_VERSION_V2]).
 * @property sessionId Identificador único aleatorio de 64 bits para correlacionar fragmentos de la misma sesión.
 * @property index Posición ordinal de este fragmento (base 1: 1..[total]).
 * @property total Cantidad total de fragmentos necesarios para reconstruir el texto cifrado.
 * @property salt Sal criptográfica de 16 bytes generada por CSPRNG.
 * @property iv Vector de inicialización AES-GCM de 12 bytes.
 * @property chunkCiphertext Segmento de bytes del texto cifrado correspondiente a este fragmento.
 */
data class TransferQrChunk(
    val version: Int,
    val sessionId: Long,
    val index: Int,
    val total: Int,
    val salt: ByteArray,
    val iv: ByteArray,
    val chunkCiphertext: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TransferQrChunk
        return version == other.version &&
            sessionId == other.sessionId &&
            index == other.index &&
            total == other.total &&
            salt.contentEquals(other.salt) &&
            iv.contentEquals(other.iv) &&
            chunkCiphertext.contentEquals(other.chunkCiphertext)
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + index
        result = 31 * result + total
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + chunkCiphertext.contentHashCode()
        return result
    }
}

/**
 * Gestor criptográfico para la transferencia segura y ultra-compacta de servicios entre dispositivos mediante códigos QR cifrados.
 *
 * Optimizaciones y seguridad:
 * - Compresión Deflate de alta densidad previa al cifrado para reducir el tamaño del payload en más de un 80%.
 * - Cifrado simétrico autenticado **AES-256-GCM** (Tag de 128 bits e IV único de 12 bytes).
 * - Derivación de clave mediante **PBKDF2 con HMAC-SHA256** (10.000 iteraciones + Salt CSPRNG de 16 bytes) a partir del PIN de 6 dígitos.
 * - Esquema **Todo o Nada (All-or-Nothing)** en versión 2: El texto cifrado completo se divide en fragmentos continuos;
 *   es matemáticamente imposible descifrar cualquier dato si falta al menos un fragmento.
 * - Ventana de expiración temporal estricta de 90 segundos con tolerancia de 15s para desajustes de reloj.
 */
object TransferCrypto {

    /**
     * Prefijo canónico que identifica un código QR de transferencia cifrado de AppOPT.
     */
    const val QR_TRANSFER_PREFIX = "appopt-transfer:"

    // Offsets para Versión 1: [Versión (1B)][Salt (16B)][IV (12B)][Ciphertext]
    private const val HEADER_V1_VERSION_BYTES = 1
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val HEADER_V1_OFFSET_SALT = HEADER_V1_VERSION_BYTES
    private const val HEADER_V1_OFFSET_IV = HEADER_V1_OFFSET_SALT + SALT_LENGTH_BYTES
    private const val HEADER_V1_OFFSET_CIPHERTEXT = HEADER_V1_OFFSET_IV + IV_LENGTH_BYTES

    // Offsets para Versión 2 (Todo o Nada): [Versión (1B)][SessionId (8B)][Index (1B)][Total (1B)][Salt (16B)][IV (12B)][ChunkCiphertext]
    private const val HEADER_V2_OFFSET_SESSION = 1
    private const val HEADER_V2_OFFSET_INDEX = HEADER_V2_OFFSET_SESSION + 8
    private const val HEADER_V2_OFFSET_TOTAL = HEADER_V2_OFFSET_INDEX + 1
    private const val HEADER_V2_OFFSET_SALT = HEADER_V2_OFFSET_TOTAL + 1
    private const val HEADER_V2_OFFSET_IV = HEADER_V2_OFFSET_SALT + SALT_LENGTH_BYTES
    private const val HEADER_V2_OFFSET_CIPHERTEXT = HEADER_V2_OFFSET_IV + IV_LENGTH_BYTES

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
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)

        val now = System.currentTimeMillis()
        val expiresAt = now + (durationSeconds * 1000L)

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
                val envelope = ByteArray(HEADER_V2_OFFSET_CIPHERTEXT + chunkBytes.size)
                envelope[0] = SecurityConfig.TRANSFER_QR_VERSION_V2.toByte()

                // Session ID (8 bytes)
                for (b in 0..7) {
                    envelope[HEADER_V2_OFFSET_SESSION + b] = (sessionId ushr (56 - b * 8)).toByte()
                }

                envelope[HEADER_V2_OFFSET_INDEX] = index.toByte()
                envelope[HEADER_V2_OFFSET_TOTAL] = total.toByte()
                System.arraycopy(salt, 0, envelope, HEADER_V2_OFFSET_SALT, SALT_LENGTH_BYTES)
                System.arraycopy(iv, 0, envelope, HEADER_V2_OFFSET_IV, IV_LENGTH_BYTES)
                System.arraycopy(chunkBytes, 0, envelope, HEADER_V2_OFFSET_CIPHERTEXT, chunkBytes.size)

                val base64Envelope = Base64.getUrlEncoder().withoutPadding().encodeToString(envelope)
                resultQrStrings.add("$QR_TRANSFER_PREFIX$base64Envelope")
            }

            return resultQrStrings
        } finally {
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
        val trimmed = qrPayload.trim()
        val rawEncoded = if (trimmed.startsWith(QR_TRANSFER_PREFIX, ignoreCase = true)) {
            trimmed.substring(QR_TRANSFER_PREFIX.length).trim()
        } else {
            trimmed
        }

        val binaryEnvelope = try {
            Base64.getUrlDecoder().decode(rawEncoded)
        } catch (_: Exception) {
            Base64.getDecoder().decode(rawEncoded)
        }

        if (binaryEnvelope.isEmpty()) {
            throw IllegalArgumentException("Sobre de transferencia vacío")
        }

        val version = binaryEnvelope[0].toInt() and 0xFF

        return when (version) {
            SecurityConfig.TRANSFER_QR_VERSION -> {
                if (binaryEnvelope.size <= HEADER_V1_OFFSET_CIPHERTEXT) {
                    throw IllegalArgumentException("Tamaño de sobre de transferencia v1 inválido")
                }
                val salt = binaryEnvelope.copyOfRange(HEADER_V1_OFFSET_SALT, HEADER_V1_OFFSET_IV)
                val iv = binaryEnvelope.copyOfRange(HEADER_V1_OFFSET_IV, HEADER_V1_OFFSET_CIPHERTEXT)
                val ciphertext = binaryEnvelope.copyOfRange(HEADER_V1_OFFSET_CIPHERTEXT, binaryEnvelope.size)
                TransferQrChunk(
                    version = version,
                    sessionId = 0L,
                    index = 1,
                    total = 1,
                    salt = salt,
                    iv = iv,
                    chunkCiphertext = ciphertext
                )
            }
            SecurityConfig.TRANSFER_QR_VERSION_V2 -> {
                if (binaryEnvelope.size <= HEADER_V2_OFFSET_CIPHERTEXT) {
                    throw IllegalArgumentException("Tamaño de sobre de transferencia v2 inválido")
                }
                var sessionId = 0L
                for (b in 0..7) {
                    sessionId = (sessionId shl 8) or (binaryEnvelope[HEADER_V2_OFFSET_SESSION + b].toLong() and 0xFF)
                }
                val index = binaryEnvelope[HEADER_V2_OFFSET_INDEX].toInt() and 0xFF
                val total = binaryEnvelope[HEADER_V2_OFFSET_TOTAL].toInt() and 0xFF
                val salt = binaryEnvelope.copyOfRange(HEADER_V2_OFFSET_SALT, HEADER_V2_OFFSET_IV)
                val iv = binaryEnvelope.copyOfRange(HEADER_V2_OFFSET_IV, HEADER_V2_OFFSET_CIPHERTEXT)
                val chunkCiphertext = binaryEnvelope.copyOfRange(HEADER_V2_OFFSET_CIPHERTEXT, binaryEnvelope.size)

                TransferQrChunk(
                    version = version,
                    sessionId = sessionId,
                    index = index,
                    total = total,
                    salt = salt,
                    iv = iv,
                    chunkCiphertext = chunkCiphertext
                )
            }
            else -> throw IllegalArgumentException("Versión de sobre de transferencia no soportada: $version")
        }
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

            // Verificar consistencia de sesión
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

            // Ensamblar texto cifrado completo
            val totalCiphertextSize = sortedChunks.sumOf { it.chunkCiphertext.size }
            val fullCiphertext = ByteArray(totalCiphertextSize)
            var currentOffset = 0
            for (chunk in sortedChunks) {
                System.arraycopy(chunk.chunkCiphertext, 0, fullCiphertext, currentOffset, chunk.chunkCiphertext.size)
                currentOffset += chunk.chunkCiphertext.size
            }

            val salt = first.salt
            val iv = first.iv

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
                    decryptedCompressedBytes = cipher.doFinal(fullCiphertext)
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
