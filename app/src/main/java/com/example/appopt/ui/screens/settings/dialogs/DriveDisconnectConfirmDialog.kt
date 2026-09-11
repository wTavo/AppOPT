package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Diálogo modal de confirmación destructiva para desvincular la cuenta de Google Drive.
 *
 * @param onConfirm Callback invocado al confirmar la desconexión de la cuenta.
 * @param onDismiss Callback invocado para cerrar el diálogo sin realizar cambios.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveDisconnectConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        title = {
            Text(
                text = stringResource(R.string.settings_drive_disconnect_confirm_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.settings_drive_disconnect_confirm_msg),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs, Alignment.End),
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
                        .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                ) {
                    Text(
                        text = stringResource(R.string.settings_drive_details_back),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = {
                        appHaptics.click()
                        onConfirm()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.xs),
                    modifier = Modifier
                        .fillMaxHeight()
                        .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                ) {
                    Text(
                        text = stringResource(R.string.settings_drive_disconnect_button),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        dismissButton = null,
        modifier = modifier
    )
}
