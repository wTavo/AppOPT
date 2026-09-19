package com.example.appopt.security

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
