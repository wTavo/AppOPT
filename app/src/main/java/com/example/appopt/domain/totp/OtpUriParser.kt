package com.example.appopt.domain.totp

import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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

/**
 * Parser estricto e independiente para URIs de autenticación estándar de dos factores
 * del tipo `otpauth://totp/...` y `otpauth://hotp/...`.
 *
 * Principio de desconfianza del usuario:
 * - Valida esquema, host, parámetros obligatorios y opcionales.
 * - Rechaza entradas malformadas, algoritmos no soportados y longitudes fuera de rango.
 */
object OtpUriParser {

    /**
     * Analiza una cadena URI `otpauth://` y extrae sus componentes criptográficos y metadatos.
     *
     * @param uriString URI obtenido mediante escaneo QR o enlace profundo.
     * @return [Result] con [ParsedOtpData] si el formato es válido, o un error explicativo si falla.
     */
    fun parse(uriString: String): Result<ParsedOtpData> {
        return runCatching {
            val trimmed = uriString.trim()
            require(trimmed.startsWith("otpauth://", ignoreCase = true)) {
                "El esquema debe comenzar con otpauth://"
            }

            val withoutScheme = trimmed.substring("otpauth://".length)
            val questionMarkIdx = withoutScheme.indexOf('?')

            val (pathPart, queryPart) = if (questionMarkIdx != -1) {
                Pair(withoutScheme.substring(0, questionMarkIdx), withoutScheme.substring(questionMarkIdx + 1))
            } else {
                Pair(withoutScheme, "")
            }

            val slashIdx = pathPart.indexOf('/')
            val (typeStr, labelPart) = if (slashIdx != -1) {
                Pair(pathPart.substring(0, slashIdx), pathPart.substring(slashIdx + 1))
            } else {
                Pair(pathPart, "")
            }

            val type = when (typeStr.lowercase()) {
                "totp" -> OtpType.TOTP
                "hotp" -> OtpType.HOTP
                else -> throw IllegalArgumentException("Tipo OTP no soportado: $typeStr")
            }

            val decodedLabel = URLDecoder.decode(labelPart, StandardCharsets.UTF_8.name()).trim()
            var issuerFromLabel = ""
            var accountNameFromLabel = decodedLabel

            if (decodedLabel.contains(":")) {
                val parts = decodedLabel.split(":", limit = 2)
                issuerFromLabel = parts[0].trim()
                accountNameFromLabel = parts[1].trim()
            }

            // Parse Query Parameters
            val queryParams = mutableMapOf<String, String>()
            if (queryPart.isNotEmpty()) {
                val pairs = queryPart.split("&")
                for (pair in pairs) {
                    val kv = pair.split("=", limit = 2)
                    if (kv.isNotEmpty()) {
                        val key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()).lowercase().trim()
                        val value = if (kv.size > 1) URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()).trim() else ""
                        queryParams[key] = value
                    }
                }
            }

            val issuerFromParam = queryParams["issuer"].orEmpty()
            val finalIssuer = issuerFromParam.ifEmpty { issuerFromLabel.ifEmpty { "Cuenta" } }
            val finalAccount = accountNameFromLabel.ifEmpty { "Usuario" }

            val rawSecret = queryParams["secret"]
                ?: throw IllegalArgumentException("El parámetro 'secret' es obligatorio")
            val sanitizedSecret = Base32.sanitize(rawSecret)
            require(Base32.isValid(sanitizedSecret)) { "El secreto Base32 contiene caracteres no válidos" }
            val secretBytes = Base32.decode(sanitizedSecret)
            require(secretBytes.isNotEmpty()) { "El secreto decodificado no puede estar vacío" }

            val algorithmParam = queryParams["algorithm"]
            val algorithm = OtpAlgorithm.fromString(algorithmParam)

            val digitsParam = queryParams["digits"]?.toIntOrNull() ?: 6
            require(digitsParam in 6..8) { "La cantidad de dígitos debe ser 6, 7 u 8 (recibido: $digitsParam)" }

            val periodParam = queryParams["period"]?.toIntOrNull() ?: 30
            require(periodParam > 0) { "El periodo debe ser mayor a 0 segundos" }

            val counterParam = queryParams["counter"]?.toLongOrNull() ?: 0L

            ParsedOtpData(
                type = type,
                issuer = finalIssuer,
                accountName = finalAccount,
                secretBase32 = sanitizedSecret,
                secretBytes = secretBytes,
                algorithm = algorithm,
                digits = digitsParam,
                period = periodParam,
                counter = counterParam
            )
        }
    }
}
