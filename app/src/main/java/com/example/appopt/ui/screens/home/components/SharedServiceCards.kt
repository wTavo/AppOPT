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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Mail
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
 * - Cabecera con avatar de marca ([ServiceBrandAvatar]), título del emisor, subtítulo de cuenta/propietario y botón de acción superior.
 * - Modo de privacidad (`hideCodes`): colapso y despliegue animado mediante [Motion.Spec.privacyCollapseSpec].
 * - Pulsar directamente sobre los dígitos copia el código al portapapeles si está activo, o solicita la obtención del código si aún no está cargado.
 * - En el extremo derecho: temporizador circular ([CircularTimeProgress]) si el código está activo, o botón de refresco ([Icons.Filled.Refresh]) para obtenerlo.
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
    onCopyCode: (String) -> Unit,
    onRequestAction: () -> Unit,
    onAcceptInvitation: () -> Unit,
    onRejectOrLeave: () -> Unit,
    onCardClick: () -> Unit = {},
    liveWindowProgress: Pair<Int, Int>? = null
) {
    val appHaptics = rememberAppHaptics()
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    var copied by remember { mutableStateOf(false) }
    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val isPending = service.status == ShareGrantStatus.PENDING

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

    val cardElevation by animateDpAsState(
        targetValue = Dimensions.Elevation.cardDefault,
        animationSpec = Motion.Spec.quickFadeSpec(),
        label = "shared_card_elevation"
    )

    val outlineBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val cardBorder = remember(outlineBorderColor) {
        BorderStroke(Dimensions.Stroke.thin, outlineBorderColor)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .onPlaced { cardCoordinates = it }
            .clickable(
                enabled = !isPending,
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    NavigationOriginTracker.updateModalOrigin(cardCoordinates)
                    appHaptics.click()
                    onCardClick()
                }
            )
            .semantics {
                contentDescription = AccessibilityUtils.buildAccountCardContentDescription(
                    context = context,
                    issuer = service.issuer,
                    accountName = service.accountName.ifBlank { service.ownerEmail },
                    code = activeCode.orEmpty(),
                    isFavorite = false
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = cardElevation
        ),
        border = cardBorder,
        shape = RoundedCornerShape(Dimensions.CornerRadius.large)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.lg)
        ) {
            // Cabecera: Avatar de Marca, Nombres y Botón de Acción/Estado Compartido
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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitleText = when {
                            isPending -> stringResource(R.string.settings_shared_invitation_from_format, service.ownerEmail)
                            liveWindowProgress != null -> {
                                val base = service.accountName.ifBlank { service.ownerEmail }
                                "$base · ${stringResource(R.string.settings_shared_live_window_progress, liveWindowProgress.first, liveWindowProgress.second)}"
                            }
                            service.accountName.isNotBlank() -> service.accountName
                            else -> stringResource(R.string.settings_shared_owner_format, service.ownerEmail)
                        }
                        if (subtitleText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        if (!isPending) {
                            NavigationOriginTracker.updateModalOrigin(cardCoordinates)
                            appHaptics.click()
                            onCardClick()
                        }
                    },
                    modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                ) {
                    Icon(
                        imageVector = if (isPending) Icons.Outlined.Mail else Icons.Outlined.Group,
                        contentDescription = stringResource(R.string.settings_shared_tab_incoming),
                        tint = if (isPending) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isPending) {
                Spacer(modifier = Modifier.height(Dimensions.Spacing.sm))
                Text(
                    text = stringResource(R.string.settings_shared_invitation_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Dimensions.Spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                ) {
                    OutlinedButton(
                        onClick = {
                            appHaptics.click()
                            onRejectOrLeave()
                        },
                        enabled = !isItemLoading,
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_shared_reject_action),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            appHaptics.click()
                            onAcceptInvitation()
                        },
                        enabled = !isItemLoading,
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_shared_accept_action),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            } else {
                // Modo de privacidad: despliegue animado idéntico a OtpCodeCard
                AnimatedVisibility(
                    visible = !hideCodes,
                    enter = expandVertically(animationSpec = Motion.Spec.privacyCollapseSpec()) +
                            fadeIn(animationSpec = Motion.Spec.quickFadeSpec()),
                    exit = shrinkVertically(animationSpec = Motion.Spec.privacyCollapseSpec()) +
                            fadeOut(animationSpec = Motion.Spec.quickFadeSpec())
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(Dimensions.Spacing.md))

                        // Cuerpo: Dígitos OTP y Temporizador / Acción de solicitud
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val isCodePresent = !activeCode.isNullOrBlank()
                            val displayCode = if (isCodePresent) formattedCode else "--- ---"

                            val fetchDescription = stringResource(R.string.settings_shared_tap_to_fetch)

                            // Zona táctil de los dígitos: toque directo para copiar o solicitar
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                                    .clickable {
                                        if (!activeCode.isNullOrBlank()) {
                                            appHaptics.copy()
                                            onCopyCode(activeCode)
                                            copied = true
                                        } else {
                                            appHaptics.click()
                                            onRequestAction()
                                        }
                                    }
                                    .padding(Dimensions.Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = displayCode,
                                    style = MaterialTheme.typography.displayMedium,
                                    color = if (isCodePresent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.semantics {
                                        if (!activeCode.isNullOrBlank()) {
                                            contentDescription = AccessibilityUtils.toAccessibleSpokenOtp(activeCode)
                                        } else {
                                            contentDescription = fetchDescription
                                        }
                                    }
                                )

                                if (copied) {
                                    Row {
                                        Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = stringResource(R.string.action_copied),
                                            tint = SafeGreen,
                                            modifier = Modifier.size(Dimensions.IconSize.medium)
                                        )
                                    }
                                }
                            }

                            if (isItemLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Dimensions.IconSize.medium),
                                    strokeWidth = Dimensions.Stroke.regular,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else if (isCodePresent) {
                                CircularTimeProgress(
                                    period = service.period
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        appHaptics.click()
                                        onRequestAction()
                                    },
                                    modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = stringResource(R.string.settings_shared_tap_to_fetch),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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

