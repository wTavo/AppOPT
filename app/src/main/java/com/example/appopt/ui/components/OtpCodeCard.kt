package com.example.appopt.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.example.appopt.R
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.repository.AccountWithCode
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.WarningOrange
import kotlinx.coroutines.delay

/**
 * Tarjeta visual para representar una cuenta 2FA y su código OTP actual con alto contraste y tipografía escalable.
 *
 * Características de diseño e interacción:
 * - Contraste nítido respecto al fondo de pantalla con borde sutil y elevación.
 * - Pulsar sobre la tarjeta (área general) abre el popup/modal de edición y detalles.
 * - Pulsar directamente sobre los dígitos copia inmediatamente el código al portapapeles.
 * - Modo de privacidad (`hideCodes`): oculta completamente los números y el contador, dejando únicamente los datos identificativos.
 *
 * @param accountWithCode Contenedor con la información de la cuenta, código activo y progreso temporal.
 * @param hideCodes Si es verdadero, oculta por completo la sección de códigos y temporizadores.
 * @param onCardClick Callback al pulsar en la tarjeta para abrir el popup modal.
 * @param onCopyCode Callback invocado al pulsar sobre los dígitos para copiar el código al portapapeles.
 * @param onToggleFavorite Callback para marcar o desmarcar como favorita.
 * @param onNextHotpCode Callback para avanzar el contador de una cuenta HOTP.
 */
@Composable
fun OtpCodeCard(
    accountWithCode: AccountWithCode,
    hideCodes: Boolean,
    onCardClick: () -> Unit,
    onCopyCode: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onNextHotpCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val account = accountWithCode.account
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
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
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCardClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Encabezado: Emisor y Favorito
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.issuer.ifEmpty { stringResource(R.string.home_default_issuer) },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (account.accountName.isNotBlank()) {
                        Text(
                            text = account.accountName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = { onToggleFavorite(account.id) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (account.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = stringResource(R.string.action_favorite),
                        tint = if (account.isFavorite) WarningOrange else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Si hideCodes está activo, ocultamos por completo los dígitos y el contador
            if (!hideCodes) {
                Spacer(modifier = Modifier.height(12.dp))

                // Cuerpo: Dígitos OTP (sin caja de color de fondo) y Temporizador / Acción HOTP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Zona táctil de los dígitos: toque directo para copiar (fondo limpio integrado)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onCopyCode(accountWithCode.code)
                                copied = true
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formattedCode,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        AnimatedVisibility(
                            visible = copied,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Row {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.action_copied),
                                    tint = SafeGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (account.type == OtpType.TOTP) {
                        CircularTimeProgress(
                            remainingSeconds = accountWithCode.remainingSeconds,
                            progress = accountWithCode.progress
                        )
                    } else {
                        IconButton(
                            onClick = { onNextHotpCode(account.id) }
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
