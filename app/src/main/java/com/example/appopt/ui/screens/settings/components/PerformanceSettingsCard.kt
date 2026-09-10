package com.example.appopt.ui.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.performance.AppCrashTracker
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.components.SettingsSectionCard
import com.example.appopt.ui.components.SettingsStatusTile
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.appSwitchColors
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.DiagnosticPdfGenerator

/**
 * Tarjeta de ajustes para el monitor de rendimiento y el reporte de diagnóstico en PDF.
 *
 * Cumplimiento de directivas:
 * - Escala tipográfica centralizada [MaterialTheme.typography] (Directiva 1).
 * - Cero cadenas quemadas y uso estricto de Sentence case en español (Directiva 2).
 * - Dimensiones centralizadas [Dimensions] (Directiva 4).
 * - Reutilización de botones animados idempotentes [AppAnimatedButton] (Directiva 5 y 24).
 * - Principio de Cero Filtraciones (*Zero-Leakage* - Directiva 9 y 15).
 * - Cobertura KDoc 100% (Directiva 7).
 *
 * @param isFpsOverlayEnabled Estado booleano de activación del monitor de rendimiento y diagnóstico en tiempo real.
 * @param onFpsOverlayChanged Callback invocado al alternar el interruptor del monitor de rendimiento.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun PerformanceSettingsCard(
    isFpsOverlayEnabled: Boolean,
    onFpsOverlayChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appHaptics = rememberAppHaptics()
    val diagnosticLogs by AppCrashTracker.logsFlow.collectAsState()
    val hasCrash = AppCrashTracker.hasCrashReport(context)
    val totalIssuesCount = diagnosticLogs.size + if (hasCrash) 1 else 0

    SettingsSectionCard(
        title = stringResource(R.string.settings_perf_title),
        description = stringResource(R.string.settings_perf_description),
        icon = Icons.Filled.Speed,
        iconTint = if (isFpsOverlayEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    ) {
        // Contenedor del interruptor de monitor de FPS y diagnóstico
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        appHaptics.click()
                        onFpsOverlayChanged(!isFpsOverlayEnabled)
                    }
                    .padding(Dimensions.Spacing.md),
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
                        text = stringResource(R.string.settings_perf_fps_monitor_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.settings_perf_fps_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

        // Panel de acciones: visible únicamente cuando el interruptor general está encendido
        AnimatedVisibility(
            visible = isFpsOverlayEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
            ) {
                // Estado del sistema unificado
                SettingsStatusTile(
                    icon = if (totalIssuesCount == 0) Icons.Filled.Speed else Icons.Filled.DeleteSweep,
                    iconTint = if (totalIssuesCount == 0) SafeGreen else UrgentRed,
                    title = if (totalIssuesCount == 0) {
                        stringResource(R.string.settings_perf_diagnostics_status_ok)
                    } else {
                        stringResource(R.string.settings_perf_diagnostics_status_issues, totalIssuesCount)
                    },
                    titleColor = if (totalIssuesCount == 0) SafeGreen else UrgentRed
                )

                // Botones de acción: Descargar PDF y Limpiar Historial (visibles solo cuando hay incidentes registrados)
                if (totalIssuesCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        AppAnimatedButton(
                            text = stringResource(R.string.settings_perf_download_pdf),
                            leadingIcon = Icons.Filled.PictureAsPdf,
                            height = Dimensions.ComponentHeight.buttonCompact,
                            onClick = {
                                appHaptics.click()
                                DiagnosticPdfGenerator.printDiagnosticReport(context)
                                true
                            },
                            modifier = Modifier.weight(1f)
                        )

                        AppAnimatedButton(
                            text = stringResource(R.string.settings_perf_clear_report),
                            leadingIcon = Icons.Filled.DeleteSweep,
                            height = Dimensions.ComponentHeight.buttonCompact,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = {
                                appHaptics.click()
                                AppCrashTracker.clearDiagnostics(context)
                                true
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
