package com.example.appopt.ui.components.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.domain.model.ShareBatchDuration
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.isDarkTheme
import com.example.appopt.ui.theme.modalCardContainerColor
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Paso modal dentro de [com.example.appopt.ui.components.AccountDetailsDialog] para compartir el acceso
 * a un servicio TOTP sin exponer la clave secreta mediante arquitectura Zero-Secret (Directivas 14, 22, 23 y 31).
 *
 * Estructura Tripartita Inmutable (Directiva 14):
 * 1. Cabecera fija superior con título estandarizado.
 * 2. Cuerpo central scrolleable aislado (`weight(1f, fill = false)`).
 * 3. Pie fijo inferior con botones simétricos 50/50 («Volver» y «Enviar invitación» animado).
 *
 * @param isDriveConnected Indica si existe una cuenta de Google vinculada en Ajustes.
 * @param connectedUserEmail Correo electrónico de la cuenta de Google vinculada actualmente para prevenir auto-envíos.
 * @param errorMessage Mensaje de error amigable si ocurre un fallo durante la comunicación con el servidor.
 * @param onShareService Acción asíncrona para registrar la invitación en el servidor (`guestEmail`, `accessMode`, `batchDuration`).
 * @param onShareSuccess Callback invocado tras completarse con éxito la animación de confirmación para cerrar el diálogo.
 * @param onBack Callback para regresar a la vista principal de detalles.
 * @param modifier Modificador de diseño Compose.
 */
@Composable
fun AccountDetailsShareStep(
    isDriveConnected: Boolean,
    connectedUserEmail: String?,
    errorMessage: String?,
    onShareService: suspend (String, ShareAccessMode, ShareBatchDuration) -> Boolean,
    onShareSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    var guestEmail by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(ShareAccessMode.ONLINE_ONLY) }
    var selectedDuration by remember { mutableStateOf(ShareBatchDuration.TWENTY_FOUR_HOURS) }

    val cleanEmail = guestEmail.trim()
    val isFormatValid = cleanEmail.contains("@") && cleanEmail.contains(".") && cleanEmail.length >= 5
    val isSelfEmail = !connectedUserEmail.isNullOrBlank() && cleanEmail.equals(connectedUserEmail.trim(), ignoreCase = true)

    val activeErrorMessage = when {
        isSelfEmail -> stringResource(R.string.share_error_self_email)
        else -> errorMessage
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimensions.Spacing.lg)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        // 1. Cabecera fija superior
        Text(
            text = stringResource(R.string.share_modal_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        // 2. Cuerpo central scrolleable aislado
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            if (!isDriveConnected) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    color = modalCardContainerColor(),
                    border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = stringResource(R.string.share_requires_connected_account_msg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Dimensions.Spacing.md)
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.share_modal_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = guestEmail,
                    onValueChange = { guestEmail = it },
                    label = { Text(text = stringResource(R.string.share_guest_email_label)) },
                    placeholder = { Text(text = stringResource(R.string.share_guest_email_placeholder)) },
                    singleLine = true,
                    isError = isSelfEmail,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    modifier = Modifier.fillMaxWidth()
                )

                // Mensaje de error inmediato (cuenta propia o fallo de conexión)
                AnimatedVisibility(
                    visible = !activeErrorMessage.isNullOrBlank(),
                    enter = fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) + expandVertically(),
                    exit = fadeOut(animationSpec = Motion.Spec.quickFadeSpec()) + shrinkVertically()
                ) {
                    Text(
                        text = activeErrorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Selector de modo de acceso (Online vs Lote Offline)
                Text(
                    text = stringResource(R.string.share_mode_section_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                ShareModeOptionCard(
                    title = stringResource(R.string.share_mode_online_title),
                    description = stringResource(R.string.share_mode_online_desc),
                    selected = selectedMode == ShareAccessMode.ONLINE_ONLY,
                    onClick = {
                        appHaptics.click()
                        selectedMode = ShareAccessMode.ONLINE_ONLY
                    }
                )

                ShareModeOptionCard(
                    title = stringResource(R.string.share_mode_single_use_title),
                    description = stringResource(R.string.share_mode_single_use_desc),
                    selected = selectedMode == ShareAccessMode.SINGLE_USE,
                    onClick = {
                        appHaptics.click()
                        selectedMode = ShareAccessMode.SINGLE_USE
                    }
                )

                ShareModeOptionCard(
                    title = stringResource(R.string.share_mode_offline_batch_title),
                    description = stringResource(R.string.share_mode_offline_batch_desc),
                    selected = selectedMode == ShareAccessMode.OFFLINE_BATCH,
                    onClick = {
                        appHaptics.click()
                        selectedMode = ShareAccessMode.OFFLINE_BATCH
                    }
                )

                AnimatedVisibility(
                    visible = selectedMode == ShareAccessMode.OFFLINE_BATCH,
                    enter = fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) + expandVertically(),
                    exit = fadeOut(animationSpec = Motion.Spec.quickFadeSpec()) + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)) {
                        Text(
                            text = stringResource(R.string.share_batch_duration_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                        ) {
                            ShareBatchDuration.entries.forEach { duration ->
                                val labelRes = when (duration) {
                                    ShareBatchDuration.ONE_HOUR -> R.string.share_batch_1h
                                    ShareBatchDuration.SIX_HOURS -> R.string.share_batch_6h
                                    ShareBatchDuration.TWENTY_FOUR_HOURS -> R.string.share_batch_24h
                                }
                                FilterChip(
                                    selected = selectedDuration == duration,
                                    onClick = {
                                        appHaptics.click()
                                        selectedDuration = duration
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(labelRes),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Pie fijo inferior con simetría 50/50 y botón animado
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.none),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
            ) {
                Text(
                    text = stringResource(R.string.settings_drive_details_back),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center
                )
            }

            AppAnimatedButton(
                text = stringResource(R.string.share_confirm_action),
                onClick = {
                    if (isSelfEmail) return@AppAnimatedButton false
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onShareService(cleanEmail, selectedMode, selectedDuration)
                },
                onActionConfirmed = onShareSuccess,
                enabled = isDriveConnected && isFormatValid && !isSelfEmail,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Tarjeta seleccionable para elegir el modo de compartición de un servicio.
 */
@Composable
private fun ShareModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.isDarkTheme
    val cardColor = if (isDark) {
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.White
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = cardColor,
        border = BorderStroke(
            if (selected) Dimensions.Stroke.regular else Dimensions.Stroke.thin,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
