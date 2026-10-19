package com.example.appopt.performance

import android.app.Activity
import android.util.Log
import android.view.Choreographer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitor centralizado de rendimiento gráfico y métricas de fotogramas (FPS).
 *
 * Capacidades de diagnóstico:
 * - Mide la tasa de refresco real en tiempo real mediante [Choreographer].
 * - Calcula la duración exacta de cada fotograma en milisegundos (ms).
 * - Detecta caídas de fotogramas (*janks*) y expone métricas reactivas.
 * - Expone flujos reactivos para la superposición visual en pantalla.
 */
object PerformanceMonitor {

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
    }

    /**
     * Detiene la captura de fotogramas y resetea las métricas calculadas.
     */
    fun stop() {
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        _currentFps.value = 0
        _averageFrameTimeMs.value = 0f
        lastFrameNanos = 0L
        windowStartNanos = 0L
        framesInWindow = 0
        accumulatedFrameDurationNanos = 0L
        janksInWindow = 0
    }

    /**
     * Reinicia el contador de tirones acumulados.
     */
    fun resetJankCount() {
        _jankCount.value = 0
    }
}

/**
 * Gestor centralizado para el rastreo y diagnóstico de fotogramas lentos (*Jank*) utilizando [JankStats].
 *
 * Principio de diseño de rendimiento:
 * - Monitorea en tiempo real tiempos de cuadro que excedan el límite de refresco del hardware (>16.6ms a 60Hz o >8.3ms a 120Hz).
 * - Asocia metadatos contextuales de la interfaz de Compose para aislar causas raíz durante el scroll o transiciones.
 * - Registra advertencias diagnósticas únicamente cuando se detecta degradación efectiva de fotogramas.
 */
object JankTracker {

    private const val TAG = "AppOPT_JankStats"

    /**
     * Listener para recibir y registrar métricas de fotogramas con degradación de rendimiento.
     */
    val onFrameListener = JankStats.OnFrameListener { frameData: FrameData ->
        if (frameData.isJank) {
            val durationMs = frameData.frameDurationUiNanos / 1_000_000.0
            val states = frameData.states.joinToString { "${it.key}=${it.value}" }
            Log.w(TAG, "Jank Frame detectado: %.2f ms | Estados: %s".format(durationMs, states))
        }
    }
}

/**
 * Conecta reactivamente el rastreador de [JankStats] a la ventana actual de Compose.
 *
 * @param screenName Nombre semántico de la pantalla o sección para correlacionar los reportes de rendimiento.
 */
@Composable
fun TrackJankMetrics(screenName: String) {
    val view = LocalView.current
    DisposableEffect(view, screenName) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val jankStats = JankStats.createAndTrack(window, JankTracker.onFrameListener)
        val metricsStateHolder = PerformanceMetricsState.getHolderForHierarchy(view)
        metricsStateHolder.state?.putState("Screen", screenName)

        onDispose {
            metricsStateHolder.state?.removeState("Screen")
            jankStats.isTrackingEnabled = false
        }
    }
}

/**
 * Registra reactivamente una operación de usuario activa (ej. arrastre de tarjeta, scroll o filtrado)
 * en el estado de [JankStats] para correlacionar tirones de rendimiento con la acción exacta.
 *
 * @param key Clave identificadora del estado (ej. "Operation", "ScrollState").
 * @param value Valor actual del estado o null para retirar la marca.
 */
@Composable
fun TrackJankOperationState(key: String, value: String?) {
    val view = LocalView.current
    DisposableEffect(view, key, value) {
        val metricsStateHolder = PerformanceMetricsState.getHolderForHierarchy(view)
        if (value != null) {
            metricsStateHolder.state?.putState(key, value)
        } else {
            metricsStateHolder.state?.removeState(key)
        }

        onDispose {
            metricsStateHolder.state?.removeState(key)
        }
    }
}

