package com.example.appopt.domain.totp

import com.example.appopt.domain.model.OtpAlgorithm
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Motor criptográfico puro para la generación de contraseñas de un solo uso
 * basadas en tiempo (TOTP - RFC 6238) y basadas en contador (HOTP - RFC 4226).
 *
 * Principio de diseño:
 * - Aislado de APIs de Android, UI y bases de datos para permitir pruebas deterministas.
 * - Validación estricta de parámetros para mitigar datos manipulados o corruptos.
 */
object TotpEngine {

    /**
     * Tabla de módulos precalculados para truncamiento rápido de dígitos (10^N).
     */
    private val DIGITS_MODULO = intArrayOf(
        1,
        10,
        100,
        1_000,
        10_000,
        100_000,
        1_000_000,
        10_000_000,
        100_000_000,
        1_000_000_000
    )

    /**
     * Genera un código TOTP para un secreto dado y un instante de tiempo en milisegundos.
     *
     * @param secretBytes Material de la clave secreta descifrada en memoria.
     * @param timeMillis Instante temporal UNIX en milisegundos (fuente de verdad).
     * @param periodSeconds Duración de la ventana temporal en segundos (típicamente 30s).
     * @param digits Cantidad de dígitos requeridos (6, 7 u 8).
     * @param algorithm Algoritmo HMAC a utilizar (SHA1, SHA256, SHA512).
     * @return Código OTP formateado con padding de ceros a la izquierda.
     */
    fun generateTotp(
        secretBytes: ByteArray,
        timeMillis: Long,
        periodSeconds: Int = com.example.appopt.security.SecurityConfig.DEFAULT_TOTP_PERIOD_SECONDS,
        digits: Int = com.example.appopt.security.SecurityConfig.DEFAULT_OTP_DIGITS,
        algorithm: OtpAlgorithm = OtpAlgorithm.SHA1
    ): String {
        require(periodSeconds > 0) { "El periodo debe ser mayor a 0 segundos" }
        require(digits in 6..8) { "La cantidad de dígitos debe ser 6, 7 u 8" }

        val timeSteps = (timeMillis / 1000L) / periodSeconds
        return generateHotp(secretBytes, timeSteps, digits, algorithm)
    }

    /**
     * Genera un código HOTP conforme al estándar RFC 4226.
     *
     * @param secretBytes Material de la clave secreta descifrada.
     * @param counter Valor entero de 64 bits del contador de eventos / pasos de tiempo.
     * @param digits Cantidad de dígitos del código generado.
     * @param algorithm Algoritmo HMAC a utilizar.
     * @return Código OTP numérico en formato String.
     */
    fun generateHotp(
        secretBytes: ByteArray,
        counter: Long,
        digits: Int = com.example.appopt.security.SecurityConfig.DEFAULT_OTP_DIGITS,
        algorithm: OtpAlgorithm = OtpAlgorithm.SHA1
    ): String {
        require(secretBytes.isNotEmpty()) { "El material secreto no puede estar vacío" }
        require(digits in 6..8) { "La cantidad de dígitos debe ser 6, 7 u 8" }

        // Convertir el contador a un arreglo de 8 bytes en formato Big-Endian (RFC 4226)
        val counterBytes = ByteBuffer.allocate(8)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(counter)
            .array()

        val mac = Mac.getInstance(algorithm.hmacAlgorithm)
        val keySpec = SecretKeySpec(secretBytes, algorithm.hmacAlgorithm)
        mac.init(keySpec)
        val hash = mac.doFinal(counterBytes)

        // Truncamiento dinámico (Dynamic Truncation - RFC 4226 sección 5.4)
        val offset = (hash[hash.size - 1].toInt() and 0x0F)
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val modulo = DIGITS_MODULO[digits]
        val otpValue = binary % modulo

        return otpValue.toString().padStart(digits, '0')
    }

    /**
     * Calcula los segundos restantes en la ventana temporal actual.
     *
     * @param timeMillis Instante temporal UNIX en milisegundos.
     * @param periodSeconds Periodo de rotación del código.
     * @return Número de segundos restantes (entre 1 y periodSeconds).
     */
    fun getRemainingSeconds(
        timeMillis: Long,
        periodSeconds: Int = com.example.appopt.security.SecurityConfig.DEFAULT_TOTP_PERIOD_SECONDS
    ): Int {
        if (periodSeconds <= 0) return 0
        val currentSecondInWindow = ((timeMillis / 1000L) % periodSeconds).toInt()
        return periodSeconds - currentSecondInWindow
    }

    /**
     * Obtiene el progreso fraccional de la ventana temporal actual para animaciones de UI.
     *
     * @param timeMillis Instante temporal UNIX en milisegundos.
     * @param periodSeconds Periodo de rotación del código.
     * @return Valor flotante entre 0.0 y 1.0.
     */
    fun getProgress(
        timeMillis: Long,
        periodSeconds: Int = com.example.appopt.security.SecurityConfig.DEFAULT_TOTP_PERIOD_SECONDS
    ): Float {
        if (periodSeconds <= 0) return 0f
        val remainingMillisInWindow = (periodSeconds * 1000L) - (timeMillis % (periodSeconds * 1000L))
        return remainingMillisInWindow.toFloat() / (periodSeconds * 1000L).toFloat()
    }
}
