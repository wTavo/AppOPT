package com.example.appopt.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Objeto de Acceso a Datos (DAO) para las operaciones en la base de datos Room.
 *
 * Principio de diseño:
 * - Consultas reactivas basadas en [Flow] para emitir actualizaciones automáticas a la UI.
 * - Los campos sensibles siempre se leen como arreglos binarios cifrados.
 * - Persistencia del orden personalizado mediante [orderIndex].
 */
@Dao
interface AccountDao {

    /**
     * Obtiene el listado completo de cuentas activas ordenadas por favoritos, orden personalizado y fecha de creación.
     */
    @Query("SELECT * FROM totp_accounts WHERE isDeleted = 0 ORDER BY isFavorite DESC, orderIndex ASC, createdAt DESC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    /**
     * Obtiene sincrónicamente todas las entidades activas en una sola consulta por lotes para descifrado de alto rendimiento.
     */
    @Query("SELECT * FROM totp_accounts WHERE isDeleted = 0")
    suspend fun getAllAccountsSync(): List<AccountEntity>

    /**
     * Obtiene el listado de cuentas en la papelera de reciclaje ordenadas por fecha de eliminación descendente.
     */
    @Query("SELECT * FROM totp_accounts WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedAccounts(): Flow<List<AccountEntity>>

    /**
     * Obtiene sincrónicamente las cuentas en papelera.
     */
    @Query("SELECT * FROM totp_accounts WHERE isDeleted = 1")
    suspend fun getDeletedAccountsSync(): List<AccountEntity>

    /**
     * Obtiene una cuenta específica por su identificador único UUID.
     */
    @Query("SELECT * FROM totp_accounts WHERE id = :id")
    suspend fun getAccountById(id: String): AccountEntity?

    /**
     * Inserta una nueva cuenta en la base de datos reemplazando si ya existe el mismo ID.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    /**
     * Actualiza los datos de una cuenta existente.
     */
    @Update
    suspend fun updateAccount(account: AccountEntity)

    /**
     * Actualiza únicamente los metadatos editables (emisor y cuenta/usuario) de una cuenta sin tocar el secreto.
     */
    @Query("UPDATE totp_accounts SET issuer = :issuer, accountName = :accountName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMetadata(id: String, issuer: String, accountName: String, updatedAt: Long = System.currentTimeMillis())

    /**
     * Actualiza el índice de orden posicional de una cuenta.
     */
    @Query("UPDATE totp_accounts SET orderIndex = :orderIndex, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOrderIndex(id: String, orderIndex: Int, updatedAt: Long = System.currentTimeMillis())

    /**
     * Actualiza atómicamente el orden de un lote de cuentas.
     *
     * @param orderedIds Lista de identificadores en su nuevo orden de visualización.
     */
    @Transaction
    suspend fun updateAccountsOrder(orderedIds: List<String>) {
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id ->
            updateOrderIndex(id, index, now)
        }
    }

    /**
     * Traslada una cuenta a la papelera de reciclaje temporal (eliminación lógica).
     *
     * @param id Identificador UUID de la cuenta.
     * @param deletedAt Timestamp UNIX en que se efectuó el traslado a papelera.
     */
    @Query("UPDATE totp_accounts SET isDeleted = 1, deletedAt = :deletedAt, isFavorite = 0, updatedAt = :deletedAt WHERE id = :id")
    suspend fun moveToTrash(id: String, deletedAt: Long = System.currentTimeMillis())

    /**
     * Restaura una cuenta desde la papelera de reciclaje a la bóveda activa.
     *
     * @param id Identificador UUID de la cuenta.
     * @param updatedAt Timestamp UNIX de reactivación para forzar sincronización.
     */
    @Query("UPDATE totp_accounts SET isDeleted = 0, deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreFromTrash(id: String, updatedAt: Long = System.currentTimeMillis())

    /**
     * Elimina físicamente una cuenta por su identificador UUID (eliminación definitiva).
     *
     * @param id Identificador UUID de la cuenta a purgar.
     */
    @Query("DELETE FROM totp_accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)

    /**
     * Purga definitivamente todas las cuentas cuya estancia en papelera supere el umbral especificado.
     *
     * @param expirationThreshold Timestamp límite antes del cual las cuentas son eliminadas.
     * @return Número de registros purgados.
     */
    @Query("DELETE FROM totp_accounts WHERE isDeleted = 1 AND deletedAt <= :expirationThreshold")
    suspend fun purgeExpiredTrash(expirationThreshold: Long): Int

    /**
     * Vacía completamente la papelera de reciclaje eliminando todos los registros marcados como eliminados.
     *
     * @return Número de registros purgados.
     */
    @Query("DELETE FROM totp_accounts WHERE isDeleted = 1")
    suspend fun emptyTrash(): Int
}
