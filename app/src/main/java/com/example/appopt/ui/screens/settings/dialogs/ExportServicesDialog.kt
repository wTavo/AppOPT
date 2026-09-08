package com.example.appopt.ui.screens.settings.dialogs

import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.security.SecurityConfig
import com.example.appopt.security.TransferCrypto
import com.example.appopt.ui.components.ServiceBrandAvatar
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.appSwitchColors
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.ui.util.QrCodeGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Diálogo modal para la exportación y transferencia offline de cuentas OTP mediante lotes de códigos QR cifrados con PIN efímero.
 *
 * Máquina de estado:
 * 1. Selección de cuentas y preferencia de conservación en el dispositivo.
 * 2. Visualización del PIN de 6 dígitos, código(s) QR cifrado(s), paginación por lotes y temporizador regresivo de 90 segundos.
 * 3. Estado expirado con posibilidad de regenerar los códigos si se agota el tiempo.
 *
 * @param accounts Lista de cuentas OTP disponibles para transferir.
 * @param onExportBatchesPayload Lambda que genera la lista de payloads cifrados por lotes a partir de los IDs seleccionados y el PIN efímero.
 * @param onCompleteExport Callback invocado al terminar la exportación con la lista de IDs exportados y la preferencia de mantener en dispositivo.
 * @param onDismiss Callback invocado para cerrar el modal.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun ExportServicesDialog(
    accounts: List<TotpAccount>,
    onExportBatchesPayload: suspend (selectedIds: Set<String>, pinChars: CharArray) -> List<String>,
    onCompleteExport: (exportedIds: Set<String>, keepOnDevice: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val selectedServiceIds = remember { mutableStateListOf<String>().apply { addAll(accounts.map { it.id }) } }
    var keepServicesOnDevice by remember { mutableStateOf(true) }
    var isShowingQr by remember { mutableStateOf(false) }
    var transferQrBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var currentQrIndex by remember { mutableIntStateOf(0) }
    var transferPin by remember { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(false) }
    var exportedServiceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var totalSessionDuration by remember { mutableIntStateOf(SecurityConfig.TRANSFER_QR_EXPIRATION_SECONDS) }
    var secondsRemaining by remember { mutableIntStateOf(SecurityConfig.TRANSFER_QR_EXPIRATION_SECONDS) }
    var isExpired by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var generationCount by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    // Temporizador regresivo de expiración del código QR y PIN (dinámico proporcional a los lotes)
    LaunchedEffect(isShowingQr, generationCount) {
        if (isShowingQr) {
            secondsRemaining = totalSessionDuration
            isExpired = false
            while (secondsRemaining > 0 && isShowingQr) {
                delay(1000L)
                secondsRemaining--
            }
            if (secondsRemaining <= 0) {
                isExpired = true
                transferQrBitmaps = emptyList()
                isPinVisible = false
            }
        }
    }

    fun generateTransferQr() {
        val idsToExport = selectedServiceIds.toSet()
        val pin = TransferCrypto.generateTransferPin()
        transferPin = pin
        isPinVisible = false
        val pinChars = pin.toCharArray()
        isGenerating = true
        scope.launch {
            try {
                val payloads = onExportBatchesPayload(idsToExport, pinChars)
                val bitmaps = payloads.mapNotNull { QrCodeGenerator.generateQrBitmap(it, size = 600) }
                totalSessionDuration = SecurityConfig.calculateTransferExpirationSeconds(bitmaps.size)
                secondsRemaining = totalSessionDuration
                transferQrBitmaps = bitmaps
                currentQrIndex = 0
                exportedServiceIds = idsToExport
                isShowingQr = true
                isExpired = false
                generationCount++
            } finally {
                pinChars.fill('0')
                isGenerating = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (isShowingQr) {
                isShowingQr = false
                transferQrBitmaps = emptyList()
                transferPin = ""
            } else {
                onDismiss()
            }
        },
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        title = {
            Text(
                text = if (isShowingQr) {
                    if (isExpired) {
                        stringResource(R.string.settings_transfer_expired_title)
                    } else {
                        stringResource(R.string.settings_export_qr_dialog_title)
                    }
                } else {
                    stringResource(R.string.settings_export_services_dialog_title)
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = Motion.Spec.modalResizeSpec())
            ) {
                if (!isShowingQr) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                    Text(
                        text = stringResource(R.string.settings_export_services_dialog_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

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
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                                ) {
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
                        Column(
                            modifier = Modifier.weight(1f).padding(end = Dimensions.Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                        ) {
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
            } else if (isExpired) {
                // Estado: Código expirado tras 90 segundos
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TimerOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(Dimensions.IconSize.hero)
                    )
                    Text(
                        text = stringResource(R.string.settings_transfer_expired_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Estado: QR(s) Cifrado(s) + PIN + Paginación por lotes + Temporizador
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                ) {
                    if (transferPin.length == 6) {
                        val formattedPin = "${transferPin.substring(0, 3)} ${transferPin.substring(3)}"
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimensions.Spacing.sm, horizontal = Dimensions.Spacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_transfer_pin_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    IconButton(
                                        onClick = {
                                            haptics.click()
                                            isPinVisible = !isPinVisible
                                        },
                                        modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                                    ) {
                                        Icon(
                                            imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isPinVisible) {
                                                stringResource(R.string.settings_transfer_pin_toggle_hide)
                                            } else {
                                                stringResource(R.string.settings_transfer_pin_toggle_show)
                                            },
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(Dimensions.IconSize.small)
                                        )
                                    }
                                }

                                Text(
                                    text = if (isPinVisible) formattedPin else stringResource(R.string.settings_transfer_pin_masked_value),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )

                                Text(
                                    text = if (isPinVisible) {
                                        stringResource(R.string.settings_transfer_pin_hint)
                                    } else {
                                        stringResource(R.string.settings_transfer_pin_toggle_show)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Paginación por lotes (si hay más de 1 código QR)
                    if (transferQrBitmaps.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentQrIndex > 0) {
                                        haptics.click()
                                        currentQrIndex--
                                        isPinVisible = false
                                    }
                                },
                                enabled = currentQrIndex > 0
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.settings_transfer_prev_code)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(
                                        R.string.settings_transfer_page_indicator,
                                        currentQrIndex + 1,
                                        transferQrBitmaps.size
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    text = stringResource(
                                        R.string.settings_transfer_selected_services_count,
                                        exportedServiceIds.size
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (currentQrIndex < transferQrBitmaps.size - 1) {
                                        haptics.click()
                                        currentQrIndex++
                                        isPinVisible = false
                                    }
                                },
                                enabled = currentQrIndex < transferQrBitmaps.size - 1
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = stringResource(R.string.settings_transfer_next_code)
                                )
                            }
                        }
                    } else if (exportedServiceIds.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.settings_transfer_selected_services_count,
                                exportedServiceIds.size
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    val currentBitmap = transferQrBitmaps.getOrNull(currentQrIndex)
                    if (currentBitmap != null) {
                        if (isPinVisible) {
                            // Superficie de protección: oculta el QR mientras el PIN está expuesto
                            Surface(
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(Dimensions.ComponentSize.qrCodeDisplay)
                                    .padding(Dimensions.Spacing.xs)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(Dimensions.Spacing.md),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(Dimensions.IconSize.hero)
                                    )
                                    Spacer(modifier = Modifier.height(Dimensions.Spacing.sm))
                                    Text(
                                        text = stringResource(R.string.settings_transfer_qr_blurred_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                color = Color.White,
                                modifier = Modifier.padding(Dimensions.Spacing.xs)
                            ) {
                                Image(
                                    bitmap = currentBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(Dimensions.ComponentSize.qrCodeDisplay)
                                        .padding(Dimensions.Spacing.xs)
                                )
                            }
                        }

                        // Barra y etiqueta de tiempo restante proporcional
                        val progress = secondsRemaining.toFloat() / maxOf(1, totalSessionDuration).toFloat()
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Dimensions.Spacing.xs),
                            color = if (secondsRemaining <= 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )

                        val expirationLabel = if (secondsRemaining >= 60) {
                            val minutes = secondsRemaining / 60
                            val seconds = secondsRemaining % 60
                            stringResource(R.string.settings_transfer_expires_in_minutes, minutes, seconds)
                        } else {
                            stringResource(R.string.settings_transfer_expires_in, secondsRemaining)
                        }

                        Text(
                            text = expirationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (secondsRemaining <= 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
        }
    },
    confirmButton = {
            var isProcessing by remember { mutableStateOf(false) }
            if (!isShowingQr) {
                Button(
                    onClick = {
                        if (!isProcessing) {
                            isProcessing = true
                            try {
                                generateTransferQr()
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    enabled = selectedServiceIds.isNotEmpty() && !isProcessing,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Text(stringResource(R.string.settings_generate_qr_button), style = MaterialTheme.typography.labelLarge)
                }
            } else if (isExpired) {
                Button(
                    onClick = {
                        if (!isGenerating) {
                            haptics.click()
                            generateTransferQr()
                        }
                    },
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimensions.IconSize.small),
                            strokeWidth = Dimensions.Spacing.xs,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.settings_transfer_regenerate_button), style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Button(
                    onClick = {
                        if (!isProcessing) {
                            isProcessing = true
                            onCompleteExport(exportedServiceIds, keepServicesOnDevice)
                        }
                    },
                    enabled = (transferQrBitmaps.isNotEmpty() || keepServicesOnDevice) && !isProcessing,
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
                    Text(stringResource(R.string.action_close), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                TextButton(onClick = {
                    isShowingQr = false
                    transferQrBitmaps = emptyList()
                    transferPin = ""
                }) {
                    Text(stringResource(R.string.settings_drive_details_back), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        modifier = modifier
    )
}
