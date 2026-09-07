package com.example.appopt.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.rememberAppHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Botón interactivo reutilizable con animación de confirmación exitosa, protección contra doble pulsación y respuesta háptica.
 *
 * Principio de diseño y seguridad:
 * - En estado normal muestra el texto descriptivo de la acción con fondo primario.
 * - Al pulsar, bloquea inmediatamente cualquier pulsación adicional concurrente ([isProcessing]).
 * - Al confirmarse la acción, emite respuesta háptica ([AppHaptics.success]), realiza una transición fluida al verde ([SafeGreen]),
 *   reemplaza el texto con una palomita blanca ([Icons.Filled.Check]) manteniendo su color vivo, y ejecuta [onActionConfirmed].
 *
 * @param text Texto descriptivo del botón en Sentence case.
 * @param onClick Acción a ejecutar al presionar. Debe retornar `true` si la acción fue exitosa y debe animarse.
 * @param onActionConfirmed Callback invocado una vez finalizada la animación de la palomita.
 * @param enabled Si es falso, el botón queda deshabilitado visual y funcionalmente.
 * @param modifier Modificador de layout.
 */
@Composable
fun AppAnimatedButton(
    text: String,
    onClick: suspend () -> Boolean,
    onActionConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val coroutineScope = rememberCoroutineScope()
    val appHaptics = rememberAppHaptics()
    var isSuccess by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    val animatedContainerColor by animateColorAsState(
        targetValue = if (isSuccess) SafeGreen else MaterialTheme.colorScheme.primary,
        animationSpec = Motion.Spec.buttonColorSpec(),
        label = "animatedButtonColor"
    )

    val isButtonInteractive = enabled && !isProcessing && !isSuccess

    Button(
        onClick = {
            if (isButtonInteractive) {
                isProcessing = true
                coroutineScope.launch {
                    try {
                        val success = onClick()
                        if (success) {
                            appHaptics.success()
                            isSuccess = true
                            delay(Motion.Duration.SUCCESS_ACTION.toLong().milliseconds)
                            onActionConfirmed()
                        } else {
                            appHaptics.error()
                            isProcessing = false
                        }
                    } catch (_: Exception) {
                        isProcessing = false
                    }
                }
            }
        },
        enabled = isButtonInteractive || isSuccess,
        colors = ButtonDefaults.buttonColors(
            containerColor = animatedContainerColor,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = if (isSuccess) SafeGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = if (isSuccess) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        modifier = modifier
            .fillMaxWidth()
            .height(Dimensions.ComponentHeight.buttonDefault)
    ) {
        AnimatedContent(
            targetState = isSuccess,
            transitionSpec = {
                fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) togetherWith
                        fadeOut(animationSpec = Motion.Spec.quickFadeSpec())
            },
            label = "animatedButtonContent"
        ) { successState ->
            if (successState) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.action_copied),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(Dimensions.IconSize.large)
                    )
                }
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
