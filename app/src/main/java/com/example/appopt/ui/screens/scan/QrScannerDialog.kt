package com.example.appopt.ui.screens.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.domain.totp.OtpUriParser
import com.example.appopt.domain.totp.ParsedOtpData
import com.example.appopt.security.CryptoManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.security.TransferCrypto
import com.example.appopt.security.TransferQrChunk
import com.example.appopt.ui.components.AccountImportSelectionList
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.components.LocalModalDismissHandler
import com.example.appopt.ui.components.ServiceBrandAvatar
import com.example.appopt.ui.screens.scan.components.CameraXBarcodeScannerView
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.SecurityAnalysisUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private enum class QrScannerStep {
    CAMERA,
    CONFIRM_SINGLE_OTP,
    TRANSFER_PIN,
    TRANSFER_SELECT_ACCOUNTS
}

/**
 * Diálogo modal para escaneo de códigos QR (alta de cuentas individuales y transferencias en lote cifradas con PIN).
 *
 * Estándares aplicados:
 * - Directiva 14: Máquina de estados monolítica dentro de [AppModalDialog] con navegación defensiva ([onBackStep]).
 * - Directiva 14: Estructura tripartita inmutable (Cabecera y Botones fijos con cuerpo central scrolleable).
 * - Directiva 22: Idempotencia y feedback háptico con [AppAnimatedButton] y [AppHaptics].
 * - Directiva 9: Zeroización de memoria tras procesar claves criptográficas.
 * - Prevención Antifraude: Verificación previa con detección de homóglifos, duplicados y consejos antiphishing.
 *
 * @param onDismiss Callback para cerrar el diálogo.
 * @param modifier Modificador de diseño Compose.
 * @param onScanSuccess Callback invocado tras la importación exitosa de una o más cuentas.
 * @param title Título del diálogo.
 */
