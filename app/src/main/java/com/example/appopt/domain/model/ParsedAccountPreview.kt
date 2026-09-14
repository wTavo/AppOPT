package com.example.appopt.domain.model

/**
 * Modelo de datos inmutable para la previsualización y selección de cuentas en flujos
 * de transferencia por código QR y restauración de copias de seguridad (Google Drive / Archivo).
 *
 * @property id Identificador único temporal o persistente de la cuenta.
 * @property issuer Nombre del emisor o servicio (ej. Google, GitHub).
 * @property accountName Nombre de cuenta o usuario asociado (ej. usuario@gmail.com).
 * @property algorithm Algoritmo HMAC utilizado ([OtpAlgorithm.SHA1], [OtpAlgorithm.SHA256], [OtpAlgorithm.SHA512]).
 * @property digits Cantidad de dígitos del código OTP (6 u 8).
 * @property period Período de validez en segundos para TOTP.
 * @property type Tipo de OTP ([OtpType.TOTP] o [OtpType.HOTP]).
 * @property counter Contador de eventos para HOTP.
 * @property isFavorite Indicador de cuenta marcada como favorita.
 * @property isAlreadyInVault Indica si una cuenta con el mismo secreto criptográfico ya existe en la bóveda local.
 * @property secretBytes Bytes del secreto en memoria volátil (zeroizados tras la importación).
 */
data class ParsedAccountPreview(
    val id: String,
    val issuer: String,
    val accountName: String,
    val algorithm: OtpAlgorithm,
    val digits: Int,
    val period: Int,
    val type: OtpType,
    val counter: Long,
    val isFavorite: Boolean,
    val isAlreadyInVault: Boolean,
    val secretBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ParsedAccountPreview
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
