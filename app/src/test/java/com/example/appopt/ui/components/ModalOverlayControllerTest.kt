package com.example.appopt.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite de pruebas unitarias para el controlador determinista [ModalOverlayController].
 *
 * Valida:
 * - Registro idempotente de identificadores modales únicos.
 * - Desregistro reactivo y retorno determinista a inactivo (`isModalActive = false`) al vaciarse el registro.
 * - Inmunidad ante desregistros múltiples o desconocidos.
 * - Limpieza forzosa con [ModalOverlayController.clearAll].
 */
class ModalOverlayControllerTest {

    @Before
    fun setUp() {
        ModalOverlayController.clearAll()
    }

    @Test
    fun initialState_hasNoModalsActive() {
        assertFalse(ModalOverlayController.isModalActive.value)
    }

    @Test
    fun registerModal_activatesOverlay() {
        ModalOverlayController.registerModal("modal-1")

        assertTrue(ModalOverlayController.isModalActive.value)
    }

    @Test
    fun registerSameModalMultipleTimes_isIdempotent() {
        ModalOverlayController.registerModal("modal-1")
        ModalOverlayController.registerModal("modal-1")

        assertTrue(ModalOverlayController.isModalActive.value)

        // Al desregistrarlo una sola vez, se debe desactivar porque era la misma instancia
        ModalOverlayController.unregisterModal("modal-1")
        assertFalse(ModalOverlayController.isModalActive.value)
    }

    @Test
    fun multipleDistinctModals_remainsActiveUntilLastOneUnregisters() {
        ModalOverlayController.registerModal("modal-1")
        ModalOverlayController.registerModal("modal-2")
        assertTrue(ModalOverlayController.isModalActive.value)

        ModalOverlayController.unregisterModal("modal-1")
        // Sigue activo porque modal-2 aún está vivo
        assertTrue(ModalOverlayController.isModalActive.value)

        ModalOverlayController.unregisterModal("modal-2")
        // Ahora sí se desactiva
        assertFalse(ModalOverlayController.isModalActive.value)
    }

    @Test
    fun unregisterUnknownModal_doesNotAffectActiveState() {
        ModalOverlayController.registerModal("modal-1")
        ModalOverlayController.unregisterModal("non-existent-modal")

        assertTrue(ModalOverlayController.isModalActive.value)
    }

    @Test
    fun clearAll_immediatelyDeactivatesAllModals() {
        ModalOverlayController.registerModal("modal-1")
        ModalOverlayController.registerModal("modal-2")
        assertTrue(ModalOverlayController.isModalActive.value)

        ModalOverlayController.clearAll()
        assertFalse(ModalOverlayController.isModalActive.value)
    }
}
