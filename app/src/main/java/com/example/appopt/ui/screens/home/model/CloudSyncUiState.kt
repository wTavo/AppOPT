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
     * Sincronización en curso o retardo de consolidación activo. Muestra animación de carga.
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
