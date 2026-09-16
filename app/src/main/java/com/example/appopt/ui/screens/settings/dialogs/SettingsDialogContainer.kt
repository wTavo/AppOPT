package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.runtime.Composable
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.ui.screens.settings.SettingsUiState

/**
 * Contenedor orquestador de diálogos modales para la pantalla de configuración.
 *
 * Directiva de diseño (Directiva 14):
 * - Centraliza la apertura defensiva de los modales de exportación, protección E2EE, restauración e historial.
 * - Garantiza que ningún diálogo permanezca abierto si la bóveda se encuentra bloqueada ([isUnlocked] = false).
 *
 * @param isUnlocked Estado de desbloqueo de la bóveda de seguridad.
 * @param uiState Estado inmutable de la pantalla de configuración.
 * @param formattedLastSync Cadena localizada del tiempo transcurrido desde la última sincronización.
 * @param showDisconnectConfirmDialog Bandera de visibilidad para confirmación de desvinculación.
 * @param onDismissDisconnectConfirm Callback para cerrar el diálogo de desvinculación.
 * @param onConfirmDisconnect Callback al confirmar la desvinculación de Google Drive.
 * @param showExportDialog Bandera de visibilidad para exportación QR de servicios.
 * @param onDismissExport Callback para cerrar el diálogo de exportación.
 * @param onExportBatchesPayload Callback generador de cargas útiles cifradas para lotes QR.
 * @param onCompleteExport Callback invocado al completar la exportación de cuentas.
 * @param showDriveProtectDialog Bandera de visibilidad para configuración de protección E2EE.
 * @param onDismissDriveProtect Callback para cerrar el diálogo de protección.
 * @param onProtectAndSync Callback para aplicar contraseña y frase de emergencia y sincronizar.
 * @param showDriveDecryptDialog Bandera de visibilidad para descifrado y restauración directa.
 * @param onDismissDriveDecrypt Callback para cerrar el diálogo de descifrado.
 * @param onRestoreDriveDecrypt Callback con la clave para descifrar y restaurar.
 * @param showBackupDetailsDialog Bandera de visibilidad para historial de versiones en la nube.
 * @param onDismissBackupDetails Callback para cerrar el historial de respaldos.
 * @param onForceRefreshBackupHistory Callback para forzar actualización del historial.
 * @param onRestoreBackupHistoryItem Callback para restaurar una versión específica.
 * @param onDeleteBackupHistoryItem Callback para eliminar una versión específica con sus caracteres de descifrado.
 * @param showOverwriteWarningDialog Bandera de visibilidad para advertencia de sobrescritura.
 * @param onDismissOverwriteWarning Callback para cerrar la advertencia de sobrescritura.
 * @param onConfirmOverwrite Callback para proceder a la creación del respaldo sobrescribiendo el existente.
 * @param onRestoreInstead Callback para cancelar la sobrescritura y optar por restaurar la copia existente.
 */
@Composable
fun SettingsDialogContainer(
    isUnlocked: Boolean,
    uiState: SettingsUiState,
    formattedLastSync: String?,
    showDisconnectConfirmDialog: Boolean,
    onDismissDisconnectConfirm: () -> Unit,
    onConfirmDisconnect: () -> Unit,
    showExportDialog: Boolean,
    onDismissExport: () -> Unit,
    onExportBatchesPayload: suspend (Set<String>, CharArray) -> List<String>,
    onCompleteExport: (Set<String>, Boolean) -> Unit,
    showDriveProtectDialog: Boolean,
    onDismissDriveProtect: () -> Unit,
    onProtectAndSync: (CharArray, CharArray) -> Unit,
    showDriveDecryptDialog: Boolean,
    onDismissDriveDecrypt: () -> Unit,
    onRestoreDriveDecrypt: (CharArray) -> Unit,
    showBackupDetailsDialog: Boolean,
    isBackupHistoryLoading: Boolean = false,
    onDismissBackupDetails: () -> Unit,
    onForceRefreshBackupHistory: () -> Unit,
    onRestoreBackupHistoryItem: (DriveBackupItem, CharArray) -> Unit,
    onDeleteBackupHistoryItem: (DriveBackupItem, CharArray) -> Unit,
    showOverwriteWarningDialog: Boolean,
    onDismissOverwriteWarning: () -> Unit,
    onConfirmOverwrite: () -> Unit,
    onRestoreInstead: () -> Unit
) {
    if (!isUnlocked) return

    // 1. Modal: Confirmación de Desvinculación de Google Drive
    if (showDisconnectConfirmDialog) {
        DriveDisconnectConfirmDialog(
            onConfirm = onConfirmDisconnect,
            onDismiss = onDismissDisconnectConfirm
        )
    }

    // 2. Modal: Selección y Exportación por Código QR
    if (showExportDialog) {
        ExportServicesDialog(
            accounts = uiState.accounts,
            onExportBatchesPayload = onExportBatchesPayload,
            onCompleteExport = onCompleteExport,
            onDismiss = onDismissExport
        )
    }

    // 3. Modal: Configuración de Protección E2EE (3 Pasos)
    if (showDriveProtectDialog) {
        DriveProtectDialog(
            onProtectAndSync = onProtectAndSync,
            onDismiss = onDismissDriveProtect
        )
    }

    // 4. Modal: Descifrado y Restauración Directa desde la Tarjeta Principal
    if (showDriveDecryptDialog) {
        DriveDecryptDialog(
            backupDateMillis = uiState.driveBackupInfo?.modifiedTimeMillis,
            deviceName = uiState.driveBackupInfo?.deviceName,
            isActual = uiState.lastSyncTimestamp > 0L && uiState.lastSyncedHash.isNotEmpty() && !uiState.hasUnsyncedChanges,
            onRestore = onRestoreDriveDecrypt,
            onDismiss = onDismissDriveDecrypt
        )
    }

    // 5. Modal: Historial de Versiones (Point-in-Time), Restauración In-Situ y Borrado Granular
    if (showBackupDetailsDialog) {
        DriveBackupDetailsDialog(
            backupItems = uiState.backupHistoryList,
            isLoading = isBackupHistoryLoading || uiState.isFetchingBackupHistory || uiState.isRefreshingBackupHistory,
            lastFetchTimestamp = uiState.lastHistoryFetchTimestamp,
            lastSyncTimestamp = uiState.lastSyncTimestamp,
            lastSyncedHash = uiState.lastSyncedHash,
            hasUnsyncedChanges = uiState.hasUnsyncedChanges,
            onForceRefresh = onForceRefreshBackupHistory,
            onRestoreBackup = onRestoreBackupHistoryItem,
            onDeleteSpecificBackup = onDeleteBackupHistoryItem,
            onDismiss = onDismissBackupDetails
        )
    }

    // 6. Modal: Advertencia de Sobrescritura de Respaldo Remoto
    if (showOverwriteWarningDialog) {
        DriveOverwriteWarningDialog(
            backupInfo = uiState.driveBackupInfo,
            formattedLastSync = formattedLastSync,
            onConfirmOverwrite = onConfirmOverwrite,
            onRestoreInstead = onRestoreInstead,
            onDismiss = onDismissOverwriteWarning
        )
    }
}
