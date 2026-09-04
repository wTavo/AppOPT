package com.example.appopt.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.appSwitchColors
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Tarjeta de ajustes para el monitor de rendimiento y superposición diagnóstica de FPS.
 *
 * @param isFpsOverlayEnabled Estado booleano de activación del contador de FPS.
 * @param onFpsOverlayChanged Callback invocado al alternar el interruptor del monitor de FPS.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun PerformanceSettingsCard(
    isFpsOverlayEnabled: Boolean,
    onFpsOverlayChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large)
    ) {
        Column(modifier = Modifier.padding(Dimensions.Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimensions.IconSize.medium)
                    )
                    Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_perf_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                        Text(
                            text = stringResource(R.string.settings_perf_fps_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isFpsOverlayEnabled,
                    onCheckedChange = { enabled ->
                        appHaptics.click()
                        onFpsOverlayChanged(enabled)
                    },
                    colors = appSwitchColors()
                )
            }
        }
    }
}
