package com.example.appopt.ui.screens.settings.dialogs.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.DateTimeFormatter

/**
 * Sub-estados de contenido dentro del paso modal de historial de respaldos.
 */
private enum class HistoryContentState {
    LOADING,
    EMPTY,
    ITEMS
}

/**
 * Paso modal para listar el historial de copias de seguridad de Google Drive (Directivas 14 y 29).
 *
 * @param backupItems Lista de respaldos encontrados en la nube.
 * @param isLoading Indica si la consulta remota está en progreso.
 * @param lastSyncTimestamp Marca de tiempo de la última sincronización local.
 * @param lastSyncedHash Firma hash de la última copia de seguridad local.
 * @param hasUnsyncedChanges Indica si hay cambios locales no sincronizados.
 * @param isCooldownActive Indica si el tiempo de espera para refrescar sigue activo.
 * @param secondsRemaining Segundos restantes de enfriamiento de la API.
 * @param onRestoreSelected Callback al pulsar restaurar en un elemento.
 * @param onDeleteSelected Callback al pulsar eliminar en un elemento.
 * @param onForceRefresh Callback para forzar la sincronización remota.
 * @param onDismiss Callback para cerrar el diálogo.
 */
@Composable
fun DriveDetailsHistoryStep(
    backupItems: List<DriveBackupItem>,
    isLoading: Boolean,
    lastSyncTimestamp: Long,
    lastSyncedHash: String,
    hasUnsyncedChanges: Boolean,
    isCooldownActive: Boolean,
    secondsRemaining: Long,
    onRestoreSelected: (DriveBackupItem) -> Unit,
    onDeleteSelected: (DriveBackupItem) -> Unit,
    onForceRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appHaptics = rememberAppHaptics()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.Spacing.lg)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        // Cabecera fija
        Text(
            text = stringResource(R.string.settings_drive_history_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Cuerpo central scrolleable aislado
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            Text(
                text = stringResource(R.string.settings_drive_history_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val contentState = when {
                isLoading -> HistoryContentState.LOADING
                backupItems.isEmpty() -> HistoryContentState.EMPTY
                else -> HistoryContentState.ITEMS
            }

            AnimatedContent(
                targetState = contentState,
                transitionSpec = { Motion.Spec.dialogStepContentTransform() },
                contentAlignment = Alignment.TopCenter,
                label = "historyContentStateTransition",
                modifier = Modifier.fillMaxWidth()
            ) { state ->
                when (state) {
                    HistoryContentState.LOADING -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Dimensions.Spacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Dimensions.IconSize.large)
                            )
                        }
                    }
                    HistoryContentState.EMPTY -> {
                        Text(
                            text = stringResource(R.string.settings_drive_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Dimensions.Spacing.lg)
                        )
                    }
                    HistoryContentState.ITEMS -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = Dimensions.ComponentSize.modalListMaxHeight),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                            contentPadding = PaddingValues(bottom = Dimensions.Spacing.xs)
                        ) {
                            items(
                                items = backupItems,
                                key = { it.fileId.ifEmpty { it.modifiedTimeMillis.toString() } }
                            ) { item ->
                                val isActual = (lastSyncTimestamp > 0L &&
                                        item.modifiedTimeMillis == lastSyncTimestamp &&
                                        lastSyncedHash.isNotEmpty() &&
                                        !hasUnsyncedChanges)

                                DriveBackupItemCard(
                                    item = item,
                                    isActual = isActual,
                                    formattedDate = DateTimeFormatter.formatRelativeSyncTime(context, item.modifiedTimeMillis),
                                    onRestoreClick = {
                                        appHaptics.click()
                                        onRestoreSelected(item)
                                    },
                                    onDeleteClick = {
                                        appHaptics.click()
                                        onDeleteSelected(item)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pie fijo de acciones
        AppDialogActionButtons(
            dismissText = stringResource(R.string.action_close),
            onDismiss = onDismiss,
            confirmText = if (isCooldownActive) {
                stringResource(R.string.settings_drive_history_cooldown_badge, secondsRemaining)
            } else {
                stringResource(R.string.settings_drive_history_refresh_action)
            },
            onConfirm = onForceRefresh,
            confirmEnabled = !isLoading && !isCooldownActive,
            isLoading = isLoading
        )
    }
}
