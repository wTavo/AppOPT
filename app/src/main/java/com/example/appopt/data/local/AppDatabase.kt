package com.example.appopt.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.appopt.security.SecurityConfig

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
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migración de esquema de versión 1 a versión 2:
         * Añade las columnas [AccountEntity.isDeleted] y [AccountEntity.deletedAt] para el sistema de papelera de 30 días.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE totp_accounts ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE totp_accounts ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            }
        }

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
                    SecurityConfig.ROOM_DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
