package com.example.appopt.util

import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.model.TotpAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de regresión para [SecurityAnalysisUtils].
 */
class SecurityAnalysisUtilsTest {

    @Test
    fun detectUnicodeSpoofing_cleanString_returnsNotSuspicious() {
        val cleanInputs = listOf(
            "Google",
            "GitHub",
            "Microsoft (Personal)",
            "Amazon Web Services",
            "Usuario: juan.perez@empresa.com",
            "Servicio 123",
            "Banco Santander México", // Acentos legítimos en bloque latino
            "Configuración y autenticación"
        )

        for (input in cleanInputs) {
            val result = SecurityAnalysisUtils.detectUnicodeSpoofing(input)
            assertFalse("'$input' no debería ser sospechoso", result.isSuspicious)
            assertFalse(result.hasInvisibleChars)
            assertFalse(result.hasMixedScripts)
            assertTrue(result.suspiciousCharacters.isEmpty())
        }
    }

    @Test
    fun detectUnicodeSpoofing_blankInput_returnsNotSuspicious() {
        val resultEmpty = SecurityAnalysisUtils.detectUnicodeSpoofing("")
        assertFalse(resultEmpty.isSuspicious)

        val resultWhitespace = SecurityAnalysisUtils.detectUnicodeSpoofing("   ")
        assertFalse(resultWhitespace.isSuspicious)
    }

    @Test
    fun detectUnicodeSpoofing_invisibleCharacters_detectedCorrectly() {
        // Zero-Width Space dentro de PayPal
        val payloadWithZeroWidth = "Pay\u200BPal"
        val resultZws = SecurityAnalysisUtils.detectUnicodeSpoofing(payloadWithZeroWidth)
        assertTrue(resultZws.isSuspicious)
        assertTrue(resultZws.hasInvisibleChars)
        assertTrue(resultZws.suspiciousCharacters.contains('\u200B'))

        // BOM al inicio
        val payloadWithBom = "\uFEFFGoogle"
        val resultBom = SecurityAnalysisUtils.detectUnicodeSpoofing(payloadWithBom)
        assertTrue(resultBom.isSuspicious)
        assertTrue(resultBom.hasInvisibleChars)

        // Soft Hyphen
        val payloadWithSh = "Dis\u00ADcord"
        val resultSh = SecurityAnalysisUtils.detectUnicodeSpoofing(payloadWithSh)
        assertTrue(resultSh.isSuspicious)
        assertTrue(resultSh.hasInvisibleChars)
    }

    @Test
    fun detectUnicodeSpoofing_mixedScriptHomoglyphs_detectedCorrectly() {
        // 'Gооgle' con 'о' cirílicas (\u043E)
        val cyrillicGoogle = "G\u043E\u043Egle"
        val resultGoogle = SecurityAnalysisUtils.detectUnicodeSpoofing(cyrillicGoogle)
        assertTrue(resultGoogle.isSuspicious)
        assertTrue(resultGoogle.hasMixedScripts)
        assertTrue(resultGoogle.suspiciousCharacters.contains('\u043E'))

        // 'PayPаl' con 'а' cirílica (\u0430)
        val cyrillicPaypal = "PayP\u0430l"
        val resultPaypal = SecurityAnalysisUtils.detectUnicodeSpoofing(cyrillicPaypal)
        assertTrue(resultPaypal.isSuspicious)
        assertTrue(resultPaypal.hasMixedScripts)
        assertTrue(resultPaypal.suspiciousCharacters.contains('\u0430'))

        // 'Micrοsoft' con 'ο' griega (\u03BF)
        val greekMicrosoft = "Micr\u03BFsoft"
        val resultMs = SecurityAnalysisUtils.detectUnicodeSpoofing(greekMicrosoft)
        assertTrue(resultMs.isSuspicious)
        assertTrue(resultMs.hasMixedScripts)
        assertTrue(resultMs.suspiciousCharacters.contains('\u03BF'))
    }

    @Test
    fun findExistingDuplicate_matchesCaseInsensitive() {
        val existing = listOf(
            TotpAccount(
                id = "acc-1",
                issuer = "Google",
                accountName = "user@gmail.com",
                algorithm = OtpAlgorithm.SHA1,
                digits = 6,
                period = 30,
                type = OtpType.TOTP,
                counter = 0,
                createdAt = 1000L,
                updatedAt = 1000L
            ),
            TotpAccount(
                id = "acc-2",
                issuer = "GitHub",
                accountName = "developer",
                algorithm = OtpAlgorithm.SHA1,
                digits = 6,
                period = 30,
                type = OtpType.TOTP,
                counter = 0,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        // Coincidencia exacta ignorando mayúsculas y espacios
        val match1 = SecurityAnalysisUtils.findExistingDuplicate(existing, "google ", " USER@gmail.com")
        assertNotNull(match1)
        assertEquals("acc-1", match1?.id)

        // No coincide el emisor
        val match2 = SecurityAnalysisUtils.findExistingDuplicate(existing, "Microsoft", "user@gmail.com")
        assertNull(match2)

        // No coincide la cuenta
        val match3 = SecurityAnalysisUtils.findExistingDuplicate(existing, "Google", "other@gmail.com")
        assertNull(match3)
    }
}
