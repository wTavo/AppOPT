package com.example.appopt.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestor de preferencias de usuario persistentes (modo de privacidad, etc.).
 *
 * @param context Contexto de la aplicación.
 */
class PreferencesManager(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Retorna si el modo de ocultar códigos está habilitado de forma persistente.
     */
    fun isHideCodesEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_HIDE_CODES, false)
    }

    /**
     * Guarda el estado de ocultar códigos en disco.
     */
    fun setHideCodesEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_HIDE_CODES, enabled).apply()
    }

    /**
     * Retorna si la cuenta de Google Drive ha sido conectada previamente.
     */
    fun isGoogleDriveConnected(): Boolean {
        return sharedPreferences.getBoolean(KEY_DRIVE_CONNECTED, false)
    }

    /**
     * Guarda el estado de conexión con Google Drive.
     */
    fun setGoogleDriveConnected(connected: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_DRIVE_CONNECTED, connected).apply()
    }

    /**
     * Retorna la marca de tiempo (timestamp en millis) de la última sincronización en Drive.
     */
    fun getLastSyncTimestamp(): Long {
        return sharedPreferences.getLong(KEY_DRIVE_LAST_SYNC, 0L)
    }

    /**
     * Guarda la marca de tiempo de la última sincronización en Drive.
     */
    fun setLastSyncTimestamp(timestamp: Long) {
        sharedPreferences.edit().putLong(KEY_DRIVE_LAST_SYNC, timestamp).apply()
    }

    companion object {
        private const val PREFS_NAME = "authenticator_user_preferences"
        private const val KEY_HIDE_CODES = "key_hide_codes"
        private const val KEY_DRIVE_CONNECTED = "key_drive_connected"
        private const val KEY_DRIVE_LAST_SYNC = "key_drive_last_sync"
    }
}
