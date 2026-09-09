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
        const val INSTANT = 50

        /** Control de frecuencia para eventos táctiles rápidos de arrastre (80ms). */
        const val DRAG_DEBOUNCE = 80

        /** Animaciones rápidas como micro-interacciones o iconos (150ms). */
        const val FAST = 150

        /** Duración estándar para transiciones de color, modales y cambios de estado (300ms). */
        const val MEDIUM = 300

        /** Transición extendida para confirmaciones de acción en botones (750ms). */
        const val SUCCESS_ACTION = 750

        /** Tiempo de permanencia para avisos de copiado al portapapeles o mensajes breves (1500ms). */
        const val FEEDBACK_TOAST = 1500

        /** Retardo incremental entre elementos consecutivos en animaciones escalonadas en cascada (45ms). */
        const val STAGGER_STEP = 45

        /** Duración de la animación de entrada y desplazamiento de cada tarjeta de servicio (350ms). */
        const val STAGGER_ITEM = 350

        /** Límite superior de elementos para el cálculo de retardo escalonado (8 elementos = 360ms máx). */
        const val MAX_STAGGER_INDEX = 8

        /** Intervalo de sondeo del bucle de arrastre manual (~60fps). Equivale a un fotograma a 60 Hz (16ms). */
        const val DRAG_POLL_INTERVAL_MS = 16

        /** Duración de la animación de rotación continua para sincronización activa en cabecera (1200ms). */
        const val SYNC_ROTATION = 1200

        /** Duración del bucle de brillo ambiental (*ambient shine*) en estado de sincronización exitosa (4000ms). */
        const val AMBIENT_SHINE_LOOP = 4000

        /** Duración de entrada para transición Fade Through entre pantallas (210ms). */
        const val NAV_FADE_THROUGH_ENTER = 210

        /** Duración de salida para transición Fade Through entre pantallas (90ms). */
        const val NAV_FADE_THROUGH_EXIT = 90

        /** Duración del desplazamiento en transición Shared X-Axis (280ms). */
        const val NAV_SHARED_X_SLIDE = 280

        /** Duración del desvanecimiento sutil en transición Shared X-Axis (180ms). */
        const val NAV_SHARED_X_FADE = 180
    }

    /**
     * Factores de paralaje y proporciones de desplazamiento en fondo.
     */
    object Parallax {
        /** Factor de desplazamiento sutil de la pantalla de fondo en Shared X-Axis (20%). */
        const val NAV_SHARED_X_FACTOR = 0.20f
    }

    /**
     * Escalas y factores de proporción para transformaciones dimensionales.
     */
    object Scale {
        /** Escala inicial reducida para transición de entrada Fade Through (96%). */
        const val NAV_FADE_THROUGH_INITIAL = 0.96f

        /** Escala final reducida para transición de salida Fade Through (96%). */
        const val NAV_FADE_THROUGH_EXIT = 0.96f
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
            durationMillis = Duration.MEDIUM,
            easing = EasingCurve.Standard
        )

        /** Especificación de transición suave para arcos de progreso y contadores circulares. */
        fun <T> progressColorSpec() = tween<T>(
            durationMillis = Duration.MEDIUM,
            easing = EasingCurve.Standard
        )

        /** Especificación rápida para apariciones y desvanecimientos de iconos (*fade*). */
        fun <T> quickFadeSpec() = tween<T>(
            durationMillis = Duration.FAST,
            easing = EasingCurve.Standard
        )

        /** Especificación de expansión/colapso para el modo de privacidad de códigos. */
        fun <T> privacyCollapseSpec() = tween<T>(
            durationMillis = Duration.MEDIUM,
            easing = EasingCurve.Emphasized
        )

        /** Especificación de entrada escalonada en cascada (*staggered entry*) para tarjetas de lista. */
        fun <T> staggerItemSpec(delayMillis: Int = 0) = tween<T>(
            durationMillis = Duration.STAGGER_ITEM,
            delayMillis = delayMillis,
            easing = EasingCurve.Emphasized
        )

        /** Especificación física con amortiguación elástica para elevaciones y escalas táctiles. */
        fun <T> springFeedbackSpec() = spring<T>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )

        /** Especificación de desvanecimiento entrante para transición de pantalla Fade Through. */
        fun <T> navFadeInSpec() = tween<T>(
            durationMillis = Duration.NAV_FADE_THROUGH_ENTER,
            delayMillis = Duration.NAV_FADE_THROUGH_EXIT,
            easing = EasingCurve.Decelerate
        )

        /** Especificación de desvanecimiento saliente para transición de pantalla Fade Through. */
        fun <T> navFadeOutSpec() = tween<T>(
            durationMillis = Duration.NAV_FADE_THROUGH_EXIT,
            easing = EasingCurve.Standard
        )

        /** Especificación de escalado dimensional para transición de pantalla Fade Through. */
        fun <T> navScaleSpec() = tween<T>(
            durationMillis = Duration.NAV_FADE_THROUGH_ENTER + Duration.NAV_FADE_THROUGH_EXIT,
            easing = EasingCurve.Standard
        )

        /** Especificación de desplazamiento para transición Shared X-Axis de Material Design 3. */
        fun <T> navSharedXSlideSpec() = tween<T>(
            durationMillis = Duration.NAV_SHARED_X_SLIDE,
            easing = EasingCurve.Emphasized
        )

        /** Especificación de desvanecimiento para transición Shared X-Axis de Material Design 3. */
        fun <T> navSharedXFadeSpec() = tween<T>(
            durationMillis = Duration.NAV_SHARED_X_FADE,
            easing = EasingCurve.Standard
        )
    }
}
