package com.example.appopt.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Utilidad centralizada para consultar y gestionar el estado de optimización de batería del sistema Android.
 *
 * Proporciona métodos para verificar si la aplicación está exenta de restricciones de ahorro
 * de energía en segundo plano (Doze Mode / App Standby) y para guiar al usuario al diálogo o
 * pantalla del sistema correspondiente para otorgar el permiso.
 */
object BatteryOptimizationHelper {

    /**
     * Comprueba si la aplicación está actualmente exenta de optimizaciones de batería del sistema.
     *
     * @param context Contexto de la aplicación para acceder al servicio del sistema [PowerManager].
     * @return `true` si la aplicación puede ejecutarse sin restricciones de batería o si la versión
     *         de Android es inferior a Marshmallow (API 23), `false` si tiene restricciones activas.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }
    }

    /**
     * Inicia una solicitud al sistema operativo para excluir a la aplicación del ahorro de batería.
     *
     * Intenta abrir de manera defensiva:
     * 1. El diálogo directo del sistema ([Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS]).
     * 2. Si falla por personalizaciones del fabricante (OEM), la lista general de optimizaciones ([Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS]).
     * 3. Como último recurso, la pantalla de información de la aplicación ([Settings.ACTION_APPLICATION_DETAILS_SETTINGS]).
     *
     * @param context Contexto utilizado para lanzar los Intents de configuración del sistema.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // Fallback silencioso sin exponer trazas técnicas al usuario
                }
            }
        }
    }
}
