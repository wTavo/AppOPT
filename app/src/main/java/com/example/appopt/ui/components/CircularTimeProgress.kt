package com.example.appopt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.WarningOrange
import com.example.appopt.util.TotpClock

/**
 * Indicador visual circular del tiempo restante en la ventana de rotación TOTP con renderizado directo en Canvas.
 *
 * Características de ultra alto rendimiento:
 * - Dibuja directamente los arcos mediante [Canvas] y [Stroke] evitando envoltorios pesados de Material.
 * - Sincronizado centralmente mediante [TotpClock] sin instanciar corrutinas independientes por cada tarjeta.
 * - Muestra los segundos restantes en el centro con tipografía semántica.
 * - Incluye descripción semántica para lectores de pantalla TalkBack.
 *
 * @param modifier Modificador de layout.
 * @param period Período en segundos de la ventana de rotación TOTP (por defecto 30).
 */
@Composable
fun CircularTimeProgress(
    modifier: Modifier = Modifier,
    period: Int = 30
) {
    val currentSecond by TotpClock.currentSecondEpoch.collectAsStateWithLifecycle()
    val remainingSeconds = (period - (currentSecond % period)).toInt().coerceIn(1, period)
    val sweepAngle = (remainingSeconds.toFloat() / period.toFloat()) * 360f

    val color = when {
        remainingSeconds <= 5 -> UrgentRed
        remainingSeconds <= 10 -> WarningOrange
        else -> MaterialTheme.colorScheme.primary
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val strokeWidthPx = with(LocalDensity.current) { Dimensions.Stroke.progressArc.toPx() }
    val secondsLabel = stringResource(R.string.card_seconds_abbrev)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(Dimensions.ComponentHeight.progressIndicator)
            .semantics {
                contentDescription = "$remainingSeconds $secondsLabel"
            }
    ) {
        Canvas(modifier = Modifier.size(Dimensions.ComponentHeight.progressIndicator)) {
            // Pista de fondo
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
            // Arco de progreso
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }

        Text(
            text = "$remainingSeconds",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}
