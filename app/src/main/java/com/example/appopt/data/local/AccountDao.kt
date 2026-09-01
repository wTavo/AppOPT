package com.example.appopt.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Objeto de Acceso a Datos (DAO) para las operaciones en la base de datos Room.
 *
 * Principio de diseño:
 * - Consultas reactivas basadas en [Flow] para emitir actualizaciones automáticas a la UI.
 * - Los campos sensibles siempre se leen como arreglos binarios cifrados.
 */
@Dao
interface AccountDao {

    /**
     * Obtiene el listado completo de cuentas ordenadas por favoritos, orden personalizado y fecha de creación.
     */
    @Query("SELECT * FROM totp_accounts ORDER BY isFavorite DESC, orderIndex ASC, createdAt DESC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

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
     * Inserta un lote de cuentas (útil para restauraciones de respaldo).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

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
     * Elimina una entidad de cuenta.
     */
    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    /**
     * Elimina una cuenta por su identificador UUID.
     */
    @Query("DELETE FROM totp_accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)

    /**
     * Elimina todas las cuentas registradas (Wipe total).
     */
    @Query("DELETE FROM totp_accounts")
    suspend fun clearAll()
}
