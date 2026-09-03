package com.example.appopt.util

import android.content.Context
import com.example.appopt.R

/**
 * Utilidades centralizadas para accesibilidad y semántica de lectores de pantalla (*TalkBack / WCAG AAA*).
 *
 * Directivas de desarrollo:
 * - PROHIBIDO dejar dígitos numéricos sin formateo accesible para lectores de pantalla.
 * - OBLIGATORIO utilizar [toAccessibleSpokenOtp] para evitar que los lectores de pantalla pronuncien
 *   los códigos TOTP como números cardinales millonarios (ej. pronunciar "1 2 3 - 4 5 6" en lugar de "ciento veintitrés mil cuatrocientos cincuenta y seis").
 */
object AccessibilityUtils {

    /**
     * Transforma una cadena de dígitos OTP en una secuencia legible individualmente por sintetizadores de voz.
     *
     * @param code Código OTP numérico en bruto (ej. "123456").
     * @return Cadena con dígitos separados por espacios y pausas (ej. "1 2 3 , 4 5 6").
     */
    fun toAccessibleSpokenOtp(code: String): String {
        if (code.isBlank()) return ""
        val digits = code.toCharArray()
        return when (digits.size) {
            6 -> "${digits.take(3).joinToString(" ")} , ${digits.drop(3).joinToString(" ")}"
            8 -> "${digits.take(4).joinToString(" ")} , ${digits.drop(4).joinToString(" ")}"
            else -> digits.joinToString(" ")
        }
    }

    /**
     * Construye una descripción semántica completa para una tarjeta de código OTP accesible para TalkBack.
     *
     * @param context Contexto de la aplicación para resolver recursos de texto.
     * @param issuer Nombre del emisor o servicio.
     * @param accountName Nombre de la cuenta o usuario.
     * @param code Código numérico actual.
     * @param remainingSeconds Segundos restantes del período TOTP.
     * @param isFavorite Indica si la cuenta está marcada como favorita.
     * @return Cadena estructurada con la información semántica para lectores de pantalla.
     */
    fun buildAccountCardContentDescription(
        context: Context,
        issuer: String,
        accountName: String,
        code: String,
        remainingSeconds: Int,
        isFavorite: Boolean
    ): String {
        val spokenCode = toAccessibleSpokenOtp(code)
        val issuerLabel = issuer.ifEmpty { context.getString(R.string.home_default_issuer) }
        val accountPart = if (accountName.isNotBlank()) ", $accountName" else ""
        val favPart = if (isFavorite) ", ${context.getString(R.string.action_favorite)}" else ""
        val timePart = if (remainingSeconds > 0) ", $remainingSeconds ${context.getString(R.string.card_seconds_abbrev)}" else ""

        return "$issuerLabel$accountPart$favPart. $spokenCode$timePart"
    }
}
