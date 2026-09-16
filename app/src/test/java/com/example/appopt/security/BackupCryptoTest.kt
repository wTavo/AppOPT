package com.example.appopt.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para [BackupCrypto] validando el esquema exclusivo de sobre criptográfico Versión 2,
 * derivación PBKDF2, cifrado AES-GCM, re-cifrado con claves existentes y detección de manipulaciones.
 */
class BackupCryptoTest {

    private val sampleJson = """{"version":1,"accounts":[{"id":"1","issuer":"Google","accountName":"test@gmail.com","secret":"JBSWY3DPEHPK3PXP"}]}"""
    private val updatedJson = """{"version":1,"accounts":[{"id":"1","issuer":"Google","accountName":"test@gmail.com","secret":"JBSWY3DPEHPK3PXP"},{"id":"2","issuer":"GitHub","accountName":"user@git.com","secret":"HXDMVJECJJWSRB3H"}]}"""
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
     * Valida que el re-cifrado en segundo plano mediante [BackupCrypto.encryptWithExistingKey]
     * actualice el contenido manteniendo intactas las ranuras de la contraseña y frase mnemónica,
     * permitiendo al usuario restaurar con su contraseña habitual.
     */
    @Test
    fun testEncryptWithExistingKeyPreservesUserPasswordDecryption() {
        val mnemonicWords = MnemonicManager.generate12WordPhrase()
        val mnemonicPhrase = MnemonicManager.normalizePhrase(mnemonicWords.joinToString(" ")).toCharArray()

        // 1. Creación inicial de respaldo por el usuario
        val initialResult = BackupCrypto.encryptBackupResult(
            plainJson = sampleJson,
            primaryPassword = correctPassword,
            emergencyMnemonic = mnemonicPhrase
        )

        // 2. Sincronización automática en segundo plano con nuevos datos pero misma clave y ranuras
        val reEncryptedBytes = BackupCrypto.encryptWithExistingKey(
            plainJson = updatedJson,
            vaultKey = initialResult.vaultKey,
            mainSlot = initialResult.mainSlot,
            emergencySlot = initialResult.emergencySlot
        )
        assertTrue(reEncryptedBytes.isNotEmpty())

        // 3. Descifrar el nuevo respaldo con la contraseña del usuario debe retornar el contenido actualizado
        val decryptMainResult = BackupCrypto.decryptBackup(reEncryptedBytes, correctPassword)
        assertTrue(decryptMainResult.isSuccess)
        assertEquals(updatedJson, decryptMainResult.getOrThrow())

        // 4. Descifrar el nuevo respaldo con la frase mnemónica debe también funcionar
        val decryptEmergencyResult = BackupCrypto.decryptBackup(reEncryptedBytes, mnemonicPhrase)
        assertTrue(decryptEmergencyResult.isSuccess)
        assertEquals(updatedJson, decryptEmergencyResult.getOrThrow())
    }

    /**
     * Valida que [BackupCrypto.decryptBackupDetailed] retorne correctamente los 32 bytes de vaultKey
     * y las ranuras para su custodia segura en Android Keystore.
     */
    @Test
    fun testDecryptBackupDetailedReturnsKeyAndSlots() {
        val mnemonicWords = MnemonicManager.generate12WordPhrase()
        val mnemonicPhrase = MnemonicManager.normalizePhrase(mnemonicWords.joinToString(" ")).toCharArray()

        val encryptedBytes = BackupCrypto.encryptDualBackup(sampleJson, correctPassword, mnemonicPhrase)
        val detailedResult = BackupCrypto.decryptBackupDetailed(encryptedBytes, correctPassword)

        assertTrue(detailedResult.isSuccess)
        val detailed = detailedResult.getOrThrow()
        assertEquals(sampleJson, detailed.plainJson)
        assertEquals(32, detailed.vaultKey.size)
        assertNotNull(detailed.mainSlot)
        assertNotNull(detailed.emergencySlot)
    }

    /**
     * Valida que un sobre con versión no compatible (v0 o v2) sea rechazado estrictamente.
     */
    @Test
    fun testRejectUnsupportedVersion() {
        val fakeV0 = """{"version":0,"salt":"AAAA","iv":"BBBB","ciphertext":"CCCC"}""".toByteArray(Charsets.UTF_8)
        val decryptV0Result = BackupCrypto.decryptBackup(fakeV0, correctPassword)
        assertTrue(decryptV0Result.isFailure)

        val fakeV2 = """{"version":2,"salt":"AAAA","iv":"BBBB","ciphertext":"CCCC"}""".toByteArray(Charsets.UTF_8)
        val decryptV2Result = BackupCrypto.decryptBackup(fakeV2, correctPassword)
        assertTrue(decryptV2Result.isFailure)
    }

    /**
     * Valida que un archivo de respaldo con el ciphertext manipulado sea rechazado por fallo de integridad.
     */
    @Test
    fun testDecryptTamperedBackupFails() {
        val encryptedBytes = BackupCrypto.encryptBackup(sampleJson, correctPassword)
        val jsonEnvelope = String(encryptedBytes, Charsets.UTF_8)

        // Manipular el ciphertext dentro del sobre JSON
        val tamperedEnvelope = jsonEnvelope.replaceFirst("\"ciphertext\":\"", "\"ciphertext\":\"X")
        val decryptResult = BackupCrypto.decryptBackup(tamperedEnvelope.toByteArray(Charsets.UTF_8), correctPassword)
        assertTrue(decryptResult.isFailure)
    }
}
