package com.example.appopt.ui.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.data.cloud.SyncFrequency
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.WarningOrange
import com.example.appopt.ui.theme.appSwitchColors

/**
 * Tarjeta de ajustes para la sincronización en la nube mediante Google Drive y copia cifrada E2EE.
 *
 * @param isDriveConnected Indica si la cuenta de Google Drive está autorizada y conectada.
 * @param isDriveLoading Indica si hay una operación asíncrona de Drive en curso.
 * @param isCheckingDriveBackup Indica si se está consultando el estado del respaldo remoto.
 * @param formattedLastSync Marca de tiempo relativa formateada de la última sincronización.
 * @param driveBackupExists Indica si se detectó una copia de seguridad remota existente en Google Drive.
 * @param hasUnsyncedChanges Indica si existen cambios locales no sincronizados con la nube.
 * @param lastSyncTimestamp Marca de tiempo en milisegundos de la última sincronización.
 * @param syncFrequency Frecuencia configurada para la sincronización periódica en segundo plano.
 * @param isSyncMobileDataAllowed Indica si se permite sincronizar con conexión de datos móviles.
 * @param onConnectClick Callback para conectar la cuenta de Google.
 * @param onManualSyncClick Callback para disparar la sincronización inmediata.
 * @param onRestoreClick Callback para iniciar el descifrado y restauración.
 * @param onCreateBackupClick Callback para crear una nueva copia de seguridad.
 * @param onBackupDetailsClick Callback para ver detalles y gestionar la copia existente.
 * @param onDisconnectClick Callback para desvincular la cuenta de Google.
 * @param onFrequencyClick Callback para abrir el selector de frecuencia de sincronización.
 * @param onMobileDataToggle Callback para alternar el permiso de datos móviles.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveSyncSettingsCard(
    isDriveConnected: Boolean,
    isDriveLoading: Boolean,
    isCheckingDriveBackup: Boolean,
    formattedLastSync: String?,
    driveBackupExists: Boolean,
    hasUnsyncedChanges: Boolean,
    lastSyncTimestamp: Long,
    syncFrequency: SyncFrequency,
    isSyncMobileDataAllowed: Boolean,
    onConnectClick: () -> Unit,
    onManualSyncClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onCreateBackupClick: () -> Unit,
    onBackupDetailsClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onFrequencyClick: () -> Unit,
    onMobileDataToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large)
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            val isSyncingActive = isDriveLoading && isDriveConnected

            // 1. Cabecera con icono, título y badge de estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = if (isDriveConnected) Icons.Filled.CloudDone else Icons.Filled.Sync,
                        contentDescription = null,
                        tint = if (isDriveConnected) SafeGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimensions.IconSize.medium)
                    )
                    Text(
                        text = stringResource(R.string.settings_drive_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                val badgeColor = when {
                    isSyncingActive -> MaterialTheme.colorScheme.primary
                    isDriveConnected -> SafeGreen
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val badgeBg = when {
                    isSyncingActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    isDriveConnected -> SafeGreen.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val badgeText = when {
                    isSyncingActive -> stringResource(R.string.settings_drive_status_syncing)
                    isDriveConnected -> stringResource(R.string.settings_drive_status_synced)
                    else -> stringResource(R.string.settings_drive_status_not_synced)
                }

                Surface(
                    shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.xs)
                    )
                }
            }

            // 2. Descripción clara
            Text(
                text = stringResource(R.string.settings_drive_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 3. Fila con Contenedor de Estado de Copia y Desvinculación
            if (isDriveConnected) {
                val isChecking = isCheckingDriveBackup
                val isSyncing = isSyncingActive
                val hasBackupInfo = !isChecking && !isSyncing && (formattedLastSync != null || driveBackupExists)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (hasBackupInfo) {
                                    Modifier.clickable { onBackupDetailsClick() }
                                } else {
                                    Modifier
                                }
                            ),
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        color = when {
                            isChecking -> WarningOrange.copy(alpha = 0.12f)
                            isSyncing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = Dimensions.Spacing.md,
                                vertical = Dimensions.Spacing.sm
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = if (isChecking || isSyncing) Icons.Filled.Sync else Icons.Filled.CloudDone,
                                    contentDescription = null,
                                    tint = when {
                                        isChecking -> WarningOrange
                                        isSyncing -> MaterialTheme.colorScheme.primary
                                        hasBackupInfo -> SafeGreen
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(Dimensions.IconSize.small)
                                )
                                Text(
                                    text = when {
                                        isChecking -> stringResource(R.string.settings_drive_checking_backup)
                                        isSyncing -> stringResource(R.string.settings_drive_syncing)
                                        formattedLastSync != null -> stringResource(R.string.settings_drive_last_sync, formattedLastSync)
                                        driveBackupExists -> stringResource(R.string.settings_drive_backup_found)
                                        else -> stringResource(R.string.settings_drive_last_sync_never)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        isChecking -> WarningOrange
                                        isSyncing -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }

                            if (hasBackupInfo) {
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(Dimensions.IconSize.small)
                                )
                            }
                        }
                    }

                    val isDisconnectAllowed = !isCheckingDriveBackup && !isDriveLoading
                    AnimatedVisibility(
                        visible = isDisconnectAllowed,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            modifier = Modifier.clickable { onDisconnectClick() }
                        ) {
                            Box(
                                modifier = Modifier.padding(Dimensions.Spacing.sm),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SyncDisabled,
                                    contentDescription = stringResource(R.string.settings_drive_disconnect_button),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(Dimensions.IconSize.medium)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Bloque de acciones
            if (!isDriveConnected) {
                Button(
                    onClick = onConnectClick,
                    enabled = !isDriveLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    if (isDriveLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimensions.IconSize.small),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = Dimensions.Stroke.regular
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_brand_google),
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.IconSize.small)
                        )
                        Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                        Text(
                            text = stringResource(R.string.settings_drive_connect_button),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            } else if (!isCheckingDriveBackup) {
                if (lastSyncTimestamp > 0L) {
                    if (hasUnsyncedChanges && !isDriveLoading) {
                        Button(
                            onClick = onManualSyncClick,
                            enabled = !isDriveLoading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_drive_sync_button),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else if (driveBackupExists) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        Button(
                            onClick = onRestoreClick,
                            enabled = !isDriveLoading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_drive_restore_button),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = onCreateBackupClick,
                            enabled = !isDriveLoading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_drive_create_button),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onCreateBackupClick,
                        enabled = !isDriveLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_drive_create_button),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // 5. Contenedor de automatización (frecuencia y datos móviles)
            if (isDriveConnected) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ) {
                    Column(
                        modifier = Modifier.padding(Dimensions.Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFrequencyClick() }
                                .padding(vertical = Dimensions.Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).padding(end = Dimensions.Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_frequency_title),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                val frequencyLabel = when (syncFrequency) {
                                    SyncFrequency.DAILY -> stringResource(R.string.settings_drive_frequency_daily)
                                    SyncFrequency.WEEKLY -> stringResource(R.string.settings_drive_frequency_weekly)
                                    SyncFrequency.MONTHLY -> stringResource(R.string.settings_drive_frequency_monthly)
                                    SyncFrequency.OFF -> stringResource(R.string.settings_drive_frequency_off)
                                }
                                Text(
                                    text = frequencyLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (syncFrequency != SyncFrequency.OFF) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onMobileDataToggle(!isSyncMobileDataAllowed) }
                                    .padding(vertical = Dimensions.Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f).padding(end = Dimensions.Spacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_drive_mobile_data_label),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_drive_mobile_data_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isSyncMobileDataAllowed,
                                    onCheckedChange = onMobileDataToggle,
                                    colors = appSwitchColors()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
