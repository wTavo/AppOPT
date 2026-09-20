package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.CloudVaultKeyStore
import com.example.appopt.ui.components.AppDestructiveConfirmDialog
import com.example.appopt.ui.screens.settings.SettingsUiState
import com.example.appopt.ui.screens.settings.coordinator.DriveDialogCoordinator
import com.example.appopt.ui.screens.settings.dialogs.components.DriveCreateBackupConfirmDialog
import com.example.appopt.ui.screens.settings.dialogs.components.DriveCreateStandardBackupConfirmDialog
import com.example.appopt.ui.screens.settings.dialogs.components.DriveDisableE2eeConfirmDialog

/**
 * Contenedor orquestador de diálogos modales para la pantalla de configuración.
 *
 * Directiva de diseño (Directiva 14):
 * - Centraliza la apertura defensiva de los modales de exportación, protección E2EE, restauración e historial.
 * - Garantiza que ningún diálogo permanezca abierto si la bóveda se encuentra bloqueada ([isUnlocked] = false).
 * - Invoca directamente a [AppDestructiveConfirmDialog] sin capas intermedias pasivas (Directiva 8).
 *
 * @param isUnlocked Estado de desbloqueo de la bóveda de seguridad.
 * @param uiState Estado inmutable de la pantalla de configuración.
 * @param coordinator Coordinador reactivo de diálogos y acciones de Google Drive.
 * @param formattedLastSync Cadena localizada del tiempo transcurrido desde la última sincronización.
 * @param onExportBatchesPayload Callback generador de cargas útiles cifradas para lotes QR.
 * @param onCompleteExport Callback invocado al completar la exportación de cuentas.
 */
@Composable
fun SettingsDialogContainer(
    isUnlocked: Boolean,
    uiState: SettingsUiState,
    coordinator: DriveDialogCoordinator,
    formattedLastSync: String?,
    onExportBatchesPayload: suspend (Set<String>, CharArray) -> List<String>,
    onCompleteExport: (Set<String>, Boolean) -> Unit
) {
    if (!isUnlocked) return

    // 1. Modal: Confirmación de Desvinculación de Google Drive (Directiva 8: in-situ con AppDestructiveConfirmDialog)
    if (coordinator.showDisconnectConfirmDialog) {
        AppDestructiveConfirmDialog(
            title = stringResource(R.string.settings_drive_disconnect_confirm_title),
            message = stringResource(R.string.settings_drive_disconnect_confirm_msg),
            confirmText = stringResource(R.string.settings_drive_disconnect_button),
            onConfirm = { coordinator.confirmDisconnect() },
            onDismiss = { coordinator.showDisconnectConfirmDialog = false },
            icon = Icons.Filled.CloudOff
        )
    }

    // 2. Modal: Selección y Exportación por Código QR
    if (coordinator.showExportDialog) {
        ExportServicesDialog(
            accounts = uiState.accounts,
            onExportBatchesPayload = onExportBatchesPayload,
            onCompleteExport = onCompleteExport,
            onDismiss = { coordinator.showExportDialog = false }
        )
    }

    // 3. Modal: Configuración de Protección E2EE (3 Pasos)
    if (coordinator.showDriveProtectDialog) {
        DriveProtectDialog(
            onProtectAndSync = { pass, mnemonic -> coordinator.protectAndSync(pass, mnemonic) },
            onDismiss = { coordinator.showDriveProtectDialog = false }
        )
    }

    // 3.1 Modal: Confirmación de Creación de Respaldo con Clave de Bóveda Existente
    if (coordinator.showCreateBackupConfirmDialog) {
        DriveCreateBackupConfirmDialog(
            onConfirm = { coordinator.createBackupWithExistingKey() },
            onUseOtherKey = {
                coordinator.showCreateBackupConfirmDialog = false
                coordinator.showDriveProtectDialog = true
            },
            onDismiss = { coordinator.showCreateBackupConfirmDialog = false }
        )
    }

    // 3.2 Modal: Confirmación de Creación de Respaldo Estándar sin Cifrado E2EE
    if (coordinator.showCreateStandardBackupConfirmDialog) {
        DriveCreateStandardBackupConfirmDialog(
            onConfirm = {
                coordinator.showCreateStandardBackupConfirmDialog = false
                coordinator.createBackupWithExistingKey()
            },
            onEnableE2ee = {
                coordinator.showCreateStandardBackupConfirmDialog = false
                coordinator.handleE2eeToggle(true)
            },
            onDismiss = { coordinator.showCreateStandardBackupConfirmDialog = false }
        )
    }

    // 4. Modal: Descifrado y Restauración Directa desde la Tarjeta Principal
    if (coordinator.showDriveDecryptDialog) {
        val targetItem = coordinator.restoreSelectedBackupItem ?: uiState.driveBackupInfo
        DriveDecryptDialog(
            fileId = targetItem?.fileId,
            backupDateMillis = targetItem?.modifiedTimeMillis,
            deviceName = targetItem?.deviceName,
            isActual = targetItem?.isMostRecent ?: false,
            isEncrypted = targetItem?.isEncrypted ?: uiState.isDriveBackupEncrypted,
            onRestore = { pass -> coordinator.restoreDriveDecrypt(pass) },
            onDismiss = {
                coordinator.showDriveDecryptDialog = false
                coordinator.restoreSelectedBackupItem = null
            }
        )
    }

    // 5. Modal: Historial de Versiones (Point-in-Time), Restauración In-Situ y Borrado Granular
    if (coordinator.showBackupDetailsDialog) {
        DriveBackupDetailsDialog(
            backupItems = uiState.backupHistoryList,
            isLoading = coordinator.isHistoryLoadingSynchronous || uiState.isFetchingBackupHistory || uiState.isRefreshingBackupHistory,
            lastFetchTimestamp = uiState.lastHistoryFetchTimestamp,
            lastSyncTimestamp = uiState.lastSyncTimestamp,
            lastSyncedHash = uiState.lastSyncedHash,
            hasUnsyncedChanges = uiState.hasUnsyncedChanges,
            onForceRefresh = { coordinator.forceRefreshHistory() },
            onRestoreBackup = { item, pass -> coordinator.restoreBackupHistoryItem(item, pass) },
            onDeleteSpecificBackup = { item, pass -> coordinator.deleteBackupHistoryItem(item, pass) },
            onDismiss = { coordinator.showBackupDetailsDialog = false }
        )
    }

    // 6. Modal: Advertencia y Confirmación para Desactivar Cifrado E2EE
    if (coordinator.showDisableE2eeConfirmDialog) {
        DriveDisableE2eeConfirmDialog(
            onConfirm = { coordinator.confirmDisableE2ee() },
            onDismiss = { coordinator.showDisableE2eeConfirmDialog = false }
        )
    }
}
