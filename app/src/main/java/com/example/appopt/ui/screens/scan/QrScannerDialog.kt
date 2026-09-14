package com.example.appopt.ui.screens.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.domain.totp.OtpUriParser
import com.example.appopt.security.CryptoManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.security.TransferCrypto
import com.example.appopt.security.TransferQrChunk
import com.example.appopt.ui.components.AccountImportSelectionList
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private enum class QrScannerStep {
    CAMERA,
    TRANSFER_PIN,
    TRANSFER_SELECT_ACCOUNTS
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onScanSuccess: () -> Unit,
    onNavigateToManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appHaptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val repository = remember { AuthenticatorApp.instance.accountRepository }

    var currentStep by remember { mutableStateOf(QrScannerStep.CAMERA) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }

    var isProcessingBarcode by remember { mutableStateOf(false) }
    var lastScannedPayload by remember { mutableStateOf<String?>(null) }

    val blockedSessionIds = remember { mutableSetOf<Long>() }
    val blockedPayloadFingerprints = remember { mutableSetOf<Int>() }

    val sessionChunks = remember { mutableStateMapOf<Int, TransferQrChunk>() }
    var currentSessionId by remember { mutableLongStateOf(0L) }
    var totalExpectedChunks by remember { mutableIntStateOf(0) }

    var pendingEncryptedPayload by remember { mutableStateOf<String?>(null) }
    var pendingEncryptedChunks by remember { mutableStateOf<List<TransferQrChunk>?>(null) }
    var transferPinInput by remember { mutableStateOf("") }
    var pinErrorMessage by remember { mutableStateOf<String?>(null) }
    var failedPinAttempts by remember { mutableIntStateOf(0) }
    var isVerifyingPin by remember { mutableStateOf(false) }

    var parsedAccounts by remember { mutableStateOf<List<ParsedAccountPreview>>(emptyList()) }
    var selectedAccountIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    AppModalDialog(
        onDismissRequest = {
            if (!isVerifyingPin) {
                if (currentStep == QrScannerStep.TRANSFER_SELECT_ACCOUNTS || currentStep == QrScannerStep.TRANSFER_PIN) {
                    currentStep = QrScannerStep.CAMERA
                    transferPinInput = ""
                    pinErrorMessage = null
                    isProcessingBarcode = false
                    pendingEncryptedPayload = null
                    pendingEncryptedChunks = null
                } else {
                    onDismiss()
                }
            }
        },
        modifier = modifier
    ) {
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
                            .padding(Dimensions.Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.scan_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!hasCameraPermission) {
                            Surface(
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(Dimensions.Spacing.lg),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(Dimensions.IconSize.hero)
                                    )
                                    Spacer(modifier = Modifier.height(Dimensions.Spacing.sm))
                                    Text(
                                        text = stringResource(R.string.scan_permission_required_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(Dimensions.Spacing.md))
                                    Button(
                                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                                    ) {
                                        Text(stringResource(R.string.action_grant_permission))
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
                                    .background(Color.Black)
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        val previewView = PreviewView(ctx).apply {
                                            scaleType = PreviewView.ScaleType.FILL_CENTER
                                        }
                                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                        val cameraExecutor = Executors.newSingleThreadExecutor()

                                        cameraProviderFuture.addListener({
                                            val cameraProvider = cameraProviderFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.surfaceProvider = previewView.surfaceProvider
                                            }

                                            val barcodeScanner = BarcodeScanning.getClient(
                                                BarcodeScannerOptions.Builder()
                                                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                                    .build()
                                            )

                                            val imageAnalysis = ImageAnalysis.Builder()
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()

                                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                                val mediaImage = imageProxy.image
                                                if (mediaImage != null && !isProcessingBarcode) {
                                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                                    barcodeScanner.process(image)
                                                        .addOnSuccessListener { barcodes ->
                                                            val rawValue = barcodes.firstOrNull()?.rawValue
                                                            if (!rawValue.isNullOrBlank() && rawValue != lastScannedPayload) {
                                                                val trimmed = rawValue.trim()
                                                                lastScannedPayload = trimmed
                                                                isProcessingBarcode = true

                                                                scope.launch {
                                                                    handleScannedBarcode(
                                                                        rawValue = trimmed,
                                                                        repository = repository,
                                                                        appHaptics = appHaptics,
                                                                        blockedSessionIds = blockedSessionIds,
                                                                        blockedPayloadFingerprints = blockedPayloadFingerprints,
                                                                        sessionChunks = sessionChunks,
                                                                        currentSessionId = currentSessionId,
                                                                        totalExpectedChunks = totalExpectedChunks,
                                                                        onSessionUpdated = { newSessionId, newExpected ->
                                                                            currentSessionId = newSessionId
                                                                            totalExpectedChunks = newExpected
                                                                        },
                                                                        onSingleOtpImported = {
                                                                            appHaptics.success()
                                                                            onScanSuccess()
                                                                            onDismiss()
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
                                                                            delay(1500)
                                                                            isProcessingBarcode = false
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        .addOnCompleteListener {
                                                            imageProxy.close()
                                                        }
                                                } else {
                                                    imageProxy.close()
                                                }
                                            }

                                            try {
                                                cameraProvider.unbindAll()
                                                val camera = cameraProvider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                                    preview,
                                                    imageAnalysis
                                                )
                                                cameraControl = camera.cameraControl
                                            } catch (_: Exception) {}
                                        }, ContextCompat.getMainExecutor(ctx))

                                        previewView
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                IconButton(
                                    onClick = {
                                        appHaptics.click()
                                        isTorchOn = !isTorchOn
                                        cameraControl?.enableTorch(isTorchOn)
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.50f),
                                        contentColor = if (isTorchOn) MaterialTheme.colorScheme.primary else Color.White
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(Dimensions.Spacing.sm)
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                        contentDescription = stringResource(R.string.scan_torch_toggle),
                                        modifier = Modifier.size(Dimensions.IconSize.small)
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.scan_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                contentPadding = PaddingValues(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.none),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                            ) {
                                Text(
                                    text = stringResource(R.string.action_close),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    appHaptics.click()
                                    onDismiss()
                                    onNavigateToManual()
                                },
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                contentPadding = PaddingValues(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.none),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Keyboard,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.IconSize.small)
                                )
                                Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                                Text(
                                    text = stringResource(R.string.scan_manual_switch),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                QrScannerStep.TRANSFER_PIN -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        Text(
                            text = stringResource(R.string.scan_transfer_pin_dialog_title),
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(
                            text = stringResource(R.string.scan_transfer_pin_dialog_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = transferPinInput,
                            onValueChange = { input ->
                                if (input.length <= 6 && input.all { it.isDigit() }) {
                                    transferPinInput = input
                                    pinErrorMessage = null
                                }
                            },
                            label = { Text(stringResource(R.string.scan_transfer_pin_input_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
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

                        AppDialogActionButtons(
                            dismissText = stringResource(R.string.settings_drive_details_back),
                            onDismiss = {
                                if (!isVerifyingPin) {
                                    currentStep = QrScannerStep.CAMERA
                                    isProcessingBarcode = false
                                }
                            },
                            confirmText = stringResource(R.string.scan_transfer_pin_confirm_button),
                            onConfirm = {
                                val payload = pendingEncryptedPayload
                                val chunks = pendingEncryptedChunks
                                if (payload == null && chunks == null) return@AppDialogActionButtons

                                isVerifyingPin = true
                                pinErrorMessage = null

                                scope.launch {
                                    val pinChars = transferPinInput.toCharArray()
                                    try {
                                        val decryptResult = if (payload != null) {
                                            TransferCrypto.decryptTransferPayload(payload, pinChars)
                                        } else {
                                            TransferCrypto.decryptAssembledChunks(chunks.orEmpty(), pinChars)
                                        }

                                        if (decryptResult.isSuccess) {
                                            val decryptedJson = decryptResult.getOrThrow()
                                            val previews = repository.parseAccountsForPreview(decryptedJson)
                                            if (previews.isNotEmpty()) {
                                                parsedAccounts = previews
                                                selectedAccountIds = previews.map { it.id }.toSet()
                                                appHaptics.success()
                                                currentStep = QrScannerStep.TRANSFER_SELECT_ACCOUNTS
                                            } else {
                                                pinErrorMessage = context.getString(R.string.scan_error_invalid_qr)
                                                appHaptics.error()
                                            }
                                        } else {
                                            failedPinAttempts++
                                            val remainingAttempts = SecurityConfig.TRANSFER_QR_MAX_PIN_ATTEMPTS - failedPinAttempts

                                            if (remainingAttempts <= 0) {
                                                payload?.let { blockedPayloadFingerprints.add(it.hashCode()) }
                                                pinErrorMessage = context.getString(R.string.scan_transfer_pin_error_max_attempts)
                                                appHaptics.error()
                                            } else {
                                                pinErrorMessage = context.getString(
                                                    R.string.scan_transfer_pin_error_incorrect_attempts,
                                                    remainingAttempts
                                                )
                                                appHaptics.error()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        pinErrorMessage = e.localizedMessage ?: context.getString(R.string.scan_error_invalid_qr)
                                        appHaptics.error()
                                    } finally {
                                        pinChars.fill('0')
                                        isVerifyingPin = false
                                    }
                                }
                            },
                            confirmEnabled = transferPinInput.length == 6 && !isVerifyingPin
                        )
                    }
                }

                QrScannerStep.TRANSFER_SELECT_ACCOUNTS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        Text(
                            text = stringResource(R.string.import_selection_title),
                            style = MaterialTheme.typography.titleLarge
                        )

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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    currentStep = QrScannerStep.CAMERA
                                    isProcessingBarcode = false
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
                                    onDismiss()
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

private suspend fun handleScannedBarcode(
    rawValue: String,
    repository: com.example.appopt.domain.repository.AccountRepository,
    appHaptics: com.example.appopt.ui.theme.AppHaptics,
    blockedSessionIds: Set<Long>,
    blockedPayloadFingerprints: Set<Int>,
    sessionChunks: MutableMap<Int, TransferQrChunk>,
    currentSessionId: Long,
    totalExpectedChunks: Int,
    onSessionUpdated: (Long, Int) -> Unit,
    onSingleOtpImported: () -> Unit,
    onTransferPayloadReady: (String) -> Unit,
    onChunksReady: (List<TransferQrChunk>) -> Unit,
    onError: suspend () -> Unit
) {
    if (rawValue.startsWith("otpauth://", ignoreCase = true)) {
        val parseResult = OtpUriParser.parse(rawValue)
        if (parseResult.isSuccess) {
            val otpData = parseResult.getOrThrow()
            try {
                repository.saveAccount(
                    issuer = otpData.issuer,
                    accountName = otpData.accountName,
                    secretBytes = otpData.secretBytes,
                    algorithm = otpData.algorithm,
                    digits = otpData.digits,
                    period = otpData.period,
                    type = otpData.type,
                    counter = otpData.counter
                )
                CryptoManager.zeroize(otpData.secretBytes)
                onSingleOtpImported()
            } catch (_: Exception) {
                onError()
            }
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

                sessionChunks[chunk.index] = chunk
                appHaptics.dragTick()

                if (sessionChunks.size == chunk.total) {
                    onChunksReady(sessionChunks.values.toList())
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
