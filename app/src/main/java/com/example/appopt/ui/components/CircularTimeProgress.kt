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
import androidx.compose.ui.unit.dp
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.WarningOrange

/**
 * Indicador visual circular del tiempo restante en la ventana de rotación TOTP con animaciones centralizadas.
 *
 * Características visuales:
 * - Muestra los segundos restantes en el centro con tipografía semántica.
 * - Cambia dinámicamente de color (Azul Primario -> Naranja de Advertencia -> Rojo de Urgencia)
 *   conforme se agota la validez del código utilizando [Motion.Spec.progressColorSpec].
 *
 * @param remainingSeconds Segundos enteros restantes en la ventana.
 * @param progress Fracción de 0.0f a 1.0f para el progreso del arco circular.
 * @param modifier Modificador de layout.
 */
@Composable
fun CircularTimeProgress(
    remainingSeconds: Int,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val indicatorColor by animateColorAsState(
        targetValue = when {
            remainingSeconds <= 5 -> UrgentRed
            remainingSeconds <= 10 -> WarningOrange
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = Motion.Spec.progressColorSpec(),
        label = "progressColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(Dimensions.ComponentHeight.progressIndicator)
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(Dimensions.ComponentHeight.progressIndicator),
            color = indicatorColor,
            strokeWidth = 3.5.dp,
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
