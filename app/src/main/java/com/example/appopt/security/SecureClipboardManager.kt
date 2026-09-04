package com.example.appopt.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Gestor del portapapeles con protección contra filtraciones y limpieza automática.
 *
 * Características de seguridad:
 * - En Android 13+ (API 33), marca los datos con `EXTRA_IS_SENSITIVE` para ocultar la previsualización del sistema.
 * - Inicia una tarea en segundo plano que elimina el código copiado tras el tiempo configurado (30 segundos).
 */
class SecureClipboardManager(
    context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private var clearJob: Job? = null
    private var lastCopiedText: String? = null

    /**
     * Copia un texto confidencial al portapapeles y programa su borrado automático.
     *
     * @param label Etiqueta descriptiva del elemento copiado.
     * @param text Contenido sensible (código OTP o clave de recuperación).
     * @param autoClearSeconds Tiempo en segundos tras el cual se limpiará el portapapeles.
     */
    fun copyToClipboard(
        label: String,
        text: String,
        autoClearSeconds: Int = SecurityConfig.CLIPBOARD_OTP_AUTO_CLEAR_SECONDS
    ) {
        if (clipboard == null) return

        val clip = ClipData.newPlainText(label, text)

        // Android 13+ (API 33): Marcar como sensible para ocultar previsualizaciones del sistema
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            clip.description.extras = extras
        }

        clipboard.setPrimaryClip(clip)
        lastCopiedText = text

        // Programar limpieza automática
        clearJob?.cancel()
        clearJob = coroutineScope.launch {
            delay((autoClearSeconds * 1000L).milliseconds)
            clearIfMatches(text)
        }
    }

    /**
     * Limpia el portapapeles si su contenido actual coincide con el texto que la aplicación copió.
     *
     * @param expectedText Texto esperado para no sobrescribir copias posteriores hechas por el usuario en otras apps.
     */
    fun clearIfMatches(expectedText: String) {
        val current = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
        if (current == expectedText || expectedText == lastCopiedText) {
            clipboard?.clearPrimaryClip()
            lastCopiedText = null
        }
    }
}
