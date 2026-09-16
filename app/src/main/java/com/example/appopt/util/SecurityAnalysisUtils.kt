package com.example.appopt.util

import com.example.appopt.domain.model.TotpAccount
import java.lang.Character.UnicodeScript

/**
 * Resultado del análisis de seguridad antifraude y detección de homóglifos Unicode en textos de emisores o cuentas.
 *
 * @property hasInvisibleChars Indica si se encontraron caracteres invisibles o de ancho cero.
 * @property hasMixedScripts Indica si se detectó mezcla sospechosa de alfabetos (ej. caracteres cirílicos/griegos dentro de palabras latinas).
 * @property suspiciousCharacters Colección de caracteres específicos identificados como anómalos o sospechosos.
 */
data class SpoofingAnalysisResult(
    val hasInvisibleChars: Boolean,
    val hasMixedScripts: Boolean,
    val suspiciousCharacters: List<Char>
) {
    /**
     * Determina si el texto analizado presenta algún indicador de riesgo de suplantación visual (*spoofing*).
     */
    val isSuspicious: Boolean
        get() = hasInvisibleChars || hasMixedScripts
}

/**
 * Utilidades puras de análisis de seguridad para la prevención del factor humano e ingeniería social en el escaneo de códigos.
 *
 * Responsabilidades:
 * - Detectar caracteres invisibles y de ancho cero empleados para eludir filtros visuales.
 * - Detectar ataques de homóglifos por mezcla de alfabetos (*Mixed-Script IDN Homoglyph Spoofing*).
 * - Identificar cuentas duplicadas o preexistentes en la bóveda antes de persistir nuevos registros.
 */
object SecurityAnalysisUtils {

    /**
     * Conjunto de caracteres invisibles, de ancho cero o de control bidireccional comúnmente utilizados en ataques de ofuscación.
     */
    private val INVISIBLE_CHARACTERS = setOf(
        '\u200B', // Zero-Width Space
        '\u200C', // Zero-Width Non-Joiner
        '\u200D', // Zero-Width Joiner
        '\u200E', // Left-to-Right Mark
        '\u200F', // Right-to-Left Mark
        '\uFEFF', // Zero-Width No-Break Space (BOM)
        '\u00AD', // Soft Hyphen
        '\u202A', // Left-to-Right Embedding
        '\u202B', // Right-to-Left Embedding
        '\u202C', // Pop Directional Formatting
        '\u202D', // Left-to-Right Override
        '\u202E', // Right-to-Right Override
        '\u2060', // Word Joiner
        '\u2061', // Function Application
        '\u2062', // Invisible Times
        '\u2063', // Invisible Separator
        '\u2064'  // Invisible Plus
    )

    /**
     * Analiza una cadena de texto (emisor o nombre de cuenta) en busca de técnicas de suplantación visual.
     *
     * @param text Texto a evaluar obtenido del código QR o entrada del usuario.
     * @return [SpoofingAnalysisResult] con el diagnóstico detallado.
     */
    fun detectUnicodeSpoofing(text: String): SpoofingAnalysisResult {
        if (text.isBlank()) {
            return SpoofingAnalysisResult(
                hasInvisibleChars = false,
                hasMixedScripts = false,
                suspiciousCharacters = emptyList()
            )
        }

        val suspiciousChars = mutableListOf<Char>()
        var foundInvisible = false

        // 1. Detección de caracteres invisibles o de control de dirección
        for (char in text) {
            if (char in INVISIBLE_CHARACTERS) {
                foundInvisible = true
                suspiciousChars.add(char)
            }
        }

        // 2. Detección de mezcla de alfabetos (ej. mezclar caracteres latinos con cirílicos o griegos)
        var foundMixedScripts = false
        val words = text.split(Regex("\\s+"))

        for (word in words) {
            if (word.length < 2) continue

            var hasLatin = false
            var hasConfusableScript = false

            for ((i, element) in word.withIndex()) {
                val codePoint = word.codePointAt(i)
                val script = try {
                    UnicodeScript.of(codePoint)
                } catch (_: IllegalArgumentException) {
                    UnicodeScript.UNKNOWN
                }

                when (script) {
                    UnicodeScript.LATIN -> hasLatin = true
                    UnicodeScript.CYRILLIC, UnicodeScript.GREEK -> {
                        hasConfusableScript = true
                        suspiciousChars.add(element)
                    }
                    else -> {
                        // Otros scripts como Han, Arabic o Common se tratan como no conflictivos
                    }
                }
            }

            if (hasLatin && hasConfusableScript) {
                foundMixedScripts = true
            }
        }

        return SpoofingAnalysisResult(
            hasInvisibleChars = foundInvisible,
            hasMixedScripts = foundMixedScripts,
            suspiciousCharacters = suspiciousChars.distinct()
        )
    }

    /**
     * Busca si ya existe una cuenta con el mismo emisor y nombre de usuario en la colección activa.
     *
     * @param existingAccounts Lista de cuentas activas en la bóveda local.
     * @param issuer Nombre del emisor o servicio a evaluar.
     * @param accountName Nombre de cuenta o usuario a evaluar.
     * @return [TotpAccount] coincidente si existe un duplicado exacto (insensible a mayúsculas), o `null` si no hay coincidencias.
     */
    fun findExistingDuplicate(
        existingAccounts: List<TotpAccount>,
        issuer: String,
        accountName: String
    ): TotpAccount? {
        val cleanIssuer = issuer.trim()
        val cleanAccount = accountName.trim()

        return existingAccounts.firstOrNull { account ->
            account.issuer.trim().equals(cleanIssuer, ignoreCase = true) &&
                account.accountName.trim().equals(cleanAccount, ignoreCase = true)
        }
    }
}
