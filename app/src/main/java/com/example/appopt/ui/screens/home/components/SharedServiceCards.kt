package com.example.appopt.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.example.appopt.R
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.domain.model.OutgoingShareGrant
import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.ui.components.CircularTimeProgress
import com.example.appopt.ui.components.ServiceBrandAvatar
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.AccessibilityUtils
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tarjeta visual para un servicio compartido conmigo (Zero-Secret) en la pantalla de inicio (Directivas 1, 4, 11, 29 y 31).
 *
 * Mantiene la misma geometría, elevación perimetral, soporte de modo privacidad ([hideCodes]) y copia al toque
 * que [com.example.appopt.ui.components.OtpCodeCard], sin almacenar ni exponer jamás la semilla Base32.
 *
 * @param service Metadatos del servicio compartido entrante.
 * @param activeCode Código OTP actual de 6/8 dígitos (obtenido en línea o del lote temporal cifrado).
 * @param hideCodes Bandera del modo privacidad de la cabecera principal.
 * @param hasValidBatch Indica si existe un lote precalculado vigente en el almacén local.
 * @param isItemLoading Indica si hay una petición en curso para esta tarjeta.
 * @param onCopyCode Callback invocado al pulsar sobre el código numérico para copiarlo al portapapeles.
 * @param onRequestAction Callback para solicitar el código en línea o descargar/renovar el lote temporal.
 */
@Composable
fun IncomingSharedOtpCard(
    service: IncomingSharedService,
    activeCode: String?,
    hideCodes: Boolean,
    hasValidBatch: Boolean,
    isItemLoading: Boolean,
    onCopyCode: (String) -> Unit,
    onRequestAction: () -> Unit
) {
    val appHaptics = rememberAppHaptics()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(Motion.Duration.FEEDBACK_TOAST.toLong().milliseconds)
            copied = false
        }
    }

    val formattedCode = remember(activeCode) {
        val raw = activeCode.orEmpty()
        when (raw.length) {
            6 -> "${raw.substring(0, 3)} ${raw.substring(3)}"
            8 -> "${raw.substring(0, 4)} ${raw.substring(4)}"
            else -> raw
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.cardDefault),
        border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    ServiceBrandAvatar(
                        issuer = service.issuer,
                        size = Dimensions.IconSize.hero
                    )
                    Spacer(modifier = Modifier.width(Dimensions.Spacing.md))
                    Column {
                        Text(
                            text = service.issuer.ifEmpty { stringResource(R.string.home_default_issuer) },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (service.accountName.isNotBlank()) {
                            Text(
                                text = service.accountName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = stringResource(R.string.settings_shared_owner_format, service.ownerEmail),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !hideCodes && !activeCode.isNullOrBlank(),
                enter = expandVertically(animationSpec = Motion.Spec.privacyCollapseSpec()) +
                        fadeIn(animationSpec = Motion.Spec.quickFadeSpec()),
                exit = shrinkVertically(animationSpec = Motion.Spec.privacyCollapseSpec()) +
                        fadeOut(animationSpec = Motion.Spec.quickFadeSpec())
            ) {
                val safeCode = activeCode.orEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimensions.Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                            .clickable {
                                if (safeCode.isNotBlank()) {
                                    appHaptics.copy()
                                    onCopyCode(safeCode)
                                    copied = true
                                }
                            }
                            .padding(Dimensions.Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formattedCode,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics {
                                contentDescription = AccessibilityUtils.toAccessibleSpokenOtp(safeCode)
                            }
                        )
                        if (copied) {
                            Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.action_copied),
                                tint = SafeGreen,
                                modifier = Modifier.size(Dimensions.IconSize.medium)
                            )
                        }
                    }

                    CircularTimeProgress(period = service.period)
                }
            }

            val buttonLabelRes = when {
                service.accessMode == ShareAccessMode.ONLINE_ONLY -> R.string.settings_shared_tap_to_fetch
                hasValidBatch -> R.string.settings_shared_batch_ready
                else -> R.string.settings_shared_download_batch_action
            }

            OutlinedButton(
                onClick = {
                    appHaptics.click()
                    onRequestAction()
                },
                enabled = !isItemLoading,
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(buttonLabelRes),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * Tarjeta visual para un servicio que el usuario propietario ha compartido con un tercero,
 * permitiendo revocar el acceso en tiempo real con un solo toque desde el inicio (Directiva 31).
 *
 * @param grant Permiso activo otorgado por el propietario.
 * @param onRevoke Callback invocado al presionar el botón «Revocar».
 */
@Composable
fun OutgoingShareGrantCard(
    grant: OutgoingShareGrant,
    onRevoke: () -> Unit
) {
    val appHaptics = rememberAppHaptics()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.cardDefault),
        border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                ServiceBrandAvatar(
                    issuer = grant.issuer,
                    size = Dimensions.IconSize.large
                )
                Spacer(modifier = Modifier.width(Dimensions.Spacing.md))
                Column {
                    Text(
                        text = grant.issuer.ifEmpty { stringResource(R.string.home_default_issuer) },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_shared_guest_format, grant.guestEmail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val modeBadge = if (grant.accessMode == ShareAccessMode.OFFLINE_BATCH) {
                        stringResource(R.string.share_mode_badge_batch, grant.batchHours)
                    } else {
                        stringResource(R.string.share_mode_badge_online)
                    }
                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                    Text(
                        text = modeBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            TextButton(
                onClick = {
                    appHaptics.click()
                    onRevoke()
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
