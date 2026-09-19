package com.example.appopt.security

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
            "日本語サービス", // CJK está permitido
            "12345_!@#$%^&*()",
            "Cuenta_de_Prueba-123"
        )

        for (input in cleanInputs) {
            val result = SecurityAnalysisUtils.detectUnicodeSpoofing(input)
            assertFalse("El texto '$input' no debería ser sospechoso", result.isSuspicious)
            assertFalse(result.hasInvisibleChars)
            assertFalse(result.hasMixedScripts)
            assertTrue(result.suspiciousCharacters.isEmpty())
        }
    }

    @Test
    fun detectUnicodeSpoofing_emptyOrBlank_returnsNotSuspicious() {
        val resultEmpty = SecurityAnalysisUtils.detectUnicodeSpoofing("")
        assertFalse(resultEmpty.isSuspicious)

        val resultWhitespace = SecurityAnalysisUtils.detectUnicodeSpoofing("   ")
        assertFalse(resultWhitespace.isSuspicious)
    }

    @Test
    fun detectUnicodeSpoofing_zeroWidthCharacters_detected() {
        // "Google" con Zero-Width Space (\u200B) intercalado
        val payloadWithZeroWidth = "Goo\u200Bgle"
        val resultZws = SecurityAnalysisUtils.detectUnicodeSpoofing(payloadWithZeroWidth)

        assertTrue(resultZws.isSuspicious)
        assertTrue(resultZws.hasInvisibleChars)
        assertEquals(listOf('\u200B'), resultZws.suspiciousCharacters)

        // "GitHub" con BOM (\uFEFF)
        val payloadWithBom = "\uFEFFGitHub"
        val resultBom = SecurityAnalysisUtils.detectUnicodeSpoofing(payloadWithBom)

        assertTrue(resultBom.isSuspicious)
        assertTrue(resultBom.hasInvisibleChars)
        assertEquals(listOf('\uFEFF'), resultBom.suspiciousCharacters)

        // "Amazon" con Soft Hyphen (\u00AD)
        val payloadWithSh = "Ama\u00ADzon"
        val resultSh = SecurityAnalysisUtils.detectUnicodeSpoofing(payloadWithSh)

        assertTrue(resultSh.isSuspicious)
        assertTrue(resultSh.hasInvisibleChars)
        assertEquals(listOf('\u00AD'), resultSh.suspiciousCharacters)
    }

    @Test
    fun detectUnicodeSpoofing_mixedScriptHomoglyphs_detected() {
        // "Google" donde la 'o' es Cyrillic Small Letter O (\u043E)
        val cyrillicGoogle = "G\u043E\u043Egle"
        val resultGoogle = SecurityAnalysisUtils.detectUnicodeSpoofing(cyrillicGoogle)

        assertTrue("Debe detectar mezcla latino-cirílica", resultGoogle.isSuspicious)
        assertTrue(resultGoogle.hasMixedScripts)
        assertEquals(listOf('\u043E'), resultGoogle.suspiciousCharacters)

        // "Paypal" donde la 'a' es Cyrillic Small Letter A (\u0430)
        val cyrillicPaypal = "P\u0430yp\u0430l"
        val resultPaypal = SecurityAnalysisUtils.detectUnicodeSpoofing(cyrillicPaypal)

        assertTrue("Debe detectar homóglifos cirílicos en Paypal", resultPaypal.isSuspicious)
        assertTrue(resultPaypal.hasMixedScripts)
        assertEquals(listOf('\u0430'), resultPaypal.suspiciousCharacters)

        // "Microsoft" donde la 'o' es Greek Small Letter Omicron (\u03BF)
        val greekMicrosoft = "Micr\u03BFs\u03BFft"
        val resultMs = SecurityAnalysisUtils.detectUnicodeSpoofing(greekMicrosoft)

        assertTrue("Debe detectar mezcla con alfabeto griego", resultMs.isSuspicious)
        assertTrue(resultMs.hasMixedScripts)
        assertEquals(listOf('\u03BF'), resultMs.suspiciousCharacters)
    }

    @Test
    fun findExistingDuplicate_caseAndWhitespaceInsensitive() {
        val existing = listOf(
            TotpAccount(
                id = "1",
                issuer = "Google",
                accountName = "user@gmail.com",
                type = OtpType.TOTP,
                algorithm = OtpAlgorithm.SHA1,
                digits = 6,
                period = 30,
                counter = 0,
                isFavorite = false,
                isDeleted = false,
                deletedAt = null,
                createdAt = 1000L,
                updatedAt = 1000L
            ),
            TotpAccount(
                id = "2",
                issuer = "GitHub",
                accountName = "octocat",
                type = OtpType.TOTP,
                algorithm = OtpAlgorithm.SHA1,
                digits = 6,
                period = 30,
                counter = 0,
                isFavorite = false,
                isDeleted = false,
                deletedAt = null,
                createdAt = 2000L,
                updatedAt = 2000L
            )
        )

        // Coincidencia exacta con espacios y mayúsculas variadas
        val match1 = SecurityAnalysisUtils.findExistingDuplicate(existing, "google ", " USER@gmail.com")
        assertNotNull(match1)
        assertEquals("1", match1?.id)

        // Mismo emisor pero diferente cuenta
        val match2 = SecurityAnalysisUtils.findExistingDuplicate(existing, "Microsoft", "user@gmail.com")
        assertNull(match2)

        // Misma cuenta pero diferente emisor
        val match3 = SecurityAnalysisUtils.findExistingDuplicate(existing, "Google", "other@gmail.com")
        assertNull(match3)
    }
}
