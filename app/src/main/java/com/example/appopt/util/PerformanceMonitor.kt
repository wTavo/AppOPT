package com.example.appopt.util

import android.util.Log
import android.view.Choreographer
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.WarningOrange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitor centralizado de rendimiento gráfico y métricas de fotogramas (FPS).
 *
 * Capacidades de diagnóstico:
 * - Mide la tasa de refresco real en tiempo real mediante [Choreographer].
 * - Calcula la duración exacta de cada fotograma en milisegundos (ms).
 * - Detecta caídas de fotogramas (*janks*) y emite registros detallados en Logcat (tag: `AppOPT-Perf`).
 * - Expone flujos reactivos para la superposición visual en pantalla.
 */
object PerformanceMonitor {

    private const val TAG = "AppOPT-Perf"

    private val _currentFps = MutableStateFlow(0)
    /** Tasa actual de fotogramas por segundo. */
    val currentFps: StateFlow<Int> = _currentFps.asStateFlow()

    private val _averageFrameTimeMs = MutableStateFlow(0f)
    /** Tiempo medio de procesamiento por fotograma en milisegundos. */
    val averageFrameTimeMs: StateFlow<Float> = _averageFrameTimeMs.asStateFlow()

    private val _jankCount = MutableStateFlow(0)
    /** Total de fotogramas con retraso o pérdida de sincronización (*janks*). */
    val jankCount: StateFlow<Int> = _jankCount.asStateFlow()

    private var isRunning = false
    private var lastFrameNanos = 0L
    private var windowStartNanos = 0L
    private var framesInWindow = 0
    private var accumulatedFrameDurationNanos = 0L
    private var janksInWindow = 0

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            if (lastFrameNanos > 0L) {
                val frameDurationNanos = frameTimeNanos - lastFrameNanos
                val frameDurationMs = frameDurationNanos / 1_000_000f

                // Pausas largas (> 64ms) corresponden a inactividad/idle entre toques, no a caídas de fotograma activas
                if (frameDurationMs <= 64f) {
                    accumulatedFrameDurationNanos += frameDurationNanos
                    framesInWindow++

                    // Fotograma con retraso (> 17.5ms) durante renderizado activo
                    if (frameDurationMs > 17.5f) {
                        janksInWindow++
                    }
                }

                // Ventana de muestreo de 1 segundo (1_000_000_000 ns)
                if (windowStartNanos == 0L) {
                    windowStartNanos = frameTimeNanos
                } else if (frameTimeNanos - windowStartNanos >= 1_000_000_000L) {
                    val avgDurationMs = if (framesInWindow > 0) {
                        (accumulatedFrameDurationNanos / framesInWindow) / 1_000_000f
                    } else 0f

                    val calculatedFps = if (avgDurationMs > 0f) {
                        (1000f / avgDurationMs).toInt().coerceIn(0, 120)
                    } else 0

                    _currentFps.value = calculatedFps
                    _averageFrameTimeMs.value = avgDurationMs
                    _jankCount.value += janksInWindow

                    // Reiniciar ventana
                    windowStartNanos = frameTimeNanos
                    framesInWindow = 0
                    accumulatedFrameDurationNanos = 0L
                    janksInWindow = 0
                }
            }

            lastFrameNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /**
     * Inicia la captura continua de fotogramas mediante [Choreographer].
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        lastFrameNanos = 0L
        windowStartNanos = 0L
        framesInWindow = 0
        accumulatedFrameDurationNanos = 0L
        janksInWindow = 0
        Choreographer.getInstance().postFrameCallback(frameCallback)
        Log.i(TAG, "🚀 Monitor de rendimiento iniciado")
    }

    /**
     * Detiene la captura de fotogramas.
     */
    fun stop() {
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        Log.i(TAG, "🛑 Monitor de rendimiento detenido")
    }

    /**
     * Reinicia el contador de tirones acumulados.
     */
    fun resetJankCount() {
        _jankCount.value = 0
    }
}

/**
 * Superposición visual compacta (HUD) para monitorizar FPS y tiempos de fotograma en tiempo real.
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
