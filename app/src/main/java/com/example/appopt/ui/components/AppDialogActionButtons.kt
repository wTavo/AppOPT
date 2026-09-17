package com.example.appopt.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
 * - Soporte integrado de indicador de carga animado mediante [CircularProgressIndicator].
 *
 * @param onDismiss Callback ejecutado al presionar el botón de salida.
 * @param modifier Modificador opcional de diseño.
 * @param dismissText Texto para el botón de salida («Cerrar» o «Volver»). Por defecto «Cerrar».
 * @param confirmText Texto opcional para el botón de acción afirmativa/destructiva.
 * @param onConfirm Callback opcional ejecutado al presionar el botón de confirmación.
 * @param confirmEnabled Bandera para habilitar o deshabilitar el botón de confirmación.
 * @param isLoading Bandera que indica si la acción está en proceso asíncrono.
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
    isLoading: Boolean = false,
    isDestructive: Boolean = false,
    minHeight: Dp = Dimensions.ComponentHeight.buttonDefault
) {
    val appHaptics = rememberAppHaptics()
    var isProcessing by remember { mutableStateOf(false) }
    val modalDismissHandler = LocalModalDismissHandler.current
    val closeText = stringResource(R.string.action_close)
    val accountCloseText = stringResource(R.string.account_modal_close_button)
    val hasConfirmAction = confirmText != null && onConfirm != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = if (hasConfirmAction) {
            Arrangement.spacedBy(Dimensions.Spacing.sm)
        } else {
            Arrangement.End
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dismissModifier = if (hasConfirmAction) {
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .heightIn(min = minHeight)
        } else {
            Modifier
                .fillMaxHeight()
                .heightIn(min = minHeight)
        }

        TextButton(
            onClick = {
                appHaptics.click()
                if (modalDismissHandler != null && (dismissText == closeText || dismissText == accountCloseText || !hasConfirmAction)) {
                    modalDismissHandler()
                } else {
                    onDismiss()
                }
            },
            enabled = !isLoading,
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.none),
            modifier = dismissModifier
        ) {
            Text(
                text = dismissText,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        }

        if (hasConfirmAction) {
            Button(
                onClick = {
                    if (!isProcessing && !isLoading) {
                        isProcessing = true
                        appHaptics.click()
                        try {
                            onConfirm()
                        } finally {
                            isProcessing = false
                        }
                    }
                },
                enabled = confirmEnabled && !isProcessing && !isLoading,
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.none),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = minHeight)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimensions.IconSize.small),
                        color = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = Dimensions.Stroke.regular
                    )
                } else {
                    Text(
                        text = confirmText,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
