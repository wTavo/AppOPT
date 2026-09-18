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
     * Valida que el generador de clave produzca exactamente 8 caracteres del alfabeto Base32 seguro.
     */
    @Test
    fun testGeneratePinFormat() {
        val pin = TransferCrypto.generateTransferPin()
        assertEquals(SecurityConfig.TRANSFER_KEY_LENGTH, pin.length)
        assertTrue(pin.all { it in SecurityConfig.TRANSFER_KEY_ALPHABET })
    }

    /**
     * Valida que una clave alfanumérica de 8 caracteres cifre y descifre correctamente,
     * incluso cuando el receptor la ingresa con formato con guion, minúsculas o espacios.
     */
    @Test
    fun testEncryptAndDecryptWith8CharKeyAndNormalization() {
        val key8 = "K7NP4M9X".toCharArray()
        val encrypted = TransferCrypto.encryptTransferPayload(
            accountsJson = sampleAccountsJson,
            pin = key8,
            durationSeconds = 90
        )

        // Descifrado con formato idéntico
        val res1 = TransferCrypto.decryptTransferPayload(encrypted, "K7NP4M9X".toCharArray())
        assertTrue(res1.isSuccess)
        assertEquals(sampleAccountsJson, res1.getOrThrow())

        // Descifrado con guion y espacios (K7NP - 4M9X)
        val res2 = TransferCrypto.decryptTransferPayload(encrypted, "K7NP - 4M9X".toCharArray())
        assertTrue(res2.isSuccess)
        assertEquals(sampleAccountsJson, res2.getOrThrow())

        // Descifrado en minúsculas con guion pegado (k7np-4m9x)
        val res3 = TransferCrypto.decryptTransferPayload(encrypted, "k7np-4m9x".toCharArray())
        assertTrue(res3.isSuccess)
        assertEquals(sampleAccountsJson, res3.getOrThrow())
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

    /**
     * Valida que múltiples fragmentos generados bajo el esquema Todo o Nada (v2) se ensamblen y descifren correctamente.
     */
    @Test
    fun testAllOrNothingMultiChunkEncryptionAndAssembly() {
        val accountsList = (1..30).joinToString(",") { i ->
            """{"i":"Service $i","a":"user$i@example.com","s":"JBSWY3DPEHPK3PXP"}"""
        }
        val largeJson = """{"v":1,"a":[$accountsList]}"""

        // Forzar fragmentos pequeños (100 bytes) para generar 3 o más códigos QR
        val qrStrings = TransferCrypto.encryptTransferPayloadInChunks(
            accountsJson = largeJson,
            pin = correctPin,
            durationSeconds = 90,
            maxChunkBytes = 100
        )

        assertTrue(qrStrings.size >= 3)

        // Parsear cada fragmento
        val chunks = qrStrings.map { TransferCrypto.parseTransferChunk(it) }
        assertEquals(qrStrings.size, chunks.size)
        assertTrue(chunks.all { it.version == SecurityConfig.TRANSFER_QR_VERSION_V2 })
        assertTrue(chunks.all { it.total == chunks.size })

        // Ensamblar y descifrar con PIN correcto
        val decryptResult = TransferCrypto.decryptAssembledChunks(chunks, correctPin)
        assertTrue(decryptResult.isSuccess)
        assertEquals(largeJson, decryptResult.getOrThrow())
    }

    /**
     * Valida que si falta al menos 1 fragmento (ej. 2 de 3), sea imposible descifrar cualquier dato (Todo o Nada).
     */
    @Test
    fun testAllOrNothingMissingChunkFailsCompletely() {
        val accountsList = (1..20).joinToString(",") { i ->
            """{"i":"Service $i","a":"user$i@example.com","s":"JBSWY3DPEHPK3PXP"}"""
        }
        val json = """{"v":1,"a":[$accountsList]}"""

        val qrStrings = TransferCrypto.encryptTransferPayloadInChunks(
            accountsJson = json,
            pin = correctPin,
            durationSeconds = 90,
            maxChunkBytes = 100
        )

        assertTrue(qrStrings.size >= 2)
        val allChunks = qrStrings.map { TransferCrypto.parseTransferChunk(it) }

        // Omitir el último fragmento
        val incompleteChunks = allChunks.dropLast(1)

        val decryptResult = TransferCrypto.decryptAssembledChunks(incompleteChunks, correctPin)
        assertTrue(decryptResult.isFailure)
        assertTrue(decryptResult.exceptionOrNull() is TransferCrypto.IncompleteTransferException)
    }

    /**
     * Valida que ingresar un PIN incorrecto con todos los fragmentos completos falle por autenticación AES-GCM.
     */
    @Test
    fun testAllOrNothingWrongPinFails() {
        val accountsList = (1..20).joinToString(",") { i ->
            """{"i":"Service $i","a":"user$i@example.com","s":"JBSWY3DPEHPK3PXP"}"""
        }
        val json = """{"v":1,"a":[$accountsList]}"""

        val qrStrings = TransferCrypto.encryptTransferPayloadInChunks(
            accountsJson = json,
            pin = correctPin,
            durationSeconds = 90,
            maxChunkBytes = 100
        )

        val allChunks = qrStrings.map { TransferCrypto.parseTransferChunk(it) }
        val decryptResult = TransferCrypto.decryptAssembledChunks(allChunks, wrongPin)
        assertTrue(decryptResult.isFailure)
        assertTrue(decryptResult.exceptionOrNull() is TransferCrypto.InvalidPinException)
    }

    /**
     * Valida que hasta 10 servicios generen exactamente 1 código QR cuando targetChunkCount es 1,
     * y que se descifren correctamente.
     */
    @Test
    fun testTargetChunkCountProducesExactNumberOfChunks() {
        val nineAccounts = (1..9).joinToString(",") { i ->
            """{"i":"Service $i","a":"user$i@example.com","s":"JBSWY3DPEHPK3PXP"}"""
        }
        val json9 = """{"v":1,"a":[$nineAccounts]}"""

        val qrStrings1 = TransferCrypto.encryptTransferPayloadInChunks(
            accountsJson = json9,
            pin = correctPin,
            durationSeconds = 90,
            targetChunkCount = 1
        )
        assertEquals(1, qrStrings1.size)

        val chunks1 = qrStrings1.map { TransferCrypto.parseTransferChunk(it) }
        val result1 = TransferCrypto.decryptAssembledChunks(chunks1, correctPin)
        assertTrue(result1.isSuccess)
        assertEquals(json9, result1.getOrThrow())

        // 11 cuentas con targetChunkCount = 2
        val elevenAccounts = (1..11).joinToString(",") { i ->
            """{"i":"Service $i","a":"user$i@example.com","s":"JBSWY3DPEHPK3PXP"}"""
        }
        val json11 = """{"v":1,"a":[$elevenAccounts]}"""

        val qrStrings2 = TransferCrypto.encryptTransferPayloadInChunks(
            accountsJson = json11,
            pin = correctPin,
            durationSeconds = 120,
            targetChunkCount = 2
        )
        assertEquals(2, qrStrings2.size)

        val chunks2 = qrStrings2.map { TransferCrypto.parseTransferChunk(it) }
        val result2 = TransferCrypto.decryptAssembledChunks(chunks2, correctPin)
        assertTrue(result2.isSuccess)
        assertEquals(json11, result2.getOrThrow())
    }
}
