package com.example.appopt.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.rememberAppHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Estados visuales de interacción para [AppAnimatedButton].
 */
enum class AnimatedButtonState {
    /** Estado neutro de reposo. */
    IDLE,
    /** Acción completada con éxito: palomita y fondo verde de seguridad. */
    SUCCESS,
    /** Acción fallida o inválida: 'X' y fondo rojo de urgencia. */
    ERROR
}

/**
 * Botón interactivo reutilizable con animación de confirmación exitosa/error, protección contra doble pulsación y respuesta háptica.
 *
 * Principio de diseño y seguridad:
 * - En estado normal muestra el texto descriptivo de la acción con color configurable o primario por defecto.
 * - Al pulsar, bloquea inmediatamente cualquier pulsación adicional concurrente ([isProcessing]).
 * - Si [onClick] retorna `true`: emite respuesta háptica ([AppHaptics.success]), realiza una transición fluida al verde ([SafeGreen]),
 *   reemplaza el texto con una palomita blanca ([Icons.Filled.Check]) y ejecuta [onActionConfirmed].
 * - Si [onClick] retorna `false` o lanza excepción: emite respuesta háptica ([AppHaptics.error]), realiza una transición fluida al rojo ([UrgentRed]),
 *   reemplaza el texto con una 'X' blanca ([Icons.Filled.Close]), y regresa al estado [AnimatedButtonState.IDLE] permitiendo corregir y reintentar.
 *
 * @param text Texto descriptivo del botón en Sentence case.
 * @param onClick Acción asíncrona a ejecutar. Debe retornar `true` si la acción fue exitosa o `false` si falló.
 * @param onActionConfirmed Callback invocado una vez finalizada la animación de la palomita.
 * @param modifier Modificador de layout Compose.
 * @param enabled Si es falso, el botón queda deshabilitado visual y funcionalmente.
 * @param containerColor Color de fondo opcional en estado normal (por defecto [MaterialTheme.colorScheme.primary]).
 * @param height Altura estandarizada del botón (por defecto [Dimensions.ComponentHeight.buttonDefault]).
 * @param leadingIcon Icono vectorial opcional colocado a la izquierda del texto.
 */
@Composable
fun AppAnimatedButton(
    text: String,
    onClick: suspend () -> Boolean,
    modifier: Modifier = Modifier,
    onActionConfirmed: () -> Unit = {},
    enabled: Boolean = true,
    containerColor: Color? = null,
    height: Dp = Dimensions.ComponentHeight.buttonDefault,
    leadingIcon: ImageVector? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val appHaptics = rememberAppHaptics()
    var buttonState by remember { mutableStateOf(AnimatedButtonState.IDLE) }
    var isProcessing by remember { mutableStateOf(false) }

    val defaultContainerColor = containerColor ?: MaterialTheme.colorScheme.primary

    val targetContainerColor = when (buttonState) {
        AnimatedButtonState.IDLE -> defaultContainerColor
        AnimatedButtonState.SUCCESS -> SafeGreen
        AnimatedButtonState.ERROR -> UrgentRed
    }

    val animatedContainerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = Motion.Spec.buttonColorSpec(),
        label = "animatedButtonColor"
    )

    val isButtonInteractive = enabled && !isProcessing && buttonState == AnimatedButtonState.IDLE
    val isCompact = height == Dimensions.ComponentHeight.buttonCompact

    Button(
        onClick = {
            if (isButtonInteractive) {
                isProcessing = true
                coroutineScope.launch {
                    try {
                        val success = onClick()
                        if (success) {
                            appHaptics.success()
                            buttonState = AnimatedButtonState.SUCCESS
                            delay(Motion.Duration.SUCCESS_ACTION.toLong().milliseconds)
                            onActionConfirmed()
                            buttonState = AnimatedButtonState.IDLE
                            isProcessing = false
                        } else {
                            appHaptics.error()
                            buttonState = AnimatedButtonState.ERROR
                            delay(Motion.Duration.SUCCESS_ACTION.toLong().milliseconds)
                            buttonState = AnimatedButtonState.IDLE
                            isProcessing = false
                        }
                    } catch (_: Exception) {
                        appHaptics.error()
                        buttonState = AnimatedButtonState.ERROR
                        delay(Motion.Duration.SUCCESS_ACTION.toLong().milliseconds)
                        buttonState = AnimatedButtonState.IDLE
                        isProcessing = false
                    }
                }
            }
        },
        enabled = isButtonInteractive || buttonState != AnimatedButtonState.IDLE,
        contentPadding = if (isCompact) PaddingValues(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.none) else ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = animatedContainerColor,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = when (buttonState) {
                AnimatedButtonState.SUCCESS -> SafeGreen
                AnimatedButtonState.ERROR -> UrgentRed
                AnimatedButtonState.IDLE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            },
            disabledContentColor = when (buttonState) {
                AnimatedButtonState.SUCCESS, AnimatedButtonState.ERROR -> MaterialTheme.colorScheme.onPrimary
                AnimatedButtonState.IDLE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        ),
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .heightIn(min = height)
    ) {
        AnimatedContent(
            targetState = buttonState,
            transitionSpec = {
                fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) togetherWith
                        fadeOut(animationSpec = Motion.Spec.quickFadeSpec())
            },
            label = "animatedButtonContent"
        ) { state ->
            when (state) {
                AnimatedButtonState.SUCCESS -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.home_sync_success),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(if (isCompact) Dimensions.IconSize.medium else Dimensions.IconSize.large)
                        )
                    }
                }
                AnimatedButtonState.ERROR -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.home_sync_error),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(if (isCompact) Dimensions.IconSize.medium else Dimensions.IconSize.large)
                        )
                    }
                }
                AnimatedButtonState.IDLE -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                    ) {
                        if (leadingIcon != null) {
                            Icon(
                                imageVector = leadingIcon,
                                contentDescription = null,
                                modifier = Modifier.size(if (isCompact) Dimensions.IconSize.small else Dimensions.IconSize.medium)
                            )
                        }
                        Text(
                            text = text,
                            style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
