package com.example.appopt.ui.navigation

import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates

/**
 * Administrador de coordenadas y orígenes de animación dinámica para transiciones de navegación y diálogos modales.
 *
 * Principio de diseño:
 * - Aísla estrictamente el origen de navegación de pantallas ([settingsOrigin], [recentlyDeletedOrigin])
 *   del origen de apertura de modales ([modalOrigin]).
 * - Evita colisiones de pivote cuando se abren y cierran diálogos dentro de una pantalla antes de navegar hacia atrás.
 */
object NavigationOriginTracker {
    /** Origen de transformación dinámico para la pantalla de Ajustes (botón de ajustes en el dock inferior). */
    var settingsOrigin: TransformOrigin = TransformOrigin(0.80f, 0.91f)

    /** Origen de transformación dinámico para la pantalla de Papelera (botón de papelera en el dock inferior). */
    var recentlyDeletedOrigin: TransformOrigin = TransformOrigin(0.65f, 0.91f)

    /** Origen de transformación dinámico para diálogos modales (tarjeta o botón emisor del modal). */
    var modalOrigin: TransformOrigin = TransformOrigin(0.50f, 0.50f)

    /**
     * Registra las coordenadas del botón de Ajustes en el dock inferior.
     *
     * @param coordinates Coordenadas del Composable de Ajustes obtenidas mediante `onPlaced`.
     */
    fun updateSettingsOrigin(coordinates: LayoutCoordinates?) {
        val origin = calculateOrigin(coordinates)
        if (origin != null) {
            settingsOrigin = origin
        }
    }

    /**
     * Registra las coordenadas del botón de Papelera en el dock inferior.
     *
     * @param coordinates Coordenadas del Composable de Papelera obtenidas mediante `onPlaced`.
     */
    fun updateRecentlyDeletedOrigin(coordinates: LayoutCoordinates?) {
        val origin = calculateOrigin(coordinates)
        if (origin != null) {
            recentlyDeletedOrigin = origin
        }
    }

    /**
     * Registra las coordenadas del componente que abre un diálogo modal.
     *
     * @param coordinates Coordenadas del Composable emisor del modal obtenidas mediante `onPlaced`.
     */
    fun updateModalOrigin(coordinates: LayoutCoordinates?) {
        val origin = calculateOrigin(coordinates)
        if (origin != null) {
            modalOrigin = origin
        }
    }

    private fun calculateOrigin(coordinates: LayoutCoordinates?): TransformOrigin? {
        if (coordinates != null && coordinates.isAttached) {
            val bounds = coordinates.boundsInRoot()
            val root = coordinates.findRootCoordinates()
            if (root.size.width > 0 && root.size.height > 0) {
                return TransformOrigin(
                    pivotFractionX = (bounds.center.x / root.size.width.toFloat()).coerceIn(0f, 1f),
                    pivotFractionY = (bounds.center.y / root.size.height.toFloat()).coerceIn(0f, 1f)
                )
            }
        }
        return null
    }
}
