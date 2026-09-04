package com.example.appopt.ui.screens.settings.dialogs

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.ui.components.ServiceBrandAvatar
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.appSwitchColors
import com.example.appopt.ui.util.QrCodeGenerator
import kotlinx.coroutines.launch

/**
 * Diálogo modal para la exportación y transferencia offline de cuentas OTP mediante código QR.
 *
 * Máquina de estado:
 * 1. Selección de cuentas y preferencia de conservación en el dispositivo.
 * 2. Visualización del código QR para escaneo directo en otro dispositivo.
 *
 * @param accounts Lista de cuentas OTP disponibles para transferir.
 * @param onExportPayload Lambda que genera el payload JSON cifrado/empaquetado a partir de los IDs seleccionados.
 * @param onCompleteExport Callback invocado al terminar la exportación con la lista de IDs exportados y la preferencia de mantener en dispositivo.
 * @param onDismiss Callback invocado para cerrar el modal.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun ExportServicesDialog(
    accounts: List<TotpAccount>,
    onExportPayload: suspend (Set<String>) -> String,
    onCompleteExport: (exportedIds: Set<String>, keepOnDevice: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val selectedServiceIds = remember { mutableStateListOf<String>().apply { addAll(accounts.map { it.id }) } }
    var keepServicesOnDevice by remember { mutableStateOf(true) }
    var isShowingQr by remember { mutableStateOf(false) }
    var transferQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var exportedServiceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = {
            if (isShowingQr) {
                isShowingQr = false
            } else {
                onDismiss()
            }
        },
        title = {
            Text(
                text = if (isShowingQr) {
                    stringResource(R.string.settings_export_qr_dialog_title)
                } else {
                    stringResource(R.string.settings_export_services_dialog_title)
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            if (!isShowingQr) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.settings_export_services_dialog_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = Dimensions.ComponentSize.modalListMaxHeight)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                    ) {
                        accounts.forEach { account ->
                            val isSelected = account.id in selectedServiceIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) {
                                            selectedServiceIds.remove(account.id)
                                        } else {
                                            selectedServiceIds.add(account.id)
                                        }
                                    }
                                    .padding(vertical = Dimensions.Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ServiceBrandAvatar(
                                    issuer = account.issuer,
                                    size = Dimensions.ComponentSize.actionIconButton
                                )
                                Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account.issuer,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    if (account.accountName.isNotBlank()) {
                                        Text(
                                            text = account.accountName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            selectedServiceIds.add(account.id)
                                        } else {
                                            selectedServiceIds.remove(account.id)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.Spacing.xs))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { keepServicesOnDevice = !keepServicesOnDevice }
                            .padding(vertical = Dimensions.Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = Dimensions.Spacing.sm)) {
                            Text(
                                text = stringResource(R.string.settings_keep_services_label),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = stringResource(R.string.settings_keep_services_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = keepServicesOnDevice,
                            onCheckedChange = { keepServicesOnDevice = it },
                            colors = appSwitchColors()
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                ) {
                if (transferQrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        color = Color.White,
                        modifier = Modifier.padding(Dimensions.Spacing.sm)
                    ) {
                        Image(
                            bitmap = transferQrBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(Dimensions.ComponentSize.qrCodeDisplay)
                                .padding(Dimensions.Spacing.sm)
                        )
                    }

                    Text(
                        text = stringResource(R.string.settings_export_qr_dialog_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_export_qr_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = Dimensions.Spacing.md)
                    )
                }
            }
        }
    },
    confirmButton = {
            if (!isShowingQr) {
                Button(
                    onClick = {
                        val idsToExport = selectedServiceIds.toSet()
                        scope.launch {
                            val payload = onExportPayload(idsToExport)
                            transferQrBitmap = QrCodeGenerator.generateQrBitmap(payload, size = 600)
                            exportedServiceIds = idsToExport
                            isShowingQr = true
                        }
                    },
                    enabled = selectedServiceIds.isNotEmpty(),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Text(stringResource(R.string.settings_generate_qr_button), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Button(
                    onClick = {
                        onCompleteExport(exportedServiceIds, keepServicesOnDevice)
                    },
                    enabled = transferQrBitmap != null || keepServicesOnDevice,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Text(
                        text = if (!keepServicesOnDevice) {
                            stringResource(R.string.settings_export_confirm_done)
                        } else {
                            stringResource(R.string.account_modal_close_button)
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        dismissButton = {
            if (!isShowingQr) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                TextButton(onClick = { isShowingQr = false }) {
                    Text(stringResource(R.string.settings_drive_details_back), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        modifier = modifier
    )
}
