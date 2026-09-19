package com.example.appopt.domain.model

/**
 * Tipos de contraseñas de un solo uso soportados.
 */
enum class OtpType {
    /** Basado en tiempo (RFC 6238). */
    TOTP,
    /** Basado en contador de eventos (RFC 4226). */
    HOTP;

    companion object {
        /**
         * Parsea una cadena de texto a un [OtpType], utilizando [TOTP] por defecto.
         */
        fun fromString(value: String?): OtpType {
            return when (value?.lowercase()?.trim()) {
                "hotp" -> HOTP
                else -> TOTP
            }
        }
    }
}
