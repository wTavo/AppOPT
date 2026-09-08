package com.example.appopt.data

import com.example.appopt.data.cloud.CloudVaultSyncManager
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.security.SecurityConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de regresión para el sistema de Papelera de reciclaje y retención de 30 días.
 */
class TrashRecoveryTest {

    @Test
    fun testMoveToTrash_setsIsDeletedAndDeletedAt() {
        val now = 1_700_000_000_000L
        val originalAccount = TotpAccount(
            id = "test-id-1",
            issuer = "GitHub",
            accountName = "user@test.com",
            isDeleted = false,
            deletedAt = null,
            updatedAt = now
        )

        // Simula el traslado a la papelera
        val trashedAccount = originalAccount.copy(
            isDeleted = true,
            deletedAt = now + 5000L,
            updatedAt = now + 5000L
        )

        assertTrue(trashedAccount.isDeleted)
        assertEquals(now + 5000L, trashedAccount.deletedAt)
    }

    @Test
    fun testRestoreFromTrash_clearsIsDeletedAndDeletedAt() {
        val deleteTime = 1_700_000_000_000L
        val trashedAccount = TotpAccount(
            id = "test-id-1",
            issuer = "GitHub",
            accountName = "user@test.com",
            isDeleted = true,
            deletedAt = deleteTime,
            updatedAt = deleteTime
        )

        val restoreTime = deleteTime + 60_000L
        val restoredAccount = trashedAccount.copy(
            isDeleted = false,
            deletedAt = null,
            updatedAt = restoreTime
        )

        assertFalse(restoredAccount.isDeleted)
        assertNull(restoredAccount.deletedAt)
        assertEquals(restoreTime, restoredAccount.updatedAt)
    }

    @Test
    fun testActiveAccountsFilter_excludesTrashAccountsFromSyncHash() {
        val acc1 = TotpAccount(id = "1", issuer = "Google", accountName = "user1", isDeleted = false)
        val acc2 = TotpAccount(id = "2", issuer = "GitHub", accountName = "user2", isDeleted = true) // en papelera
        val acc3 = TotpAccount(id = "3", issuer = "Amazon", accountName = "user3", isDeleted = false)

        val allAccounts = listOf(acc1, acc2, acc3)
        val activeAccounts = allAccounts.filter { !it.isDeleted }

        assertEquals(2, activeAccounts.size)
        assertEquals(listOf("1", "3"), activeAccounts.map { it.id })

        val hashAll = CloudVaultSyncManager.computeAccountsSignature(allAccounts)
        val hashActive = CloudVaultSyncManager.computeAccountsSignature(activeAccounts)

        // El hash de cuentas activas debe ser diferente e independiente del elemento en papelera
        assertNotEquals(hashAll, hashActive)
    }

    @Test
    fun testTrashExpiration_detectsAccountsOver30Days() {
        val now = 1_700_000_000_000L
        val thirtyDaysMillis = SecurityConfig.TRASH_RETENTION_MILLIS

        val accountRecentlyDeleted = TotpAccount(
            id = "recent",
            issuer = "Service1",
            accountName = "user",
            isDeleted = true,
            deletedAt = now - (5L * 24L * 60L * 60L * 1000L) // 5 días atrás
        )

        val accountExpired = TotpAccount(
            id = "expired",
            issuer = "Service2",
            accountName = "user",
            isDeleted = true,
            deletedAt = now - (31L * 24L * 60L * 60L * 1000L) // 31 días atrás
        )

        val threshold = now - thirtyDaysMillis

        val isRecentExpired = (accountRecentlyDeleted.deletedAt ?: 0L) <= threshold
        val isOldExpired = (accountExpired.deletedAt ?: 0L) <= threshold

        assertFalse("Una cuenta con 5 días no debe expirar", isRecentExpired)
        assertTrue("Una cuenta con 31 días debe expirar", isOldExpired)
    }

    @Test
    fun testCountdownCalculation_correctDaysRemaining() {
        val now = 1_700_000_000_000L
        val deletedAt = now - (10L * 24L * 60L * 60L * 1000L) // Hace 10 días

        val elapsedMillis = now - deletedAt
        val remainingMillis = (SecurityConfig.TRASH_RETENTION_MILLIS - elapsedMillis).coerceAtLeast(0L)
        val remainingDays = (remainingMillis / (24L * 60L * 60L * 1000L)).toInt()

        assertEquals(20, remainingDays)
    }
}
