package com.example.appopt.ui.screens.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.security.TransferCrypto
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Pantalla de escaneo de códigos QR 2FA con CameraX, Google ML Kit y soporte para transferencias cifradas con PIN.
 *
 * Principio de privacidad y seguridad:
 * - El procesamiento de la imagen del código QR se realiza exclusivamente en el hardware local.
 * - Soporta códigos QR cifrados con AES-256-GCM solicitando un PIN de 6 dígitos antes de descifrar.
 * - No se guardan fotografías ni se envían datos a ningún servidor externo.
 *
 * @param onScanSuccess Callback invocado al escanear e importar exitosamente una cuenta.
 * @param onNavigateToManual Callback para navegar a la pantalla de adición manual.
 * @param onNavigateBack Callback para regresar a la pantalla anterior.
 * @param modifier Modificador de layout.
 */
@OptIn(ExperimentalGetImage::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onScanSuccess: () -> Unit,
    onNavigateToManual: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appHaptics = rememberAppHaptics()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val repository = AuthenticatorApp.instance.accountRepository
    val invalidQrErrorText = stringResource(R.string.scan_error_invalid_qr)
    val cameraInitErrorText = stringResource(R.string.scan_error_camera_init)
    val incorrectPinErrorText = stringResource(R.string.scan_transfer_pin_error_incorrect)
    val expiredQrErrorText = stringResource(R.string.scan_transfer_pin_error_expired)

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

    var isProcessingQr by remember { mutableStateOf(false) }

    // Estado del modal de ingreso de PIN para transferencias cifradas
    var pendingEncryptedPayload by remember { mutableStateOf<String?>(null) }
    var transferPinInput by remember { mutableStateOf("") }
    var pinErrorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifyingPin by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToManual) {
                        Icon(
                            Icons.Filled.Keyboard,
                            contentDescription = stringResource(R.string.home_add_manual_option)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
        ) {
            if (hasCameraPermission) {
                val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
                val barcodeScannerOptions = remember {
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                }
                val barcodeScanner = remember { BarcodeScanning.getClient(barcodeScannerOptions) }

                DisposableEffect(Unit) {
                    onDispose {
                        cameraExecutor.shutdown()
                        barcodeScanner.close()
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            var lastAnalysisTimestamp = 0L

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val now = System.currentTimeMillis()
                                if (isProcessingQr || pendingEncryptedPayload != null || (now - lastAnalysisTimestamp < 200L)) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                lastAnalysisTimestamp = now

                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )

                                    barcodeScanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                if (barcode.valueType == Barcode.TYPE_TEXT || barcode.valueType == Barcode.TYPE_UNKNOWN) {
                                                    val rawValue = barcode.rawValue ?: continue
                                                    val trimmed = rawValue.trim()

                                                    // 1. Caso QR Cifrado con PIN
                                                    if (trimmed.startsWith(TransferCrypto.QR_TRANSFER_PREFIX, ignoreCase = true) && !isProcessingQr && pendingEncryptedPayload == null) {
                                                        appHaptics.click()
                                                        pendingEncryptedPayload = trimmed
                                                        transferPinInput = ""
                                                        pinErrorMessage = null
                                                        break
                                                    }

                                                    // 2. Caso estándar otpauth:// o JSON legado
                                                    if ((trimmed.startsWith("otpauth://", ignoreCase = true) || trimmed.startsWith("{")) && !isProcessingQr && pendingEncryptedPayload == null) {
                                                        isProcessingQr = true
                                                        scope.launch {
                                                            val importResult = repository.importAccountsFromTransfer(trimmed)
                                                            importResult.onSuccess {
                                                                appHaptics.success()
                                                                onScanSuccess()
                                                            }.onFailure { _ ->
                                                                appHaptics.error()
                                                                isProcessingQr = false
                                                                snackbarHostState.showSnackbar(invalidQrErrorText)
                                                            }
                                                        }
                                                        break
                                                    }
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

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (_: Exception) {
                                appHaptics.error()
                                scope.launch {
                                    snackbarHostState.showSnackbar(cameraInitErrorText)
                                }
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Marco guía del escáner QR
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Dimensions.Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(Dimensions.ComponentSize.qrScannerBox)
                            .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
                            .border(Dimensions.Stroke.thick, MaterialTheme.colorScheme.primary, RoundedCornerShape(Dimensions.CornerRadius.large))
                    )

                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xl))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(
                            text = stringResource(R.string.scan_hint),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = Dimensions.Spacing.lg, vertical = Dimensions.Spacing.sm)
                        )
                    }
                }
            } else {
                // Estado de permiso denegado
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Dimensions.Spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(Dimensions.IconSize.hero)
                    )

                    Spacer(modifier = Modifier.height(Dimensions.Spacing.lg))

                    Text(
                        text = stringResource(R.string.scan_permission_required_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(Dimensions.Spacing.sm))

                    Text(
                        text = stringResource(R.string.scan_permission_required_description),
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xl))

                    Button(
                        onClick = {
                            appHaptics.click()
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(stringResource(R.string.action_grant_permission), style = MaterialTheme.typography.labelLarge)
                    }

                    Spacer(modifier = Modifier.height(Dimensions.Spacing.md))

                    OutlinedButton(
                        onClick = {
                            appHaptics.click()
                            onNavigateToManual()
                        },
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(stringResource(R.string.home_add_manual_option), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    // Modal: Solicitud de PIN para Transferencia Cifrada (AES-256-GCM)
    if (pendingEncryptedPayload != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isVerifyingPin) {
                    pendingEncryptedPayload = null
                    transferPinInput = ""
                    pinErrorMessage = null
                    isProcessingQr = false
                }
            },
            shape = RoundedCornerShape(Dimensions.CornerRadius.large),
            title = {
                Text(
                    text = stringResource(R.string.scan_transfer_pin_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
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
                            text = pinErrorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val payload = pendingEncryptedPayload ?: return@Button
                        val pinChars = transferPinInput.toCharArray()
                        isVerifyingPin = true
                        scope.launch {
                            try {
                                val result = repository.importAccountsFromTransfer(payload, pinChars)
                                result.onSuccess {
                                    appHaptics.success()
                                    pendingEncryptedPayload = null
                                    transferPinInput = ""
                                    pinErrorMessage = null
                                    onScanSuccess()
                                }.onFailure { error ->
                                    appHaptics.error()
                                    pinErrorMessage = when (error) {
                                        is TransferCrypto.ExpiredTransferException -> expiredQrErrorText
                                        is TransferCrypto.InvalidPinException -> incorrectPinErrorText
                                        else -> incorrectPinErrorText
                                    }
                                }
                            } finally {
                                pinChars.fill('0')
                                isVerifyingPin = false
                            }
                        }
                    },
                    enabled = transferPinInput.length == 6 && !isVerifyingPin,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Text(stringResource(R.string.scan_transfer_pin_confirm_button), style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isVerifyingPin) {
                            pendingEncryptedPayload = null
                            transferPinInput = ""
                            pinErrorMessage = null
                            isProcessingQr = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}
