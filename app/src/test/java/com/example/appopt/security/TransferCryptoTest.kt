package com.example.appopt.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de regresión para [TransferCrypto] validando el cifrado AES-256-GCM,
 * la derivación de clave por PIN efímero, la expiración temporal de 90 segundos y el rechazo de PINs incorrectos.
 */
class TransferCryptoTest {

    private val sampleAccountsJson = """{"v":1,"a":[{"i":"GitHub","a":"alice","s":"JBSWY3DPEHPK3PXP"}]}"""
    private val correctPin = "482915".toCharArray()
    private val wrongPin = "111222".toCharArray()

    /**
     * Valida que el generador de PIN produzca exactamente 6 dígitos numéricos.
     */
    @Test
    fun testGeneratePinFormat() {
        val pin = TransferCrypto.generateTransferPin()
        assertEquals(6, pin.length)
        assertTrue(pin.all { it.isDigit() })
    }

    /**
     * Valida que un payload cifrado con el PIN correcto se descifre con fidelidad exacta.
     */
    @Test
    fun testEncryptAndDecryptWithCorrectPin() {
        val encryptedPayload = TransferCrypto.encryptTransferPayload(
            accountsJson = sampleAccountsJson,
            pin = correctPin,
            durationSeconds = 90
        )

        assertTrue(encryptedPayload.startsWith(TransferCrypto.QR_TRANSFER_PREFIX))

        val decryptResult = TransferCrypto.decryptTransferPayload(encryptedPayload, correctPin)
        assertTrue(decryptResult.isSuccess)
        assertEquals(sampleAccountsJson, decryptResult.getOrThrow())
    }

    /**
     * Valida que exportar un conjunto masivo de 25 cuentas genere un payload comprimido y compacto
     * perfectamente integrable en una matriz de código QR sin exceder los límites físicos.
     */
    @Test
    fun testEncryptLargeAccountListIsCompact() {
        val accountsList = (1..25).joinToString(",") { i ->
            """{"i":"Service $i","a":"user$i@example.com","s":"JBSWY3DPEHPK3PXP"}"""
        }
        val largeJson = """{"v":1,"a":[$accountsList]}"""

        val encryptedPayload = TransferCrypto.encryptTransferPayload(
            accountsJson = largeJson,
            pin = correctPin,
            durationSeconds = 90
        )

        // El payload completo no debe exceder 1500 caracteres para asegurar lectura instantánea en cualquier cámara
        assertTrue("Payload demasiado grande: ${encryptedPayload.length}", encryptedPayload.length < 1500)

        val decryptResult = TransferCrypto.decryptTransferPayload(encryptedPayload, correctPin)
        assertTrue(decryptResult.isSuccess)
        assertEquals(largeJson, decryptResult.getOrThrow())
    }

    /**
     * Valida que un PIN incorrecto sea rechazado y lance [TransferCrypto.InvalidPinException].
     */
    @Test
    fun testDecryptWithWrongPinFails() {
        val encryptedPayload = TransferCrypto.encryptTransferPayload(
            accountsJson = sampleAccountsJson,
            pin = correctPin,
            durationSeconds = 90
        )

        val decryptResult = TransferCrypto.decryptTransferPayload(encryptedPayload, wrongPin)
        assertTrue(decryptResult.isFailure)
        assertTrue(decryptResult.exceptionOrNull() is TransferCrypto.InvalidPinException)
    }

    /**
     * Valida que un payload con duración negativa o vencida sea rechazado por expiración [TransferCrypto.ExpiredTransferException].
     */
    @Test
    fun testDecryptExpiredTransferFails() {
        // Duración negativa (-30 segundos) para simular expiración inmediata
        val expiredPayload = TransferCrypto.encryptTransferPayload(
            accountsJson = sampleAccountsJson,
            pin = correctPin,
            durationSeconds = -30
        )

        val decryptResult = TransferCrypto.decryptTransferPayload(expiredPayload, correctPin)
        assertTrue(decryptResult.isFailure)
        assertTrue(decryptResult.exceptionOrNull() is TransferCrypto.ExpiredTransferException)
    }

    /**
     * Valida que un payload alterado o corrupto falle por integridad AES-GCM.
     */
    @Test
    fun testCorruptedPayloadFails() {
        val encryptedPayload = TransferCrypto.encryptTransferPayload(
            accountsJson = sampleAccountsJson,
            pin = correctPin,
            durationSeconds = 90
        )

        // Alterar caracteres del ciphertext
        val corruptedPayload = encryptedPayload.replace("a", "b")
        val decryptResult = TransferCrypto.decryptTransferPayload(corruptedPayload, correctPin)
        assertTrue(decryptResult.isFailure)
    }
}
