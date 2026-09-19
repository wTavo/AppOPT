package com.example.appopt.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 * Fila para un permiso no concedido, con énfasis visual en rojo/alerta y botón de acción (Directiva 29).
 *
 * @param icon Icono representativo del permiso.
 * @param title Nombre del permiso o funcionalidad.
 * @param description Explicación del propósito del permiso.
 * @param onRequestPermission Callback para invocar la solicitud del permiso.
 * @param modifier Modificador de diseño.
 */
@Composable
fun PendingPermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.20f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(Dimensions.IconSize.medium)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    appHaptics.click()
                    onRequestPermission()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
            ) {
                Text(
                    text = stringResource(R.string.settings_permission_action_grant),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * Fila para un permiso concedido, presentado con tono verde seguro dentro del contenedor desplegable (Directiva 29).
 *
 * @param icon Icono representativo del permiso.
 * @param title Nombre del permiso o funcionalidad.
 * @param description Explicación del propósito del permiso.
 * @param modifier Modificador de diseño.
 */
@Composable
fun GrantedPermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SafeGreen,
                modifier = Modifier.size(Dimensions.IconSize.medium)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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
        }
    }
}
