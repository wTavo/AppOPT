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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.components.SettingsSectionCard
import com.example.appopt.ui.components.SettingsStatusTile
import com.example.appopt.ui.navigation.NavigationOriginTracker
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.appSwitchColors

/**
 * Tarjeta de ajustes para la sincronización en la nube mediante Google Drive y copia cifrada E2EE.
 *
 * @param isDriveConnected Indica si la cuenta de Google Drive está autorizada y conectada.
 * @param isDriveLoading Indica si hay una operación asíncrona de Drive en curso.
 * @param formattedLastSync Marca de tiempo relativa formateada de la última sincronización.
 * @param driveBackupExists Indica si se detectó una copia de seguridad remota existente en Google Drive.
 * @param hasUnsyncedChanges Indica si existen cambios locales no sincronizados con la nube.
 * @param lastSyncTimestamp Marca de tiempo en milisegundos de la última sincronización.
 * @param isAutoSyncEnabled Indica si la copia automática al hacer cambios está activada.
 * @param onConnectClick Callback para conectar la cuenta de Google.
 * @param onManualSyncClick Callback para disparar la sincronización inmediata.
 * @param onCreateBackupClick Callback para crear una nueva copia de seguridad.
 * @param onBackupDetailsClick Callback para ver detalles y gestionar la copia existente.
 * @param onDisconnectClick Callback para desvincular la cuenta de Google.
 * @param onAutoSyncToggle Callback para activar o desactivar la copia automática al hacer cambios.
 * @param onMobileDataToggle Callback para alternar el permiso de datos móviles.
 * @param modifier Modificador de diseño Compose opcional.
 * @param hasLocalAccounts Indica si existen cuentas o servicios 2FA locales registrados en la bóveda.
 */
@Composable
fun DriveSyncSettingsCard(
    isDriveConnected: Boolean,
    isDriveLoading: Boolean,
    formattedLastSync: String?,
    driveBackupExists: Boolean,
    hasUnsyncedChanges: Boolean,
    lastSyncTimestamp: Long,
    isAutoSyncEnabled: Boolean,
    isSyncMobileDataAllowed: Boolean,
    onConnectClick: () -> Unit,
    onManualSyncClick: () -> Unit,
    onCreateBackupClick: () -> Unit,
    onBackupDetailsClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onAutoSyncToggle: (Boolean) -> Unit,
    onMobileDataToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hasLocalAccounts: Boolean = true
) {
    var backupDetailsCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var createBackupCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var disconnectCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val isSyncingActive = isDriveLoading && isDriveConnected

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

    SettingsSectionCard(
        title = stringResource(R.string.settings_drive_title),
        description = stringResource(R.string.settings_drive_description),
        icon = if (isDriveConnected) Icons.Filled.CloudDone else Icons.Filled.Sync,
        iconTint = if (isDriveConnected) SafeGreen else MaterialTheme.colorScheme.primary,
        headerTrailing = {
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
        },
        modifier = modifier
    ) {
        // 3. Fila con Contenedor de Estado de Copia y Desvinculación
        if (isDriveConnected) {
            val hasBackupInfo = !isSyncingActive && (formattedLastSync != null || driveBackupExists)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsStatusTile(
                    icon = when {
                        isSyncingActive -> Icons.Filled.Sync
                        hasBackupInfo -> Icons.Filled.CloudDone
                        else -> Icons.Filled.Search
                    },
                    iconTint = when {
                        isSyncingActive -> MaterialTheme.colorScheme.primary
                        hasBackupInfo -> SafeGreen
                        else -> MaterialTheme.colorScheme.primary
                    },
                    title = when {
                        isSyncingActive -> stringResource(R.string.settings_drive_syncing)
                        formattedLastSync != null -> stringResource(R.string.settings_drive_last_sync, formattedLastSync)
                        driveBackupExists -> stringResource(R.string.settings_drive_backup_found)
                        else -> stringResource(R.string.settings_drive_press_to_search_backups)
                    },
                    titleColor = when {
                        isSyncingActive -> MaterialTheme.colorScheme.primary
                        hasBackupInfo -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.primary
                    },
                    backgroundColor = when {
                        isSyncingActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        hasBackupInfo -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    },
                    onClick = if (!isSyncingActive) {
                        {
                            NavigationOriginTracker.updateModalOrigin(backupDetailsCoordinates)
                            onBackupDetailsClick()
                        }
                    } else null,
                    trailingContent = if (!isSyncingActive) {
                        {
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = if (hasBackupInfo) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Dimensions.IconSize.small)
                            )
                        }
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .onPlaced { backupDetailsCoordinates = it }
                )

                val isDisconnectAllowed = !isDriveLoading
                AnimatedVisibility(
                    visible = isDisconnectAllowed,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .onPlaced { disconnectCoordinates = it }
                            .clickable {
                                NavigationOriginTracker.updateModalOrigin(disconnectCoordinates)
                                onDisconnectClick()
                            }
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
            } else {
                if (lastSyncTimestamp > 0L) {
                    if (hasUnsyncedChanges && !isDriveLoading && hasLocalAccounts) {
                        Button(
                            onClick = onManualSyncClick,
                            enabled = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_drive_sync_button),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else if (hasLocalAccounts) {
                    Button(
                        onClick = {
                            NavigationOriginTracker.updateModalOrigin(createBackupCoordinates)
                            onCreateBackupClick()
                        },
                        enabled = !isDriveLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPlaced { createBackupCoordinates = it },
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_drive_create_button),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // 5. Contenedor de automatización (Copia automática al hacer cambios y datos móviles)
            if (isDriveConnected && lastSyncTimestamp > 0L) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(Dimensions.Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        // Switch principal: Copia automática al hacer cambios
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAutoSyncToggle(!isAutoSyncEnabled) }
                                .padding(vertical = Dimensions.Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = Dimensions.Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_auto_sync_title),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = stringResource(R.string.settings_drive_auto_sync_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isAutoSyncEnabled,
                                onCheckedChange = onAutoSyncToggle,
                                colors = appSwitchColors()
                            )
                        }

                        if (isAutoSyncEnabled) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onMobileDataToggle(!isSyncMobileDataAllowed) }
                                    .padding(vertical = Dimensions.Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = Dimensions.Spacing.sm),
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
