package com.example.appopt.ui.screens.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.R
import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Pantalla de registro manual de una cuenta TOTP con escala tipográfica estandarizada y animaciones centralizadas.
 *
 * Características de diseño:
 * - Campos obligatorios prioritarios: Servicio/Emisor y Clave Secreta Base32.
 * - Campo opcional: Nombre de cuenta / usuario.
 * - Opciones avanzadas (Algoritmo HMAC y Dígitos) agrupadas en un panel desplegable colapsado por defecto.
 * - Indicador de validación de clave Base32 en tiempo real.
 * - Botón de guardado animado reutilizable ([AppAnimatedButton]): transiciona a verde con palomita al confirmar.
 *
 * @param viewModel ViewModel encargado de la lógica y validación criptográfica del formulario.
 * @param onNavigateBack Callback para regresar a la pantalla anterior.
 * @param modifier Modificador de layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    viewModel: AddAccountViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appHaptics = rememberAppHaptics()
    var showAdvancedOptions by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_account_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Dimensions.Spacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.lg)
        ) {
            Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))

            // 1. Emisor / Servicio (Obligatorio)
            OutlinedTextField(
                value = uiState.issuer,
                onValueChange = viewModel::onIssuerChanged,
                label = { Text(stringResource(R.string.add_account_issuer_label), style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Business, contentDescription = null)
                },
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                modifier = Modifier.fillMaxWidth()
            )

            // 2. Clave Secreta Base32 (Obligatoria)
            OutlinedTextField(
                value = uiState.secret,
                onValueChange = viewModel::onSecretChanged,
                label = { Text(stringResource(R.string.add_account_secret_label), style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Key, contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.secret.isNotEmpty()) {
                        if (uiState.isSecretValid) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = stringResource(R.string.add_account_valid_indicator),
                                tint = SafeGreen
                            )
                        } else {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = stringResource(R.string.add_account_invalid_indicator),
                                tint = UrgentRed
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                modifier = Modifier.fillMaxWidth()
            )

            // 3. Nombre de Cuenta / Usuario (Opcional)
            OutlinedTextField(
                value = uiState.accountName,
                onValueChange = viewModel::onAccountNameChanged,
                label = { Text(stringResource(R.string.add_account_name_label), style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.PersonOutline, contentDescription = null)
                },
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                modifier = Modifier.fillMaxWidth()
            )

            // Sección colapsable de Opciones Avanzadas
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
                    .clickable {
                        appHaptics.click()
                        showAdvancedOptions = !showAdvancedOptions
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(Dimensions.Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)) {
                            Text(
                                text = stringResource(R.string.add_account_advanced_options),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.add_account_advanced_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = if (showAdvancedOptions) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null
                        )
                    }

                    AnimatedVisibility(
                        visible = showAdvancedOptions,
                        enter = fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) + expandVertically(),
                        exit = fadeOut(animationSpec = Motion.Spec.quickFadeSpec()) + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = Dimensions.Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                        ) {
                            // Algoritmo HMAC
                            Text(
                                text = stringResource(R.string.add_account_algorithm_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                            ) {
                                OtpAlgorithm.entries.forEach { algo ->
                                    FilterChip(
                                        selected = uiState.algorithm == algo,
                                        onClick = { viewModel.onAlgorithmChanged(algo) },
                                        label = { Text(algo.standardName, style = MaterialTheme.typography.labelMedium) }
                                    )
                                }
                            }

                            // Cantidad de dígitos
                            Text(
                                text = stringResource(R.string.add_account_digits_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                            ) {
                                listOf(6, 8).forEach { digit ->
                                    FilterChip(
                                        selected = uiState.digits == digit,
                                        onClick = { viewModel.onDigitsChanged(digit) },
                                        label = { Text(stringResource(R.string.add_account_digits_format, digit), style = MaterialTheme.typography.labelMedium) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Mensaje de error si la validación falla
            if (uiState.errorMessageResId != null) {
                Text(
                    text = stringResource(uiState.errorMessageResId!!),
                    color = UrgentRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(Dimensions.Spacing.sm))

            // Botón de Guardado animado reutilizable
            AppAnimatedButton(
                text = stringResource(R.string.action_save),
                enabled = uiState.issuer.isNotBlank() && uiState.isSecretValid,
                onClick = {
                    viewModel.saveAccount()
                    true
                },
                onActionConfirmed = onNavigateBack
            )

            Spacer(modifier = Modifier.height(Dimensions.Spacing.sm))
        }
    }
}
