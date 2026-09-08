package com.example.appopt.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.appopt.data.cloud.SyncFrequency
import com.example.appopt.util.PerformanceMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

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

    private val _isHideCodesEnabled = MutableStateFlow(sharedPreferences.getBoolean(KEY_HIDE_CODES, false))
    val isHideCodesEnabledFlow: StateFlow<Boolean> = _isHideCodesEnabled.asStateFlow()

    private val _isDriveConnected = MutableStateFlow(sharedPreferences.getBoolean(KEY_DRIVE_CONNECTED, false))
    val isGoogleDriveConnectedFlow: StateFlow<Boolean> = _isDriveConnected.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(sharedPreferences.getLong(KEY_DRIVE_LAST_SYNC, 0L))
    val lastSyncTimestampFlow: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _lastSyncedVaultHash = MutableStateFlow(sharedPreferences.getString(KEY_LAST_VAULT_HASH, null))
    val lastSyncedVaultHashFlow: StateFlow<String?> = _lastSyncedVaultHash.asStateFlow()

    private val _isAutoSyncEnabled = MutableStateFlow(sharedPreferences.getBoolean(KEY_AUTO_SYNC_ENABLED, true))
    val isAutoSyncEnabledFlow: StateFlow<Boolean> = _isAutoSyncEnabled.asStateFlow()

    private val _isSyncMobileData = MutableStateFlow(sharedPreferences.getBoolean(KEY_SYNC_MOBILE_DATA, false))
    val isSyncMobileDataAllowedFlow: StateFlow<Boolean> = _isSyncMobileData.asStateFlow()

    private val _isFpsOverlayEnabled = MutableStateFlow(sharedPreferences.getBoolean(KEY_FPS_OVERLAY, true))
    val isFpsOverlayEnabledFlow: StateFlow<Boolean> = _isFpsOverlayEnabled.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            KEY_HIDE_CODES -> _isHideCodesEnabled.value = prefs.getBoolean(KEY_HIDE_CODES, false)
            KEY_DRIVE_CONNECTED -> _isDriveConnected.value = prefs.getBoolean(KEY_DRIVE_CONNECTED, false)
            KEY_DRIVE_LAST_SYNC -> _lastSyncTimestamp.value = prefs.getLong(KEY_DRIVE_LAST_SYNC, 0L)
            KEY_LAST_VAULT_HASH -> _lastSyncedVaultHash.value = prefs.getString(KEY_LAST_VAULT_HASH, null)
            KEY_AUTO_SYNC_ENABLED -> _isAutoSyncEnabled.value = prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, true)
            KEY_SYNC_MOBILE_DATA -> _isSyncMobileData.value = prefs.getBoolean(KEY_SYNC_MOBILE_DATA, false)
            KEY_FPS_OVERLAY -> _isFpsOverlayEnabled.value = prefs.getBoolean(KEY_FPS_OVERLAY, true)
        }
    }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    /**
     * Retorna si el modo de ocultar códigos está habilitado de forma persistente.
     */
    fun isHideCodesEnabled(): Boolean {
        return _isHideCodesEnabled.value
    }

    /**
     * Guarda el estado de ocultar códigos en disco.
     */
    fun setHideCodesEnabled(enabled: Boolean) {
        _isHideCodesEnabled.value = enabled
        sharedPreferences.edit { putBoolean(KEY_HIDE_CODES, enabled) }
    }

    /**
     * Retorna si la cuenta de Google Drive ha sido conectada previamente.
     */
    fun isGoogleDriveConnected(): Boolean {
        return _isDriveConnected.value
    }

    /**
     * Guarda el estado de conexión con Google Drive.
     */
    fun setGoogleDriveConnected(connected: Boolean) {
        _isDriveConnected.value = connected
        sharedPreferences.edit { putBoolean(KEY_DRIVE_CONNECTED, connected) }
    }

    /**
     * Retorna la marca de tiempo (timestamp en millis) de la última sincronización en Drive.
     */
    fun getLastSyncTimestamp(): Long {
        return _lastSyncTimestamp.value
    }

    /**
     * Guarda la marca de tiempo de la última sincronización en Drive.
     */
    fun setLastSyncTimestamp(timestamp: Long) {
        _lastSyncTimestamp.value = timestamp
        sharedPreferences.edit { putLong(KEY_DRIVE_LAST_SYNC, timestamp) }
    }

    /**
     * Retorna si la copia de seguridad automática está activada (por defecto true).
     */
    fun isAutoSyncEnabled(): Boolean {
        return _isAutoSyncEnabled.value
    }

    /**
     * Guarda el estado de activación de la copia de seguridad automática.
     */
    fun setAutoSyncEnabled(enabled: Boolean) {
        _isAutoSyncEnabled.value = enabled
        sharedPreferences.edit { putBoolean(KEY_AUTO_SYNC_ENABLED, enabled) }
    }

    /**
     * Retorna si la sincronización puede usar datos móviles (por defecto false -> solo Wi-Fi).
     */
    fun isSyncMobileDataAllowed(): Boolean {
        return _isSyncMobileData.value
    }

    /**
     * Guarda la preferencia de uso de datos móviles para sincronización.
     */
    fun setSyncMobileDataAllowed(allowed: Boolean) {
        _isSyncMobileData.value = allowed
        sharedPreferences.edit { putBoolean(KEY_SYNC_MOBILE_DATA, allowed) }
    }

    /**
     * Retorna el último hash SHA-256 de la bóveda sincronizada en la nube.
     */
    fun getLastSyncedVaultHash(): String? {
        return _lastSyncedVaultHash.value
    }

    /**
     * Guarda el hash SHA-256 de la bóveda sincronizada.
     */
    fun setLastSyncedVaultHash(hash: String) {
        _lastSyncedVaultHash.value = hash
        sharedPreferences.edit { putString(KEY_LAST_VAULT_HASH, hash) }
    }

    /**
     * Retorna la frecuencia configurada para la copia de seguridad automática.
     */
    fun getSyncFrequency(): SyncFrequency {
        val name = sharedPreferences.getString(KEY_SYNC_FREQUENCY, null)
        return if (name != null) {
            SyncFrequency.fromName(name)
        } else {
            if (isAutoSyncEnabled()) SyncFrequency.DAILY else SyncFrequency.OFF
        }
    }

    /**
     * Guarda la frecuencia configurada para la copia de seguridad automática.
     */
    fun setSyncFrequency(frequency: SyncFrequency) {
        sharedPreferences.edit {
            putString(KEY_SYNC_FREQUENCY, frequency.name)
                .putBoolean(KEY_AUTO_SYNC_ENABLED, frequency != SyncFrequency.OFF)
        }
    }

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
        sharedPreferences.edit { putBoolean(KEY_FPS_OVERLAY, enabled) }
        if (enabled) {
            PerformanceMonitor.start()
        } else {
            PerformanceMonitor.stop()
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
