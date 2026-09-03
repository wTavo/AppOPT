package com.example.appopt.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para [MnemonicManager] y su integración con el cifrado de copias de seguridad.
 */
class MnemonicManagerTest {

    @Test
    fun testGenerate12WordPhraseGeneratesValidWords() {
        val words = MnemonicManager.generate12WordPhrase()
        assertEquals(12, words.size)
        assertTrue(words.all { it.isNotBlank() })

        val phrase = words.joinToString(" ")
        assertTrue(MnemonicManager.isValid12WordPhrase(phrase))
    }

    @Test
    fun testValidationRejectsInvalidPhrase() {
        // Menos de 12 palabras
        assertFalse(MnemonicManager.isValid12WordPhrase("abandon ability able"))

        // Palabras que no están en el diccionario BIP-39
        val invalidPhrase = "abandon ability able about above absent absorb abstract absurd abuse access nonexistingword"
        assertFalse(MnemonicManager.isValid12WordPhrase(invalidPhrase))
    }

    @Test
    fun testNormalizePhraseTrimsAndHandlesWhitespace() {
        val raw = "  abandon   ABILITY   able   about  above  absent absorb abstract absurd abuse access account  "
        val normalized = MnemonicManager.normalizePhrase(raw)
        val expected = "abandon ability able about above absent absorb abstract absurd abuse access account"
        assertEquals(expected, normalized)
        assertTrue(MnemonicManager.isValid12WordPhrase(normalized))
    }

    @Test
    fun testEncryptAndDecryptWithMnemonicPhrase() {
        val sampleJson = """{"version":1,"accounts":[]}"""
        val words = MnemonicManager.generate12WordPhrase()
        val phrase = MnemonicManager.normalizePhrase(words.joinToString(" "))
        val passChars = phrase.toCharArray()

        val encryptedBytes = BackupCrypto.encryptBackup(sampleJson, passChars)
        val decryptResult = BackupCrypto.decryptBackup(encryptedBytes, passChars)

        assertTrue(decryptResult.isSuccess)
        assertEquals(sampleJson, decryptResult.getOrThrow())
    }
}