@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onScanSuccess: () -> Unit = {},
    title: String = stringResource(R.string.scan_title)
) {
    val appHaptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val repository = remember { AuthenticatorApp.instance.accountRepository }
    val existingAccounts by repository.getAccounts().collectAsStateWithLifecycle(initialValue = emptyList())

    val importAllAlreadyExistErrorText = stringResource(R.string.scan_import_all_already_exist)
    val pinMaxAttemptsErrorText = stringResource(R.string.scan_transfer_pin_error_max_attempts)
    val pinIncorrectAttemptsFormat = stringResource(R.string.scan_transfer_pin_error_incorrect_attempts)
    val qrExpiredErrorText = stringResource(R.string.scan_transfer_error_expired)

    var currentStep by remember { mutableStateOf(QrScannerStep.CAMERA) }

    var isCameraActive by remember { mutableStateOf(false) }
    var isProcessingBarcode by remember { mutableStateOf(false) }
    var lastScannedPayload by remember { mutableStateOf<String?>(null) }

    val blockedSessionIds = remember { mutableSetOf<Long>() }
    val blockedPayloadFingerprints = remember { mutableSetOf<Int>() }

    val sessionChunks = remember { mutableStateMapOf<Int, TransferQrChunk>() }
    var currentSessionId by remember { mutableLongStateOf(0L) }
    var totalExpectedChunks by remember { mutableIntStateOf(0) }
    var lastCapturedChunkIndex by remember { mutableIntStateOf(0) }
    var duplicateChunkIndex by remember { mutableStateOf<Int?>(null) }
    var capturedChunkAnimationIndex by remember { mutableStateOf<Int?>(null) }

    var pendingSingleOtp by remember { mutableStateOf<ParsedOtpData?>(null) }
    var pendingEncryptedPayload by remember { mutableStateOf<String?>(null) }
    var pendingEncryptedChunks by remember { mutableStateOf<List<TransferQrChunk>?>(null) }
    var transferPinInput by remember { mutableStateOf("") }
    var pinErrorMessage by remember { mutableStateOf<String?>(null) }
    var failedPinAttempts by remember { mutableIntStateOf(0) }
    var isVerifyingPin by remember { mutableStateOf(false) }

    var parsedAccounts by remember { mutableStateOf<List<ParsedAccountPreview>>(emptyList()) }
    var selectedAccountIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val resetToCamera: () -> Unit = {
        pendingSingleOtp?.secretBytes?.let { CryptoManager.zeroize(it) }
        pendingSingleOtp = null
        pendingEncryptedPayload = null
        pendingEncryptedChunks = null
        sessionChunks.clear()
        currentSessionId = 0L
        totalExpectedChunks = 0
        transferPinInput = ""
        pinErrorMessage = null
        failedPinAttempts = 0
        isVerifyingPin = false
        parsedAccounts = emptyList()
        selectedAccountIds = emptySet()
        lastScannedPayload = null
        lastCapturedChunkIndex = 0
        duplicateChunkIndex = null
        capturedChunkAnimationIndex = null
        isProcessingBarcode = false
        currentStep = QrScannerStep.CAMERA
    }

    val handleDismiss: () -> Unit = {
        resetToCamera()
        onDismiss()
    }

    AppModalDialog(
        onDismissRequest = handleDismiss,
        onBackStep = {
            when (currentStep) {
                QrScannerStep.CONFIRM_SINGLE_OTP -> {
                    resetToCamera()
                    true
                }
                QrScannerStep.TRANSFER_SELECT_ACCOUNTS, QrScannerStep.TRANSFER_PIN -> {
                    if (!isVerifyingPin) {
                        resetToCamera()
                        true
                    } else {
                        false
                    }
                }
                QrScannerStep.CAMERA -> false
            }
        },
        modifier = modifier
    ) {
        val modalDismissHandler = LocalModalDismissHandler.current

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = { Motion.Spec.dialogStepContentTransform() },
            label = "QrScannerStepTransition"
        ) { step ->
            when (step) {
                QrScannerStep.CAMERA -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg)
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. Cabecera fija
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 2. Cuerpo central scrolleable aislado
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CameraXBarcodeScannerView(
                                isProcessingBarcode = isProcessingBarcode,
                                isCameraActive = isCameraActive,
                                onCameraActiveChange = { isCameraActive = it },
                                onBarcodeScanned = { rawValue ->
                                    if (rawValue != lastScannedPayload) {
                                        lastScannedPayload = rawValue
                                        isProcessingBarcode = true

                                        scope.launch {
                                            handleScannedBarcode(
                                                rawValue = rawValue,
                                                appHaptics = appHaptics,
                                                blockedSessionIds = blockedSessionIds,
                                                blockedPayloadFingerprints = blockedPayloadFingerprints,
                                                sessionChunks = sessionChunks,
                                                currentSessionId = currentSessionId,
                                                onSessionUpdated = { newSessionId, totalChunks ->
                                                    currentSessionId = newSessionId
                                                    totalExpectedChunks = totalChunks
                                                    lastCapturedChunkIndex = 0
                                                    duplicateChunkIndex = null
                                                    capturedChunkAnimationIndex = null
                                                },
                                                onChunkScanned = { chunkIndex ->
                                                    lastCapturedChunkIndex = chunkIndex
                                                    duplicateChunkIndex = null
                                                    capturedChunkAnimationIndex = chunkIndex
                                                    scope.launch {
                                                        delay(850L.milliseconds)
                                                        if (capturedChunkAnimationIndex == chunkIndex) {
                                                            capturedChunkAnimationIndex = null
                                                        }
                                                    }
                                                    scope.launch {
                                                        delay(300L.milliseconds)
                                                        isProcessingBarcode = false
                                                    }
                                                },
                                                onChunkDuplicate = { chunkIndex ->
                                                    duplicateChunkIndex = chunkIndex
                                                    scope.launch {
                                                        delay(500L.milliseconds)
                                                        isProcessingBarcode = false
                                                    }
                                                },
                                                onSingleOtpScanned = { parsedOtp ->
                                                    appHaptics.dragTick()
                                                    pendingSingleOtp = parsedOtp
                                                    currentStep = QrScannerStep.CONFIRM_SINGLE_OTP
                                                },
                                                onTransferPayloadReady = { payloadToDecrypt ->
                                                    pendingEncryptedPayload = payloadToDecrypt
                                                    pendingEncryptedChunks = null
                                                    transferPinInput = ""
                                                    pinErrorMessage = null
                                                    failedPinAttempts = 0
                                                    currentStep = QrScannerStep.TRANSFER_PIN
                                                },
                                                onChunksReady = { chunksToDecrypt ->
                                                    pendingEncryptedChunks = chunksToDecrypt
                                                    pendingEncryptedPayload = null
                                                    transferPinInput = ""
                                                    pinErrorMessage = null
                                                    failedPinAttempts = 0
                                                    currentStep = QrScannerStep.TRANSFER_PIN
                                                },
                                                onError = {
                                                    appHaptics.error()
                                                    delay(Motion.Duration.FEEDBACK_TOAST.toLong().milliseconds)
                                                    isProcessingBarcode = false
                                                }
                                            )
                                        }
                                    }
                                },
                                overlayContent = {
                                    QrCaptureSuccessBadge(capturedChunkIndex = capturedChunkAnimationIndex)
                                }
                            )

                            if (totalExpectedChunks > 1 && sessionChunks.size < totalExpectedChunks) {
                                Surface(
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        width = Dimensions.Stroke.thin,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Dimensions.Spacing.md),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                                    ) {
                                        // Cantidad de códigos QR que lleva capturados
                                        Text(
                                            text = stringResource(
                                                R.string.scan_transfer_chunk_count_header,
                                                sessionChunks.size,
                                                totalExpectedChunks
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )

                                        // Fila de pastillas (Pills) con el estado individual de cada fragmento
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(
                                                Dimensions.Spacing.sm,
                                                Alignment.CenterHorizontally
                                            ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            for (chunkIndex in 1..totalExpectedChunks) {
                                                val isScanned = sessionChunks.containsKey(chunkIndex)
                                                val pillContainerColor = if (isScanned) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                                }
                                                val pillContentColor = if (isScanned) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                                                    color = pillContainerColor,
                                                    border = if (!isScanned) {
                                                        BorderStroke(
                                                            width = Dimensions.Stroke.thin,
                                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                        )
                                                    } else null
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(
                                                            horizontal = Dimensions.Spacing.sm,
                                                            vertical = Dimensions.Spacing.xs
                                                        ),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isScanned) {
                                                                Icons.Default.CheckCircle
                                                            } else {
                                                                Icons.Default.HourglassEmpty
                                                            },
                                                            contentDescription = null,
                                                            tint = pillContentColor,
                                                            modifier = Modifier.size(Dimensions.IconSize.small)
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.scan_transfer_chunk_pill_label, chunkIndex),
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = if (isScanned) FontWeight.Bold else FontWeight.Normal,
                                                            color = pillContentColor
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        val isDuplicate = duplicateChunkIndex != null
                                        if (isDuplicate) {
                                            Text(
                                                text = stringResource(
                                                    R.string.scan_transfer_chunk_duplicate_title,
                                                    duplicateChunkIndex ?: 1
                                                ),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.error,
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        val statusDesc = if (isDuplicate) {
                                            stringResource(R.string.scan_transfer_chunk_duplicate_desc)
                                        } else {
                                            stringResource(R.string.scan_transfer_chunk_captured_desc)
                                        }

                                        Text(
                                            text = statusDesc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.scan_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // 3. Pie fijo de acciones
                        AppDialogActionButtons(
                            onDismiss = handleDismiss,
                            dismissText = stringResource(R.string.action_close)
                        )
                    }
                }

                QrScannerStep.CONFIRM_SINGLE_OTP -> {
                    val otp = pendingSingleOtp
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg)
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        // 1. Cabecera fija
                        Text(
                            text = stringResource(R.string.scan_confirm_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 2. Cuerpo central scrolleable aislado
                        if (otp != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.scan_confirm_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                // Tarjeta visual de identidad del servicio
                                Surface(
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Dimensions.Spacing.md),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                                    ) {
                                        ServiceBrandAvatar(
                                            issuer = otp.issuer,
                                            size = Dimensions.IconSize.hero
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = otp.issuer,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = otp.accountName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                                            val technicalInfo = if (otp.type == OtpType.TOTP) {
                                                stringResource(
                                                    R.string.scan_confirm_type_format,
                                                    otp.type.name,
                                                    otp.digits,
                                                    otp.period
                                                )
                                            } else {
                                                stringResource(
                                                    R.string.scan_confirm_type_hotp_format,
                                                    otp.type.name,
                                                    otp.digits
                                                )
                                            }
                                            Text(
                                                text = technicalInfo,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                // Diagnóstico de homóglifos o caracteres invisibles
                                val spoofingResult = remember(otp.issuer, otp.accountName) {
                                    val issuerCheck = SecurityAnalysisUtils.detectUnicodeSpoofing(otp.issuer)
                                    if (issuerCheck.isSuspicious) issuerCheck else SecurityAnalysisUtils.detectUnicodeSpoofing(otp.accountName)
                                }
                                if (spoofingResult.isSuspicious) {
                                    Surface(
                                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(Dimensions.Spacing.md),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(Dimensions.IconSize.medium)
                                            )
                                            Text(
                                                text = stringResource(R.string.scan_confirm_warning_spoofing),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }

                                // Diagnóstico de cuenta existente o duplicada
                                val existingDuplicate = remember(otp.issuer, otp.accountName, existingAccounts) {
                                    SecurityAnalysisUtils.findExistingDuplicate(existingAccounts, otp.issuer, otp.accountName)
                                }
                                if (existingDuplicate != null) {
                                    Surface(
                                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(Dimensions.Spacing.md),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(Dimensions.IconSize.medium)
                                            )
                                            Text(
                                                text = stringResource(
                                                    R.string.scan_confirm_warning_duplicate,
                                                    otp.issuer,
                                                    otp.accountName
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }

                                // Tarjeta educativa antiphishing
                                Surface(
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Dimensions.Spacing.md),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(Dimensions.IconSize.medium)
                                        )
                                        Text(
                                            text = stringResource(R.string.scan_confirm_phishing_tip),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // 3. Pie fijo de acciones simétricas
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        otp.secretBytes.let { CryptoManager.zeroize(it) }
                                        pendingSingleOtp = null
                                        isProcessingBarcode = false
                                        lastScannedPayload = null
                                        currentStep = QrScannerStep.CAMERA
                                    },
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                    contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.none),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_drive_details_back),
                                        style = MaterialTheme.typography.labelLarge,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                AppAnimatedButton(
                                    text = stringResource(R.string.scan_confirm_save_button),
                                    onClick = {
                                        scope.launch {
                                            repository.saveAccount(
                                                issuer = otp.issuer,
                                                accountName = otp.accountName,
                                                secretBytes = otp.secretBytes,
                                                algorithm = otp.algorithm,
                                                digits = otp.digits,
                                                period = otp.period,
                                                type = otp.type,
                                                counter = otp.counter
                                            )
                                            CryptoManager.zeroize(otp.secretBytes)
                                        }
                                        true
                                    },
                                    onActionConfirmed = {
                                        appHaptics.success()
                                        onScanSuccess()
                                        if (modalDismissHandler != null) {
                                            modalDismissHandler()
                                        } else {
                                            onDismiss()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                QrScannerStep.TRANSFER_PIN -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg)
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        // 1. Cabecera fija
                        Text(
                            text = stringResource(R.string.scan_transfer_pin_dialog_title),
                            style = MaterialTheme.typography.titleLarge
                        )

                        // 2. Cuerpo central scrolleable aislado
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                        ) {
                            Text(
                                text = stringResource(R.string.scan_transfer_pin_dialog_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = transferPinInput,
                                onValueChange = { input ->
                                    val cleaned = input.filter { it.isLetterOrDigit() }.take(SecurityConfig.TRANSFER_KEY_LENGTH).uppercase()
                                    transferPinInput = cleaned
                                    pinErrorMessage = null
                                },
                                label = { Text(stringResource(R.string.scan_transfer_pin_input_label)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    capitalization = KeyboardCapitalization.Characters
                                ),
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            )

                            if (pinErrorMessage != null) {
                                Text(
                                    text = pinErrorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // 3. Pie fijo de acciones
                        AppDialogActionButtons(
                            dismissText = stringResource(R.string.settings_drive_details_back),
                            onDismiss = {
                                if (!isVerifyingPin) {
                                    resetToCamera()
                                }
                            },
                            confirmText = stringResource(R.string.scan_transfer_pin_confirm_button),
                            onConfirm = {
                                if (transferPinInput.length !in setOf(SecurityConfig.TRANSFER_QR_PIN_LENGTH, SecurityConfig.TRANSFER_KEY_LENGTH)) {
                                    return@AppDialogActionButtons
                                }

                                isVerifyingPin = true
                                val pinChars = transferPinInput.toCharArray()

                                scope.launch {
                                    val result = try {
                                        if (pendingEncryptedChunks != null) {
                                            TransferCrypto.decryptAssembledChunks(
                                                pendingEncryptedChunks.orEmpty(),
                                                pinChars
                                            )
                                        } else if (pendingEncryptedPayload != null) {
                                            TransferCrypto.decryptTransferPayload(
                                                pendingEncryptedPayload.orEmpty(),
                                                pinChars
                                            )
                                        } else {
                                            Result.failure(IllegalArgumentException())
                                        }
                                    } finally {
                                        CryptoManager.zeroize(pinChars)
                                    }

                                    result.fold(
                                        onSuccess = { decryptedJson ->
                                            val previews = repository.parseAccountsForPreview(decryptedJson)
                                            if (previews.isNotEmpty()) {
                                                parsedAccounts = previews
                                                selectedAccountIds = previews.map { it.id }.toSet()
                                                currentStep = QrScannerStep.TRANSFER_SELECT_ACCOUNTS
                                            } else {
                                                pinErrorMessage = importAllAlreadyExistErrorText
                                                appHaptics.error()
                                            }
                                            isVerifyingPin = false
                                        },
                                        onFailure = { error ->
                                            if (error is TransferCrypto.ExpiredTransferException) {
                                                pinErrorMessage = qrExpiredErrorText
                                                appHaptics.error()
                                                delay(Motion.Duration.FEEDBACK_TOAST.toLong().milliseconds)
                                                resetToCamera()
                                            } else {
                                                failedPinAttempts++
                                                val remaining = SecurityConfig.TRANSFER_QR_MAX_PIN_ATTEMPTS - failedPinAttempts

                                                if (remaining <= 0) {
                                                    if (currentSessionId != 0L) {
                                                        blockedSessionIds.add(currentSessionId)
                                                    }
                                                    pendingEncryptedPayload?.let { blockedPayloadFingerprints.add(it.hashCode()) }
                                                    pinErrorMessage = pinMaxAttemptsErrorText
                                                    appHaptics.error()
                                                    delay(Motion.Duration.FEEDBACK_TOAST.toLong().milliseconds)
                                                    resetToCamera()
                                                } else {
                                                    pinErrorMessage = String.format(pinIncorrectAttemptsFormat, remaining)
                                                    appHaptics.error()
                                                }
                                            }
                                            isVerifyingPin = false
                                        }
                                    )
                                }
                            },
                            confirmEnabled = (transferPinInput.length == SecurityConfig.TRANSFER_KEY_LENGTH || transferPinInput.length == SecurityConfig.TRANSFER_QR_PIN_LENGTH) && !isVerifyingPin
                        )
                    }
                }

                QrScannerStep.TRANSFER_SELECT_ACCOUNTS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg)
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        // 1. Cabecera fija
                        Text(
                            text = stringResource(R.string.import_selection_title),
                            style = MaterialTheme.typography.titleLarge
                        )

                        // 2. Cuerpo central scrolleable aislado
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                        ) {
                            Text(
                                text = stringResource(R.string.import_selection_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            AccountImportSelectionList(
                                accounts = parsedAccounts,
                                selectedIds = selectedAccountIds,
                                onToggleAccount = { id ->
                                    selectedAccountIds = if (id in selectedAccountIds) {
                                        selectedAccountIds - id
                                    } else {
                                        selectedAccountIds + id
                                    }
                                },
                                onSelectAll = {
                                    selectedAccountIds = parsedAccounts.map { it.id }.toSet()
                                },
                                onDeselectAll = {
                                    selectedAccountIds = emptySet()
                                }
                            )
                        }

                        // 3. Pie fijo de acciones
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    resetToCamera()
                                },
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.none),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_details_back),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center
                                )
                            }

                            AppAnimatedButton(
                                text = stringResource(R.string.import_selection_confirm_button, selectedAccountIds.size),
                                onClick = {
                                    val accountsToImport = parsedAccounts.filter { it.id in selectedAccountIds }
                                    repository.importSelectedAccounts(accountsToImport)
                                    true
                                },
                                onActionConfirmed = {
                                    onScanSuccess()
                                    if (modalDismissHandler != null) {
                                        modalDismissHandler()
                                    } else {
                                        onDismiss()
                                    }
                                },
                                enabled = selectedAccountIds.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Procesa de forma segura un código QR escaneado (individual otpauth:// o transferencia de respaldo).
 *
 * Administra la detección de fragmentos individuales y transferencias multi-código con orden flexible,
 * emitiendo retroalimentación táctil y visual ante capturas exitosas o códigos duplicados.
 *
 * @param rawValue Cadena bruta leída por el escáner.
 * @param appHaptics Controlador de vibración y háptica del sistema.
 * @param blockedSessionIds Identificadores de sesiones bloqueadas por exceso de intentos.
 * @param blockedPayloadFingerprints Huellas de cargas útiles bloqueadas.
 * @param sessionChunks Mapa de fragmentos acumulados para la sesión activa.
 * @param currentSessionId Identificador de la sesión activa de transferencia.
 * @param onSessionUpdated Callback invocado cuando se detecta una nueva sesión o cambia la cantidad esperada de fragmentos.
 * @param onChunkScanned Callback invocado al registrar exitosamente un fragmento nuevo.
 * @param onChunkDuplicate Callback invocado al re-escanear un fragmento ya presente en la sesión.
 * @param onSingleOtpScanned Callback invocado al escanear una clave OTP individual (otpauth://).
 * @param onTransferPayloadReady Callback invocado para transferencias de un único fragmento.
 * @param onChunksReady Callback invocado cuando se recopilan todos los fragmentos requeridos.
 * @param onError Callback invocado ante fallos de análisis o bloqueos por seguridad.
 */
private suspend fun handleScannedBarcode(
    rawValue: String,
    appHaptics: com.example.appopt.ui.theme.AppHaptics,
    blockedSessionIds: Set<Long>,
    blockedPayloadFingerprints: Set<Int>,
    sessionChunks: MutableMap<Int, TransferQrChunk>,
    currentSessionId: Long,
    onSessionUpdated: (Long, Int) -> Unit,
    onChunkScanned: (Int) -> Unit,
    onChunkDuplicate: (Int) -> Unit,
    onSingleOtpScanned: (ParsedOtpData) -> Unit,
    onTransferPayloadReady: (String) -> Unit,
    onChunksReady: (List<TransferQrChunk>) -> Unit,
    onError: suspend () -> Unit
) {
    if (rawValue.startsWith("otpauth://", ignoreCase = true)) {
        val parseResult = OtpUriParser.parse(rawValue)
        if (parseResult.isSuccess) {
            val otpData = parseResult.getOrThrow()
            onSingleOtpScanned(otpData)
        } else {
            onError()
        }
        return
    }

    if (rawValue.startsWith(TransferCrypto.QR_TRANSFER_PREFIX, ignoreCase = true)) {
        if (rawValue.hashCode() in blockedPayloadFingerprints) {
            onError()
            return
        }

        try {
            val chunk = TransferCrypto.parseTransferChunk(rawValue)
            if (chunk.total > 1) {
                if (chunk.sessionId in blockedSessionIds) {
                    onError()
                    return
                }

                if (chunk.sessionId != currentSessionId) {
                    sessionChunks.clear()
                    onSessionUpdated(chunk.sessionId, chunk.total)
                }

                if (sessionChunks.containsKey(chunk.index)) {
                    appHaptics.click()
                    onChunkDuplicate(chunk.index)
                } else {
                    sessionChunks[chunk.index] = chunk
                    appHaptics.success()
                    onChunkScanned(chunk.index)

                    if (sessionChunks.size == chunk.total) {
                        delay(750L.milliseconds)
                        onChunksReady(sessionChunks.values.toList())
                    }
                }
            } else {
                onTransferPayloadReady(rawValue)
            }
        } catch (_: Exception) {
            onTransferPayloadReady(rawValue)
        }
        return
    }

    onError()
}

/**
 * Insignia animada central proyectada sobre el visor de cámara al capturar exitosamente un código QR.
 *
 * Emerge con animación de rebote elástico suave y desaparece automáticamente tras confirmar la captura.
 *
 * @param capturedChunkIndex Índice numérico del código QR capturado, o `null` si no hay animación activa.
 * @param modifier Modificador de diseño Compose.
 */
@Composable
private fun BoxScope.QrCaptureSuccessBadge(
    capturedChunkIndex: Int?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = capturedChunkIndex != null,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = Motion.Duration.FAST,
                easing = Motion.EasingCurve.Standard
            )
        ) + scaleIn(
            initialScale = 0.75f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = Motion.Duration.FAST,
                easing = Motion.EasingCurve.Standard
            )
        ) + scaleOut(
            targetScale = 0.85f,
            animationSpec = tween(
                durationMillis = Motion.Duration.FAST,
                easing = Motion.EasingCurve.Standard
            )
        ),
        modifier = modifier.align(Alignment.Center)
    ) {
        Surface(
            shape = RoundedCornerShape(Dimensions.CornerRadius.large),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            border = BorderStroke(
                width = Dimensions.Stroke.regular,
                color = MaterialTheme.colorScheme.primary
            ),
            shadowElevation = Dimensions.Elevation.cardDragging,
            modifier = Modifier.padding(Dimensions.Spacing.md)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs),
                modifier = Modifier.padding(
                    horizontal = Dimensions.Spacing.lg,
                    vertical = Dimensions.Spacing.md
                )
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(Dimensions.IconSize.hero)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(Dimensions.IconSize.large)
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.scan_transfer_center_captured_badge,
                        capturedChunkIndex ?: 1
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
