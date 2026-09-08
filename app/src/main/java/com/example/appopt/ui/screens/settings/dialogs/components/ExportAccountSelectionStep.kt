package com.example.appopt.ui.screens.settings.dialogs.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.ui.components.ServiceBrandAvatar
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.appSwitchColors

/**
 * Paso 1 del diálogo de exportación: Selección de cuentas y preferencia de conservación en el dispositivo.
 *
 * @param accounts Lista completa de cuentas OTP disponibles para transferir.
 * @param selectedServiceIds Conjunto o lista de IDs de servicios seleccionados.
 * @param onToggleSelection Callback al alternar la selección de un servicio.
 * @param keepServicesOnDevice Estado del switch para mantener los servicios en el dispositivo origen.
 * @param onKeepServicesChanged Callback al alternar la preferencia de conservar servicios.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun ExportAccountSelectionStep(
    accounts: List<TotpAccount>,
    selectedServiceIds: List<String>,
    onToggleSelection: (String) -> Unit,
    keepServicesOnDevice: Boolean,
    onKeepServicesChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        Text(
            text = stringResource(R.string.settings_export_services_dialog_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = Dimensions.ComponentSize.modalListMaxHeight)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
        ) {
            accounts.forEach { account ->
                val isSelected = account.id in selectedServiceIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSelection(account.id) }
                        .padding(vertical = Dimensions.Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ServiceBrandAvatar(
                        issuer = account.issuer,
                        size = Dimensions.ComponentSize.actionIconButton
                    )
                    Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                    ) {
                        Text(
                            text = account.issuer,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (account.accountName.isNotBlank()) {
                            Text(
                                text = account.accountName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection(account.id) }
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.Spacing.xs))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onKeepServicesChanged(!keepServicesOnDevice) }
                .padding(vertical = Dimensions.Spacing.xs),
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
                colors = appSwitchColors()
            )
        }
    }
}
