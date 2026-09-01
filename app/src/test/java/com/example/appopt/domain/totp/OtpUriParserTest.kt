package com.example.appopt.domain.totp

import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para [OtpUriParser] validando el análisis y la sanitización
 * de URIs `otpauth://totp/...` y `otpauth://hotp/...`.
 */
class OtpUriParserTest {

    /**
     * Valida el análisis de un URI TOTP estándar con emisor, usuario, secreto y periodo.
     */
    @Test
    fun testParseStandardTotpUri() {
        val uri = "otpauth://totp/Google:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Google&algorithm=SHA1&digits=6&period=30"
        val result = OtpUriParser.parse(uri)

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals(OtpType.TOTP, data.type)
        assertEquals("Google", data.issuer)
        assertEquals("user@example.com", data.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", data.secretBase32)
        assertEquals(OtpAlgorithm.SHA1, data.algorithm)
        assertEquals(6, data.digits)
        assertEquals(30, data.period)
    }

    /**
     * Valida la correcta decodificación de caracteres especiales codificados en URL (espacios, etc.).
     */
    @Test
    fun testParseUriWithEncodedCharacters() {
        val uri = "otpauth://totp/Rockstar%20Games:john%20doe?secret=MZXW6YTB&issuer=Rockstar%20Games&algorithm=SHA256&digits=8"
        val result = OtpUriParser.parse(uri)

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("Rockstar Games", data.issuer)
        assertEquals("john doe", data.accountName)
        assertEquals(OtpAlgorithm.SHA256, data.algorithm)
        assertEquals(8, data.digits)
    }

    /**
     * Valida el análisis de URIs de tipo HOTP con parámetro de contador inicial.
     */
    @Test
    fun testParseHotpUri() {
        val uri = "otpauth://hotp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&counter=42"
        val result = OtpUriParser.parse(uri)

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals(OtpType.HOTP, data.type)
        assertEquals("GitHub", data.issuer)
        assertEquals("octocat", data.accountName)
        assertEquals(42L, data.counter)
    }

    /**
     * Valida el principio de desconfianza del usuario: rechazar URIs con esquemas erróneos,
     * secretos faltantes o caracteres ilegales.
     */
    @Test
    fun testParseInvalidUriFailsGracefully() {
        val invalidScheme = "https://example.com"
        assertTrue(OtpUriParser.parse(invalidScheme).isFailure)

        val missingSecret = "otpauth://totp/Test:user"
        assertTrue(OtpUriParser.parse(missingSecret).isFailure)

        val invalidSecret = "otpauth://totp/Test:user?secret=InvalidChars89!"
        assertTrue(OtpUriParser.parse(invalidSecret).isFailure)
    }
}
