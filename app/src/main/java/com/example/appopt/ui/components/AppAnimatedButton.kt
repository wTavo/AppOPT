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
import androidx.compose.ui.graphics.Color
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
 * Botón interactivo reutilizable con animación de confirmación exitosa y respuesta háptica.
 *
 * Comportamiento:
 * - En estado normal muestra el texto descriptivo de la acción.
 * - Al pulsar, si la acción se confirma, emite respuesta háptica de éxito ([AppHaptics.success]),
 *   realiza una transición fluida hacia el color verde de seguridad ([SafeGreen]),
 *   reemplaza el texto con una palomita ([Icons.Filled.Check]) durante [Motion.Duration.SUCCESS_ACTION] ms, y
 *   finalmente ejecuta el callback [onActionConfirmed].
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

    val animatedContainerColor by animateColorAsState(
        targetValue = if (isSuccess) SafeGreen else MaterialTheme.colorScheme.primary,
        animationSpec = Motion.Spec.buttonColorSpec(),
        label = "animatedButtonColor"
    )

    Button(
        onClick = {
            if (!isSuccess) {
                coroutineScope.launch {
                    val success = onClick()
                    if (success) {
                        appHaptics.success()
                        isSuccess = true
                        delay(Motion.Duration.SUCCESS_ACTION.toLong().milliseconds)
                        onActionConfirmed()
                    }
                }
            }
        },
        enabled = enabled && !isSuccess,
        colors = ButtonDefaults.buttonColors(
            containerColor = animatedContainerColor,
            contentColor = Color.White
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
                        tint = Color.White,
                        modifier = Modifier.size(Dimensions.IconSize.large)
                    )
                }
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
