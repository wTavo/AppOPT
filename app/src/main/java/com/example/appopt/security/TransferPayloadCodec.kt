package com.example.appopt.security

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Codificador y decodificador binario de sobres de transferencia QR y compresión de alta densidad.
 *
 * Aísla el empaquetado de cabeceras, la codificación Base64 y los algoritmos Deflate/Inflate
 * de la lógica criptográfica pura (Directivas 8, 9 y 29).
 */
object TransferPayloadCodec {

    // Offsets para Versión 1: [Versión (1B)][Salt (16B)][IV (12B)][Ciphertext]
    const val HEADER_V1_VERSION_BYTES = 1
    const val SALT_LENGTH_BYTES = 16
    const val IV_LENGTH_BYTES = 12
    const val HEADER_V1_OFFSET_SALT = HEADER_V1_VERSION_BYTES
    const val HEADER_V1_OFFSET_IV = HEADER_V1_OFFSET_SALT + SALT_LENGTH_BYTES
    const val HEADER_V1_OFFSET_CIPHERTEXT = HEADER_V1_OFFSET_IV + IV_LENGTH_BYTES

    // Offsets para Versión 2 (Todo o Nada): [Versión (1B)][SessionId (8B)][Index (1B)][Total (1B)][Salt (16B)][IV (12B)][ChunkCiphertext]
    const val HEADER_V2_OFFSET_SESSION = 1
    const val HEADER_V2_OFFSET_INDEX = HEADER_V2_OFFSET_SESSION + 8
    const val HEADER_V2_OFFSET_TOTAL = HEADER_V2_OFFSET_INDEX + 1
    const val HEADER_V2_OFFSET_SALT = HEADER_V2_OFFSET_TOTAL + 1
    const val HEADER_V2_OFFSET_IV = HEADER_V2_OFFSET_SALT + SALT_LENGTH_BYTES
    const val HEADER_V2_OFFSET_CIPHERTEXT = HEADER_V2_OFFSET_IV + IV_LENGTH_BYTES

    /**
     * Comprime un arreglo de bytes utilizando el algoritmo Deflate (nivel de compresión máxima).
     *
     * @param data Arreglo de bytes sin comprimir.
     * @return Arreglo de bytes comprimido.
     */
    fun compress(data: ByteArray): ByteArray {
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
     * Descomprime un arreglo de bytes previamente comprimido con Deflate.
     *
     * @param data Arreglo de bytes comprimido.
     * @return Arreglo de bytes descomprimido.
     */
    fun decompress(data: ByteArray): ByteArray {
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
     * Empaqueta un fragmento individual de versión 2 en su sobre binario codificado en Base64 URL-safe.
     *
     * @param sessionId Identificador aleatorio de sesión de transferencia.
     * @param index Índice ordinal del fragmento (1..total).
     * @param total Total de fragmentos de la sesión.
     * @param salt Sal criptográfica de 16 bytes.
     * @param iv Vector de inicialización de 12 bytes.
     * @param chunkBytes Segmento de texto cifrado de este fragmento.
     * @return Cadena Base64 URL-safe sin padding con el sobre binario completo.
     */
    fun encodeChunkV2(
        sessionId: Long,
        index: Int,
        total: Int,
        salt: ByteArray,
        iv: ByteArray,
        chunkBytes: ByteArray
    ): String {
        val envelope = ByteArray(HEADER_V2_OFFSET_CIPHERTEXT + chunkBytes.size)
        envelope[0] = SecurityConfig.TRANSFER_QR_VERSION_V2.toByte()

        for (b in 0..7) {
            envelope[HEADER_V2_OFFSET_SESSION + b] = (sessionId ushr (56 - b * 8)).toByte()
        }

        envelope[HEADER_V2_OFFSET_INDEX] = index.toByte()
        envelope[HEADER_V2_OFFSET_TOTAL] = total.toByte()
        System.arraycopy(salt, 0, envelope, HEADER_V2_OFFSET_SALT, SALT_LENGTH_BYTES)
        System.arraycopy(iv, 0, envelope, HEADER_V2_OFFSET_IV, IV_LENGTH_BYTES)
        System.arraycopy(chunkBytes, 0, envelope, HEADER_V2_OFFSET_CIPHERTEXT, chunkBytes.size)

        return Base64.getUrlEncoder().withoutPadding().encodeToString(envelope)
    }

    /**
     * Parsea e inspecciona la cabecera binaria de un código QR sin descifrar su contenido.
     *
     * @param qrPayload Cadena de texto leída del código QR.
     * @param qrPrefix Prefijo canónico de transferencia (ej. "appopt-transfer:").
     * @return Instancia de [TransferQrChunk].
     * @throws IllegalArgumentException Si el payload está vacío o tiene una estructura inválida.
     */
    fun parseChunk(qrPayload: String, qrPrefix: String): TransferQrChunk {
        val trimmed = qrPayload.trim()
        val rawEncoded = if (trimmed.startsWith(qrPrefix, ignoreCase = true)) {
            trimmed.substring(qrPrefix.length).trim()
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

        return when (val version = binaryEnvelope[0].toInt() and 0xFF) {
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
}
