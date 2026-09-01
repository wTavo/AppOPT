package com.example.appopt.ui.screens.add

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.R
import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import kotlinx.coroutines.delay

/**
 * Pantalla de registro manual de una cuenta TOTP con escala tipográfica estandarizada.
 *
 * Características de diseño:
 * - Campos obligatorios prioritarios: Servicio/Emisor y Clave Secreta Base32 (sin texto de ejemplo intrusivo).
 * - Campo opcional: Nombre de cuenta / correo.
 * - Opciones avanzadas (Algoritmo HMAC y Dígitos) agrupadas en un panel desplegable colapsado por defecto.
 * - Validación en tiempo real y vista previa del código OTP generado.
 * - Botón de guardado animado: al confirmarse el guardado, transiciona a verde y reemplaza el texto con una palomita de éxito.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    viewModel: AddAccountViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var isSavingSuccessful by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSavedSuccessfully) {
        if (uiState.isSavedSuccessfully) {
            isSavingSuccessful = true
            delay(750)
            onNavigateBack()
        }
    }

    val saveButtonColor by animateColorAsState(
        targetValue = if (isSavingSuccessful) SafeGreen else MaterialTheme.colorScheme.primary,
        animationSpec = tween(durationMillis = 300),
        label = "saveAccountButtonColor"
    )

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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Emisor / Servicio (Obligatorio)
            OutlinedTextField(
                value = uiState.issuer,
                onValueChange = viewModel::onIssuerChanged,
                label = { Text(stringResource(R.string.add_account_issuer_label)) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Business, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 2. Clave Secreta Base32 (Obligatoria)
            OutlinedTextField(
                value = uiState.secret,
                onValueChange = viewModel::onSecretChanged,
                label = { Text(stringResource(R.string.add_account_secret_label)) },
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
                modifier = Modifier.fillMaxWidth()
            )

            // 3. Nombre de Cuenta / Usuario (Opcional)
            OutlinedTextField(
                value = uiState.accountName,
                onValueChange = viewModel::onAccountNameChanged,
                label = { Text(stringResource(R.string.add_account_name_label)) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.PersonOutline, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Vista previa del código OTP generado en vivo
            AnimatedVisibility(visible = uiState.previewCode != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.add_account_preview_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.previewCode ?: "",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Sección colapsable de Opciones Avanzadas
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showAdvancedOptions = !showAdvancedOptions },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
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
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Algoritmo HMAC
                            Text(
                                text = stringResource(R.string.add_account_algorithm_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OtpAlgorithm.values().forEach { algo ->
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = UrgentRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botón de Guardado animado
            Button(
                onClick = { viewModel.saveAccount() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = saveButtonColor),
                enabled = (uiState.issuer.isNotBlank() && uiState.isSecretValid) || isSavingSuccessful
            ) {
                AnimatedContent(
                    targetState = isSavingSuccessful,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                    label = "saveAccountButtonContent"
                ) { saved ->
                    if (saved) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.action_save),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
