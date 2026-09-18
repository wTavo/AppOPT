package com.example.appopt.ui.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.unit.IntSize

/**
 * Administrador de coordenadas y origen de animación dinámica para transiciones de navegación.
 *
 * Captura en tiempo real los límites y el centro exacto en píxeles del componente que dispara
 * la navegación ([LayoutCoordinates]), calculando el [TransformOrigin] normalizado respecto a la ventana raíz.
 * Esto garantiza que la animación nazca y muera con precisión milimétrica en el centro real del botón
 * en cualquier resolución, densidad o modo de barra de navegación de Android.
 */
object NavigationOriginTracker {
    /** Origen de transformación dinámico actual para la animación de entrada y salida. */
    var currentOrigin: TransformOrigin = TransformOrigin(0.50f, 0.91f)

    /** Centro absoluto en píxeles del componente emisor en la ventana raíz. */
    var originCenterPx: Offset? = null

    /** Tamaño en píxeles de la ventana raíz. */
    var rootSizePx: IntSize? = null

    /**
     * Registra las coordenadas del componente emisor calculando su centro relativo exacto.
     *
     * @param coordinates Coordenadas del Composable obtenidas mediante `onPlaced`.
     */
    fun updateFromCoordinates(coordinates: LayoutCoordinates?) {
        if (coordinates != null && coordinates.isAttached) {
            val bounds = coordinates.boundsInRoot()
            val root = coordinates.findRootCoordinates()
            originCenterPx = bounds.center
            rootSizePx = root.size
            if (root.size.width > 0 && root.size.height > 0) {
                currentOrigin = TransformOrigin(
                    pivotFractionX = (bounds.center.x / root.size.width.toFloat()).coerceIn(0f, 1f),
                    pivotFractionY = (bounds.center.y / root.size.height.toFloat()).coerceIn(0f, 1f)
                )
            }
        }
    }
}
