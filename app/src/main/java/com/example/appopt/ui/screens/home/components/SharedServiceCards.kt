package com.example.appopt.ui.screens.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.appopt.R
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.domain.model.OutgoingShareGrant
import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.domain.model.ShareGrantStatus
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.components.CircularTimeProgress
import com.example.appopt.ui.components.ModalTone
import com.example.appopt.ui.components.ServiceBrandAvatar
import com.example.appopt.ui.navigation.NavigationOriginTracker
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.WarningOrange
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.AccessibilityUtils
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tarjeta visual para representar un servicio compartido recibido (Zero-Secret) en la pantalla de inicio,
 * homologada en geometría, sombras, elevación animada, tipografía, animación de privacidad y respuesta háptica con [com.example.appopt.ui.components.OtpCodeCard] (Directivas 1, 3, 4, 10, 11 y 31).
 *
 * Características de diseño e interacción:
 * - Geometría perimetral, bordes y elevación animada [Dimensions.Elevation.cardDefault] idénticos a las tarjetas de cuentas propias.
 * - Cabecera con avatar de marca ([ServiceBrandAvatar]), título del emisor, subtítulo de cuenta/propietario con icono compartido e indicador de favorito.
 * - Modo de privacidad (`hideCodes`): colapso y despliegue animado mediante [Motion.Spec.privacyCollapseSpec].
 * - Pulsar directamente sobre los dígitos copia el código al portapapeles si está activo, o solicita la obtención del código si aún no está cargado.
 * - Junto a los dígitos: badge estilizado tipo píldora indicando el avance de códigos temporales ([liveWindowProgress]) y confirmación animada de copiado.
 * - En el extremo derecho: temporizador circular ([CircularTimeProgress]) si el código está activo, o botón de revelación ([Icons.Outlined.Visibility]) para obtenerlo y revelarlo.
 *
 * @param service Metadatos del servicio compartido entrante.
 * @param activeCode Código OTP actual de 6/8 dígitos (obtenido en línea o del lote temporal cifrado).
 * @param hideCodes Bandera del modo privacidad de la cabecera principal.
 * @param hasValidBatch Indica si existe un lote precalculado vigente en el almacén local.
 * @param isItemLoading Indica si hay una petición en curso para esta tarjeta.
 * @param modifier Modificador de diseño Compose opcional.
 * @param onCopyCode Callback invocado al pulsar sobre el código numérico para copiarlo al portapapeles.
 * @param onRequestAction Callback para solicitar el código en línea o descargar/renovar el lote temporal.
 * @param onAcceptInvitation Callback invocado cuando el invitado acepta explícitamente la invitación.
 * @param onRejectOrLeave Callback invocado cuando el invitado rechaza la invitación.
 * @param onToggleFavorite Callback para marcar o desmarcar el servicio compartido como favorito.
 * @param onCardClick Callback invocado al tocar la tarjeta aceptada para abrir el diálogo de detalles.
 * @param liveWindowProgress Par opcional `(pasoActual, totalPasos)` que indica el código vigente dentro de la brecha de 3 códigos.
 */
@Composable
fun IncomingSharedOtpCard(
    service: IncomingSharedService,
    activeCode: String?,
    hideCodes: Boolean,
    hasValidBatch: Boolean,
    isItemLoading: Boolean,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    onCopyCode: (String) -> Unit,
    onRequestAction: () -> Unit,
    onAcceptInvitation: () -> Unit,
    onRejectOrLeave: () -> Unit,
    onToggleFavorite: (String) -> Unit = {},
    onCardClick: () -> Unit = {},
    liveWindowProgress: Pair<Int, Int>? = null
) {
    com.example.appopt.ui.components.OtpCodeCard(
        service = service,
        activeCode = activeCode,
        hideCodes = hideCodes,
        modifier = modifier,
        isDragging = isDragging,
        liveWindowProgress = liveWindowProgress,
        isItemLoading = isItemLoading,
        onCopyCode = onCopyCode,
        onRequestAction = onRequestAction,
        onToggleFavorite = onToggleFavorite,
        onCardClick = onCardClick,
        onAcceptInvitation = onAcceptInvitation,
        onRejectInvitation = onRejectOrLeave
    )
}

/**
 * Sub-estados del diálogo modal de detalles para un servicio compartido recibido.
 */
private enum class IncomingSharedDialogStep {
    DETAILS,
    CONFIRM_LEAVE
}

/**
 * Diálogo modal unificado (Directiva 14) para consultar detalles de un servicio compartido recibido,
 * renovar su lote sin conexión (si aplica) o quitarlo de la lista con confirmación destructiva.
 *
 * @param service Servicio compartido recibido.
 * @param hasValidBatch Indica si dispone de un lote sin conexión activo.
 * @param isLoading Indica si hay una operación en progreso.
 * @param onDismiss Callback para cerrar el diálogo.
 * @param onRenewOfflineBatch Callback para descargar o renovar el lote sin conexión.
 * @param onConfirmLeave Callback para quitar definitivamente el servicio compartido de la lista.
 */
