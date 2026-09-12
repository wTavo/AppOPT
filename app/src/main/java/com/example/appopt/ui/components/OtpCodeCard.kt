package com.example.appopt.ui.components

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
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.appopt.R
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.repository.AccountWithCode
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.WarningOrange
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.AccessibilityUtils
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tarjeta visual para representar una cuenta 2FA y su código OTP actual con alto contraste, tipografía escalable, ripple nativo y arrastre.
 *
 * Características de diseño e interacción:
 * - Pulsar sobre la tarjeta genera el efecto ripple visual y abre el modal de detalle/edición.
 * - Reordenamiento por pulsación prolongada (*Long press*) configurable mediante [isReorderEnabled].
 * - Pulsar directamente sobre los dígitos copia inmediatamente el código al portapapeles.
 * - Modo de privacidad (`hideCodes`): anima suavemente el colapso y despliegue de los números y contador mediante [Motion.Spec.privacyCollapseSpec].
 *
 * @param accountWithCode Contenedor con la información de la cuenta, código activo y progreso temporal.
 * @param hideCodes Si es verdadero, oculta por completo la sección de códigos y temporizadores con animación fluida.
 * @param isDragging Si es verdadero, resalta visualmente la tarjeta mientras se arrastra para reordenar.
 * @param isReorderEnabled Si es verdadero, habilita la detección de pulsación prolongada para reordenar la tarjeta.
 * @param onCardClick Callback al pulsar en la tarjeta para abrir el popup modal.
 * @param onCopyCode Callback invocado al pulsar sobre los dígitos para copiar el código al portapapeles.
 * @param onToggleFavorite Callback para marcar o desmarcar como favorita.
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
    val appHaptics = rememberAppHaptics()
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(Motion.Duration.FEEDBACK_TOAST.toLong().milliseconds)
            copied = false
        }
    }

    // Formatear código: e.g. "123 456" para 6 dígitos o "1234 5678" para 8 dígitos
    val formattedCode = remember(accountWithCode.code) {
        val raw = accountWithCode.code
        when (raw.length) {
            6 -> "${raw.substring(0, 3)} ${raw.substring(3)}"
            8 -> "${raw.substring(0, 4)} ${raw.substring(4)}"
            else -> raw
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
            .clickable(
                enabled = !isDragging,
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    appHaptics.click()
                    onCardClick()
                }
            )
            .semantics {
                contentDescription = AccessibilityUtils.buildAccountCardContentDescription(
                    context = context,
                    issuer = account.issuer,
                    accountName = account.accountName,
                    code = accountWithCode.code,
                    isFavorite = account.isFavorite
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        border = if (isDragging) BorderStroke(Dimensions.Stroke.regular, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.lg)
        ) {
            // Cabecera: Avatar de Marca, Nombres, Botón de Favorito y Manija de Arrastre
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
                        issuer = account.issuer,
                        size = Dimensions.IconSize.hero
                    )

                    Spacer(modifier = Modifier.width(Dimensions.Spacing.md))

                    Column {
                        Text(
                            text = account.issuer.ifEmpty { stringResource(R.string.home_default_issuer) },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        if (account.accountName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                            Text(
                                text = account.accountName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        appHaptics.click()
                        onToggleFavorite(account.id)
                    },
                    modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                ) {
                    Icon(
                        imageVector = if (account.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = stringResource(R.string.action_favorite),
                        tint = if (account.isFavorite) WarningOrange else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Modo de privacidad: despliegue animado de dígitos y temporizador
            androidx.compose.animation.AnimatedVisibility(
                visible = !hideCodes,
                enter = androidx.compose.animation.expandVertically(animationSpec = Motion.Spec.privacyCollapseSpec()) +
                        androidx.compose.animation.fadeIn(animationSpec = Motion.Spec.quickFadeSpec()),
                exit = androidx.compose.animation.shrinkVertically(animationSpec = Motion.Spec.privacyCollapseSpec()) +
                        androidx.compose.animation.fadeOut(animationSpec = Motion.Spec.quickFadeSpec())
            ) {
                Column {
                    Spacer(modifier = Modifier.height(Dimensions.Spacing.md))

                    // Cuerpo: Dígitos OTP y Temporizador / Acción HOTP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Zona táctil de los dígitos: toque directo para copiar
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                                .clickable {
                                    appHaptics.copy()
                                    onCopyCode(accountWithCode.code)
                                    copied = true
                                }
                                .padding(Dimensions.Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formattedCode,
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.semantics {
                                    contentDescription = AccessibilityUtils.toAccessibleSpokenOtp(accountWithCode.code)
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

                        if (account.type == OtpType.TOTP) {
                            CircularTimeProgress(
                                period = account.period
                            )
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
                }
            }
        }
    }
}
