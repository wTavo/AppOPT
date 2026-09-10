package com.example.appopt.ui.screens.home.model

/**
 * Representa los estados reactivos visuales de la sincronización en la nube para el encabezado principal de la aplicación.
 */
enum class CloudSyncUiState {
    /**
     * Estado neutro de reposo. Muestra el título estándar "Authenticator".
     */
    IDLE,

    /**
     * Intervalo de consolidación o espera de 20 segundos previo a la subida en la nube. Muestra el título en amarillo/ámbar.
     */
    PENDING,

    /**
     * Sincronización en curso activa. Muestra animación de carga y rotación.
     */
    SYNCING,

    /**
     * Sincronización completada con éxito. Muestra contorno verde, icono de verificación y brillo animado.
     */
    SUCCESS,

    /**
     * Error o fallo en la comunicación con la nube. Muestra contorno rojo e icono de advertencia.
     */
    ERROR
}
