package com.example.appopt.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.appopt.data.local.AppDatabase

/**
 * Catálogo centralizado de migraciones de esquema para la base de datos local Room ([AppDatabase]).
 *
 * Cada objeto [Migration] representa una transición determinística e irreversible entre versiones del
 * esquema local de SQLite, garantizando la preservación total de los registros 2FA sin pérdida de datos.
 */
object DatabaseMigrations {

    /**
     * Migración de esquema de versión 1 a versión 2:
     * Añade las columnas `isDeleted` y `deletedAt` a la tabla [AppDatabase.TABLE_ACCOUNTS_NAME]
     * para dar soporte al sistema de papelera de reciclaje con retención defensiva de 30 días.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE ${AppDatabase.TABLE_ACCOUNTS_NAME} ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE ${AppDatabase.TABLE_ACCOUNTS_NAME} ADD COLUMN deletedAt INTEGER DEFAULT NULL")
        }
    }

    /**
     * Arreglo inmutable con todas las migraciones registradas para su inyección en [AppDatabase.getInstance].
     */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2
    )
}
