package com.example.appopt.data.cloud

import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.model.TotpAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pruebas unitarias para [CloudVaultSyncManager].
 *
 * Valida que la firma criptográfica SHA-256 de las cuentas de la bóveda sea determinística
 * y no se vea alterada por cambios meramente estéticos o de ordenamiento local.
 */
class CloudVaultSyncManagerTest {

    private val account1 = TotpAccount(
        id = "uuid-1",
        issuer = "Google",
        accountName = "user1@gmail.com",
        type = OtpType.TOTP,
        algorithm = OtpAlgorithm.SHA1,
        digits = 6,
        period = 30,
        counter = 0L,
        isFavorite = false,
        orderIndex = 0,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val account2 = TotpAccount(
        id = "uuid-2",
        issuer = "GitHub",
        accountName = "user2",
        type = OtpType.TOTP,
        algorithm = OtpAlgorithm.SHA1,
        digits = 6,
        period = 30,
        counter = 0L,
        isFavorite = false,
        orderIndex = 1,
        createdAt = 2000L,
        updatedAt = 2000L
    )

    @Test
    fun computeAccountsSignature_returnsConsistentHash_whenReordered() {
        val listOriginal = listOf(account1, account2)
        val listReordered = listOf(
            account2.copy(orderIndex = 0, updatedAt = 5000L),
            account1.copy(orderIndex = 1, updatedAt = 5000L)
        )

        val hashOriginal = CloudVaultSyncManager.computeAccountsSignature(listOriginal)
        val hashReordered = CloudVaultSyncManager.computeAccountsSignature(listReordered)

        assertEquals("La firma SHA-256 debe ser idéntica independientemente del orden o timestamp de reordenamiento", hashOriginal, hashReordered)
    }

    @Test
    fun computeAccountsSignature_returnsConsistentHash_whenFavoriteStatusChanges() {
        val listOriginal = listOf(account1, account2)
        val listFavorited = listOf(
            account1.copy(isFavorite = true, updatedAt = 3000L),
            account2
        )

        val hashOriginal = CloudVaultSyncManager.computeAccountsSignature(listOriginal)
        val hashFavorited = CloudVaultSyncManager.computeAccountsSignature(listFavorited)

        assertEquals("La firma no debe cambiar al marcar/desmarcar como favorito", hashOriginal, hashFavorited)
    }

    @Test
    fun computeAccountsSignature_detectsNewAccount() {
        val listOriginal = listOf(account1)
        val listWithNew = listOf(account1, account2)

        val hashOriginal = CloudVaultSyncManager.computeAccountsSignature(listOriginal)
        val hashWithNew = CloudVaultSyncManager.computeAccountsSignature(listWithNew)

        assertNotEquals("La firma debe cambiar cuando se agrega una cuenta", hashOriginal, hashWithNew)
    }

    @Test
    fun computeAccountsSignature_detectsModifiedMetadata() {
        val listOriginal = listOf(account1, account2)
        val listModified = listOf(
            account1.copy(accountName = "newemail@gmail.com"),
            account2
        )

        val hashOriginal = CloudVaultSyncManager.computeAccountsSignature(listOriginal)
        val hashModified = CloudVaultSyncManager.computeAccountsSignature(listModified)

        assertNotEquals("La firma debe cambiar cuando se edita el nombre de usuario o emisor", hashOriginal, hashModified)
    }
}
