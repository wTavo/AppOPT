package com.example.appopt.ui.components.details

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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.appopt.R
import com.example.appopt.domain.model.OutgoingShareGrant
import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.domain.model.ShareBatchDuration
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Paso modal dentro de [com.example.appopt.ui.components.AccountDetailsDialog] para compartir el acceso
 * a un servicio TOTP sin exponer la clave secreta y revocar invitados en tiempo real (Directivas 14, 29 y 31).
 *
 * Estructura Tripartita Inmutable (Directiva 14):
 * 1. Cabecera fija superior con título estandarizado.
 * 2. Cuerpo central scrolleable aislado (`weight(1f, fill = false)`).
 * 3. Pie fijo inferior con botones simétricos 50/50 («Volver» y «Compartir acceso»).
 *
 * @param activeGrants Lista de permisos activos otorgados para este servicio.
 * @param isSharing Bandera de carga asíncrona durante el registro del permiso.
 * @param errorMessage Mensaje de error amigable si ocurre un fallo.
 * @param onShareService Callback invocado al confirmar la compartición `(guestEmail, accessMode, batchDuration)`.
 * @param onRevokeGrant Callback invocado al revocar inmediatamente el acceso de un invitado `(grantId)`.
 * @param onBack Callback para regresar a la vista principal de detalles.
 */
@Composable
fun AccountDetailsShareStep(
    activeGrants: List<OutgoingShareGrant>,
    isSharing: Boolean,
    errorMessage: String?,
    onShareService: (String, ShareAccessMode, ShareBatchDuration) -> Unit,
    onRevokeGrant: (String) -> Unit,
    onBack: () -> Unit
) {
    val appHaptics = rememberAppHaptics()
    var guestEmail by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(ShareAccessMode.ONLINE_ONLY) }
    var selectedDuration by remember { mutableStateOf(ShareBatchDuration.TWENTY_FOUR_HOURS) }

    val cleanEmail = guestEmail.trim()
    val isEmailValid = cleanEmail.contains("@") && cleanEmail.contains(".") && cleanEmail.length >= 5

    Column(
        modifier = Modifier
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
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
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                modifier = Modifier.fillMaxWidth()
            )

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

            // Mensaje de error animado sin saltos bruscos
            AnimatedVisibility(
                visible = !errorMessage.isNullOrBlank(),
                enter = fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) + expandVertically(),
                exit = fadeOut(animationSpec = Motion.Spec.quickFadeSpec()) + shrinkVertically()
            ) {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Lista de invitados activos con opción de revocación inmediata
            if (activeGrants.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.share_active_grants_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)) {
                    activeGrants.forEach { grant ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = Dimensions.Spacing.md,
                                        vertical = Dimensions.Spacing.sm
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = grant.guestEmail,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val modeText = if (grant.accessMode == ShareAccessMode.OFFLINE_BATCH) {
                                        stringResource(R.string.share_mode_badge_batch, grant.batchHours)
                                    } else {
                                        stringResource(R.string.share_mode_badge_online)
                                    }
                                    Text(
                                        text = modeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        appHaptics.click()
                                        onRevokeGrant(grant.grantId)
                                    }
                                ) {
                                    Text(
                                        text = stringResource(R.string.share_revoke_action),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = UrgentRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Pie fijo inferior con simetría 50/50
        AppDialogActionButtons(
            dismissText = stringResource(R.string.settings_drive_details_back),
            confirmText = stringResource(R.string.share_confirm_action),
            onDismiss = onBack,
            onConfirm = {
                onShareService(cleanEmail, selectedMode, selectedDuration)
            },
            confirmEnabled = isEmailValid && !isSharing,
            isLoading = isSharing
        )
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
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
