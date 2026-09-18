package com.example.appopt.ui.screens.scan.components

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Componente modular de escáner CameraX integrado con MLKit Barcode Scanning.
 *
 * Responsabilidades:
 * - Activación bajo demanda: Permanece en estado de reposo limpio hasta que el usuario activa explícitamente la cámara.
 * - Animación de carga real y asíncrona: Muestra un indicador de progreso mientras el hardware de la cámara se inicializa.
 * - Solicitud y diagnóstico interactivo de permisos de cámara.
 * - Binding automático al ciclo de vida del LifecycleOwner.
 * - Análisis continuo de frames en segundo plano con estrategia [ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST].
 * - Control de linterna / flash con respuesta háptica integrada.
 *
 * @param isProcessingBarcode Bandera que pausa la emisión de nuevos códigos mientras se procesa el actual.
 * @param onBarcodeScanned Callback invocado cuando se detecta un código QR válido.
 * @param modifier Modificador de diseño Compose.
 * @param isCameraActive Indica si la cámara ha sido activada por el usuario o por navegación previa.
 * @param onCameraActiveChange Callback invocado cuando el estado de activación de la cámara cambia.
 * @param onNavigateToManual Callback opcional invocado si el usuario decide ingresar la clave manualmente ante falta de permisos.
 * @param overlayContent Contenido composable superpuesto opcional alineado en el contenedor de cámara.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraXBarcodeScannerView(
    isProcessingBarcode: Boolean,
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
    isCameraActive: Boolean = false,
    onCameraActiveChange: (Boolean) -> Unit = {},
    onNavigateToManual: () -> Unit = {},
    overlayContent: @Composable BoxScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appHaptics = rememberAppHaptics()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isCameraInitializing by remember(isCameraActive) {
        mutableStateOf(isCameraActive && hasCameraPermission)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
            if (!isGranted) {
                isCameraInitializing = false
                onCameraActiveChange(false)
            }
        }
    )

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }

    if (!isCameraActive) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(Dimensions.ComponentSize.qrScannerBox)
                .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(Dimensions.Spacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.lg)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimensions.IconSize.hero)
                )

                Button(
                    onClick = {
                        appHaptics.click()
                        isCameraInitializing = true
                        if (!hasCameraPermission) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                        onCameraActiveChange(true)
                    },
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    modifier = Modifier.heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.IconSize.small)
                    )
                    Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                    Text(
                        text = stringResource(R.string.scan_camera_start_action),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    } else if (!hasCameraPermission) {
        QrCameraPermissionView(
            onRequestPermission = {
                isCameraInitializing = true
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onNavigateToManual = onNavigateToManual,
            modifier = modifier
                .fillMaxWidth()
                .height(Dimensions.ComponentSize.qrScannerBox)
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(Dimensions.ComponentSize.qrScannerBox)
                .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val cameraExecutor = Executors.newSingleThreadExecutor()

                    cameraProviderFuture.addListener({
                        try {
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
                                            if (!rawValue.isNullOrBlank()) {
                                                onBarcodeScanned(rawValue.trim())
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            cameraControl = camera.cameraControl
                            isCameraInitializing = false
                        } catch (_: Exception) {
                            isCameraInitializing = false
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isCameraInitializing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.70f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimensions.IconSize.hero),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = Dimensions.Spacing.xs
                    )
                }
            }

            if (!isCameraInitializing) {
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
                        .size(Dimensions.ComponentSize.actionIconButton)
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = stringResource(R.string.scan_torch_toggle),
                        modifier = Modifier.size(Dimensions.IconSize.small)
                    )
                }
            }

            overlayContent()
        }
    }
}
