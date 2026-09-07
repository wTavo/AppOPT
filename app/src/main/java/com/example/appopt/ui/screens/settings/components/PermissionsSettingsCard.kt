package com.example.appopt.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Tarjeta de ajustes para la visualización y gestión de los permisos recomendados de la aplicación.
 *
 * Presenta el estado en tiempo real (permitido o pendiente) de los permisos de cámara,
 * notificaciones y ahorro de batería, explicando la función de cada uno y permitiendo
 * otorgarlos o configurarlos de forma interactiva.
 *
 * @param isCameraGranted Indica si el permiso de cámara para escaneo QR está concedido.
 * @param isNotificationGranted Indica si las notificaciones del sistema están habilitadas.
 * @param isBatteryOptimizationIgnored Indica si la app está excluida del ahorro de batería.
 * @param onRequestCameraPermission Callback invocado al solicitar el permiso de cámara.
 * @param onRequestNotificationPermission Callback invocado al solicitar el permiso de notificaciones.
 * @param onRequestBatteryOptimization Callback invocado al solicitar la exclusión del ahorro de batería.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun PermissionsSettingsCard(
    isCameraGranted: Boolean,
    isNotificationGranted: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    onRequestCameraPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
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
            // Cabecera de la sección
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimensions.IconSize.medium)
                )
                Text(
                    text = stringResource(R.string.settings_permissions_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                text = stringResource(R.string.settings_permissions_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Lista de permisos individuales
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
            ) {
                PermissionItemRow(
                    icon = Icons.Filled.CameraAlt,
                    title = stringResource(R.string.settings_permission_camera_title),
                    description = stringResource(R.string.settings_permission_camera_desc),
                    isGranted = isCameraGranted,
                    onRequestPermission = onRequestCameraPermission
                )

                PermissionItemRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.settings_permission_notifications_title),
                    description = stringResource(R.string.settings_permission_notifications_desc),
                    isGranted = isNotificationGranted,
                    onRequestPermission = onRequestNotificationPermission
                )

                PermissionItemRow(
                    icon = Icons.Filled.BatterySaver,
                    title = stringResource(R.string.settings_permission_battery_title),
                    description = stringResource(R.string.settings_permission_battery_desc),
                    isGranted = isBatteryOptimizationIgnored,
                    onRequestPermission = onRequestBatteryOptimization
                )
            }
        }
    }
}

/**
 * Fila reutilizable para la presentación del estado y acción de un permiso específico.
 *
 * @param icon Icono representativo del permiso.
 * @param title Nombre del permiso o funcionalidad.
 * @param description Explicación del propósito del permiso en la aplicación.
 * @param isGranted Indica si el permiso se encuentra activo o autorizado.
 * @param onRequestPermission Callback para invocar la solicitud o ajuste del permiso.
 */
@Composable
private fun PermissionItemRow(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    val appHaptics = rememberAppHaptics()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.Spacing.md),
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
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) SafeGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimensions.IconSize.small)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                if (isGranted) {
                    Surface(
                        shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                        color = SafeGreen.copy(alpha = 0.12f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs),
                            modifier = Modifier.padding(
                                horizontal = Dimensions.Spacing.sm,
                                vertical = Dimensions.Spacing.xs
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = SafeGreen,
                                modifier = Modifier.size(Dimensions.IconSize.small)
                            )
                            Text(
                                text = stringResource(R.string.settings_permission_granted),
                                style = MaterialTheme.typography.labelSmall,
                                color = SafeGreen
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            appHaptics.click()
                            onRequestPermission()
                        },
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_permission_action_grant),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
