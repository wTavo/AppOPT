package com.example.appopt.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.ui.components.SettingsSectionCard
import com.example.appopt.ui.theme.Dimensions

/**
 * Tarjeta de ajustes para la transferencia offline y exportación/importación de servicios mediante código QR.
 *
 * @param accounts Lista de cuentas OTP actualmente registradas.
 * @param onExportClick Callback invocado al presionar el botón de exportación.
 * @param onImportClick Callback invocado al presionar el botón de importación (escáner QR).
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun TransferSettingsCard(
    accounts: List<TotpAccount>,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSectionCard(
        title = stringResource(R.string.settings_transfer_title),
        description = stringResource(R.string.settings_transfer_description),
        icon = Icons.Filled.QrCodeScanner,
        modifier = modifier
    ) {
        if (accounts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
            ) {
                Button(
                    onClick = onExportClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Text(
                        text = stringResource(R.string.settings_export_services_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onImportClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Text(
                        text = stringResource(R.string.settings_import_services_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        } else {
            Button(
                onClick = onImportClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
            ) {
                Text(
                    text = stringResource(R.string.settings_import_services_button),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
