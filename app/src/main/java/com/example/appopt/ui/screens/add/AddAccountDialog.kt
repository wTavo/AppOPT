package com.example.appopt.ui.screens.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.totp.Base32
import com.example.appopt.security.CryptoManager
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Diálogo modal para el registro manual de una cuenta 2FA en la bóveda local.
 *
 * Cumple estrictamente con la Directiva 14 (AppModalDialog monolítico con ventana fija y Compose GPU morphing),
 * Directiva 1 (Tipografía M3 centralizada), Directiva 2 (Textos centralizados en Sentence case),
 * Directiva 4 (Espaciados centralizados y límite de altura scrolleable) y Directiva 22 (AppAnimatedButton con confirmación).
 *
 * @param onDismiss Callback invocado para cerrar el diálogo.
 * @param onAccountSaved Callback opcional invocado al guardar exitosamente la cuenta.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onAccountSaved: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val repository = remember { AuthenticatorApp.instance.accountRepository }
    val appHaptics = rememberAppHaptics()

    var issuer by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var secretInput by remember { mutableStateOf("") }
    var selectedAlgorithm by remember { mutableStateOf(OtpAlgorithm.SHA1) }
    var selectedDigits by remember { mutableStateOf(6) }
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
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            // Título del Diálogo
            Text(
                text = stringResource(R.string.add_account_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Campo: Servicio o emisor
            OutlinedTextField(
                value = issuer,
                onValueChange = { issuer = it },
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
                onValueChange = { accountName = it },
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
                onValueChange = { secretInput = it },
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

            // Panel desplegable: Opciones avanzadas
            Card(
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
                    .clickable {
                        appHaptics.click()
                        showAdvancedOptions = !showAdvancedOptions
                    }
            ) {
                Column(
                    modifier = Modifier.padding(Dimensions.Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.add_account_advanced_options),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(
                        visible = showAdvancedOptions,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = Dimensions.Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                        ) {
                            Text(
                                text = stringResource(R.string.add_account_algorithm_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                            ) {
                                OtpAlgorithm.entries.forEach { algorithm ->
                                    FilterChip(
                                        selected = selectedAlgorithm == algorithm,
                                        onClick = {
                                            appHaptics.click()
                                            selectedAlgorithm = algorithm
                                        },
                                        label = { Text(algorithm.name, style = MaterialTheme.typography.labelMedium) }
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.add_account_digits_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                            ) {
                                listOf(6, 8).forEach { digits ->
                                    FilterChip(
                                        selected = selectedDigits == digits,
                                        onClick = {
                                            appHaptics.click()
                                            selectedDigits = digits
                                        },
                                        label = {
                                            Text(
                                                stringResource(R.string.add_account_digits_format, digits),
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Botones de acción simétricos (Directiva 14 & 23)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón "Cerrar" a la izquierda
                TextButton(
                    onClick = onDismiss,
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

                // Botón "Guardar en bóveda" a la derecha con animación de éxito
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
                        onDismiss()
                    },
                    enabled = isFormValid,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
