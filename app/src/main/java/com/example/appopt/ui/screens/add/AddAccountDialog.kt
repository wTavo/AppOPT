package com.example.appopt.ui.screens.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.totp.Base32
import com.example.appopt.security.CryptoManager
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.components.LocalModalDismissHandler
import com.example.appopt.ui.screens.add.components.AddAccountAdvancedOptions
import com.example.appopt.ui.screens.add.components.AddAccountFormFields
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Diálogo modal para el registro manual de una cuenta 2FA en la bóveda local.
 *
 * Cumple estrictamente con la Directiva 14 (AppModalDialog monolítico con ventana fija y Compose GPU morphing),
 * Directiva 1 (Tipografía M3 centralizada), Directiva 2 (Textos centralizados en Sentence case),
 * Directiva 4 (Espaciados centralizados y límite de altura scrolleable), Directiva 22 (AppAnimatedButton con confirmación)
 * y Directiva 29 (Desacoplamiento modular de subcomponentes).
 *
 * @param onDismiss Callback invocado para cerrar el diálogo.
 * @param modifier Modificador de diseño Compose opcional.
 * @param onAccountSaved Callback opcional invocado al guardar exitosamente la cuenta.
 */
@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onAccountSaved: () -> Unit = {}
) {
    val repository = remember { AuthenticatorApp.instance.accountRepository }
    val appHaptics = rememberAppHaptics()

    var issuer by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var secretInput by remember { mutableStateOf("") }
    var selectedAlgorithm by remember { mutableStateOf(OtpAlgorithm.SHA1) }
    var selectedDigits by remember { mutableIntStateOf(6) }
    var showAdvancedOptions by remember { mutableStateOf(false) }

    val sanitizedSecret = remember(secretInput) { Base32.sanitize(secretInput) }
    val isSecretValid = remember(sanitizedSecret) {
        sanitizedSecret.isNotBlank() && Base32.isValid(sanitizedSecret)
    }
    val isFormValid = issuer.isNotBlank() && isSecretValid

    AppModalDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.lg)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            // 1. Título Fijo del Diálogo
            Text(
                text = stringResource(R.string.add_account_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 2. Contenido Central Scrolleable
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
            ) {
                AddAccountFormFields(
                    issuer = issuer,
                    onIssuerChange = { issuer = it },
                    accountName = accountName,
                    onAccountNameChange = { accountName = it },
                    secretInput = secretInput,
                    onSecretInputChange = { secretInput = it },
                    sanitizedSecret = sanitizedSecret,
                    isSecretValid = isSecretValid
                )

                AddAccountAdvancedOptions(
                    showAdvancedOptions = showAdvancedOptions,
                    onToggleAdvancedOptions = { showAdvancedOptions = !showAdvancedOptions },
                    selectedAlgorithm = selectedAlgorithm,
                    onAlgorithmChange = { selectedAlgorithm = it },
                    selectedDigits = selectedDigits,
                    onDigitsChange = { selectedDigits = it }
                )
            }

            // 3. Botones de Acción Fijos en la parte inferior (Directiva 14 & 23)
            val modalDismissHandler = LocalModalDismissHandler.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        appHaptics.click()
                        if (modalDismissHandler != null) {
                            modalDismissHandler()
                        } else {
                            onDismiss()
                        }
                    },
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.none),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                ) {
                    Text(
                        text = stringResource(R.string.action_close),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                }

                AppAnimatedButton(
                    text = stringResource(R.string.action_save),
                    onClick = {
                        if (!isFormValid) return@AppAnimatedButton false
                        val secretBytes = Base32.decode(sanitizedSecret)
                        try {
                            repository.saveAccount(
                                issuer = issuer.trim(),
                                accountName = accountName.trim(),
                                secretBytes = secretBytes,
                                algorithm = selectedAlgorithm,
                                digits = selectedDigits,
                                period = 30,
                                type = OtpType.TOTP
                            )
                            true
                        } catch (_: Exception) {
                            false
                        } finally {
                            CryptoManager.zeroize(secretBytes)
                        }
                    },
                    onActionConfirmed = {
                        onAccountSaved()
                        if (modalDismissHandler != null) {
                            modalDismissHandler()
                        } else {
                            onDismiss()
                        }
                    },
                    enabled = isFormValid,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
