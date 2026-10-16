package com.example.appopt.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Group
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.appopt.R
import com.example.appopt.domain.model.AccountWithCode
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.model.ShareGrantStatus
import com.example.appopt.ui.navigation.NavigationOriginTracker
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.WarningOrange
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.AccessibilityUtils
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Componente base unificado para todas las tarjetas OTP de la aplicación (Directivas 1, 3, 4, 5, 10, 11 y 31).
 *
 * Centraliza la geometría perimetral, bordes, sombras dinámicas con elevación animada, ripple nativo,
 * avatar de marca, tipografía, animación de privacidad, confirmación de copiado y respuesta háptica.
 */
@Composable
private fun BaseOtpCard(
    issuer: String,
    subtitle: String,
    isFavorite: Boolean,
    hideCodes: Boolean,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    subtitleLeadingIcon: ImageVector? = null,
    accessibilityDescription: String,
    onCardClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    displayCode: String,
    rawCodeToCopy: String?,
    liveWindowProgress: Pair<Int, Int>? = null,
    onRequestAction: (() -> Unit)? = null,
    onCopyCode: (String) -> Unit,
    rightActionContent: @Composable () -> Unit,
    bottomContent: (@Composable () -> Unit)? = null
) {
    val appHaptics = rememberAppHaptics()
    val interactionSource = remember { MutableInteractionSource() }

    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(Motion.Duration.FEEDBACK_TOAST.toLong().milliseconds)
            copied = false
        }
    }

    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val cardElevation by animateDpAsState(
        targetValue = if (isDragging) Dimensions.Elevation.cardDragging else Dimensions.Elevation.cardDefault,
        animationSpec = Motion.Spec.quickFadeSpec(),
        label = "card_drag_elevation"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val cardBorder = remember(isDragging, primaryColor, outlineBorderColor) {
        if (isDragging) {
            BorderStroke(Dimensions.Stroke.regular, primaryColor)
        } else {
            BorderStroke(Dimensions.Stroke.thin, outlineBorderColor)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .onPlaced { cardCoordinates = it }
            .clickable(
                enabled = !isDragging,
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    NavigationOriginTracker.updateModalOrigin(cardCoordinates)
                    appHaptics.click()
                    onCardClick()
                }
            )
            .semantics {
                contentDescription = accessibilityDescription
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
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
            // Cabecera: Avatar de Marca, Nombres, Icono opcional y Botón de Favorito
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
                        issuer = issuer,
                        size = Dimensions.IconSize.hero
                    )

                    Spacer(modifier = Modifier.width(Dimensions.Spacing.md))

                    Column {
                        Text(
                            text = issuer.ifEmpty { stringResource(R.string.home_default_issuer) },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                            ) {
                                if (subtitleLeadingIcon != null) {
                                    Icon(
                                        imageVector = subtitleLeadingIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(Dimensions.IconSize.small)
                                    )
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = {
                        appHaptics.click()
                        onToggleFavorite()
                    },
                    modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = stringResource(R.string.action_favorite),
                        tint = if (isFavorite) WarningOrange else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (bottomContent != null) {
                Spacer(modifier = Modifier.height(Dimensions.Spacing.md))
                bottomContent()
            } else {
                // Modo de privacidad: despliegue animado de dígitos y temporizador
                AnimatedVisibility(
                    visible = !hideCodes,
                    enter = expandVertically(animationSpec = Motion.Spec.privacyCollapseSpec()) +
                            fadeIn(animationSpec = Motion.Spec.quickFadeSpec()),
                    exit = shrinkVertically(animationSpec = Motion.Spec.privacyCollapseSpec()) +
                            fadeOut(animationSpec = Motion.Spec.quickFadeSpec())
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(Dimensions.Spacing.md))

                        // Cuerpo: Dígitos OTP y Temporizador / Acción
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val isCodePresent = !rawCodeToCopy.isNullOrBlank()

                            // Zona táctil de los dígitos: toque directo para copiar o solicitar
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                                    .clickable {
                                        if (isCodePresent) {
                                            appHaptics.copy()
                                            onCopyCode(rawCodeToCopy)
                                            copied = true
                                        } else if (onRequestAction != null) {
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
                                        contentDescription = if (isCodePresent) {
                                            AccessibilityUtils.toAccessibleSpokenOtp(rawCodeToCopy)
                                        } else {
                                            "Toca para revelar el código"
                                        }
                                    }
                                )

                                if (liveWindowProgress != null && isCodePresent) {
                                    Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                                    Surface(
                                        shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = stringResource(
                                                R.string.settings_shared_live_window_progress,
                                                liveWindowProgress.first,
                                                liveWindowProgress.second
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(
                                                horizontal = Dimensions.Spacing.sm,
                                                vertical = Dimensions.Spacing.xs
                                            )
                                        )
                                    }
                                }

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

                            rightActionContent()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta visual para representar una cuenta 2FA propia y su código OTP actual con alto contraste,
 * tipografía escalable, ripple nativo y arrastre.
 *
 * @param accountWithCode Contenedor con la información de la cuenta y su código activo.
 * @param hideCodes Si es verdadero, oculta la sección de dígitos y temporizadores mediante animación fluida.
 * @param modifier Modificador opcional Compose.
 * @param isDragging Si es verdadero, resalta visualmente la elevación y bordes durante el arrastre.
 * @param onCardClick Callback al pulsar en la tarjeta para abrir el popup modal de detalles.
 * @param onCopyCode Callback invocado al pulsar sobre los dígitos para copiar el código al portapapeles.
 * @param onToggleFavorite Callback para alternar el estado de favorito.
 * @param onNextHotpCode Callback para avanzar el contador de una cuenta HOTP.
 */
@Composable
fun OtpCodeCard(
    accountWithCode: AccountWithCode,
    hideCodes: Boolean,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    onCardClick: () -> Unit,
    onCopyCode: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onNextHotpCode: (String) -> Unit
) {
    val account = accountWithCode.account
    val context = LocalContext.current
    val appHaptics = rememberAppHaptics()

    val formattedCode = remember(accountWithCode.code) {
        formatOtpDigits(accountWithCode.code)
    }

    BaseOtpCard(
        issuer = account.issuer,
        subtitle = account.accountName,
        isFavorite = account.isFavorite,
        hideCodes = hideCodes,
        modifier = modifier,
        isDragging = isDragging,
        subtitleLeadingIcon = null,
        accessibilityDescription = AccessibilityUtils.buildAccountCardContentDescription(
            context = context,
            issuer = account.issuer,
            accountName = account.accountName,
            code = accountWithCode.code,
            isFavorite = account.isFavorite
        ),
        onCardClick = onCardClick,
        onToggleFavorite = { onToggleFavorite(account.id) },
        displayCode = formattedCode,
        rawCodeToCopy = accountWithCode.code,
        onCopyCode = onCopyCode,
        rightActionContent = {
            if (account.type == OtpType.TOTP) {
                CircularTimeProgress(period = account.period)
            } else {
                IconButton(
                    onClick = {
                        appHaptics.click()
                        onNextHotpCode(account.id)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.home_next_hotp_code),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}

/**
 * Sobrecarga unificada de [OtpCodeCard] para representar un servicio compartido recibido (Zero-Secret).
 *
 * Comparte exactamente el 100% de la arquitectura visual, geometría perimetral, bordes,
 * elevaciones animadas, modo de privacidad y respuesta háptica que las cuentas propias (Directivas 1, 3, 4, 5 y 31).
 *
 * @param service Metadatos del servicio compartido entrante.
 * @param activeCode Código OTP actual (obtenido en línea o del lote temporal cifrado) o null si aún no se revela.
 * @param hideCodes Si es verdadero, oculta la sección de dígitos y temporizadores mediante animación fluida.
 * @param liveWindowProgress Par opcional `(pasoActual, totalPasos)` que indica el código vigente dentro de la brecha de 3 códigos.
 * @param isItemLoading Indica si hay una petición de red en curso para esta tarjeta.
 * @param modifier Modificador opcional Compose.
 * @param onCardClick Callback al pulsar en la tarjeta para abrir el diálogo de detalles del servicio compartido.
 * @param onCopyCode Callback invocado al pulsar sobre los dígitos para copiar el código al portapapeles.
 * @param onRequestAction Callback para solicitar/revelar el código en línea o descargar/renovar el lote.
 * @param onToggleFavorite Callback para alternar el estado de favorito.
 * @param onAcceptInvitation Callback opcional para invitaciones pendientes cuando el invitado acepta.
 * @param onRejectInvitation Callback opcional para invitaciones pendientes cuando el invitado rechaza.
 */
@Composable
fun OtpCodeCard(
    service: IncomingSharedService,
    activeCode: String?,
    hideCodes: Boolean,
    liveWindowProgress: Pair<Int, Int>?,
    isItemLoading: Boolean,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    onCardClick: () -> Unit,
    onCopyCode: (String) -> Unit,
    onRequestAction: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAcceptInvitation: (() -> Unit)? = null,
    onRejectInvitation: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val appHaptics = rememberAppHaptics()
    val isPending = service.status == ShareGrantStatus.PENDING
    val isCodePresent = !activeCode.isNullOrBlank()

    val formattedCode = remember(activeCode) {
        if (!activeCode.isNullOrBlank()) formatOtpDigits(activeCode) else "--- ---"
    }

    val subtitleText = when {
        isPending -> stringResource(R.string.settings_shared_invitation_from_format, service.ownerEmail)
        service.accountName.isNotBlank() -> {
            "${service.accountName} · ${stringResource(R.string.settings_shared_owner_format, service.ownerEmail)}"
        }
        else -> stringResource(R.string.settings_shared_owner_format, service.ownerEmail)
    }

    val accessibilityDesc = AccessibilityUtils.buildAccountCardContentDescription(
        context = context,
        issuer = service.issuer,
        accountName = service.accountName,
        code = activeCode ?: "",
        isFavorite = service.isFavorite
    )

    BaseOtpCard(
        issuer = service.issuer,
        subtitle = subtitleText,
        isFavorite = service.isFavorite,
        hideCodes = hideCodes,
        modifier = modifier,
        isDragging = isDragging,
        subtitleLeadingIcon = Icons.Outlined.Group,
        accessibilityDescription = accessibilityDesc,
        onCardClick = onCardClick,
        onToggleFavorite = { onToggleFavorite(service.grantId) },
        displayCode = formattedCode,
        rawCodeToCopy = activeCode,
        liveWindowProgress = liveWindowProgress,
        onRequestAction = onRequestAction,
        onCopyCode = onCopyCode,
        rightActionContent = {
            if (isItemLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimensions.IconSize.medium),
                    strokeWidth = Dimensions.Stroke.regular,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (isCodePresent) {
                CircularTimeProgress(period = service.period)
            } else {
                IconButton(
                    onClick = {
                        appHaptics.click()
                        onRequestAction()
                    },
                    modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = stringResource(R.string.settings_shared_tap_to_fetch),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        bottomContent = if (isPending && onAcceptInvitation != null && onRejectInvitation != null) {
            {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                ) {
                    OutlinedButton(
                        onClick = {
                            appHaptics.click()
                            onRejectInvitation()
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
            }
        } else null
    )
}

/**
 * Formatea una cadena numérica OTP insertando un espacio de separación legible (ej. "123 456" o "1234 5678").
 */
private fun formatOtpDigits(raw: String): String {
    return when (raw.length) {
        6 -> "${raw.substring(0, 3)} ${raw.substring(3)}"
        8 -> "${raw.substring(0, 4)} ${raw.substring(4)}"
        else -> raw
    }
}
