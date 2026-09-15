package com.example.appopt.ui.screens.scan.components
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.theme.Dimensions

/**
 * Diálogo modal para solicitar el PIN de 6 dígitos necesario para descifrar un paquete de transferencia OTP.
 *
 * Cumple estrictamente con la Directiva 14 (AppModalDialog monolítico con ventana fija y Compose GPU morphing).
 *
 * @param pinInput Valor actual ingresado para el PIN.
 * @param onPinChange Callback al modificar el valor del PIN.
 * @param pinErrorMessage Mensaje de error a mostrar si el PIN fue incorrecto o expiró.
 * @param isVerifying Indicador de procesamiento asíncrono de verificación.
 * @param onConfirm Callback al confirmar la verificación con el PIN actual.
 * @param onDismiss Callback al cancelar o descartar el diálogo.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun TransferPinPromptDialog(
    pinInput: String,
    onPinChange: (String) -> Unit,
    pinErrorMessage: String?,
    isVerifying: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppModalDialog(
        onDismissRequest = {
            if (!isVerifying) {
                onDismiss()
            }
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.lg)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            // Cabecera fija
            Text(
                text = stringResource(R.string.scan_transfer_pin_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )

            // Cuerpo central scrolleable aislado
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
            ) {
                Text(
                    text = stringResource(R.string.scan_transfer_pin_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all { it.isDigit() }) {
                            onPinChange(input)
                        }
                    },
                    label = { Text(stringResource(R.string.scan_transfer_pin_input_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                )

                if (pinErrorMessage != null) {
                    Text(
                        text = pinErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Pie fijo de acciones
            AppDialogActionButtons(
                dismissText = stringResource(R.string.action_close),
                onDismiss = {
                    if (!isVerifying) {
                        onDismiss()
                    }
                },
                confirmText = stringResource(R.string.scan_transfer_pin_confirm_button),
                onConfirm = onConfirm,
                confirmEnabled = pinInput.length == 6 && !isVerifying
            )
        }
    }
}
