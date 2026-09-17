package com.example.appopt.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controlador reactivo centralizado para coordinar el desenfoque de fondo (*frosted glass*) en Compose GPU
 * cuando un diálogo modal interactivo se encuentra activo en la aplicación.
 *
 * Arquitectura de compatibilidad universal:
 * En diversos fabricantes (como Samsung One UI en series Galaxy A), el sistema operativo restringe
 * el desenfoque entre ventanas independientes ([android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND] con mBlurEnabled=false).
 * Este controlador expone un flujo de estado reactivo observable por la raíz de navegación ([com.example.appopt.ui.navigation.AppNavigation])
 * para aplicar el efecto Modifier.blur directamente mediante los shaders internos de Skia, garantizando un
 * desenfoque líquido y real en cualquier dispositivo Android 12+ (API 31+).
 */
object ModalOverlayController {

    private val activeModalIds = mutableSetOf<String>()
    private val _isModalActive = MutableStateFlow(false)

    /**
     * Flujo reactivo inmutable que indica si hay al menos un diálogo modal desplegado en la vista.
     */
    val isModalActive: StateFlow<Boolean> = _isModalActive.asStateFlow()

    /**
     * Registra la presencia activa de un diálogo modal mediante su identificador único.
     * Operación idempotente y segura para hilos.
     *
     * @param modalId Identificador único inmutable de la instancia del diálogo modal.
     */
    @Synchronized
    fun registerModal(modalId: String) {
        activeModalIds.add(modalId)
        _isModalActive.value = true
    }

    /**
     * Da de baja un diálogo modal activo (al iniciar su repliegue o al desmontarse de la composición).
     * Operación idempotente y segura para hilos.
     *
     * @param modalId Identificador único inmutable de la instancia del diálogo modal.
     */
    @Synchronized
    fun unregisterModal(modalId: String) {
        activeModalIds.remove(modalId)
        _isModalActive.value = activeModalIds.isNotEmpty()
    }

    /**
     * Limpia de forma forzosa cualquier registro modal residual (por ejemplo, ante el bloqueo de la bóveda).
     */
    @Synchronized
    fun clearAll() {
        activeModalIds.clear()
        _isModalActive.value = false
    }
}
