package com.example.appopt.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions

/**
 * Diálogo modal reutilizable para confirmaciones destructivas y alertas críticas (Directivas 14, 22 y 23).
 *
 * Características:
 * - Icono de alerta con tinte de error centrado y tamaño estándar ([Dimensions.IconSize.large]).
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
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(Dimensions.IconSize.large)
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            AppDialogActionButtons(
                dismissText = dismissText,
                onDismiss = onDismiss,
                confirmText = confirmText,
                onConfirm = onConfirm,
                isDestructive = true
            )
        },
        dismissButton = null,
        modifier = modifier
    )
}
