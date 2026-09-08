package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.appopt.R
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.security.MnemonicManager
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

/**
 * Diálogo modal con máquina de estados unificada (Single-Dialog State Machine) para inspeccionar el historial
 * de versiones de respaldo en Google Drive (Point-in-Time Recovery), restaurar versiones específicas mediante
 * descifrado directo en el modal, o eliminarlas de forma granular/total.
 *
 * Cumple con la Directiva 14 (navegación modal defensiva sin desmontaje/parpadeo de ventanas) y estandarización
 * de nomenclatura de botones («Cerrar» para vista principal, «Volver» para sub-estados).
 *
 * @param backupItems Lista de versiones de respaldo disponibles ordenadas por fecha.
 * @param isLoading Indica si hay una operación asíncrona de carga o borrado en curso.
 * @param onRestoreBackup Callback invocado para restaurar una versión específica con sus caracteres de descifrado.
 * @param onDeleteSpecificBackup Callback invocado para eliminar una versión específica.
 * @param onDeleteAllConfirmed Callback invocado para eliminar todas las copias de seguridad de la nube.
 * @param onDismiss Callback invocado para cerrar el modal.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveBackupDetailsDialog(
    backupItems: List<DriveBackupItem>,
    isLoading: Boolean,
    onRestoreBackup: (DriveBackupItem, CharArray) -> Unit,
    onDeleteSpecificBackup: (DriveBackupItem) -> Unit,
    onDeleteAllConfirmed: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appHaptics = rememberAppHaptics()

    var pendingRestoreBackup by remember { mutableStateOf<DriveBackupItem?>(null) }
    var restoreSecretText by remember { mutableStateOf("") }
    var isRestoreSecretVisible by remember { mutableStateOf(false) }

    var pendingDeleteBackup by remember { mutableStateOf<DriveBackupItem?>(null) }
    var isConfirmingDeleteAll by remember { mutableStateOf(false) }
    var currentTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val now = System.currentTimeMillis()
            currentTick = now
            val millisUntilNextMinute = 60_000L - (now % 60_000L)
            delay(millisUntilNextMinute.coerceAtLeast(1_000L).milliseconds)
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (pendingRestoreBackup != null) {
                pendingRestoreBackup = null
                restoreSecretText = ""
            } else if (pendingDeleteBackup != null) {
                pendingDeleteBackup = null
            } else if (isConfirmingDeleteAll) {
                isConfirmingDeleteAll = false
            } else {
                onDismiss()
            }
        },
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        title = {
            val titleText = when {
                pendingRestoreBackup != null -> stringResource(R.string.settings_drive_decrypt_title)
                pendingDeleteBackup != null -> stringResource(R.string.settings_drive_delete_version_confirm_title)
                isConfirmingDeleteAll -> stringResource(R.string.settings_drive_delete_all_confirm_title)
                else -> stringResource(R.string.settings_drive_history_title)
            }
            val titleColor = if (pendingDeleteBackup != null || isConfirmingDeleteAll) {
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
                    DriveBackupDecryptForm(
                        targetBackup = pendingRestoreBackup!!,
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
                isConfirmingDeleteAll -> {
                    Text(
                        text = stringResource(R.string.settings_drive_delete_all_confirm_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_drive_history_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isLoading && backupItems.isEmpty()) {
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

                                    DriveBackupItemCard(
                                        item = item,
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
                isConfirmingDeleteAll -> {
                    Button(
                        onClick = {
                            isConfirmingDeleteAll = false
                            onDeleteAllConfirmed()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_drive_delete_confirm_btn),
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
            when {
                pendingRestoreBackup != null || pendingDeleteBackup != null || isConfirmingDeleteAll -> {
                    TextButton(onClick = {
                        pendingRestoreBackup = null
                        restoreSecretText = ""
                        pendingDeleteBackup = null
                        isConfirmingDeleteAll = false
                    }) {
                        Text(
                            text = stringResource(R.string.settings_drive_details_back),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                backupItems.isNotEmpty() -> {
                    TextButton(
                        onClick = {
                            appHaptics.click()
                            isConfirmingDeleteAll = true
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.IconSize.small)
                        )
                        Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                        Text(
                            text = stringResource(R.string.settings_drive_delete_all_action),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        },
        modifier = modifier
    )
}

/**
 * Sub-componente para el formulario de descifrado y restauración de una versión de respaldo.
 */
