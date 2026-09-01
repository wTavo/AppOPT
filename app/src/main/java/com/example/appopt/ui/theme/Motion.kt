package com.example.appopt.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Sistema centralizado de movimiento y animaciones (Material Design 3 Motion System).
 *
 * Directiva del proyecto:
 * - PROHIBIDO definir milisegundos o especificaciones de animación arbitrarias (*magic numbers*) en los Composables.
 * - OBLIGATORIO consumir las duraciones, curvas (*easings*) y especificaciones (*AnimationSpec*) centralizadas en este archivo.
 */
object Motion {

    /**
     * Duraciones estándar en milisegundos.
     */
    object Duration {
        /** Transiciones instantáneas o micro-ajustes visuales (50ms). */
        const val Instant = 50

        /** Control de frecuencia para eventos táctiles rápidos de arrastre (80ms). */
        const val DragDebounce = 80

        /** Animaciones rápidas como micro-interacciones o iconos (150ms). */
        const val Fast = 150

        /** Duración estándar para transiciones de color, modales y cambios de estado (300ms). */
        const val Medium = 300

        /** Transición extendida para confirmaciones de acción en botones (750ms). */
        const val SuccessAction = 750

        /** Tiempo de permanencia para avisos de copiado al portapapeles o mensajes breves (1500ms). */
        const val FeedbackToast = 1500
    }

    /**
     * Curvas de aceleración y desaceleración (*Easings*).
     */
    object EasingCurve {
        /** Desaceleración suave estándar de Material 3 para entradas a pantalla. */
        val Standard: Easing = FastOutSlowInEasing

        /** Desaceleración enfatizada para elementos que capturan la atención del usuario. */
        val Emphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

        /** Salida lineal con frenado suave para elementos en salida. */
        val Decelerate: Easing = LinearOutSlowInEasing
    }

    /**
     * Especificaciones de animación reutilizables ([androidx.compose.animation.core.AnimationSpec]).
     */
    object Spec {
        /** Especificación para la transición de color de fondo en botones al confirmar éxito. */
        fun <T> buttonColorSpec() = tween<T>(
            durationMillis = Duration.Medium,
            easing = EasingCurve.Standard
        )

        /** Especificación de transición suave para arcos de progreso y contadores circulares. */
        fun <T> progressColorSpec() = tween<T>(
            durationMillis = Duration.Medium,
            easing = EasingCurve.Standard
        )

        /** Especificación rápida para apariciones y desvanecimientos de iconos (*fade*). */
        fun <T> quickFadeSpec() = tween<T>(
            durationMillis = Duration.Fast,
            easing = EasingCurve.Standard
        )

        /** Especificación física con amortiguación elástica para elevaciones y escalas táctiles. */
        fun <T> springFeedbackSpec() = spring<T>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    }
}
