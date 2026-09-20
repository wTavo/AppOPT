package com.example.appopt.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions

/**
 * Diálogo modal reutilizable para confirmaciones destructivas y alertas críticas (Directivas 14, 22 y 23).
 *
 * Características:
 * - Renderizado en ventana estática fluida mediante [AppModalDialog] con tono destructivo ([ModalTone.DESTRUCTIVE]).
 * - Fondo de superficie neutro estándar y borde perimetral sutil, resaltando la severidad en controles y títulos.
 * - Icono de alerta e indicación visual de acción destructiva en esquema de color de error.
 * - Tipografía Material 3 estandarizada ([MaterialTheme.typography.titleLarge] y [MaterialTheme.typography.bodyMedium]).
 * - Barra de botones unificada mediante [AppDialogActionButtons] con soporte de idempotencia y altura mínima obligatoria de 50.dp.
 * - Descarte seguro («Cerrar» o «Volver») posicionado a la izquierda y acción afirmativa destructiva a la derecha.
 *
 * @param title Título principal del diálogo.
 * @param message Mensaje descriptivo o advertencia del impacto de la acción.
 * @param confirmText Texto para el botón de acción destructiva.
 * @param onConfirm Callback ejecutado al confirmar la acción destructiva.
 * @param onDismiss Callback ejecutado al descartar o cancelar el diálogo.
 * @param modifier Modificador de diseño Compose opcional.
 * @param dismissText Texto para el botón de descarte (por defecto «Cerrar»).
 * @param icon Icono vectorial superior (por defecto [Icons.Filled.DeleteForever]).
 */
@Composable
fun AppDestructiveConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = stringResource(R.string.action_close),
    icon: ImageVector = Icons.Filled.DeleteForever
) {
    AppModalDialog(
        onDismissRequest = onDismiss,
        tone = ModalTone.DESTRUCTIVE,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(Dimensions.IconSize.large)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))

            AppDialogActionButtons(
                dismissText = dismissText,
                onDismiss = onDismiss,
                confirmText = confirmText,
                onConfirm = onConfirm,
                isDestructive = true
            )
        }
    }
}
