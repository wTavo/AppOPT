package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.data.cloud.SyncFrequency
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Diálogo modal para seleccionar la frecuencia de sincronización en segundo plano con Google Drive.
 *
 * @param currentFrequency Frecuencia de sincronización actualmente configurada.
 * @param onFrequencySelected Callback invocado al seleccionar una nueva frecuencia de sincronización.
 * @param onDismiss Callback invocado para cerrar el diálogo.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun SyncFrequencyDialog(
    currentFrequency: SyncFrequency,
    onFrequencySelected: (SyncFrequency) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()
    AppModalDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            // Cabecera fija
            Text(
                text = stringResource(R.string.settings_drive_frequency_title),
                style = MaterialTheme.typography.titleLarge
            )

            // Cuerpo central scrolleable
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
            ) {
                val frequencyOptions = listOf(
                    SyncFrequency.MINUTES_15 to R.string.settings_drive_frequency_15min,
                    SyncFrequency.HOURLY to R.string.settings_drive_frequency_hourly,
                    SyncFrequency.DAILY to R.string.settings_drive_frequency_daily,
                    SyncFrequency.WEEKLY to R.string.settings_drive_frequency_weekly,
                    SyncFrequency.MONTHLY to R.string.settings_drive_frequency_monthly,
                    SyncFrequency.OFF to R.string.settings_drive_frequency_off
                )

                frequencyOptions.forEach { (frequencyOption, labelRes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                appHaptics.click()
                                onFrequencySelected(frequencyOption)
                            }
                            .padding(vertical = Dimensions.Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentFrequency == frequencyOption,
                            onClick = {
                                appHaptics.click()
                                onFrequencySelected(frequencyOption)
                            }
                        )
                        Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // Pie fijo de acciones
            AppDialogActionButtons(
                dismissText = stringResource(R.string.action_close),
                onDismiss = onDismiss
            )
        }
    }
}
