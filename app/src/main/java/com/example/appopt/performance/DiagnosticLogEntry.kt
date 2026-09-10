package com.example.appopt.performance

import androidx.compose.runtime.Immutable

/**
 * Nivel de severidad de un evento diagnóstico registrado.
 */
enum class DiagnosticSeverity {
    /** Advertencia sobre una condición no óptima pero no crítica. */
    WARNING,
    /** Error operacional capturado (ej. fallo de red o formato inesperado). */
    ERROR,
    /** Cierre forzoso o excepción no controlada que detuvo el proceso. */
    CRASH
}

/**
 * Entrada inmutable que representa un evento de diagnóstico o error operacional sanitizado.
 *
 * @param timestamp Marca de tiempo Unix en milisegundos cuando ocurrió el evento.
 * @param severity Nivel de severidad del evento ([DiagnosticSeverity]).
 * @param tag Etiqueta o componente de origen (ej. "GoogleDriveManager", "RoomDB").
 * @param message Mensaje descriptivo sanitizado (sin claves ni secretos expuestos).
 * @param stackTrace Traza de la excepción opcional sanitizada.
 */
@Immutable
data class DiagnosticLogEntry(
    val timestamp: Long,
    val severity: DiagnosticSeverity,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
)
