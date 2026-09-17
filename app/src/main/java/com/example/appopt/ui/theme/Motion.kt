package com.example.appopt.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

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

        /** Animaciones rápidas como micro-interacciones o iconos (150ms). */
        const val FAST = 150

        /** Duración estándar para transiciones de color, modales y cambios de estado (300ms). */
        const val MEDIUM = 300

        /** Transición extendida para confirmaciones de acción en botones (750ms). */
        const val SUCCESS_ACTION = 750

        /** Tiempo de permanencia para avisos de copiado al portapapeles o mensajes breves (1500ms). */
        const val FEEDBACK_TOAST = 1500

        /** Duración de la animación de rotación continua para sincronización activa en cabecera (1200ms). */
        const val SYNC_ROTATION = 1200

        /** Duración del bucle de brillo ambiental (*ambient shine*) en estado de sincronización exitosa (4000ms). */
        const val AMBIENT_SHINE_LOOP = 4000

        /** Duración de la animación de escala para expansión desde botón (380ms). */
        const val NAV_EXPAND_SCALE = 380

        /** Duración ágil para repliegue y colapso de modales y pantallas hacia su botón de origen (220ms). */
        const val NAV_COLLAPSE_SCALE = 220

        /** Duración del desvanecimiento final de opacidad en la salida al replegarse al botón (90ms). */
        const val NAV_COLLAPSE_FADE = 90

        /** Duración del desvanecimiento entrante suave para pasos internos de diálogos y modales (180ms). */
        const val DIALOG_STEP_FADE_IN = 180

        /** Duración del desvanecimiento rápido de salida en pasos de diálogo (90ms). */
        const val DIALOG_STEP_FADE_OUT = 90

        /** Tiempo de permanencia antes de restaurar el estado visual de sincronización en cabecera (3000ms). */
        const val SYNC_STATUS_RESET = 3000

        /** Tiempo rápido de permanencia para restauración de estado de sincronización (2500ms). */
        const val SYNC_STATUS_FAST_RESET = 2500
    }

    /**
     * Escalas y factores de proporción para transformaciones dimensionales.
     */
    object Scale {
        /** Escala inicial y final replegada al punto cero del icono del botón (0%). */
        const val NAV_BUTTON_COLLAPSE = 0.0f

        /** Escala de contracción en profundidad para la pantalla de fondo en transición de esquina (94%). */
        const val NAV_BACKGROUND_SHRINK = 0.94f
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

        /** Especificación de escalado para transición de pantalla que nace y se expande desde el botón. */
        fun <T> navButtonExpandScaleSpec() = tween<T>(
            durationMillis = Duration.NAV_EXPAND_SCALE,
            easing = EasingCurve.Standard
        )

        /** Especificación de escalado para repliegue ágil hacia el botón de origen. */
        fun <T> navButtonCollapseScaleSpec() = tween<T>(
            durationMillis = Duration.NAV_COLLAPSE_SCALE,
            easing = EasingCurve.Standard
        )

        /** Especificación de desvanecimiento en salida retrasado para desvanecerse solo al final del colapso en el botón. */
        fun <T> navButtonCollapseFadeSpec() = tween<T>(
            durationMillis = Duration.NAV_COLLAPSE_FADE,
            delayMillis = Duration.NAV_COLLAPSE_SCALE - Duration.NAV_COLLAPSE_FADE,
            easing = EasingCurve.Standard
        )

        /** Especificación de transición entrante para fondo y desenfoque modal (380ms). */
        fun <T> modalScrimEnterSpec() = tween<T>(
            durationMillis = Duration.NAV_EXPAND_SCALE,
            easing = EasingCurve.Standard
        )

        /** Especificación de transición saliente ágil para fondo y desenfoque modal (220ms). */
        fun <T> modalScrimExitSpec() = tween<T>(
            durationMillis = Duration.NAV_COLLAPSE_SCALE,
            easing = EasingCurve.Standard
        )

        /**
         * Transición fluida estilo Dynamic Island Morphing para pasos y sub-estados dentro de diálogos modales.
         *
         * En conjunción con [com.example.appopt.ui.components.AppModalDialog] (ventana estática de pantalla completa en Android):
         * 1. **Fase de salida (90 ms):** El contenido saliente se desvanece de inmediato ([fadeOut]).
         * 2. **Ajuste dimensional líquido:** La tarjeta modal muta sus dimensiones suavemente en Compose GPU
         *    con física de resortes elásticos sin rebote ([spring] con [Spring.DampingRatioNoBouncy] y [Spring.StiffnessMediumLow]).
         * 3. **Fase de entrada (180 ms, retardo 70 ms):** El nuevo contenido emerge suavemente ([fadeIn]).
         * 4. **Sin recorte invasivo:** [clip] = false preserva sombras y esquinas intactas durante la mutación.
         *
         * @return [ContentTransform] lista para consumirse en `AnimatedContent(transitionSpec = { Motion.Spec.dialogStepContentTransform() })`.
         */
        fun dialogStepContentTransform(): ContentTransform {
            val exitSpec = fadeOut(
                animationSpec = tween(
                    durationMillis = Duration.DIALOG_STEP_FADE_OUT,
                    easing = EasingCurve.Standard
                )
            )

            val enterSpec = fadeIn(
                animationSpec = tween(
                    durationMillis = Duration.DIALOG_STEP_FADE_IN,
                    delayMillis = 70,
                    easing = EasingCurve.Decelerate
                )
            )

            return ContentTransform(
                targetContentEnter = enterSpec,
                initialContentExit = exitSpec,
                sizeTransform = SizeTransform(
                    clip = false,
                    sizeAnimationSpec = { _, _ ->
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    }
                )
            )
        }
    }
}
