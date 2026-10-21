package com.example.appopt.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.appopt.data.local.migrations.DatabaseMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Pruebas unitarias para validar las migraciones de esquema de [AppDatabase] (Directivas 8 y 16).
 */
class DatabaseMigrationsTest {

    @Test
    fun `migration 1 to 2 should target correct versions`() {
        assertEquals(1, DatabaseMigrations.MIGRATION_1_2.startVersion)
        assertEquals(2, DatabaseMigrations.MIGRATION_1_2.endVersion)
    }

    @Test
    fun `all migrations array should include migration 1 to 2`() {
        assertTrue(DatabaseMigrations.ALL_MIGRATIONS.contains(DatabaseMigrations.MIGRATION_1_2))
    }

    @Test
    fun `migration 1 to 2 should execute alter table commands for deleted columns`() {
        val executedSql = mutableListOf<String>()

        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        DatabaseMigrations.MIGRATION_1_2.migrate(fakeDb)

        assertTrue(executedSql.any { it.contains("ADD COLUMN isDeleted") })
        assertTrue(executedSql.any { it.contains("ADD COLUMN deletedAt") })
    }
}
