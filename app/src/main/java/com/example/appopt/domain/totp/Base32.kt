package com.example.appopt.domain.totp

/**
 * Implementación estricta y pura en Kotlin del algoritmo de codificación y decodificación
 * Base32 conforme al estándar RFC 4648.
 *
 * Características de seguridad:
 * - Valida caracteres de entrada contra el alfabeto canónico (A-Z, 2-7).
 * - Sanea entradas de usuario tolerando espacios y guiones comunes al ingresar claves a mano.
 * - No recurre a librerías externas obsoletas.
 */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val DECODE_TABLE = IntArray(128) { -1 }

    init {
        for (i in ALPHABET.indices) {
            val char = ALPHABET[i]
            DECODE_TABLE[char.code] = i
            DECODE_TABLE[char.lowercaseChar().code] = i
        }
    }

    /**
     * Comprueba si una cadena de texto contiene únicamente caracteres válidos del alfabeto Base32.
     *
     * @param input Cadena a validar.
     * @return true si es una cadena Base32 válida no vacía; false en caso contrario.
     */
    fun isValid(input: String): Boolean {
        val sanitized = sanitize(input)
        if (sanitized.isEmpty()) return false
        for (c in sanitized) {
            if (c.code >= DECODE_TABLE.size || DECODE_TABLE[c.code] == -1) {
                return false
            }
        }
        return true
    }

    /**
     * Limpia la entrada de caracteres de separación (espacios, guiones, padding) y la convierte a mayúsculas.
     *
     * @param input Entrada en bruto introducida por el usuario o leída de un QR.
     * @return Cadena saneada lista para decodificar.
     */
    fun sanitize(input: String): String {
        return input.replace(" ", "")
            .replace("-", "")
            .replace("=", "")
            .trim()
            .uppercase()
    }

    /**
     * Decodifica una cadena Base32 en su representación en bytes binarios.
     *
     * @param input Cadena Base32 saneada o en bruto.
     * @return Arreglo de bytes binarios decodificados.
     * @throws IllegalArgumentException si la cadena contiene caracteres no pertenecientes al alfabeto Base32.
     */
    fun decode(input: String): ByteArray {
        val sanitized = sanitize(input)
        if (sanitized.isEmpty()) return ByteArray(0)

        for (c in sanitized) {
            require(c.code < DECODE_TABLE.size && DECODE_TABLE[c.code] != -1) {
                "Carácter Base32 inválido: '$c'"
            }
        }

        val outputSize = (sanitized.length * 5) / 8
        val result = ByteArray(outputSize)
        var buffer = 0
        var bitsLeft = 0
        var index = 0

        for (c in sanitized) {
            val value = DECODE_TABLE[c.code]
            buffer = (buffer shl 5) or value
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                if (index < outputSize) {
                    result[index++] = (buffer shr bitsLeft).toByte()
                }
            }
        }

        return result
    }

    /**
     * Codifica un arreglo de bytes en una cadena Base32.
     *
     * @param bytes Arreglo de bytes binarios.
     * @param withPadding Indica si se debe rellenar con caracteres '=' hasta múltiplo de 8.
     * @return Cadena codificada en Base32.
     */
    fun encode(bytes: ByteArray, withPadding: Boolean = false): String {
        if (bytes.isEmpty()) return ""

        val sb = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0

        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8

            while (bitsLeft >= 5) {
                bitsLeft -= 5
                val index = (buffer shr bitsLeft) and 0x1F
                sb.append(ALPHABET[index])
            }
        }

        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            sb.append(ALPHABET[index])
        }

        if (withPadding) {
            while (sb.length % 8 != 0) {
                sb.append('=')
            }
        }

        return sb.toString()
    }
}
