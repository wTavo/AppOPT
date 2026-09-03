package com.example.appopt.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    /**
     * Retorna si la copia de seguridad automática está activada (por defecto true).
     */
    fun isAutoSyncEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_SYNC_ENABLED, true)
    }

    /**
     * Guarda si la copia de seguridad automática está activada.
     */
    fun setAutoSyncEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply()
    }

    /**
     * Retorna si la sincronización puede usar datos móviles (por defecto false -> solo Wi-Fi).
     */
    fun isSyncMobileDataAllowed(): Boolean {
        return sharedPreferences.getBoolean(KEY_SYNC_MOBILE_DATA, false)
    }

    /**
     * Guarda la preferencia de uso de datos móviles para sincronización.
     */
    fun setSyncMobileDataAllowed(allowed: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SYNC_MOBILE_DATA, allowed).apply()
    }

    /**
     * Retorna el último hash SHA-256 de la bóveda sincronizada en la nube.
     */
    fun getLastSyncedVaultHash(): String? {
        return sharedPreferences.getString(KEY_LAST_VAULT_HASH, null)
    }

    /**
     * Guarda el hash SHA-256 de la bóveda sincronizada.
     */
    fun setLastSyncedVaultHash(hash: String) {
        sharedPreferences.edit().putString(KEY_LAST_VAULT_HASH, hash).apply()
    }

    /**
     * Retorna la frecuencia configurada para la copia de seguridad automática.
     */
    fun getSyncFrequency(): com.example.appopt.data.cloud.SyncFrequency {
        val name = sharedPreferences.getString(KEY_SYNC_FREQUENCY, null)
        return if (name != null) {
            com.example.appopt.data.cloud.SyncFrequency.fromName(name)
        } else {
            if (isAutoSyncEnabled()) com.example.appopt.data.cloud.SyncFrequency.DAILY else com.example.appopt.data.cloud.SyncFrequency.OFF
        }
    }

    /**
     * Guarda la frecuencia configurada para la copia de seguridad automática.
     */
    fun setSyncFrequency(frequency: com.example.appopt.data.cloud.SyncFrequency) {
        sharedPreferences.edit()
            .putString(KEY_SYNC_FREQUENCY, frequency.name)
            .putBoolean(KEY_AUTO_SYNC_ENABLED, frequency != com.example.appopt.data.cloud.SyncFrequency.OFF)
            .apply()
    }

    private val _isFpsOverlayEnabled = kotlinx.coroutines.flow.MutableStateFlow(
        sharedPreferences.getBoolean(KEY_FPS_OVERLAY, true)
    )
    /** Flujo reactivo del estado de visualización de FPS y rendimiento. */
    val isFpsOverlayEnabledFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isFpsOverlayEnabled.asStateFlow()

    /**
     * Retorna si la superposición visual de FPS y registros de rendimiento está habilitada.
     */
    fun isFpsOverlayEnabled(): Boolean {
        return _isFpsOverlayEnabled.value
    }

    /**
     * Guarda el estado de la superposición visual de FPS y activa/detiene el monitor.
     */
    fun setFpsOverlayEnabled(enabled: Boolean) {
        _isFpsOverlayEnabled.value = enabled
        sharedPreferences.edit().putBoolean(KEY_FPS_OVERLAY, enabled).apply()
        if (enabled) {
            com.example.appopt.util.PerformanceMonitor.start()
        } else {
            com.example.appopt.util.PerformanceMonitor.stop()
        }
    }

    companion object {
        private const val PREFS_NAME = "authenticator_user_preferences"
        private const val KEY_HIDE_CODES = "key_hide_codes"
        private const val KEY_DRIVE_CONNECTED = "key_drive_connected"
        private const val KEY_DRIVE_LAST_SYNC = "key_drive_last_sync"
        private const val KEY_AUTO_SYNC_ENABLED = "key_auto_sync_enabled"
        private const val KEY_SYNC_MOBILE_DATA = "key_sync_mobile_data"
        private const val KEY_LAST_VAULT_HASH = "key_last_vault_hash"
        private const val KEY_SYNC_FREQUENCY = "key_sync_frequency"
        private const val KEY_FPS_OVERLAY = "key_fps_overlay"
    }
}
