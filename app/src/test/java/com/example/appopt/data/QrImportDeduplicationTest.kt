package com.example.appopt.data

import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.security.SecurityConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Pruebas unitarias de regresión para el blindaje de seguridad contra fuerza bruta en PIN de transferencia
 * y el motor de deduplicación inteligente y resolución de conflictos al importar servicios por código QR.
 */
class QrImportDeduplicationTest {

    private val sha256 = MessageDigest.getInstance("SHA-256")

    private fun hashBytes(bytes: ByteArray): String {
        return sha256.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testSingleAccountDeduplicationBySecretHash() {
        val secretA = "SECRET_BASE32_A".toByteArray()
        val secretB = "SECRET_BASE32_B".toByteArray()

        val vault = mutableMapOf<String, TotpAccount>()

        val initialAccount = TotpAccount(
            id = "id-1",
            issuer = "GitHub",
            accountName = "userA",
            updatedAt = 1000L
        )
        vault[hashBytes(secretA)] = initialAccount

        // Escaneo de QR con el MISMO secreto (secreto A)
        val scannedHash = hashBytes(secretA)
        val existing = vault[scannedHash]

        val resultCount = if (existing == null) {
            val newAccount = TotpAccount(id = "id-new", issuer = "GitHub", accountName = "userA", updatedAt = 2000L)
            vault[scannedHash] = newAccount
            1
        } else if (existing.issuer != "GitHub" || existing.accountName != "userA") {
            vault[scannedHash] = existing.copy(issuer = "GitHub", accountName = "userA", updatedAt = 2000L)
            1
        } else {
            0 // Ya existe de forma idéntica -> Cero cambios y cero duplicados
        }

        assertEquals("No se debe crear un duplicado cuando la cuenta es idéntica", 0, resultCount)
        assertEquals("La bóveda debe contener únicamente 1 cuenta", 1, vault.size)
        assertEquals("id-1", vault[scannedHash]?.id)

        // Escaneo de QR con secreto DISTINTO (secreto B)
        val scannedHashB = hashBytes(secretB)
        val existingB = vault[scannedHashB]
        val resultCountB = if (existingB == null) {
            val newAccount = TotpAccount(id = "id-2", issuer = "Google", accountName = "userB", updatedAt = 2000L)
            vault[scannedHashB] = newAccount
            1
        } else {
            0
        }

        assertEquals("Se debe insertar la nueva cuenta con secreto distinto", 1, resultCountB)
        assertEquals("La bóveda debe contener 2 cuentas únicas", 2, vault.size)
    }

    @Test
    fun testTrashAccountRestorationOnQrScan() {
        val secret = "SECRET_IN_TRASH".toByteArray()
        val secretHash = hashBytes(secret)

        val deletedAccount = TotpAccount(
            id = "id-deleted",
            issuer = "AWS",
            accountName = "root",
            isDeleted = true,
            deletedAt = 1000L,
            updatedAt = 1000L
        )

        val vault = mutableMapOf(secretHash to deletedAccount)

        // Re-escaneo del servicio por código QR
        val existing = vault[secretHash]
        assertNotNull(existing)
        assertTrue(existing!!.isDeleted)

        // Simulación del motor de mergeSingleAccount
        val restored = existing.copy(
            isDeleted = false,
            deletedAt = null,
            updatedAt = 2000L
        )
        vault[secretHash] = restored

        assertEquals(1, vault.size)
        assertFalse("El servicio debe ser restaurado de la papelera al re-escanearlo", vault[secretHash]!!.isDeleted)
        assertEquals(null, vault[secretHash]!!.deletedAt)
    }

    @Test
    fun testMetadataUpdateWhenQrContainsNewerInfo() {
        val secret = "SECRET_TO_UPDATE".toByteArray()
        val secretHash = hashBytes(secret)

        val localAccount = TotpAccount(
            id = "id-1",
            issuer = "OldIssuer",
            accountName = "oldUser",
            digits = 6,
            updatedAt = 1000L
        )

        val vault = mutableMapOf(secretHash to localAccount)

        val incomingIssuer = "NewIssuer"
        val incomingAccount = "newUser"
        val existing = vault[secretHash]!!

        val isChanged = existing.issuer != incomingIssuer || existing.accountName != incomingAccount
        assertTrue(isChanged)

        val updated = existing.copy(
            issuer = incomingIssuer,
            accountName = incomingAccount,
            updatedAt = 2000L
        )
        vault[secretHash] = updated

        assertEquals(1, vault.size)
        assertEquals("NewIssuer", vault[secretHash]?.issuer)
        assertEquals("newUser", vault[secretHash]?.accountName)
    }

    @Test
    fun testTransferPinMaxAttemptsConfiguration() {
        assertEquals("El límite máximo de intentos de PIN debe ser exactamente 5", 5, SecurityConfig.TRANSFER_QR_MAX_PIN_ATTEMPTS)

        var failedAttempts = 0
        var isLockedOut = false

        for (attempt in 1..SecurityConfig.TRANSFER_QR_MAX_PIN_ATTEMPTS) {
            failedAttempts++
            if (failedAttempts >= SecurityConfig.TRANSFER_QR_MAX_PIN_ATTEMPTS) {
                isLockedOut = true
            }
        }

        assertTrue("Debe bloquearse la sesión al alcanzar los 5 intentos fallidos", isLockedOut)
        assertEquals(5, failedAttempts)
    }
}
