package com.example.appopt.ui.common

import androidx.compose.runtime.Immutable

/**
 * Jerarquía sellada inmutable para el modelado determinístico de estados de pantalla (*UiState Pattern*).
 *
 * Directivas de desarrollo:
 * - PROHIBIDO utilizar múltiples variables booleanas dispersas para controlar estados de carga, error y datos.
 * - OBLIGATORIO representar los estados asíncronos y de pantalla mediante [UiState] para garantizar
 *   una arquitectura unidireccional de datos (*UDF / MVI*).
 *
 * @param T Tipo de dato que contiene el estado exitoso.
 */
@Immutable
sealed interface UiState<out T> {

    /** Estado inicial en reposo sin operaciones activas. */
    data object Idle : UiState<Nothing>

    /** Estado de carga activa con progreso indeterminado o bloqueo visual. */
    data object Loading : UiState<Nothing>

    /** Estado exitoso con datos listos para renderizar. */
    data class Success<out T>(val data: T) : UiState<T>

    /** Estado vacío sin elementos disponibles (ej. búsqueda sin resultados, lista vacía). */
    data object Empty : UiState<Nothing>

    /**
     * Estado de fallo con mensaje de error o recurso de texto internacionalizado.
     *
     * @property message Mensaje de error en texto plano (opcional).
     * @property messageRes Identificador de recurso `R.string.*` (opcional).
     */
    data class Error(
        val message: String? = null,
        val messageRes: Int? = null
    ) : UiState<Nothing>
}
