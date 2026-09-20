package com.example.appopt.ui.screens.settings

import androidx.compose.runtime.Immutable
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.domain.model.TotpAccount

/**
 * Estado inmutable de la pantalla de Ajustes y sincronización en la nube.
 *
 * Directiva de diseño:
 * - Inmutabilidad estricta marcada con [@Immutable] para estabilidad en Compose (Directiva 21).
 * - Centraliza estados de Drive, histórico de respaldos, diagnóstico de FPS y WorkManager.
 *
 * @param accounts Lista de cuentas OTP activas en la bóveda local.
 * @param isDriveConnected Indica si la cuenta de Google Drive está vinculada.
 * @param isDriveLoading Indica si hay una operación de red o sincronización en curso iniciada localmente.
 * @param driveBackupExists Indica si existe al menos una copia de seguridad en Google Drive.
 * @param driveBackupInfo Información de metadatos de la copia más reciente.
 * @param backupHistoryList Lista de versiones históricas en Google Drive ordenadas por fecha descendente.
 * @param lastSyncTimestamp Marca de tiempo Unix de la última sincronización confirmada.
 * @param lastSyncedHash Huella digital SHA-256 de las cuentas en el momento de la última sincronización.
 * @param hasUnsyncedChanges Indica si hay cambios locales pendientes de sincronizar con la nube.
 * @param isFpsOverlayEnabled Indica si la superposición diagnóstica de FPS está activa.
 * @param isAutoSyncEnabled Indica si la sincronización periódica en segundo plano está activada.
 * @param isSyncMobileDataAllowed Indica si se permite la sincronización a través de datos móviles.
 * @param isFetchingBackupHistory Indica si se está consultando el historial de versiones en la nube en segundo plano.
 * @param isRefreshingBackupHistory Indica si el usuario solicitó un refresco manual explícito con animación de carga.
 * @param isAutoSyncRunning Indica si hay un worker de WorkManager ejecutando sincronización reactiva o periódica.
 * @param lastHistoryFetchTimestamp Marca de tiempo Unix de la última consulta del historial de respaldos.
 */
@Immutable
data class SettingsUiState(
    val accounts: List<TotpAccount> = emptyList(),
    val isDriveConnected: Boolean = false,
    val isDriveLoading: Boolean = false,
    val driveBackupExists: Boolean = false,
    val driveBackupInfo: DriveBackupItem? = null,
    val backupHistoryList: List<DriveBackupItem> = emptyList(),
    val lastSyncTimestamp: Long = 0L,
    val lastSyncedHash: String = "",
    val hasUnsyncedChanges: Boolean = false,
    val isFpsOverlayEnabled: Boolean = false,
    val isAutoSyncEnabled: Boolean = false,
    val isSyncMobileDataAllowed: Boolean = false,
    val isDriveBackupEncrypted: Boolean = true,
    val isFetchingBackupHistory: Boolean = false,
    val isRefreshingBackupHistory: Boolean = false,
    val isAutoSyncRunning: Boolean = false,
    val lastHistoryFetchTimestamp: Long = 0L
) {
    /** Indica si cualquier proceso de sincronización local, global o en segundo plano está en curso. */
    val isSyncActive: Boolean
        get() = isDriveLoading || isAutoSyncRunning
}
