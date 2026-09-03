package com.example.appopt.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.WarningOrange

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Indicador visual circular del tiempo restante en la ventana de rotación TOTP con animaciones centralizadas.
 *
 * Características de alto rendimiento:
 * - Aísla las actualizaciones del temporizador a 4 Hz dentro de su propio canvas sin provocar recomposiciones en la lista ni tarjetas.
 * - Muestra los segundos restantes en el centro con tipografía semántica.
 * - Incluye descripción semántica para lectores de pantalla TalkBack.
 * - Cambia dinámicamente de color (Azul Primario -> Naranja de Advertencia -> Rojo de Urgencia)
 *   conforme se agota la validez del código utilizando [Motion.Spec.progressColorSpec].
 *
 * @param period Período en segundos de la ventana de rotación TOTP (por defecto 30).
 * @param modifier Modificador de layout.
 */
@Composable
fun CircularTimeProgress(
    period: Int = 30,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            currentTime = now
            val millisUntilNextSecond = 1000L - (now % 1000L)
            delay(millisUntilNextSecond.coerceAtLeast(50L))
        }
    }

    val remainingSeconds = (period - ((currentTime / 1000L) % period)).toInt().coerceIn(1, period)
    val progress = remainingSeconds.toFloat() / period.toFloat()

    val indicatorColor by animateColorAsState(
        targetValue = when {
            remainingSeconds <= 5 -> UrgentRed
            remainingSeconds <= 10 -> WarningOrange
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = Motion.Spec.progressColorSpec(),
        label = "progressColor"
    )

    val secondsLabel = stringResource(R.string.card_seconds_abbrev)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(Dimensions.ComponentHeight.progressIndicator)
            .semantics {
                contentDescription = "$remainingSeconds $secondsLabel"
            }
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(Dimensions.ComponentHeight.progressIndicator),
            color = indicatorColor,
            strokeWidth = Dimensions.Stroke.progressArc,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )

        Text(
            text = "$remainingSeconds",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = indicatorColor
        )
    }
}
