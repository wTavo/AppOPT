package com.example.appopt.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.data.cloud.SyncFrequency
import com.example.appopt.performance.PerformanceMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

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

    private val _lastBackupHistoryFetchTimestamp = MutableStateFlow(sharedPreferences.getLong(KEY_LAST_BACKUP_HISTORY_FETCH, 0L))
    val lastBackupHistoryFetchTimestampFlow: StateFlow<Long> = _lastBackupHistoryFetchTimestamp.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            KEY_HIDE_CODES -> _isHideCodesEnabled.value = prefs.getBoolean(KEY_HIDE_CODES, false)
            KEY_DRIVE_CONNECTED -> _isDriveConnected.value = prefs.getBoolean(KEY_DRIVE_CONNECTED, false)
            KEY_DRIVE_LAST_SYNC -> _lastSyncTimestamp.value = prefs.getLong(KEY_DRIVE_LAST_SYNC, 0L)
            KEY_LAST_VAULT_HASH -> _lastSyncedVaultHash.value = prefs.getString(KEY_LAST_VAULT_HASH, null)
            KEY_AUTO_SYNC_ENABLED -> _isAutoSyncEnabled.value = prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, true)
            KEY_SYNC_MOBILE_DATA -> _isSyncMobileData.value = prefs.getBoolean(KEY_SYNC_MOBILE_DATA, false)
            KEY_FPS_OVERLAY -> _isFpsOverlayEnabled.value = prefs.getBoolean(KEY_FPS_OVERLAY, true)
            KEY_LAST_BACKUP_HISTORY_FETCH -> _lastBackupHistoryFetchTimestamp.value = prefs.getLong(KEY_LAST_BACKUP_HISTORY_FETCH, 0L)
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
     * Indica si la bóveda local ha sido inicializada y sincronizada con Google Drive en este dispositivo.
     *
     * Una bóveda se considera inicializada cuando la cuenta de Google Drive está conectada
     * y se ha creado o restaurado exitosamente al menos una copia de seguridad ([getLastSyncTimestamp] > 0).
     *
     * @return `true` si la bóveda está inicializada y lista para sincronización automática, `false` en caso contrario.
     */
    fun isCloudVaultInitialized(): Boolean {
        return isGoogleDriveConnected() && getLastSyncTimestamp() > 0L
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
     * Guarda el estado de la superposición visual de FPS y activa/detiene el monitor y rastreo de diagnóstico.
     */
    fun setFpsOverlayEnabled(enabled: Boolean) {
        _isFpsOverlayEnabled.value = enabled
        sharedPreferences.edit { putBoolean(KEY_FPS_OVERLAY, enabled) }
        com.example.appopt.performance.AppCrashTracker.isEnabled = enabled
        if (enabled) {
            PerformanceMonitor.start()
        } else {
            PerformanceMonitor.stop()
        }
    }

    /**
     * Retorna la marca de tiempo de la última consulta de historial de copias de seguridad de Google Drive.
     */
    fun getLastBackupHistoryFetchTimestamp(): Long {
        return _lastBackupHistoryFetchTimestamp.value
    }

    /**
     * Guarda la marca de tiempo de la última consulta de historial de copias de seguridad.
     */
    fun setLastBackupHistoryFetchTimestamp(timestamp: Long) {
        _lastBackupHistoryFetchTimestamp.value = timestamp
        sharedPreferences.edit { putLong(KEY_LAST_BACKUP_HISTORY_FETCH, timestamp) }
    }

    /**
     * Recupera la lista de versiones de respaldo de Google Drive almacenadas localmente en caché.
     *
     * @return Lista de [DriveBackupItem] o lista vacía si no hay caché persistida.
     */
    fun getCachedBackupHistory(): List<DriveBackupItem> {
        val jsonStr = sharedPreferences.getString(KEY_CACHED_BACKUP_HISTORY, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<DriveBackupItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    DriveBackupItem(
                        fileId = obj.getString("fileId"),
                        fileName = obj.getString("fileName"),
                        modifiedTimeMillis = obj.getLong("modifiedTimeMillis"),
                        sizeBytes = obj.getLong("sizeBytes"),
                        deviceName = obj.optString("deviceName", ""),
                        isMostRecent = obj.optBoolean("isMostRecent", false)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Guarda de forma persistente la lista de versiones de respaldo de Google Drive en caché local JSON.
     *
     * @param items Lista de [DriveBackupItem] a guardar.
     */
    fun setCachedBackupHistory(items: List<DriveBackupItem>) {
        try {
            val jsonArray = JSONArray()
            for (item in items) {
                val obj = JSONObject().apply {
                    put("fileId", item.fileId)
                    put("fileName", item.fileName)
                    put("modifiedTimeMillis", item.modifiedTimeMillis)
                    put("sizeBytes", item.sizeBytes)
                    put("deviceName", item.deviceName)
                    put("isMostRecent", item.isMostRecent)
                }
                jsonArray.put(obj)
            }
            sharedPreferences.edit { putString(KEY_CACHED_BACKUP_HISTORY, jsonArray.toString()) }
        } catch (_: Exception) {
            // Ignorar errores no críticos de persistencia de caché
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
        private const val KEY_LAST_BACKUP_HISTORY_FETCH = "key_last_backup_history_fetch"
        private const val KEY_CACHED_BACKUP_HISTORY = "key_cached_backup_history"
    }
}
