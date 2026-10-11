package com.example.appopt.ui.screens.settings.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.components.SettingsSectionCard
import com.example.appopt.ui.navigation.NavigationOriginTracker
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Tarjeta de ajustes para administrar los servicios compartidos Zero-Secret (Directivas 4, 5, 7 y 31).
 *
 * Permite consultar los servicios que otros usuarios han compartido con el dispositivo actual
 * (en modo en línea o por lote temporal) y revocar en tiempo real los permisos otorgados a terceros.
 *
 * @param onManageSharedServicesClick Callback invocado al presionar el botón de gestión.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun SharedServicesSettingsCard(
    onManageSharedServicesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()
    var buttonCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    SettingsSectionCard(
        title = stringResource(R.string.settings_shared_title),
        description = stringResource(R.string.settings_shared_description),
        icon = Icons.Filled.Share,
        modifier = modifier
    ) {
        Button(
            onClick = {
                appHaptics.click()
                NavigationOriginTracker.updateModalOrigin(buttonCoordinates)
                onManageSharedServicesClick()
            },
            modifier = Modifier
                .fillMaxWidth()
                .onPlaced { buttonCoordinates = it },
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
        ) {
            Text(
                text = stringResource(R.string.settings_shared_open_button),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
