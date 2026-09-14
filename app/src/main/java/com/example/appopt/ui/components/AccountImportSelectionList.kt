package com.example.appopt.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.appopt.R
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Componente modular reutilizable que muestra la lista de cuentas detectadas en una transferencia
 * o copia de seguridad para su selección individual o masiva antes de guardarse en la bóveda local.
 *
 * Cumple con la Directiva 4 (límite de altura scrolleable modalListMaxHeight), Directiva 5 (DRY)
 * y Directiva 14 (estándar de diálogos modales y paddings calibrados).
 *
 * @param accounts Lista de cuentas previsualizadas ([ParsedAccountPreview]).
 * @param selectedIds Conjunto de identificadores de cuentas actualmente seleccionadas.
 * @param onToggleAccount Callback al marcar o desmarcar una cuenta específica.
 * @param onSelectAll Callback al pulsar Seleccionar todo.
 * @param onDeselectAll Callback al pulsar Deseleccionar todo.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun AccountImportSelectionList(
    accounts: List<ParsedAccountPreview>,
    selectedIds: Set<String>,
    onToggleAccount: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()
    val allSelected = accounts.isNotEmpty() && selectedIds.size == accounts.size

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
    ) {
        // 1. Barra de control superior: Conteo y acción Seleccionar/Deseleccionar todo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.import_selection_count,
                    selectedIds.size,
                    accounts.size
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(
                onClick = {
                    appHaptics.click()
                    if (allSelected) onDeselectAll() else onSelectAll()
                },
                contentPadding = PaddingValues(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.none)
            ) {
                Text(
                    text = if (allSelected) {
                        stringResource(R.string.import_selection_deselect_all)
                    } else {
                        stringResource(R.string.import_selection_select_all)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 2. Lista scrolleable confinada a modalListMaxHeight
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = Dimensions.ComponentSize.modalListMaxHeight),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
            contentPadding = PaddingValues(bottom = Dimensions.Spacing.xs)
        ) {
            items(
                items = accounts,
                key = { it.id }
            ) { account ->
                val isChecked = account.id in selectedIds

                AccountSelectionCard(
                    account = account,
                    isChecked = isChecked,
                    onToggle = {
                        appHaptics.click()
                        onToggleAccount(account.id)
                    }
                )
            }
        }
    }
}

/**
 * Tarjeta individual para previsualizar una cuenta con su avatar, emisor, usuario, badges y checkbox.
 */
@Composable
private fun AccountSelectionCard(
    account: ParsedAccountPreview,
    isChecked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = if (isChecked) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            Dimensions.Stroke.thin,
            if (isChecked) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimensions.Spacing.md,
                    vertical = Dimensions.Spacing.sm
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            // Avatar de marca oficial
            ServiceBrandAvatar(
                issuer = account.issuer,
                size = Dimensions.IconSize.large
            )

            // Información de emisor y cuenta
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.none)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                ) {
                    Text(
                        text = account.issuer,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Badge de estado (Nuevo vs En la bóveda)
                    if (account.isAlreadyInVault) {
                        Surface(
                            shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.60f),
                            modifier = Modifier.padding(start = Dimensions.Spacing.xs)
                        ) {
                            Text(
                                text = stringResource(R.string.import_selection_badge_existing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = Dimensions.Spacing.xs, vertical = Dimensions.Spacing.none)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                            color = SafeGreen.copy(alpha = 0.15f),
                            modifier = Modifier.padding(start = Dimensions.Spacing.xs)
                        ) {
                            Text(
                                text = stringResource(R.string.import_selection_badge_new),
                                style = MaterialTheme.typography.labelSmall,
                                color = SafeGreen,
                                modifier = Modifier.padding(horizontal = Dimensions.Spacing.xs, vertical = Dimensions.Spacing.none)
                            )
                        }
                    }
                }

                if (account.accountName.isNotBlank()) {
                    Text(
                        text = account.accountName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Detalles técnicos compactos (Tipo + Dígitos)
                Text(
                    text = "${account.type.name} • ${account.digits} dígitos",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                )
            }

            // Checkbox interactivo
            Checkbox(
                checked = isChecked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
