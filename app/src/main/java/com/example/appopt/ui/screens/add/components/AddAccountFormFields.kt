package com.example.appopt.ui.screens.add.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed

/**
 * Campos de formulario para el registro manual de cuentas (Emisor, Usuario y Clave Base32) (Directiva 29).
 *
 * @param issuer Nombre del emisor o servicio.
 * @param onIssuerChange Callback al cambiar el emisor.
 * @param accountName Nombre de cuenta o usuario.
 * @param onAccountNameChange Callback al cambiar la cuenta.
 * @param secretInput Clave secreta Base32 ingresada.
 * @param onSecretInputChange Callback al escribir la clave.
 * @param sanitizedSecret Clave limpia sin espacios ni caracteres ajenos.
 * @param isSecretValid Indica si la clave Base32 tiene un formato criptográfico válido.
 * @param modifier Modificador de diseño.
 */
@Composable
fun AddAccountFormFields(
    issuer: String,
    onIssuerChange: (String) -> Unit,
    accountName: String,
    onAccountNameChange: (String) -> Unit,
    secretInput: String,
    onSecretInputChange: (String) -> Unit,
    sanitizedSecret: String,
    isSecretValid: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        // Campo: Servicio o emisor
        OutlinedTextField(
            value = issuer,
            onValueChange = onIssuerChange,
            label = { Text(stringResource(R.string.add_account_issuer_label)) },
            placeholder = {
                Text(
                    text = stringResource(R.string.add_account_issuer_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Business,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                keyboardType = KeyboardType.Text
            ),
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            modifier = Modifier.fillMaxWidth()
        )

        // Campo: Cuenta o usuario
        OutlinedTextField(
            value = accountName,
            onValueChange = onAccountNameChange,
            label = { Text(stringResource(R.string.add_account_name_label)) },
            placeholder = {
                Text(
                    text = stringResource(R.string.add_account_name_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.PersonOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Email
            ),
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            modifier = Modifier.fillMaxWidth()
        )

        // Campo: Clave secreta Base32
        OutlinedTextField(
            value = secretInput,
            onValueChange = onSecretInputChange,
            label = { Text(stringResource(R.string.add_account_secret_label)) },
            placeholder = {
                Text(
                    text = stringResource(R.string.add_account_secret_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            supportingText = {
                if (sanitizedSecret.isNotBlank() && !isSecretValid) {
                    Text(
                        text = stringResource(R.string.add_account_secret_invalid_hint),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (sanitizedSecret.isNotBlank()) {
                    if (isSecretValid) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.add_account_valid_indicator),
                            tint = SafeGreen
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = stringResource(R.string.add_account_invalid_indicator),
                            tint = UrgentRed
                        )
                    }
                }
            },
            singleLine = true,
            isError = sanitizedSecret.isNotBlank() && !isSecretValid,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
