package com.example.appopt.domain.model

/**
 * Modelo de dominio inmutable que representa una cuenta 2FA registrada en la aplicación.
 *
 * @property id Identificador único UUID de la cuenta.
 * @property issuer Emisor o nombre del servicio (ej. "Google", "GitHub").
 * @property accountName Identificador de la cuenta o usuario (ej. correo electrónico).
 * @property type Tipo de algoritmo de un solo uso ([OtpType.TOTP] o [OtpType.HOTP]).
 * @property algorithm Algoritmo HMAC a utilizar ([OtpAlgorithm.SHA1], SHA256, SHA512).
 * @property digits Cantidad de dígitos generados (6, 7 u 8).
 * @property period Periodo de rotación del código en segundos (típicamente 30s).
 * @property counter Valor del contador de eventos para tokens HOTP.
 * @property isFavorite Indica si el usuario fijó esta cuenta en la parte superior.
 * @property orderIndex Posición para ordenamiento personalizado.
 * @property createdAt Timestamp UNIX de creación.
 * @property updatedAt Timestamp UNIX de última modificación.
 */
data class TotpAccount(
    val id: String,
    val issuer: String,
    val accountName: String,
    val type: OtpType = OtpType.TOTP,
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
    val counter: Long = 0L,
    val isFavorite: Boolean = false,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
