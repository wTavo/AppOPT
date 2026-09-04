package com.example.appopt.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.appopt.security.SecurityConfig

/**
 * Base de datos local Room de la aplicación para el almacenamiento offline de cuentas.
 */
@Database(
    entities = [AccountEntity::class],
    version = 1,
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
