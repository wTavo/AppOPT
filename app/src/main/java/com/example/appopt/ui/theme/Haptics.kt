package com.example.appopt.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Sistema centralizado de respuesta háptica (*Haptic Feedback Engine*).
 *
 * Directiva de desarrollo:
 * - PROHIBIDO disparar vibraciones con tiempos o intensidades arbitrarias en los Composables.
 * - OBLIGATORIO utilizar los métodos semánticos de [AppHaptics] para garantizar coherencia táctil
 *   y compatibilidad con las directrices de diseño de Android (API 26 a API 34+).
 *
 * @param hapticFeedback Instancia de [HapticFeedback] provista por Jetpack Compose.
 * @param vibrator Instancia del servicio de vibración del sistema para efectos avanzados.
 */
class AppHaptics(
    private val hapticFeedback: HapticFeedback,
    private val vibrator: Vibrator?
) {

    /**
     * Tap ultra ligero para pulsación de botones interactivos o cambios de pestañas.
     */
    fun click() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /**
     * Toque de confirmación al copiar un código OTP al portapapeles.
     */
    fun copy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /**
     * Vibración de doble pulso suave para confirmaciones positivas (biometría exitosa, sincronización completada, guardado).
     */
    fun success() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator?.hasVibrator() == true) {
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1.0f, 80)
                    .compose()
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasVibrator() == true) {
            val timings = longArrayOf(0, 40, 60, 40)
            val amplitudes = intArrayOf(0, 150, 0, 200)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /**
     * Alerta táctil en acciones destructivas (ej. eliminar servicio, eliminar copia de seguridad) o errores de validación.
     */
    fun error() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasVibrator() == true) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /**
     * Micro-tick para la retroalimentación continua durante el reordenamiento de tarjetas (*Drag & Drop*).
     */
    fun dragTick() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}

/**
 * Helper Composable que recuerda y provee la instancia centralizada de [AppHaptics].
 *
 * @return Instancia activa de [AppHaptics] vinculada al contexto actual.
 */
@Composable
fun rememberAppHaptics(): AppHaptics {
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current

    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    return remember(hapticFeedback, vibrator) {
        AppHaptics(hapticFeedback, vibrator)
    }
}
