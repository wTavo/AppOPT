package com.example.appopt.ui.screens.settings.dialogs.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.ui.components.AccountImportSelectionList
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.appSwitchColors

/**
 * Paso 1 del diálogo de exportación: Selección de cuentas y preferencia de conservación en el dispositivo.
 * Reutiliza [AccountImportSelectionList] para estandarizar la selección granular y masiva (Directivas 5 y 14).
 *
 * @param accounts Lista completa de cuentas OTP disponibles para transferir.
 * @param selectedServiceIds Conjunto de IDs de servicios seleccionados.
 * @param onToggleSelection Callback al alternar la selección de un servicio.
 * @param onSelectAll Callback para seleccionar todos los servicios.
 * @param onDeselectAll Callback para desmarcar todos los servicios.
 * @param keepServicesOnDevice Estado del switch para mantener los servicios en el dispositivo origen.
 * @param onKeepServicesChanged Callback al alternar la preferencia de conservar servicios.
 * @param modifier Modificador de diseño Compose opcional.
 * @param enabled Indica si los controles interactivos están habilitados.
 */
@Composable
fun ExportAccountSelectionStep(
    accounts: List<TotpAccount>,
    selectedServiceIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    keepServicesOnDevice: Boolean,
    onKeepServicesChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val previews = remember(accounts) {
        accounts.map { account ->
            ParsedAccountPreview(
                id = account.id,
                issuer = account.issuer,
                accountName = account.accountName,
                algorithm = account.algorithm,
                digits = account.digits,
                period = account.period,
                type = account.type,
                counter = account.counter,
                isFavorite = account.isFavorite,
                isAlreadyInVault = true,
                secretBytes = ByteArray(0)
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        Text(
            text = stringResource(R.string.settings_export_services_dialog_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AccountImportSelectionList(
            accounts = previews,
            selectedIds = selectedServiceIds,
            onToggleAccount = { if (enabled) onToggleSelection(it) },
            onSelectAll = { if (enabled) onSelectAll() },
            onDeselectAll = { if (enabled) onDeselectAll() },
            showBadges = false
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.Spacing.xs))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                .clickable(enabled = enabled) { onKeepServicesChanged(!keepServicesOnDevice) }
                .padding(vertical = Dimensions.Spacing.xs, horizontal = Dimensions.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Dimensions.Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
            ) {
                Text(
                    text = stringResource(R.string.settings_keep_services_label),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = stringResource(R.string.settings_keep_services_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = keepServicesOnDevice,
                onCheckedChange = onKeepServicesChanged,
                enabled = enabled,
                colors = appSwitchColors()
            )
        }
    }
}
