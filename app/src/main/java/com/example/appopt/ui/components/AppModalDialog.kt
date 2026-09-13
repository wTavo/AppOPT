package com.example.appopt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.appopt.ui.theme.Dimensions

/**
 * Contenedor modal centralizado con ventana estática a pantalla completa y renderizado 100% en Compose GPU.
 *
 * Resuelve de forma definitiva el conflicto de redimensionamiento nativo en Android:
 * Al fijar [DialogProperties.usePlatformDefaultWidth] en `false`, la ventana del sistema operativo
 * permanece a pantalla completa sin enviar llamadas IPC continuas a `WindowManagerService`.
 * La tarjeta visual ([Surface]) muta sus dimensiones libremente dentro de Compose GPU
 * con física de resortes elásticos (Dynamic Island Fluid Morphing) sin saltos ni vibraciones.
 *
 * @param onDismissRequest Callback invocado al presionar fuera de la tarjeta o el botón atrás del sistema.
 * @param modifier Modificador Compose opcional para la tarjeta visual.
 * @param properties Propiedades de configuración del diálogo modal.
 * @param content Contenido interno del diálogo (usualmente envuelto en un `AnimatedContent` monolítico).
 */
@Composable
fun AppModalDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false
    ),
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        // Fondo oscurecido (Scrim) a pantalla completa con descarte al hacer clic afuera
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            // Tarjeta modal del diálogo: Su tamaño se adapta dinámicamente al contenido
            Surface(
                modifier = modifier
                    .safeDrawingPadding()
                    .padding(
                        horizontal = Dimensions.Spacing.xl,
                        vertical = Dimensions.Spacing.xxl
                    )
                    .widthIn(min = 280.dp, max = 380.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Intercepta clics dentro de la tarjeta para evitar descarte accidental
                    ),
                shape = RoundedCornerShape(Dimensions.CornerRadius.large),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = Dimensions.Elevation.modal,
                shadowElevation = Dimensions.Elevation.modal
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimensions.Spacing.lg)
                ) {
                    content()
                }
            }
        }
    }
}
