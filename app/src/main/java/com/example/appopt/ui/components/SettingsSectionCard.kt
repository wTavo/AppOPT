package com.example.appopt.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.example.appopt.ui.theme.Dimensions

/**
 * Contenedor estándar y reutilizable para secciones y tarjetas de la pantalla de ajustes (*Settings*).
 *
 * Unifica la geometría, padding interior, tipografía de cabecera y descripción según las directivas
 * de diseño del proyecto (Material Design 3 y principio DRY).
 *
 * @param title Texto del título de la sección.
 * @param description Texto descriptivo o explicativo de la funcionalidad.
 * @param icon Icono vectorial representativo de la sección.
 * @param iconTint Color de tinte aplicado al icono principal de la cabecera.
 * @param headerTrailing Componente composable opcional alineado a la derecha de la cabecera (ej. Switch o Badge).
 * @param modifier Modificador de diseño Compose opcional.
 * @param content Contenido interno de la tarjeta (formularios, interruptores, botones o listas de estado).
 */
@Composable
fun SettingsSectionCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    headerTrailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {}
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
            // Bloque de encabezado y descripción con espaciado uniforme
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Cabecera estandarizada con Icono, Título y Control Trailing sin padding interactivo artificial
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
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
                            modifier = Modifier.size(Dimensions.IconSize.medium)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (headerTrailing != null) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                            headerTrailing()
                        }
                    }
                }

                // Descripción de la sección
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Contenido dinámico / interactivo de la tarjeta
            content()
        }
    }
}
