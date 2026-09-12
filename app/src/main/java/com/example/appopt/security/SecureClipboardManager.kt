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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado inmutable que describe el elemento sensible actualmente copiado y su tiempo restante de retención.
 *
 * @property label Etiqueta identificadora del elemento copiado o `null` si el portapapeles no tiene contenido activo protegido.
 * @property remainingSeconds Segundos restantes antes de la purga automática del portapapeles (0 cuando está inactivo).
 */
data class ClipboardItemState(
    val label: String? = null,
    val remainingSeconds: Int = 0
)

/**
 * Gestor del portapapeles con protección contra filtraciones y limpieza automática determinista.
 *
 * Características de seguridad:
 * - En Android 13+ (API 33), marca los datos con `EXTRA_IS_SENSITIVE` para ocultar la previsualización del sistema.
 * - Inicia una tarea en segundo plano en el ciclo de vida de la aplicación que elimina el dato copiado tras el tiempo configurado.
 * - Expone un flujo reactivo [clipboardState] para que la interfaz sincronice temporizadores y estados de botones incluso al cerrar y reabrir diálogos.
 *
 * @param context Contexto de la aplicación para acceder al servicio del sistema [ClipboardManager].
 * @param coroutineScope Ámbito de corrutinas para ejecutar la cuenta regresiva y la purga segura.
 */
class SecureClipboardManager(
    context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private var clearJob: Job? = null
    private var lastCopiedText: String? = null

    private val _clipboardState = MutableStateFlow(ClipboardItemState())
    val clipboardState: StateFlow<ClipboardItemState> = _clipboardState.asStateFlow()

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

        // Programar limpieza automática y emisión de cuenta regresiva
        clearJob?.cancel()
        clearJob = coroutineScope.launch {
            for (sec in autoClearSeconds downTo 1) {
                _clipboardState.value = ClipboardItemState(label = label, remainingSeconds = sec)
                delay(1000L)
            }
            clearIfMatches(text)
            _clipboardState.value = ClipboardItemState()
        }
    }

    /**
     * Copia un texto confidencial al portapapeles con confirmación háptica y notificación visual.
     *
     * @param context Contexto para mostrar el Toast.
     * @param label Etiqueta descriptiva del elemento copiado.
     * @param text Contenido sensible.
     * @param feedbackMessage Mensaje opcional para el Toast de confirmación.
     * @param onHaptics Lambda opcional para disparar vibración háptica de copia.
     * @param autoClearSeconds Tiempo en segundos tras el cual se limpiará el portapapeles.
     */
    fun copySecurelyWithFeedback(
        context: Context,
        label: String,
        text: String,
        feedbackMessage: String? = null,
        onHaptics: (() -> Unit)? = null,
        autoClearSeconds: Int = SecurityConfig.CLIPBOARD_OTP_AUTO_CLEAR_SECONDS
    ) {
        onHaptics?.invoke()
        copyToClipboard(label, text, autoClearSeconds)
        if (!feedbackMessage.isNullOrBlank()) {
            android.widget.Toast.makeText(context, feedbackMessage, android.widget.Toast.LENGTH_SHORT).show()
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
            _clipboardState.value = ClipboardItemState()
        }
    }
}

