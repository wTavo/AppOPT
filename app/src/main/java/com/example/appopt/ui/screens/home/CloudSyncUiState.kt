package com.example.appopt.ui.screens.home

/**
 * Representa los estados reactivos visuales de la sincronización en la nube para el encabezado principal de la aplicación.
 *
 * Estados de ciclo de vida de sincronización (Directiva 13, 24 y SRP):
 * - [IDLE]: Reposo o estado neutro (título estándar "Authenticator").
 * - [PENDING]: Intervalo de consolidación previo a la subida en la nube (amarillo/ámbar).
 * - [SYNCING]: Sincronización en curso activa con animación de rotación.
 * - [SUCCESS]: Sincronización completada con éxito con brillo animado y verificación verde.
 * - [ERROR]: Fallo en la comunicación con la nube con contorno de error.
 */
enum class CloudSyncUiState {
    /** Estado neutro de reposo. Muestra el título estándar "Authenticator". */
    IDLE,

    /** Intervalo de consolidación previo a la subida en la nube. Muestra el título en amarillo/ámbar. */
    PENDING,

    /** Sincronización en curso activa. Muestra animación de carga y rotación. */
    SYNCING,

    /** Sincronización completada con éxito. Muestra contorno verde, icono de verificación y brillo animado. */
    SUCCESS,

    /** Error o fallo en la comunicación con la nube. Muestra contorno rojo e icono de advertencia. */
    ERROR
}
