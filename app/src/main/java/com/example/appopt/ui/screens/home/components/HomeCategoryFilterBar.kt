package com.example.appopt.ui.screens.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Categorías de filtrado rápido en la parte superior de la pantalla de Inicio.
 */
enum class HomeCategoryFilter {
    /** Muestra todos los servicios 2FA propios (con favoritos fijados al principio). */
    ALL,

    /** Muestra únicamente los servicios marcados como favoritos. */
    FAVORITES,

    /** Muestra los servicios compartidos recibidos y enviados. */
    SHARED
}

/**
 * Barra superior de filtros en píldora para alternar entre «Todos», «Favoritos» y «Compartidos».
 *
 * Se ubica en la zona superior de la pantalla de Inicio para permitir un filtrado inmediato con un solo toque,
 * manteniendo la lista principal limpia y libre de cabeceras intermedias.
 *
 * @param selectedFilter Filtro actualmente activo ([HomeCategoryFilter]).
 * @param onFilterSelected Callback invocado al seleccionar un filtro.
 * @param pendingSharedCount Cantidad de invitaciones de servicios compartidos pendientes de aceptar (muestra indicador visual).
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun HomeCategoryFilterBar(
    selectedFilter: HomeCategoryFilter,
    onFilterSelected: (HomeCategoryFilter) -> Unit,
    modifier: Modifier = Modifier,
    pendingSharedCount: Int = 0
) {
    val appHaptics = rememberAppHaptics()

    Surface(
        shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Dimensions.Elevation.none,
        shadowElevation = Dimensions.Elevation.cardDefault,
        border = BorderStroke(
            Dimensions.Stroke.thin,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeFilterChipItem(
                label = stringResource(R.string.home_filter_all),
                icon = Icons.Filled.Apps,
                isSelected = selectedFilter == HomeCategoryFilter.ALL,
                onClick = {
                    if (selectedFilter != HomeCategoryFilter.ALL) {
                        appHaptics.click()
                        onFilterSelected(HomeCategoryFilter.ALL)
                    }
                },
                modifier = Modifier.weight(1f)
            )

            HomeFilterChipItem(
                label = stringResource(R.string.home_filter_favorites),
                icon = Icons.Filled.Star,
                isSelected = selectedFilter == HomeCategoryFilter.FAVORITES,
                onClick = {
                    if (selectedFilter != HomeCategoryFilter.FAVORITES) {
                        appHaptics.click()
                        onFilterSelected(HomeCategoryFilter.FAVORITES)
                    }
                },
                modifier = Modifier.weight(1f)
            )

            HomeFilterChipItem(
                label = stringResource(R.string.home_filter_shared),
                icon = Icons.Filled.Group,
                isSelected = selectedFilter == HomeCategoryFilter.SHARED,
                badgeCount = pendingSharedCount,
                onClick = {
                    if (selectedFilter != HomeCategoryFilter.SHARED) {
                        appHaptics.click()
                        onFilterSelected(HomeCategoryFilter.SHARED)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Segmento individual interactivo dentro de [HomeCategoryFilterBar].
 *
 * @param label Texto del filtro.
 * @param icon Icono representativo del filtro.
 * @param isSelected Indica si el filtro se encuentra seleccionado.
 * @param onClick Acción al pulsar el segmento.
 * @param modifier Modificador de peso y layout.
 * @param badgeCount Contador opcional de notificaciones pendientes (ej. invitaciones de servicios compartidos).
 */
@Composable
private fun HomeFilterChipItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        animationSpec = Motion.Spec.quickFadeSpec(),
        label = "filterChipContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = Motion.Spec.quickFadeSpec(),
        label = "filterChipContentColor"
    )

    Surface(
        shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
        color = containerColor,
        modifier = modifier
            .height(Dimensions.ComponentHeight.buttonCompact)
            .clip(RoundedCornerShape(Dimensions.CornerRadius.pill))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimensions.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(Dimensions.IconSize.small)
            )

            Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (badgeCount > 0) {
                Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    modifier = Modifier.size(Dimensions.IconSize.small)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = badgeCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onTertiary
                            }
                        )
                    }
                }
            }
        }
    }
}
