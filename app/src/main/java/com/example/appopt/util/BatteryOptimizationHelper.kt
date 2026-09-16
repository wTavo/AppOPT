package com.example.appopt.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Utilidad centralizada para consultar y gestionar el estado de optimización de batería del sistema Android.
 *
 * Proporciona métodos para verificar si la aplicación está exenta de restricciones de ahorro
 * de energía en segundo plano (Doze Mode / App Standby) y para solicitar al sistema operativo
 * el diálogo nativo de exclusión directa sin obligar al usuario a navegar manualmente en los ajustes generales.
 */
object BatteryOptimizationHelper {

    /**
     * Comprueba si la aplicación está actualmente exenta de optimizaciones de batería del sistema.
     *
     * @param context Contexto de la aplicación para acceder al servicio del sistema [PowerManager].
     * @return `true` si la aplicación puede ejecutarse sin restricciones de batería, `false` si tiene restricciones activas.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    /**
     * Construye el [Intent] del sistema para mostrar el diálogo directo de solicitud de exclusión de batería.
     *
     * @param context Contexto de la aplicación.
     * @return [Intent] configurado con la acción [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS] y el URI del paquete.
     */
    @SuppressLint("BatteryLife")
    fun createIgnoreBatteryOptimizationIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
        }
    }

    /**
     * Inicia una solicitud al sistema operativo mostrando el diálogo emergente directo para permitir
     * la ejecución en segundo plano sin restricciones.
     *
     * @param context Contexto utilizado para lanzar el [Intent] del sistema.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = createIgnoreBatteryOptimizationIntent(context).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                // Fallback silencioso sin exponer trazas técnicas (Directiva 15)
            }
        }
    }
}