@Composable
fun IncomingSharedServiceDetailsDialog(
    service: IncomingSharedService,
    hasValidBatch: Boolean,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRenewOfflineBatch: () -> Unit,
    onConfirmLeave: () -> Unit
) {
    val appHaptics = rememberAppHaptics()
    var step by remember { mutableStateOf(IncomingSharedDialogStep.DETAILS) }

    val modeLabel = when (service.accessMode) {
        ShareAccessMode.OFFLINE_BATCH -> stringResource(R.string.share_mode_badge_batch, service.batchHours)
        ShareAccessMode.SINGLE_USE -> stringResource(R.string.share_mode_badge_single_use)
        ShareAccessMode.ONLINE_ONLY -> stringResource(R.string.share_mode_badge_online)
    }

    AppModalDialog(
        onDismissRequest = onDismiss,
        onBackStep = {
            if (step == IncomingSharedDialogStep.CONFIRM_LEAVE) {
                step = IncomingSharedDialogStep.DETAILS
                true
            } else {
                false
            }
        },
        tone = if (step == IncomingSharedDialogStep.CONFIRM_LEAVE) ModalTone.DESTRUCTIVE else ModalTone.STANDARD
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { Motion.Spec.dialogStepContentTransform() },
            label = "IncomingSharedDetailsStepTransition"
        ) { targetStep ->
            when (targetStep) {
                IncomingSharedDialogStep.DETAILS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        // 1. Cabecera fija superior
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ServiceBrandAvatar(
                                issuer = service.issuer,
                                size = Dimensions.IconSize.hero
                            )
                            Spacer(modifier = Modifier.width(Dimensions.Spacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = service.issuer.ifEmpty { stringResource(R.string.home_default_issuer) },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (service.accountName.isNotBlank()) {
                                    Text(
                                        text = service.accountName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 2. Cuerpo central scrolleable aislado
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(Dimensions.Spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_shared_owner_format, service.ownerEmail),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.share_status_accepted_badge, modeLabel),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SafeGreen
                                    )
                                }
                            }

                            if (service.accessMode == ShareAccessMode.OFFLINE_BATCH) {
                                OutlinedButton(
                                    onClick = {
                                        appHaptics.click()
                                        onRenewOfflineBatch()
                                        onDismiss()
                                    },
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (hasValidBatch) R.string.settings_shared_batch_ready
                                            else R.string.settings_shared_download_batch_action
                                        ),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }

                        // 3. Pie fijo inferior con simetría 50/50
                        AppDialogActionButtons(
                            dismissText = stringResource(R.string.action_close),
                            confirmText = stringResource(R.string.settings_shared_leave_action),
                            onDismiss = onDismiss,
                            onConfirm = {
                                step = IncomingSharedDialogStep.CONFIRM_LEAVE
                            },
                            isDestructive = true
                        )
                    }
                }

                IncomingSharedDialogStep.CONFIRM_LEAVE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        // 1. Cabecera fija superior destructiva
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(Dimensions.IconSize.hero)
                        )
                        Text(
                            text = stringResource(R.string.settings_shared_leave_confirm_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )

                        // 2. Cuerpo central scrolleable aislado
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.settings_shared_leave_confirm_desc, service.ownerEmail),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        // 3. Pie fijo inferior con simetría 50/50
                        AppDialogActionButtons(
                            dismissText = stringResource(R.string.settings_drive_details_back),
                            confirmText = stringResource(R.string.settings_shared_leave_action),
                            onDismiss = {
                                step = IncomingSharedDialogStep.DETAILS
                            },
                            onConfirm = {
                                onConfirmLeave()
                                onDismiss()
                            },
                            isLoading = isLoading,
                            isDestructive = true
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta visual para un servicio que el usuario propietario ha compartido con un tercero,
 * mostrando si la invitación sigue pendiente o activa y permitiendo revocar en tiempo real (Directiva 31).
 *
 * @param grant Permiso o invitación otorgado por el propietario.
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
                    val modeBadge = when (grant.accessMode) {
                        ShareAccessMode.OFFLINE_BATCH ->
                            stringResource(R.string.share_mode_badge_batch, grant.batchHours)
                        ShareAccessMode.SINGLE_USE ->
                            stringResource(R.string.share_mode_badge_single_use)
                        ShareAccessMode.ONLINE_ONLY ->
                            stringResource(R.string.share_mode_badge_online)
                    }
                    val isPending = grant.status == ShareGrantStatus.PENDING
                    val statusBadge = if (isPending) {
                        stringResource(R.string.share_status_pending_badge, modeBadge)
                    } else {
                        stringResource(R.string.share_status_accepted_badge, modeBadge)
                    }
                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                    Text(
                        text = statusBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPending) MaterialTheme.colorScheme.tertiary else SafeGreen
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

