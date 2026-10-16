package com.example.appopt.ui.screens.home.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.components.ModalTone
import com.example.appopt.ui.components.ServiceBrandAvatar
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.rememberAppHaptics

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
