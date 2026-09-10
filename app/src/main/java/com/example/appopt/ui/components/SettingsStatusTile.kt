package com.example.appopt.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen

/**
 * Contenedor de estado estilizado y reutilizable para filas informativas y de diagnóstico dentro de tarjetas de ajustes.
 *
 * Cumple con el estándar de geometría de superficies secundarias (`CornerRadius.medium`, `Spacing.md`, `bodyMedium`).
 *
 * @param icon Icono indicativo del estado.
 * @param title Texto principal del estado o diagnóstico.
 * @param iconTint Tinte cromático para el icono (por defecto [SafeGreen]).
 * @param titleColor Color del texto del título (por defecto `onSurface`).
 * @param subtitle Texto explicativo secundario opcional.
 * @param iconSize Tamaño del icono (por defecto [Dimensions.IconSize.small]).
 * @param backgroundColor Color de fondo de la superficie (por defecto `surfaceVariant`).
 * @param onClick Acción opcional al presionar sobre la fila.
 * @param trailingContent Componente composable opcional al final de la fila (ej. Chevron o Badge).
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun SettingsStatusTile(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    iconTint: Color = SafeGreen,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitle: String? = null,
    iconSize: Dp = Dimensions.IconSize.small,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(iconSize)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = titleColor
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}
