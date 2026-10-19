package com.example.appopt.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.domain.model.ShareGrantStatus
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.AccessibilityUtils

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
 * @param isDragging Si es verdadero, resalta visualmente la elevación y bordes durante el arrastre.
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
