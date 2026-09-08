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

    @Test
    fun computeEntitiesSignature_matchesAccountsSignatureEquivalently() {
        val entity1 = com.example.appopt.data.local.AccountEntity(
            id = account1.id,
            issuer = account1.issuer,
            accountName = account1.accountName,
            encryptedSecret = byteArrayOf(1, 2, 3),
            iv = byteArrayOf(4, 5, 6),
            algorithm = account1.algorithm.name,
            digits = account1.digits,
            period = account1.period,
            type = account1.type.name,
            counter = account1.counter,
            isFavorite = account1.isFavorite,
            orderIndex = account1.orderIndex,
            createdAt = account1.createdAt,
            updatedAt = account1.updatedAt
        )

        val entity2 = com.example.appopt.data.local.AccountEntity(
            id = account2.id,
            issuer = account2.issuer,
            accountName = account2.accountName,
            encryptedSecret = byteArrayOf(7, 8, 9),
            iv = byteArrayOf(10, 11, 12),
            algorithm = account2.algorithm.name,
            digits = account2.digits,
            period = account2.period,
            type = account2.type.name,
            counter = account2.counter,
            isFavorite = account2.isFavorite,
            orderIndex = account2.orderIndex,
            createdAt = account2.createdAt,
            updatedAt = account2.updatedAt
        )

        val accountsHash = CloudVaultSyncManager.computeAccountsSignature(listOf(account1, account2))
        val entitiesHash = CloudVaultSyncManager.computeEntitiesSignature(listOf(entity1, entity2))

        assertEquals("La huella de entidades Room debe ser idéntica a la huella de modelos de dominio", accountsHash, entitiesHash)
    }
}

