package com.example.appopt.ui.components.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
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
import com.example.appopt.R
import com.example.appopt.domain.model.AccountWithCode
import com.example.appopt.domain.model.OtpType
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.components.CircularTimeProgress
import com.example.appopt.ui.components.ServiceBrandAvatar
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.rememberAppHaptics
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Paso modal de visualización de detalles del servicio, dígitos OTP y temporizador circular (Directivas 14 y 29).
 *
 * @param accountWithCode Entidad de la cuenta con código activo.
 * @param onEditClick Callback para entrar en modo edición.
 * @param onDeleteClick Callback para solicitar confirmación de eliminación.
 * @param onCopyCode Callback para copiar el código OTP al portapapeles.
 * @param onDismiss Callback para cerrar el diálogo modal.
 */
@Composable
fun AccountDetailsViewStep(
    accountWithCode: AccountWithCode,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCopyCode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val account = accountWithCode.account
    val appHaptics = rememberAppHaptics()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(Motion.Duration.FEEDBACK_TOAST.toLong().milliseconds)
            copied = false
        }
    }

    val formattedCode = remember(accountWithCode.code) {
        val raw = accountWithCode.code
        when (raw.length) {
            6 -> "${raw.substring(0, 3)} ${raw.substring(3)}"
            8 -> "${raw.substring(0, 4)} ${raw.substring(4)}"
            else -> raw
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.Spacing.lg)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        // Cabecera fija con Título e Iconos de acción (Lápiz y Basurero)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.account_modal_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        appHaptics.click()
                        onEditClick()
                    },
                    modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.action_edit),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = {
                        appHaptics.click()
                        onDeleteClick()
                    },
                    modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = UrgentRed
                    )
                }
            }
        }

        // Cuerpo central scrolleable aislado
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            // Avatar de Marca y Nombre de Cuenta
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimensions.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ServiceBrandAvatar(
                    issuer = account.issuer,
                    size = Dimensions.IconSize.hero
                )

                Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))

                Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)) {
                    Text(
                        text = account.issuer.ifEmpty { stringResource(R.string.home_default_issuer) },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (account.accountName.isNotBlank()) {
                        Text(
                            text = account.accountName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Tarjeta de Dígitos OTP grandes con contador circular
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(Dimensions.CornerRadius.large)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
                        .clickable {
                            appHaptics.copy()
                            onCopyCode(accountWithCode.code)
                            copied = true
                        }
                        .padding(vertical = Dimensions.Spacing.lg, horizontal = Dimensions.Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        Text(
                            text = formattedCode,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )

                        AnimatedVisibility(
                            visible = copied,
                            enter = fadeIn(animationSpec = Motion.Spec.quickFadeSpec()),
                            exit = fadeOut(animationSpec = Motion.Spec.quickFadeSpec())
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.action_copied),
                                tint = SafeGreen,
                                modifier = Modifier.size(Dimensions.IconSize.large)
                            )
                        }
                    }

                    if (account.type == OtpType.TOTP) {
                        CircularTimeProgress(
                            period = account.period
                        )
                    }
                }
            }
        }

        // Pie fijo con Botón Cerrar
        AppDialogActionButtons(
            dismissText = stringResource(R.string.account_modal_close_button),
            onDismiss = onDismiss
        )
    }
}
