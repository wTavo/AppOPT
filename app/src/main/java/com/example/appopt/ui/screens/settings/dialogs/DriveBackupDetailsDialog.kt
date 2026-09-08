package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.example.appopt.R
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.security.MnemonicManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.screens.settings.dialogs.components.DriveBackupDecryptForm
import com.example.appopt.ui.screens.settings.dialogs.components.DriveBackupItemCard
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

/**
 * Diálogo modal con máquina de estados unificada (Single-Dialog State Machine) para inspeccionar el historial
 * de versiones de respaldo en Google Drive (Point-in-Time Recovery), restaurar versiones específicas mediante
 * descifrado directo en el modal, o eliminarlas de forma individual.
 *
 * Cumple con la Directiva 14 (navegación modal defensiva sin desmontaje/parpadeo de ventanas) y estandarización
 * de nomenclatura de botones («Cerrar» para vista principal, «Volver» para sub-estados).
 *
 * @param backupItems Lista de versiones de respaldo disponibles ordenadas por fecha.
 * @param isLoading Indica si hay una operación asíncrona de consulta, recarga o borrado en curso.
 * @param lastFetchTimestamp Marca de tiempo de la última consulta al historial para cálculo del enfriamiento.
 * @param lastSyncTimestamp Marca de tiempo de la última sincronización local confirmada.
 * @param hasUnsyncedChanges Indica si hay cambios locales sin sincronizar en este dispositivo.
 * @param onForceRefresh Callback invocado para forzar una consulta fresca a Google Drive al presionar el botón de refresco.
 * @param onRestoreBackup Callback invocado para restaurar una versión específica con sus caracteres de descifrado.
 * @param onDeleteSpecificBackup Callback invocado para eliminar una versión específica.
 * @param onDismiss Callback invocado para cerrar el modal.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveBackupDetailsDialog(
    backupItems: List<DriveBackupItem>,
    isLoading: Boolean,
    lastFetchTimestamp: Long = 0L,
    lastSyncTimestamp: Long = 0L,
    hasUnsyncedChanges: Boolean = false,
    onForceRefresh: () -> Unit = {},
    onRestoreBackup: (DriveBackupItem, CharArray) -> Unit,
    onDeleteSpecificBackup: (DriveBackupItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appHaptics = rememberAppHaptics()

    var pendingRestoreBackup by remember { mutableStateOf<DriveBackupItem?>(null) }
    var restoreSecretText by remember { mutableStateOf("") }
    var isRestoreSecretVisible by remember { mutableStateOf(false) }

    var pendingDeleteBackup by remember { mutableStateOf<DriveBackupItem?>(null) }
    var currentTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            currentTick = System.currentTimeMillis()
            delay(1_000L.milliseconds)
        }
    }

    val elapsed = (currentTick - lastFetchTimestamp).coerceAtLeast(0L)
    val remainingMillis = (SecurityConfig.BACKUP_HISTORY_CACHE_TTL_MILLIS - elapsed).coerceAtLeast(0L)
    val secondsRemaining = (remainingMillis + 999L) / 1000L
    val isCooldownActive = secondsRemaining > 0L

    AlertDialog(
        onDismissRequest = {
            if (pendingRestoreBackup != null) {
                pendingRestoreBackup = null
                restoreSecretText = ""
            } else if (pendingDeleteBackup != null) {
                pendingDeleteBackup = null
            } else {
                onDismiss()
            }
        },
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        title = {
            val titleText = when {
                pendingRestoreBackup != null -> stringResource(R.string.settings_drive_decrypt_title)
                pendingDeleteBackup != null -> stringResource(R.string.settings_drive_delete_version_confirm_title)
                else -> stringResource(R.string.settings_drive_history_title)
            }
            val titleColor = if (pendingDeleteBackup != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                color = titleColor
            )
        },
        text = {
            when {
                pendingRestoreBackup != null -> {
                    val pendingTarget = pendingRestoreBackup!!
                    val isPendingActual = lastSyncTimestamp > 0L &&
                            !hasUnsyncedChanges &&
                            pendingTarget.isMostRecent
                    DriveBackupDecryptForm(
                        targetBackup = pendingTarget,
                        isActual = isPendingActual,
                        restoreSecretText = restoreSecretText,
                        onRestoreSecretChange = { restoreSecretText = it },
                        isRestoreSecretVisible = isRestoreSecretVisible,
                        onToggleSecretVisibility = { isRestoreSecretVisible = !isRestoreSecretVisible }
                    )
                }
                pendingDeleteBackup != null -> {
                    Text(
                        text = stringResource(R.string.settings_drive_delete_version_confirm_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Dimensions.IconSize.large),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_history_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                                Box(
                                    modifier = Modifier.height(Dimensions.IconSize.large),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(Dimensions.IconSize.small),
                                            strokeWidth = Dimensions.Stroke.thin
                                        )
                                    } else if (isCooldownActive) {
                                        Surface(
                                            shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.settings_drive_history_cooldown_badge, secondsRemaining),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                modifier = Modifier.padding(
                                                    horizontal = Dimensions.Spacing.xs,
                                                    vertical = Dimensions.Spacing.xs / 2
                                                )
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = {
                                                appHaptics.click()
                                                onForceRefresh()
                                            },
                                            modifier = Modifier.size(Dimensions.IconSize.large)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = stringResource(R.string.settings_drive_history_refresh_action),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(Dimensions.IconSize.small)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimensions.Spacing.lg),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(Dimensions.IconSize.large))
                            }
                        } else if (backupItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimensions.Spacing.md),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_history_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = Dimensions.ComponentSize.modalListMaxHeight),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                            ) {
                                items(backupItems, key = { it.fileId }) { item ->
                                    val formattedDate = remember(item.modifiedTimeMillis, currentTick) {
                                        DateTimeFormatter.formatRelativeSyncTime(context, item.modifiedTimeMillis)
                                    }
                                    val isActual = lastSyncTimestamp > 0L &&
                                            !hasUnsyncedChanges &&
                                            item.isMostRecent

                                    DriveBackupItemCard(
                                        item = item,
                                        isActual = isActual,
                                        formattedDate = formattedDate,
                                        onDeleteClick = {
                                            appHaptics.click()
                                            pendingDeleteBackup = item
                                        },
                                        onRestoreClick = {
                                            appHaptics.click()
                                            restoreSecretText = ""
                                            isRestoreSecretVisible = false
                                            pendingRestoreBackup = item
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                pendingRestoreBackup != null -> {
                    var isProcessing by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            if (!isProcessing) {
                                isProcessing = true
                                val targetItem = pendingRestoreBackup!!
                                val normalizedSecret = if (restoreSecretText.contains(" ")) {
                                    MnemonicManager.normalizePhrase(restoreSecretText)
                                } else {
                                    restoreSecretText.trim()
                                }
                                val passChars = normalizedSecret.toCharArray()
                                try {
                                    onRestoreBackup(targetItem, passChars)
                                } finally {
                                    passChars.fill('0')
                                    pendingRestoreBackup = null
                                    restoreSecretText = ""
                                    isProcessing = false
                                }
                            }
                        },
                        enabled = restoreSecretText.isNotBlank(),
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_drive_decrypt_and_restore),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                pendingDeleteBackup != null -> {
                    Button(
                        onClick = {
                            val target = pendingDeleteBackup
                            pendingDeleteBackup = null
                            if (target != null) {
                                onDeleteSpecificBackup(target)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_drive_delete_version_action),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                else -> {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.action_close),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (pendingRestoreBackup != null || pendingDeleteBackup != null) {
                TextButton(onClick = {
                    pendingRestoreBackup = null
                    restoreSecretText = ""
                    pendingDeleteBackup = null
                }) {
                    Text(
                        text = stringResource(R.string.settings_drive_details_back),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        modifier = modifier
    )
}

