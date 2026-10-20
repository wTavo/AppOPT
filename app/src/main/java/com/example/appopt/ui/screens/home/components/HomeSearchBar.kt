package com.example.appopt.ui.screens.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Barra de búsqueda interactiva en contenedor de píldora para la cabecera principal (Directivas 4 y 29).
 *
 * Mantiene la misma altura calibrada que los controles de la cabecera superior y ubica el botón de cierre (X)
 * ergonómicamente a la izquierda para cerrar la barra sin desplazamiento lateral del pulgar.
 *
 * @param searchQuery Texto de búsqueda actual.
 * @param onSearchQueryChange Callback al escribir en la barra.
 * @param onCloseSearch Callback para cerrar la búsqueda y limpiar el texto.
 * @param modifier Modificador de diseño.
 */
@Composable
fun HomeSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()
    val barHeight = Dimensions.ComponentSize.actionIconButton + Dimensions.Spacing.sm

    Surface(
        shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Dimensions.Elevation.none,
        shadowElevation = Dimensions.Elevation.cardDefault,
        border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimensions.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Botón de Cerrar (X) a la Izquierda (Ergonomía pulgar)
            IconButton(
                onClick = {
                    appHaptics.click()
                    onCloseSearch()
                },
                modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_close_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensions.IconSize.medium)
                )
            }

            Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))

            // 2. Campo de búsqueda centralizado
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.home_search_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.weight(1f)
            )

            // 3. Botón para limpiar texto si hay búsqueda activa
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = {
                        appHaptics.click()
                        onSearchQueryChange("")
                    },
                    modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(Dimensions.IconSize.small)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
            }
        }
    }
}