@Composable
private fun DriveBackupDecryptForm(
    targetBackup: DriveBackupItem,
    restoreSecretText: String,
    onRestoreSecretChange: (String) -> Unit,
    isRestoreSecretVisible: Boolean,
    onToggleSecretVisibility: () -> Unit
) {
    val context = LocalContext.current
    val formattedDate = remember(targetBackup.modifiedTimeMillis) {
        DateTimeFormatter.formatRelativeSyncTime(context, targetBackup.modifiedTimeMillis)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        Surface(
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(
                Dimensions.Stroke.thin,
                if (targetBackup.isMostRecent) SafeGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(Dimensions.IconSize.hero)
                        .background(
                            color = (if (targetBackup.isMostRecent) SafeGreen else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (targetBackup.isMostRecent) Icons.Filled.CloudDone else Icons.Filled.Restore,
                        contentDescription = null,
                        tint = if (targetBackup.isMostRecent) SafeGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimensions.IconSize.small)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                    ) {
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (targetBackup.isMostRecent) {
                            Surface(
                                shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                                color = SafeGreen.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_version_most_recent_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SafeGreen,
                                    modifier = Modifier.padding(
                                        horizontal = Dimensions.Spacing.xs,
                                        vertical = Dimensions.Spacing.xs / 2
                                    )
                                )
                            }
                        }
                    }

                    if (targetBackup.deviceName.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.settings_drive_decrypt_device, targetBackup.deviceName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_drive_decrypt_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = restoreSecretText,
            onValueChange = onRestoreSecretChange,
            label = { Text(stringResource(R.string.settings_drive_decrypt_input_label)) },
            visualTransformation = if (isRestoreSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleSecretVisibility) {
                    Icon(
                        imageVector = if (isRestoreSecretVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null
                    )
                }
            },
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Sub-componente para renderizar la tarjeta de cada versión individual de respaldo en la lista.
 */
@Composable
private fun DriveBackupItemCard(
    item: DriveBackupItem,
    formattedDate: String,
    onDeleteClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = if (item.isMostRecent) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        },
        border = BorderStroke(
            Dimensions.Stroke.thin,
            if (item.isMostRecent) SafeGreen.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(Dimensions.IconSize.hero)
                            .background(
                                color = (if (item.isMostRecent) SafeGreen else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.isMostRecent) Icons.Filled.CloudDone else Icons.Filled.CloudQueue,
                            contentDescription = null,
                            tint = if (item.isMostRecent) SafeGreen else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.IconSize.small)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                        ) {
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (item.isMostRecent) FontWeight.Bold else FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (item.isMostRecent) {
                                Surface(
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                                    color = SafeGreen.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_drive_version_most_recent_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = SafeGreen,
                                        modifier = Modifier.padding(
                                            horizontal = Dimensions.Spacing.xs,
                                            vertical = Dimensions.Spacing.xs / 2
                                        )
                                    )
                                }
                            }
                        }

                        if (item.deviceName.isNotBlank()) {
                            Text(
                                text = item.deviceName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.settings_drive_delete_version_action),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(Dimensions.IconSize.small)
                    )
                }
            }

            if (!item.isMostRecent) {
                Button(
                    onClick = onRestoreClick,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimensions.ComponentHeight.buttonCompact)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.IconSize.small)
                    )
                    Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                    Text(
                        text = stringResource(R.string.settings_drive_restore_version_action),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
