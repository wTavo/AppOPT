package com.example.appopt.ui.screens.settings.dialogs
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import com.example.appopt.ui.components.AppModalDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.appopt.R
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.security.SecurityConfig
import com.example.appopt.security.TransferCrypto
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.screens.settings.dialogs.components.ExportAccountSelectionStep
import com.example.appopt.ui.screens.settings.dialogs.components.ExportExpiredStep
import com.example.appopt.ui.screens.settings.dialogs.components.ExportQrCarouselStep
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.util.QrCodeGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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
    var selectedServiceIds by remember(accounts) { mutableStateOf(accounts.map { it.id }.toSet()) }
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
                delay(1000L.milliseconds)
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
        val idsToExport = selectedServiceIds
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
            } catch (_: Exception) {
                isGenerating = false
            } finally {
                pinChars.fill('0')
            }
        }
    }

    val currentExportState = when {
        !isShowingQr -> ExportSubState.ACCOUNT_SELECTION
        isExpired -> ExportSubState.EXPIRED
        else -> ExportSubState.QR_CAROUSEL
    }

    AppModalDialog(
        onDismissRequest = {
            if (isGenerating && !isShowingQr) return@AppModalDialog
            isGenerating = false
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
        onBackStep = {
            if (isShowingQr) {
                isGenerating = false
                if (currentExportState == ExportSubState.QR_CAROUSEL) {
                    onCompleteExport(exportedServiceIds, keepServicesOnDevice)
                }
                isShowingQr = false
                transferQrBitmaps = emptyList()
                transferPin = ""
                true
            } else {
                if (isGenerating) return@AppModalDialog true
                false
            }
        },
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = currentExportState,
            transitionSpec = { Motion.Spec.dialogStepContentTransform() },
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxWidth(),
            label = "exportServicesStepTransition"
        ) { state ->
                when (state) {
                    ExportSubState.ACCOUNT_SELECTION -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimensions.Spacing.lg)
                                .imePadding(),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                        ) {
                            // Cabecera fija
                            Text(
                                text = stringResource(R.string.settings_export_services_dialog_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Cuerpo central scrolleable aislado
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                            ) {

                            ExportAccountSelectionStep(
                                accounts = accounts,
                                selectedServiceIds = selectedServiceIds,
                                onToggleSelection = { id ->
                                    selectedServiceIds = if (id in selectedServiceIds) {
                                        selectedServiceIds - id
                                    } else {
                                        selectedServiceIds + id
                                    }
                                },
                                onSelectAll = {
                                    selectedServiceIds = accounts.map { it.id }.toSet()
                                },
                                onDeselectAll = {
                                    selectedServiceIds = emptySet()
                                },
                                keepServicesOnDevice = keepServicesOnDevice,
                                onKeepServicesChanged = { keepServicesOnDevice = it },
                                enabled = !isGenerating
                            )
                            }

                            // Pie fijo de acciones
                            AppDialogActionButtons(
                                confirmText = stringResource(R.string.settings_generate_qr_button),
                                onConfirm = { generateTransferQr() },
                                dismissText = stringResource(R.string.action_close),
                                onDismiss = {
                                    if (!isGenerating) onDismiss()
                                },
                                confirmEnabled = selectedServiceIds.isNotEmpty() && !isGenerating,
                                isLoading = isGenerating
                            )
                        }
                    }
                    ExportSubState.QR_CAROUSEL -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimensions.Spacing.lg)
                                .imePadding(),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                        ) {
                            // Cabecera fija
                            Text(
                                text = stringResource(R.string.settings_export_qr_dialog_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Cuerpo central scrolleable aislado
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                            ) {

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

                            // Pie fijo de acciones
                            AppDialogActionButtons(
                                dismissText = if (!keepServicesOnDevice) {
                                    stringResource(R.string.settings_export_confirm_done)
                                } else {
                                    stringResource(R.string.action_close)
                                },
                                onDismiss = {
                                    onCompleteExport(exportedServiceIds, keepServicesOnDevice)
                                    isShowingQr = false
                                    transferQrBitmaps = emptyList()
                                    transferPin = ""
                                    onDismiss()
                                }
                            )
                        }
                    }
                    ExportSubState.EXPIRED -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimensions.Spacing.lg)
                                .imePadding(),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                        ) {
                            // Cabecera fija
                            Text(
                                text = stringResource(R.string.settings_transfer_expired_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error
                            )

                            // Cuerpo central scrolleable aislado
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                            ) {

                            ExportExpiredStep()
                            }

                            // Pie fijo de acciones
                            AppDialogActionButtons(
                                confirmText = stringResource(R.string.settings_transfer_regenerate_button),
                                onConfirm = { generateTransferQr() },
                                dismissText = stringResource(R.string.action_close),
                                onDismiss = {
                                    if (!isGenerating) {
                                        isShowingQr = false
                                        transferQrBitmaps = emptyList()
                                        transferPin = ""
                                        onDismiss()
                                    }
                                },
                                confirmEnabled = !isGenerating,
                                isLoading = isGenerating
                            )
                        }
                    }
                }
        }
    }
}
