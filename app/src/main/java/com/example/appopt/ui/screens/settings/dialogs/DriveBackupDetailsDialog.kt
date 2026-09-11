package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.example.appopt.R
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.security.MnemonicManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.screens.settings.dialogs.components.DriveBackupDecryptForm
import com.example.appopt.ui.screens.settings.dialogs.components.DriveBackupItemCard
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

/**
 * Sub-estados internos del diálogo modal de historial de respaldos en la nube.
 */
private enum class DriveDetailsSubState {
    HISTORY,
    RESTORE_DECRYPT,
    DELETE_SINGLE
}

/**
 * Diálogo modal con máquina de estados unificada (Single-Dialog State Machine) para inspeccionar el historial
 * de versiones de respaldo en Google Drive (Point-in-Time Recovery), restaurar versiones específicas mediante
 * descifrado directo en el modal, o eliminarlas individualmente mediante autenticación criptográfica.
 *
 * Cumple con la Directiva 14 (navegación modal defensiva sin desmontaje/parpadeo de ventanas) y estandarización
 * de nomenclatura de botones («Cerrar» para vista principal, «Volver» para sub-estados).
 *
 * @param backupItems Lista de versiones de respaldo disponibles ordenadas por fecha.
 * @param isLoading Indica si hay una operación asíncrona de consulta, recarga o borrado en curso.
 * @param lastFetchTimestamp Marca de tiempo de la última consulta al historial para cálculo del enfriamiento.
 * @param lastSyncTimestamp Marca de tiempo de la última sincronización local confirmada.
 * @param lastSyncedHash Huella digital criptográfica SHA-256 de la última sincronización confirmada en este dispositivo.
 * @param hasUnsyncedChanges Indica si hay cambios locales sin sincronizar en este dispositivo.
 * @param onForceRefresh Callback invocado para forzar una consulta fresca a Google Drive al presionar el botón de refresco.
 * @param onRestoreBackup Callback invocado para restaurar una versión específica con sus caracteres de descifrado.
 * @param onDeleteSpecificBackup Callback invocado para eliminar una versión específica con sus caracteres de descifrado.
 * @param onDismiss Callback invocado para cerrar el modal.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveBackupDetailsDialog(
    backupItems: List<DriveBackupItem>,
    isLoading: Boolean,
    lastFetchTimestamp: Long = 0L,
    lastSyncTimestamp: Long = 0L,
    lastSyncedHash: String = "",
    hasUnsyncedChanges: Boolean = false,
    onForceRefresh: () -> Unit = {},
    onRestoreBackup: (DriveBackupItem, CharArray) -> Unit,
    onDeleteSpecificBackup: (DriveBackupItem, CharArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appHaptics = rememberAppHaptics()

    var pendingRestoreBackup by remember { mutableStateOf<DriveBackupItem?>(null) }
    var restoreSecretText by remember { mutableStateOf("") }
    var isRestoreSecretVisible by remember { mutableStateOf(false) }

    var pendingDeleteBackup by remember { mutableStateOf<DriveBackupItem?>(null) }
    var deleteSecretText by remember { mutableStateOf("") }
    var isDeleteSecretVisible by remember { mutableStateOf(false) }

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

    val currentDeviceId = remember { com.example.appopt.AuthenticatorApp.instance.preferencesManager.getInstallationId() }

    val currentSubState = when {
        pendingRestoreBackup != null -> DriveDetailsSubState.RESTORE_DECRYPT
        pendingDeleteBackup != null -> DriveDetailsSubState.DELETE_SINGLE
        else -> DriveDetailsSubState.HISTORY
    }

    AlertDialog(
        onDismissRequest = {
            if (pendingRestoreBackup != null) {
                pendingRestoreBackup = null
                restoreSecretText = ""
            } else if (pendingDeleteBackup != null) {
                pendingDeleteBackup = null
                deleteSecretText = ""
            } else {
                onDismiss()
            }
        },
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        title = {
            AnimatedContent(
                targetState = currentSubState,
                transitionSpec = { Motion.Spec.dialogStepContentTransform() },
                label = "driveDetailsTitleTransition"
            ) { subState ->
                Text(
                    text = when (subState) {
                        DriveDetailsSubState.RESTORE_DECRYPT -> stringResource(R.string.settings_drive_decrypt_title)
                        DriveDetailsSubState.DELETE_SINGLE -> stringResource(R.string.settings_drive_delete_version_confirm_title)
                        DriveDetailsSubState.HISTORY -> stringResource(R.string.settings_drive_history_title)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = if (subState == DriveDetailsSubState.DELETE_SINGLE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        },
        text = {
            AnimatedContent(
                targetState = currentSubState,
                transitionSpec = { Motion.Spec.dialogStepContentTransform() },
                contentAlignment = Alignment.TopStart,
                modifier = Modifier.fillMaxWidth(),
                label = "driveDetailsSubStateTransition"
            ) { subState ->
                when (subState) {
                    DriveDetailsSubState.RESTORE_DECRYPT -> {
                        val backupToRestore = pendingRestoreBackup
                        if (backupToRestore != null) {
                            DriveBackupDecryptForm(
                                targetBackup = backupToRestore,
                                isActual = false,
                                restoreSecretText = restoreSecretText,
                                onRestoreSecretChange = { restoreSecretText = it },
                                isRestoreSecretVisible = isRestoreSecretVisible,
                                onToggleSecretVisibility = { isRestoreSecretVisible = !isRestoreSecretVisible },
                                hintText = stringResource(R.string.settings_drive_decrypt_hint)
                            )
                        }
                    }
                    DriveDetailsSubState.DELETE_SINGLE -> {
                        val backupToDelete = pendingDeleteBackup
                        if (backupToDelete != null) {
                            DriveBackupDecryptForm(
                                targetBackup = backupToDelete,
                                isActual = false,
                                restoreSecretText = deleteSecretText,
                                onRestoreSecretChange = { deleteSecretText = it },
                                isRestoreSecretVisible = isDeleteSecretVisible,
                                onToggleSecretVisibility = { isDeleteSecretVisible = !isDeleteSecretVisible },
                                hintText = stringResource(R.string.settings_drive_delete_version_auth_hint)
                            )
                        }
                    }
                    DriveDetailsSubState.HISTORY -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                        ) {
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_drive_history_subtitle),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                                    Box(
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
                                val latestFromCurrentDevice = backupItems.firstOrNull {
                                    if (it.deviceId.isNotBlank()) {
                                        it.deviceId == currentDeviceId
                                    } else {
                                        lastSyncTimestamp > 0L && it.modifiedTimeMillis == lastSyncTimestamp
                                    }
                                }

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = Dimensions.ComponentSize.modalListMaxHeight),
                                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                                    contentPadding = PaddingValues(bottom = Dimensions.Spacing.xs)
                                ) {
                                    items(backupItems, key = { it.fileId }) { item ->
                                        val formattedDate = remember(item.modifiedTimeMillis, currentTick) {
                                            DateTimeFormatter.formatRelativeSyncTime(context, item.modifiedTimeMillis)
                                        }
                                        val isFromCurrentDevice = if (item.deviceId.isNotBlank()) {
                                            item.deviceId == currentDeviceId
                                        } else {
                                            lastSyncTimestamp > 0L && item.modifiedTimeMillis == lastSyncTimestamp
                                        }

                                        val isActual = isFromCurrentDevice &&
                                                lastSyncTimestamp > 0L &&
                                                lastSyncedHash.isNotEmpty() &&
                                                !hasUnsyncedChanges &&
                                                (item.fileId == latestFromCurrentDevice?.fileId)

                                        DriveBackupItemCard(
                                            item = item,
                                            isActual = isActual,
                                            formattedDate = formattedDate,
                                            onDeleteClick = {
                                                appHaptics.click()
                                                deleteSecretText = ""
                                                isDeleteSecretVisible = false
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
            }
        },
        confirmButton = {
            AnimatedContent(
                targetState = currentSubState,
                transitionSpec = { Motion.Spec.dialogStepContentTransform() },
                modifier = Modifier.fillMaxWidth(),
                label = "driveDetailsButtonsTransition"
            ) { subState ->
                when (subState) {
                    DriveDetailsSubState.HISTORY -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    appHaptics.click()
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                            ) {
                                Text(
                                    text = stringResource(R.string.action_close),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    DriveDetailsSubState.RESTORE_DECRYPT -> {
                        var isProcessing by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    appHaptics.click()
                                    pendingRestoreBackup = null
                                    restoreSecretText = ""
                                },
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_details_back),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            Button(
                                onClick = {
                                    if (!isProcessing) {
                                        isProcessing = true
                                        val targetItem = pendingRestoreBackup
                                        if (targetItem != null) {
                                            val normalizedSecret = if (restoreSecretText.contains(" ")) {
                                                MnemonicManager.normalizePhrase(restoreSecretText)
                                            } else {
                                                restoreSecretText.trim()
                                            }
                                            val passChars = normalizedSecret.toCharArray()
                                            onRestoreBackup(targetItem, passChars)
                                            pendingRestoreBackup = null
                                            restoreSecretText = ""
                                        }
                                        isProcessing = false
                                    }
                                },
                                enabled = restoreSecretText.isNotBlank(),
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.xs),
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_decrypt_and_restore),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    DriveDetailsSubState.DELETE_SINGLE -> {
                        var isProcessing by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    appHaptics.click()
                                    pendingDeleteBackup = null
                                    deleteSecretText = ""
                                },
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_details_back),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            Button(
                                onClick = {
                                    if (!isProcessing) {
                                        isProcessing = true
                                        val targetItem = pendingDeleteBackup
                                        if (targetItem != null) {
                                            val normalizedSecret = if (deleteSecretText.contains(" ")) {
                                                MnemonicManager.normalizePhrase(deleteSecretText)
                                            } else {
                                                deleteSecretText.trim()
                                            }
                                            val passChars = normalizedSecret.toCharArray()
                                            onDeleteSpecificBackup(targetItem, passChars)
                                            pendingDeleteBackup = null
                                            deleteSecretText = ""
                                        }
                                        isProcessing = false
                                    }
                                },
                                enabled = deleteSecretText.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.xs),
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_delete_version_action),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        },
        dismissButton = null,
        modifier = modifier
    )
}

