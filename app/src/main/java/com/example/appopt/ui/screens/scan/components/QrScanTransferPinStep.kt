package com.example.appopt.ui.screens.scan.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.theme.Dimensions

/**
 * Sub-pantalla de ingreso de PIN/clave para descifrar transferencias de cuentas por lotes.
 *
 * @param pin Valor actual del PIN de transferencia ingresado.
 * @param onPinChange Callback invocado al modificar el valor del PIN.
 * @param errorMessage Mensaje de error a mostrar si el descifrado falla, o `null`.
 * @param isVerifyingPin Indica si la operación criptográfica de verificación está en curso.
 * @param onBack Callback para volver a la pantalla de cámara o resetear la sesión.
 * @param onConfirm Callback invocado para validar y descifrar el paquete de migración.
 * @param modifier Modificador de diseño Compose.
 */
@Composable
fun QrScanTransferPinStep(
    pin: String,
    onPinChange: (String) -> Unit,
    errorMessage: String?,
    isVerifyingPin: Boolean,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimensions.Spacing.lg)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        // 1. Cabecera fija
        Text(
            text = stringResource(R.string.scan_transfer_pin_dialog_title),
            style = MaterialTheme.typography.titleLarge
        )

        // 2. Cuerpo central scrolleable aislado
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
                value = pin,
                onValueChange = { input ->
                    val cleaned = input.filter { it.isLetterOrDigit() }.take(SecurityConfig.TRANSFER_KEY_LENGTH).uppercase()
                    onPinChange(cleaned)
                },
                label = { Text(stringResource(R.string.scan_transfer_pin_input_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters
                ),
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // 3. Pie fijo de acciones
        val isPinLengthValid = pin.length in setOf(SecurityConfig.TRANSFER_QR_PIN_LENGTH, SecurityConfig.TRANSFER_KEY_LENGTH)
        AppDialogActionButtons(
            dismissText = stringResource(R.string.settings_drive_details_back),
            onDismiss = onBack,
            confirmText = stringResource(R.string.scan_transfer_pin_confirm_button),
            onConfirm = onConfirm,
            confirmEnabled = isPinLengthValid && !isVerifyingPin
        )
    }
}
