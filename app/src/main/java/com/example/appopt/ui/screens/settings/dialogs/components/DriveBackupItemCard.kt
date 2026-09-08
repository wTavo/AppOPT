package com.example.appopt.ui.screens.settings.dialogs.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.appopt.R
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen

/**
 * Componente visual para renderizar la tarjeta de una versión de respaldo en Google Drive.
 *
 * Muestra el estado de sincronización (icono CloudDone/CloudQueue), la fecha relativa formateada,
 * el nombre del dispositivo origen, la insignia "Actual" (si coincide con el estado local activo)
 * y los botones de acción para eliminar o restaurar.
 *
 * @param item Datos de la versión de respaldo remota.
 * @param isActual Indica si la versión corresponde al estado actual sincronizado del dispositivo.
 * @param formattedDate Cadena de fecha relativa formateada para visualización.
 * @param onDeleteClick Callback invocado al presionar el botón de eliminar.
 * @param onRestoreClick Callback invocado al presionar el botón de restaurar.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveBackupItemCard(
    item: DriveBackupItem,
    isActual: Boolean,
    formattedDate: String,
    onDeleteClick: () -> Unit,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = if (isActual) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        },
        border = BorderStroke(
            Dimensions.Stroke.thin,
            if (isActual) SafeGreen.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ),
        modifier = modifier.fillMaxWidth()
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
                                color = (if (isActual) SafeGreen else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isActual) Icons.Filled.CloudDone else Icons.Filled.CloudQueue,
                            contentDescription = null,
                            tint = if (isActual) SafeGreen else MaterialTheme.colorScheme.primary,
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
                                fontWeight = if (isActual) FontWeight.Bold else FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            if (isActual) {
                                Surface(
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                                    color = SafeGreen.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_drive_version_actual_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = SafeGreen,
                                        maxLines = 1,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
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

            if (!isActual) {
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
