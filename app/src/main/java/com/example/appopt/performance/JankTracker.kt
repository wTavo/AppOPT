package com.example.appopt.performance

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState

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
