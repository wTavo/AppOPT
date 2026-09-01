package com.example.appopt.domain.model

/**
 * Algoritmos criptográficos HMAC soportados conforme al estándar RFC 6238.
 *
 * @property standardName Nombre canónico en el estándar URI (`SHA1`, `SHA256`, `SHA512`).
 * @property hmacAlgorithm Nombre del algoritmo en la API JCE de Java Cryptography (`HmacSHA1`, etc.).
 */
enum class OtpAlgorithm(val standardName: String, val hmacAlgorithm: String) {
    SHA1("SHA1", "HmacSHA1"),
    SHA256("SHA256", "HmacSHA256"),
    SHA512("SHA512", "HmacSHA512");

    companion object {
        /**
         * Parsea una cadena de texto a un [OtpAlgorithm], utilizando [SHA1] por defecto si no se especifica.
         */
        fun fromString(value: String?): OtpAlgorithm {
            return when (value?.uppercase()?.trim()) {
                "SHA256", "HMAC-SHA256", "HMACSHA256" -> SHA256
                "SHA512", "HMAC-SHA512", "HMACSHA512" -> SHA512
                else -> SHA1
            }
        }
    }
}

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
