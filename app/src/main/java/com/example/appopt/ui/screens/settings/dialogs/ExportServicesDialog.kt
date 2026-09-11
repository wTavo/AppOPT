package com.example.appopt.ui.screens.settings.dialogs

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.security.SecurityConfig
import com.example.appopt.security.TransferCrypto
import com.example.appopt.ui.screens.settings.dialogs.components.ExportAccountSelectionStep
import com.example.appopt.ui.screens.settings.dialogs.components.ExportExpiredStep
import com.example.appopt.ui.screens.settings.dialogs.components.ExportQrCarouselStep
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.ui.util.QrCodeGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sub-estados del diálogo modal de exportación de servicios.
 */
private enum class ExportSubState {
    ACCOUNT_SELECTION,
    QR_CAROUSEL,
    EXPIRED
}

/**
 * Diálogo modal para la exportación y transferencia offline de cuentas OTP mediante lotes de códigos QR cifrados con PIN efímero.
 *
 * Máquina de estado modular:
 * 1. Selección de cuentas y preferencia de conservación en el dispositivo ([ExportAccountSelectionStep]).
 * 2. Visualización del PIN de 6 dígitos, código(s) QR cifrado(s), paginación por lotes y temporizador regresivo ([ExportQrCarouselStep]).
 * 3. Estado expirado ([ExportExpiredStep]) con posibilidad de regenerar los códigos si se agota el tiempo.
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

    /**
     * Genera de forma asíncrona los códigos QR cifrados y el PIN efímero de 6 dígitos.
     */
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

    val currentExportState = when {
        !isShowingQr -> ExportSubState.ACCOUNT_SELECTION
        isExpired -> ExportSubState.EXPIRED
        else -> ExportSubState.QR_CAROUSEL
    }

    AlertDialog(
        onDismissRequest = {
            if (isShowingQr) {
                if (currentExportState == ExportSubState.QR_CAROUSEL) {
                    onCompleteExport(exportedServiceIds, keepServicesOnDevice)
                }
                isShowingQr = false
                transferQrBitmaps = emptyList()
                transferPin = ""
            }
            onDismiss()
        },
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        title = {
            AnimatedContent(
                targetState = currentExportState,
                transitionSpec = { Motion.Spec.dialogStepContentTransform() },
                label = "exportServicesTitleTransition"
            ) { exportState ->
                Text(
                    text = when (exportState) {
                        ExportSubState.EXPIRED -> stringResource(R.string.settings_transfer_expired_title)
                        ExportSubState.QR_CAROUSEL -> stringResource(R.string.settings_export_qr_dialog_title)
                        ExportSubState.ACCOUNT_SELECTION -> stringResource(R.string.settings_export_services_dialog_title)
                    },
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            AnimatedContent(
                targetState = currentExportState,
                transitionSpec = { Motion.Spec.dialogStepContentTransform() },
                contentAlignment = Alignment.TopStart,
                modifier = Modifier.fillMaxWidth(),
                label = "exportServicesStepTransition"
            ) { state ->
                when (state) {
                    ExportSubState.ACCOUNT_SELECTION -> {
                        ExportAccountSelectionStep(
                            accounts = accounts,
                            selectedServiceIds = selectedServiceIds,
                            onToggleSelection = { id ->
                                if (id in selectedServiceIds) {
                                    selectedServiceIds.remove(id)
                                } else {
                                    selectedServiceIds.add(id)
                                }
                            },
                            keepServicesOnDevice = keepServicesOnDevice,
                            onKeepServicesChanged = { keepServicesOnDevice = it }
                        )
                    }
                    ExportSubState.EXPIRED -> {
                        ExportExpiredStep()
                    }
                    ExportSubState.QR_CAROUSEL -> {
                        ExportQrCarouselStep(
                            transferQrBitmaps = transferQrBitmaps,
                            currentQrIndex = currentQrIndex,
                            onSelectQrIndex = { currentQrIndex = it; isPinVisible = false },
                            transferPin = transferPin,
                            isPinVisible = isPinVisible,
                            onTogglePinVisibility = { isPinVisible = !isPinVisible },
                            exportedCount = exportedServiceIds.size,
                            secondsRemaining = secondsRemaining,
                            totalSessionDuration = totalSessionDuration
                        )
                    }
                }
            }
        },
        confirmButton = {
            AnimatedContent(
                targetState = currentExportState,
                transitionSpec = { Motion.Spec.dialogStepContentTransform() },
                modifier = Modifier.fillMaxWidth(),
                label = "exportServicesButtonsTransition"
            ) { exportState ->
                when (exportState) {
                    ExportSubState.ACCOUNT_SELECTION -> {
                        var isProcessing by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    haptics.click()
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                            ) {
                                Text(
                                    text = stringResource(R.string.action_close),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center
                                )
                            }

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
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.xs),
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_generate_qr_button),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    ExportSubState.QR_CAROUSEL -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    haptics.click()
                                    onCompleteExport(exportedServiceIds, keepServicesOnDevice)
                                    isShowingQr = false
                                    transferQrBitmaps = emptyList()
                                    transferPin = ""
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                            ) {
                                Text(
                                    text = if (!keepServicesOnDevice) {
                                        stringResource(R.string.settings_export_confirm_done)
                                    } else {
                                        stringResource(R.string.action_close)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    ExportSubState.EXPIRED -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    haptics.click()
                                    isShowingQr = false
                                    transferQrBitmaps = emptyList()
                                    transferPin = ""
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                            ) {
                                Text(
                                    text = stringResource(R.string.action_close),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Button(
                                onClick = {
                                    if (!isGenerating) {
                                        haptics.click()
                                        generateTransferQr()
                                    }
                                },
                                enabled = !isGenerating,
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.xs),
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                            ) {
                                if (isGenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(Dimensions.IconSize.small),
                                        strokeWidth = Dimensions.Spacing.xs,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.settings_transfer_regenerate_button),
                                        style = MaterialTheme.typography.labelLarge,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = null,
        modifier = modifier
    )
}
