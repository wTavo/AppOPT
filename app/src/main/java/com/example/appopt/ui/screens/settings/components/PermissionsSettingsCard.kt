package com.example.appopt.ui.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.components.SettingsSectionCard
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Modelo de datos interno para estructurar los permisos recomendados en la interfaz de ajustes.
 *
 * @param id Identificador único del permiso.
 * @param icon Icono representativo del permiso.
 * @param titleRes Recurso de texto para el título del permiso.
 * @param descriptionRes Recurso de texto para la descripción explicativa del permiso.
 * @param isGranted Indica si el permiso se encuentra actualmente concedido en el sistema.
 * @param onRequest Callback ejecutado al pulsar el botón para conceder o configurar el permiso.
 */
private data class PermissionItemData(
    val id: String,
    val icon: ImageVector,
    val titleRes: Int,
    val descriptionRes: Int,
    val isGranted: Boolean,
    val onRequest: () -> Unit
)

/**
 * Tarjeta de ajustes para la visualización y gestión de los permisos recomendados de la aplicación (Directiva 29).
 *
 * Presenta los permisos no concedidos de forma destacada en color rojo con su botón de acción alineado,
 * mientras que los permisos ya otorgados se agrupan en un contenedor desplegable para optimizar
 * el espacio vertical en pantalla según las directivas de Material Design 3.
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
    val appHaptics = rememberAppHaptics()
    var isGrantedSectionExpanded by rememberSaveable { mutableStateOf(false) }

    val permissions = listOf(
        PermissionItemData(
            id = "camera",
            icon = Icons.Filled.CameraAlt,
            titleRes = R.string.settings_permission_camera_title,
            descriptionRes = R.string.settings_permission_camera_desc,
            isGranted = isCameraGranted,
            onRequest = onRequestCameraPermission
        ),
        PermissionItemData(
            id = "notifications",
            icon = Icons.Filled.Notifications,
            titleRes = R.string.settings_permission_notifications_title,
            descriptionRes = R.string.settings_permission_notifications_desc,
            isGranted = isNotificationGranted,
            onRequest = onRequestNotificationPermission
        ),
        PermissionItemData(
            id = "battery",
            icon = Icons.Filled.BatterySaver,
            titleRes = R.string.settings_permission_battery_title,
            descriptionRes = R.string.settings_permission_battery_desc,
            isGranted = isBatteryOptimizationIgnored,
            onRequest = onRequestBatteryOptimization
        )
    )

    val pendingPermissions = permissions.filter { !it.isGranted }
    val grantedPermissions = permissions.filter { it.isGranted }

    SettingsSectionCard(
        title = stringResource(R.string.settings_permissions_title),
        description = stringResource(R.string.settings_permissions_description),
        icon = Icons.Filled.Security,
        modifier = modifier
    ) {
        // Permisos pendientes (fuera del desplegable, en tono rojo)
        if (pendingPermissions.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
            ) {
                pendingPermissions.forEach { item ->
                    PendingPermissionRow(
                        icon = item.icon,
                        title = stringResource(item.titleRes),
                        description = stringResource(item.descriptionRes),
                        onRequestPermission = item.onRequest
                    )
                }
            }
        }

        // Permisos ya concedidos (dentro de contenedor desplegable)
        if (grantedPermissions.isNotEmpty()) {
            val arrowRotation by animateFloatAsState(
                targetValue = if (isGrantedSectionExpanded) 180f else 0f,
                animationSpec = Motion.Spec.privacyCollapseSpec(),
                label = "granted_permissions_arrow_rotation"
            )

            Surface(
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                appHaptics.click()
                                isGrantedSectionExpanded = !isGrantedSectionExpanded
                            }
                            .padding(Dimensions.Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = SafeGreen,
                                modifier = Modifier.size(Dimensions.IconSize.small)
                            )
                            Text(
                                text = stringResource(
                                    R.string.settings_permissions_granted_count,
                                    grantedPermissions.size
                                ),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(Dimensions.IconSize.medium)
                                .graphicsLayer { rotationZ = arrowRotation }
                        )
                    }

                    AnimatedVisibility(
                        visible = isGrantedSectionExpanded,
                        enter = fadeIn(Motion.Spec.privacyCollapseSpec()) + expandVertically(Motion.Spec.privacyCollapseSpec()),
                        exit = fadeOut(Motion.Spec.privacyCollapseSpec()) + shrinkVertically(Motion.Spec.privacyCollapseSpec())
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(
                                    start = Dimensions.Spacing.md,
                                    end = Dimensions.Spacing.md,
                                    bottom = Dimensions.Spacing.md
                                ),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                        ) {
                            grantedPermissions.forEach { item ->
                                GrantedPermissionRow(
                                    icon = item.icon,
                                    title = stringResource(item.titleRes),
                                    description = stringResource(item.descriptionRes)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
