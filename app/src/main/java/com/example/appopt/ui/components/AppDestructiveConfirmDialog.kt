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
 * Diálogo modal reutilizable para confirmaciones estándar o destructivas (Directivas 14, 22 y 23).
 *
 * Características:
 * - Renderizado en ventana fluida mediante [AppModalDialog] respetando el [tone] ([ModalTone.STANDARD] o [ModalTone.DESTRUCTIVE]).
 * - Fondo de superficie neutro estándar y borde perimetral sutil.
 * - Tipografía Material 3 estandarizada ([MaterialTheme.typography.titleLarge] y [MaterialTheme.typography.bodyMedium]).
 * - Barra de botones simétrica 50/50 mediante [AppDialogActionButtons] con altura mínima estandarizada.
 * - Descarte seguro («Cerrar» o «Volver») a la izquierda y acción afirmativa a la derecha.
 *
 * @param title Título principal del diálogo.
 * @param message Mensaje descriptivo o advertencia del impacto de la acción.
 * @param confirmText Texto para el botón de acción afirmativa o destructiva.
 * @param onConfirm Callback ejecutado al confirmar la acción.
 * @param onDismiss Callback ejecutado al descartar el diálogo.
 * @param modifier Modificador de diseño Compose opcional.
 * @param dismissText Texto para el botón de descarte (por defecto «Cerrar»).
 * @param icon Icono vectorial superior opcional.
 * @param tone Tono semántico del modal ([ModalTone.STANDARD] o [ModalTone.DESTRUCTIVE]).
 */
@Composable
fun AppConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = stringResource(R.string.action_close),
    icon: ImageVector? = null,
    tone: ModalTone = ModalTone.STANDARD
) {
    AppModalDialog(
        onDismissRequest = onDismiss,
        tone = tone,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md),
            horizontalAlignment = if (icon != null) Alignment.CenterHorizontally else Alignment.Start
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (tone == ModalTone.DESTRUCTIVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimensions.IconSize.large)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = if (tone == ModalTone.DESTRUCTIVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = if (icon != null) TextAlign.Center else TextAlign.Start
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (icon != null) TextAlign.Center else TextAlign.Start
            )

            Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))

            AppDialogActionButtons(
                dismissText = dismissText,
                onDismiss = onDismiss,
                confirmText = confirmText,
                onConfirm = onConfirm,
                isDestructive = tone == ModalTone.DESTRUCTIVE
            )
        }
    }
}

/**
 * Diálogo modal reutilizable para confirmaciones destructivas y alertas críticas (Directivas 14, 22 y 23).
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
    AppConfirmDialog(
        title = title,
        message = message,
        confirmText = confirmText,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        dismissText = dismissText,
        icon = icon,
        tone = ModalTone.DESTRUCTIVE
    )
}
