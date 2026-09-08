package com.example.appopt.ui.screens.home.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions

import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Diálogo modal para seleccionar el método de incorporación de cuentas (Escaneo QR o Ingreso Manual).
 *
 * @param onScanQr Option seleccionada para escanear código QR de servicio.
 * @param onAddManual Option seleccionada para introducir clave secreta manualmente.
 * @param onDismiss Callback invocado para cancelar y cerrar el modal.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun AddAccountOptionsDialog(
    onScanQr: () -> Unit,
    onAddManual: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        title = {
            Text(
                text = stringResource(R.string.home_add_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
            ) {
                Button(
                    onClick = {
                        appHaptics.click()
                        onScanQr()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.IconSize.medium)
                    )
                    Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                    Text(
                        text = stringResource(R.string.home_scan_qr_option),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = {
                        appHaptics.click()
                        onAddManual()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Keyboard,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.IconSize.medium)
                    )
                    Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                    Text(
                        text = stringResource(R.string.home_add_manual_option),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = {
                    appHaptics.click()
                    onDismiss()
                }
            ) {
                Text(
                    text = stringResource(R.string.action_close),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        modifier = modifier
    )
}
