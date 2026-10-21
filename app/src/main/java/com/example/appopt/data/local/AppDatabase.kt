package com.example.appopt.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.appopt.data.local.migrations.DatabaseMigrations

/**
 * Base de datos local Room de la aplicación para el almacenamiento offline de cuentas.
 */
@Database(
    entities = [AccountEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Proporciona acceso a las operaciones DAO de cuentas.
     */
    abstract fun accountDao(): AccountDao

    companion object {
        /**
         * Nombre del archivo SQLite de la base de datos local Room.
         */
        const val DATABASE_NAME = "authenticator_vault.db"

        /**
         * Nombre canónico de la tabla de cuentas OTP en SQLite / Room.
         */
        const val TABLE_ACCOUNTS_NAME = "totp_accounts"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Obtiene la instancia singleton de la base de datos de la bóveda local.
         *
         * @param context Contexto de la aplicación.
         * @return [AppDatabase] inicializada.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
