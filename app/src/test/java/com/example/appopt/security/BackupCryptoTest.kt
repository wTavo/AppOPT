package com.example.appopt.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para [BackupCrypto] validando la derivación de clave con PBKDF2,
 * el cifrado AES-GCM y la detección de contraseñas erróneas / manipulación de datos.
 */
class BackupCryptoTest {

    private val sampleJson = """{"version":1,"accounts":[{"id":"1","issuer":"Google","accountName":"test@gmail.com","secret":"JBSWY3DPEHPK3PXP"}]}"""
    private val correctPassword = "MasterPassword123!".toCharArray()
    private val wrongPassword = "WrongPassword999!".toCharArray()

    /**
     * Valida que un respaldo cifrado con la contraseña correcta se descifre idénticamente.
     */
    @Test
    fun testEncryptAndDecryptWithCorrectPassword() {
        val encryptedBytes = BackupCrypto.encryptBackup(sampleJson, correctPassword)
        assertTrue(encryptedBytes.isNotEmpty())

        val decryptResult = BackupCrypto.decryptBackup(encryptedBytes, correctPassword)
        assertTrue(decryptResult.isSuccess)
        assertEquals(sampleJson, decryptResult.getOrThrow())
    }

    /**
     * Valida que una contraseña incorrecta falle por verificación de etiqueta criptográfica (Zero-Trust).
     */
    @Test
    fun testDecryptWithWrongPasswordFails() {
        val encryptedBytes = BackupCrypto.encryptBackup(sampleJson, correctPassword)
        val decryptResult = BackupCrypto.decryptBackup(encryptedBytes, wrongPassword)
        assertTrue(decryptResult.isFailure)
    }

    /**
     * Valida que un respaldo cifrado con esquema Dual-Slot (v2) pueda ser descifrado tanto
     * con la contraseña principal como con la frase mnemónica de 12 palabras de emergencia.
     */
    @Test
    fun testEncryptDualBackupDecryptsWithBothSlots() {
        val mnemonicWords = MnemonicManager.generate12WordPhrase()
        val mnemonicPhrase = MnemonicManager.normalizePhrase(mnemonicWords.joinToString(" ")).toCharArray()

        val encryptedBytes = BackupCrypto.encryptDualBackup(sampleJson, correctPassword, mnemonicPhrase)
        assertTrue(encryptedBytes.isNotEmpty())

        // 1. Descifrar con la contraseña principal
        val decryptMainResult = BackupCrypto.decryptBackup(encryptedBytes, correctPassword)
        assertTrue(decryptMainResult.isSuccess)
        assertEquals(sampleJson, decryptMainResult.getOrThrow())

        // 2. Descifrar con la frase de emergencia de 12 palabras
        val decryptEmergencyResult = BackupCrypto.decryptBackup(encryptedBytes, mnemonicPhrase)
        assertTrue(decryptEmergencyResult.isSuccess)
        assertEquals(sampleJson, decryptEmergencyResult.getOrThrow())

        // 3. Descifrar con credencial incorrecta debe fallar
        val decryptWrongResult = BackupCrypto.decryptBackup(encryptedBytes, wrongPassword)
        assertTrue(decryptWrongResult.isFailure)
    }

    /**
     * Valida que un archivo de respaldo con el ciphertext manipulado sea rechazado por fallo de integridad.
     */
    @Test
    fun testDecryptTamperedBackupFails() {
        val encryptedBytes = BackupCrypto.encryptBackup(sampleJson, correctPassword)
        val jsonEnvelope = String(encryptedBytes, Charsets.UTF_8)

        // Manipular el ciphertext dentro del sobre JSON
        val tamperedEnvelope = jsonEnvelope.replaceFirst("ciphertext\":\"", "ciphertext\":\"X")
        val decryptResult = BackupCrypto.decryptBackup(tamperedEnvelope.toByteArray(Charsets.UTF_8), correctPassword)
        assertTrue(decryptResult.isFailure)
    }
}
