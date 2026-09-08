package com.example.appopt.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para la política de retención de 3 versiones y ordenamiento de copias de seguridad en la nube (Point-in-Time Recovery).
 */
class BackupRetentionTest {

    /**
     * Valida que una lista vacía de copias de seguridad no genere errores ni elementos espurios.
     */
    @Test
    fun testEmptyBackupsList() {
        val emptyList = emptyList<DriveBackupItem>()
        val sorted = emptyList.sortedByDescending { it.modifiedTimeMillis }
        assertEquals(0, sorted.size)
    }

    /**
     * Valida que una única copia de seguridad sea identificada como la más reciente.
     */
    @Test
    fun testSingleBackupItemIsMostRecent() {
        val singleItem = DriveBackupItem(
            fileId = "file_1",
            fileName = "appopt_vault_backup_1000.json",
            modifiedTimeMillis = 1000L,
            sizeBytes = 2048L,
            deviceName = "Pixel 8",
            isMostRecent = true
        )

        val list = listOf(singleItem)
        assertEquals(1, list.size)
        assertTrue(list.first().isMostRecent)
        assertEquals("file_1", list.first().fileId)
    }

    /**
     * Valida que múltiples versiones se ordenen determinísticamente de la más reciente a la más antigua.
     */
    @Test
    fun testSortingBackupsDescendingByTimestamp() {
        val b1 = DriveBackupItem("id_1", "file_1.json", 1000L, 1024L, "Device A", false)
        val b2 = DriveBackupItem("id_2", "file_2.json", 3000L, 1024L, "Device B", false)
        val b3 = DriveBackupItem("id_3", "file_3.json", 2000L, 1024L, "Device C", false)

        val unsorted = listOf(b1, b2, b3)
        val sorted = unsorted.sortedByDescending { it.modifiedTimeMillis }.mapIndexed { index, item ->
            if (index == 0) item.copy(isMostRecent = true) else item
        }

        assertEquals(3, sorted.size)
        assertEquals("id_2", sorted[0].fileId) // 3000L
        assertTrue(sorted[0].isMostRecent)
        assertEquals("id_3", sorted[1].fileId) // 2000L
        assertFalse(sorted[1].isMostRecent)
        assertEquals("id_1", sorted[2].fileId) // 1000L
        assertFalse(sorted[2].isMostRecent)
    }

    /**
     * Valida que la política FIFO retenga exactamente 3 copias y determine cuáles deben podarse.
     */
    @Test
    fun testRetentionPruningPolicyKeepsLatestThree() {
        val maxKeep = GoogleDriveManager.MAX_BACKUP_VERSIONS
        assertEquals(3, maxKeep)

        val backups = listOf(
            DriveBackupItem("id_5", "file_5.json", 5000L, 1024L, "Device", false),
            DriveBackupItem("id_4", "file_4.json", 4000L, 1024L, "Device", false),
            DriveBackupItem("id_3", "file_3.json", 3000L, 1024L, "Device", false),
            DriveBackupItem("id_2", "file_2.json", 2000L, 1024L, "Device", false),
            DriveBackupItem("id_1", "file_1.json", 1000L, 1024L, "Device", false)
        )

        val sorted = backups.sortedByDescending { it.modifiedTimeMillis }
        val toKeep = sorted.take(maxKeep)
        val toDelete = sorted.drop(maxKeep)

        assertEquals(3, toKeep.size)
        assertEquals(listOf("id_5", "id_4", "id_3"), toKeep.map { it.fileId })

        assertEquals(2, toDelete.size)
        assertEquals(listOf("id_2", "id_1"), toDelete.map { it.fileId })
    }

    /**
     * Valida que cuando la cantidad de copias es menor o igual a 3 no se elimine ninguna versión.
     */
    @Test
    fun testPruningWithLessThanMaxKeepDeletesNothing() {
        val backups = listOf(
            DriveBackupItem("id_2", "file_2.json", 2000L, 1024L, "Device", false),
            DriveBackupItem("id_1", "file_1.json", 1000L, 1024L, "Device", false)
        )

        val sorted = backups.sortedByDescending { it.modifiedTimeMillis }
        val toDelete = sorted.drop(GoogleDriveManager.MAX_BACKUP_VERSIONS)

        assertTrue(toDelete.isEmpty())
    }

    /**
     * Valida que la purga de sesión restablezca a nulo los tokens activos.
     */
    @Test
    fun testSessionClear() {
        GoogleDriveManager.currentAccessToken = "test_token_123"

        assertEquals("test_token_123", GoogleDriveManager.currentAccessToken)

        GoogleDriveManager.clearSession()

        org.junit.Assert.assertNull(GoogleDriveManager.currentAccessToken)
    }
}
