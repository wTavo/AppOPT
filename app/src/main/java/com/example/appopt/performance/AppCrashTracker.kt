package com.example.appopt.performance

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Gestor centralizado y autónomo para la captura de crasheos no controlados y registro de errores diagnósticos.
 *
 * Principio de privacidad y seguridad (*Zero-Leakage* - Directiva 9 y 15):
 * - Intercepta cierres forzosos mediante [Thread.UncaughtExceptionHandler] y los persiste de forma privada en disco.
 * - Sanitiza y enmascara de forma determinística secretos criptográficos, URIs `otpauth://`, claves Base32 y tokens OAuth2.
 * - Mantiene un buffer circular en memoria para los últimos 20 errores no fatales operacionales.
 * - No transmite datos a servidores externos, resguardando la privacidad del usuario.
 */
object AppCrashTracker {

    private const val TAG = "AppOPT_Diagnostics"
    private const val CRASH_FILE_NAME = "last_crash_report.txt"
    private const val MAX_NON_FATAL_LOGS = 20

    private val secretPattern = Regex("(?i)(secret=|otpauth://|key=|pass=|token=)([A-Za-z0-9+/=_.%~-]{8,})")
    private val base32RawPattern = Regex("\\b[2-7A-Za-z]{16,64}\\b")

    private val nonFatalBuffer = ConcurrentLinkedDeque<DiagnosticLogEntry>()
    private val _logsFlow = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    /** Flujo reactivo de entradas de diagnóstico recientes para la interfaz. */
    val logsFlow: StateFlow<List<DiagnosticLogEntry>> = _logsFlow.asStateFlow()

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var isInstalled = false

    /**
     * Bandera atómica que determina si el registro de diagnóstico y rastreo de cierres está activo.
     */
    @Volatile
    var isEnabled: Boolean = false

    /**
     * Inicializa el interceptor global de excepciones no controladas.
     *
     * @param context Contexto de la aplicación.
     * @param enabled Estado inicial de activación según las preferencias del usuario.
     */
    fun install(context: Context, enabled: Boolean = false) {
        isEnabled = enabled
        if (isInstalled) return
        isInstalled = true

        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                if (isEnabled) {
                    handleUncaughtException(context, thread, throwable)
                }
            } catch (_: Exception) {
                // Prevenir fallos en cascada en el manejador de crasheos
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Registra un error técnico operacional no fatal en el buffer de diagnóstico.
     *
     * @param tag Etiqueta o componente de origen (ej. "GoogleDriveManager", "RoomDB").
     * @param message Mensaje explicativo del problema.
     * @param throwable Excepción capturada opcional.
     * @param severity Nivel de severidad ([DiagnosticSeverity.ERROR] o [DiagnosticSeverity.WARNING]).
     */
    fun logNonFatal(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        severity: DiagnosticSeverity = DiagnosticSeverity.ERROR
    ) {
        if (!isEnabled) return

        val sanitizedMsg = sanitize(message)
        val sanitizedTrace = throwable?.let { sanitize(getStackTraceString(it)) }

        val entry = DiagnosticLogEntry(
            timestamp = System.currentTimeMillis(),
            severity = severity,
            tag = tag,
            message = sanitizedMsg,
            stackTrace = sanitizedTrace
        )

        nonFatalBuffer.addFirst(entry)
        while (nonFatalBuffer.size > MAX_NON_FATAL_LOGS) {
            nonFatalBuffer.pollLast()
        }

        _logsFlow.update { nonFatalBuffer.toList() }

        try {
            if (severity == DiagnosticSeverity.WARNING) {
                Log.w(TAG, "[$tag] $sanitizedMsg", throwable)
            } else {
                Log.e(TAG, "[$tag] $sanitizedMsg", throwable)
            }
        } catch (_: Throwable) {
            // Manejo defensivo para entornos de ejecución sin framework de Android (Unit tests JVM)
        }
    }

    /**
     * Procesa y persiste el reporte de un crasheo inesperado en el almacenamiento interno privado.
     */
    private fun handleUncaughtException(context: Context, thread: Thread, throwable: Throwable) {
        val crashFile = File(context.filesDir, CRASH_FILE_NAME)
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        throwable.printStackTrace(printWriter)
        val rawStackTrace = stringWriter.toString()

        val sanitizedTrace = sanitize(rawStackTrace)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val dateString = dateFormat.format(Date())

        @Suppress("DEPRECATION")
        val threadId = thread.id
        val report = buildString {
            appendLine("=== REPORTE DE CRASHEO DE APPOPT ===")
            appendLine("Fecha: $dateString")
            appendLine("Hilo: ${thread.name} (id=$threadId)")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Versión Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Excepción: ${throwable.javaClass.name}")
            appendLine("Mensaje: ${sanitize(throwable.message.orEmpty())}")
            appendLine()
            appendLine("--- STACK TRACE ---")
            appendLine(sanitizedTrace)
            appendLine("=====================================")
        }

        crashFile.writeText(report)
    }

    /**
     * Comprueba si existe un reporte de crasheo guardado de una sesión previa.
     *
     * @param context Contexto de la aplicación.
     * @return `true` si existe un archivo de crasheo, `false` en caso contrario.
     */
    fun hasCrashReport(context: Context): Boolean {
        val crashFile = File(context.filesDir, CRASH_FILE_NAME)
        return crashFile.exists() && crashFile.length() > 0L
    }

    /**
     * Obtiene el contenido del último reporte de crasheo guardado.
     *
     * @param context Contexto de la aplicación.
     * @return Texto del reporte de crasheo o null si no existe.
     */
    fun getLastCrashReport(context: Context): String? {
        val crashFile = File(context.filesDir, CRASH_FILE_NAME)
        return if (crashFile.exists() && crashFile.length() > 0L) {
            crashFile.readText()
        } else null
    }

    /**
     * Elimina el archivo de reporte de crasheo y vacía el buffer de errores.
     *
     * @param context Contexto de la aplicación.
     */
    fun clearDiagnostics(context: Context) {
        val crashFile = File(context.filesDir, CRASH_FILE_NAME)
        if (crashFile.exists()) {
            crashFile.delete()
        }
        nonFatalBuffer.clear()
        _logsFlow.update { emptyList() }
    }

    /**
     * Sanitiza una cadena de texto eliminando cualquier rastro de claves Base32, tokens o contraseñas.
     *
     * @param input Texto sin procesar.
     * @return Texto con los secretos enmascarados como `[SECRETO_ENMASCARADO]`.
     */
    fun sanitize(input: String): String {
        if (input.isBlank()) return input
        var result = secretPattern.replace(input) { matchResult ->
            "${matchResult.groupValues[1]}[SECRETO_ENMASCARADO]"
        }
        result = base32RawPattern.replace(result) { matchResult ->
            val value = matchResult.value
            if (value.matches(Regex("^[A-Z2-7]+$")) && value.length >= 16) {
                "[SECRETO_ENMASCARADO]"
            } else {
                value
            }
        }
        return result
    }

    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }
}
