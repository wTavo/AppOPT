package com.example.appopt.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Barra de acciones unificada y ergonómica para diálogos modales (Directivas 14, 22 y 23).
 *
 * Características:
 * - Soporta 2 botones (Descarte/Volver a la izquierda + Acción a la derecha) o 1 botón solitario (Cerrar a la derecha).
 * - Sincroniza la altura de los controles mediante [IntrinsicSize.Min] y [Dimensions.ComponentHeight.buttonDefault] (50.dp).
 * - Idempotencia nativa: previene múltiples pulsaciones rápidas en acciones mutantes o destructivas.
 * - Respuesta háptica semántica automática mediante [rememberAppHaptics].
 *
 * @param onDismiss Callback ejecutado al presionar el botón de salida.
 * @param modifier Modificador opcional de diseño.
 * @param dismissText Texto para el botón de salida («Cerrar» o «Volver»). Por defecto «Cerrar».
 * @param confirmText Texto opcional para el botón de acción afirmativa/destructiva.
 * @param onConfirm Callback opcional ejecutado al presionar el botón de confirmación.
 * @param confirmEnabled Bandera para habilitar o deshabilitar el botón de confirmación.
 * @param isDestructive Si es verdadero, colorea el botón de confirmación con el esquema de error.
 * @param minHeight Altura mínima obligatoria para los botones (por defecto 50.dp).
 */
@Composable
fun AppDialogActionButtons(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = stringResource(R.string.action_close),
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    isDestructive: Boolean = false,
    minHeight: Dp = Dimensions.ComponentHeight.buttonDefault
) {
    val appHaptics = rememberAppHaptics()
    var isProcessing by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = if (confirmText != null && onConfirm != null) {
            Arrangement.spacedBy(Dimensions.Spacing.xs, Alignment.End)
        } else {
            Arrangement.End
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = {
                appHaptics.click()
                onDismiss()
            },
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            modifier = Modifier
                .fillMaxHeight()
                .heightIn(min = minHeight)
        ) {
            Text(
                text = dismissText,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        }

        if (confirmText != null && onConfirm != null) {
            Button(
                onClick = {
                    if (!isProcessing) {
                        isProcessing = true
                        appHaptics.click()
                        try {
                            onConfirm()
                        } finally {
                            isProcessing = false
                        }
                    }
                },
                enabled = confirmEnabled && !isProcessing,
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.xs),
                modifier = Modifier
                    .fillMaxHeight()
                    .heightIn(min = minHeight)
            ) {
                Text(
                    text = confirmText,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
