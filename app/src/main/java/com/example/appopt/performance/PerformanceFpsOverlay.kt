package com.example.appopt.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.WarningOrange

/**
 * Superposición visual compacta (HUD) para monitorizar FPS y tiempos de fotograma en tiempo real.
 *
 * Directiva de diseño:
 * - Emplea dimensiones y colores centralizados del sistema de diseño.
 * - Permite expandir detalles de fotograma con un toque interactivo.
 *
 * @param modifier Modificador de layout.
 */
@Composable
fun PerformanceFpsOverlay(
    modifier: Modifier = Modifier
) {
    val fps by PerformanceMonitor.currentFps.collectAsStateWithLifecycle()
    val avgFrameTime by PerformanceMonitor.averageFrameTimeMs.collectAsStateWithLifecycle()
    val janks by PerformanceMonitor.jankCount.collectAsStateWithLifecycle()

    var showDetails by remember { mutableStateOf(false) }

    val statusColor = when {
        fps >= 55 -> SafeGreen
        fps >= 35 -> WarningOrange
        else -> UrgentRed
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimensions.CornerRadius.pill))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
            .clickable {
                showDetails = !showDetails
                if (!showDetails) {
                    PerformanceMonitor.resetJankCount()
                }
            }
            .padding(
                horizontal = Dimensions.Spacing.sm,
                vertical = Dimensions.Spacing.xs
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .size(Dimensions.Spacing.sm)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Text(
                text = stringResource(R.string.perf_fps_format, fps),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (showDetails) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )

                Text(
                    text = stringResource(R.string.perf_frame_time_format, avgFrameTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )

                Text(
                    text = stringResource(R.string.perf_janks_format, janks),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (janks > 0) WarningOrange else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
