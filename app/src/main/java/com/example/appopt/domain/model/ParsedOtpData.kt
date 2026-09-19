package com.example.appopt.domain.model

/**
 * Datos extraídos y validados tras analizar un URI `otpauth://`.
 */
data class ParsedOtpData(
    val type: OtpType,
    val issuer: String,
    val accountName: String,
    val secretBase32: String,
    val secretBytes: ByteArray,
    val algorithm: OtpAlgorithm,
    val digits: Int,
    val period: Int,
    val counter: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ParsedOtpData

        if (type != other.type) return false
        if (issuer != other.issuer) return false
        if (accountName != other.accountName) return false
        if (secretBase32 != other.secretBase32) return false
        if (!secretBytes.contentEquals(other.secretBytes)) return false
        if (algorithm != other.algorithm) return false
        if (digits != other.digits) return false
        if (period != other.period) return false
        if (counter != other.counter) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + issuer.hashCode()
        result = 31 * result + accountName.hashCode()
        result = 31 * result + secretBase32.hashCode()
        result = 31 * result + secretBytes.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + digits
        result = 31 * result + period
        result = 31 * result + counter.hashCode()
        return result
    }
}
