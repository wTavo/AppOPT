package com.example.appopt.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.example.appopt.AuthenticatorApp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appopt.R
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.ui.screens.scan.QrScannerDialog
import com.example.appopt.ui.screens.scan.QrScannerMode
import com.example.appopt.ui.screens.settings.components.DriveSyncSettingsCard
import com.example.appopt.ui.screens.settings.components.PerformanceSettingsCard
import com.example.appopt.ui.screens.settings.components.PermissionsSettingsCard
import com.example.appopt.ui.screens.settings.components.TransferSettingsCard
import com.example.appopt.ui.screens.settings.dialogs.SettingsDialogContainer
import com.example.appopt.ui.screens.settings.dialogs.rememberDriveDialogCoordinator
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.BatteryOptimizationHelper
import com.example.appopt.util.DateTimeFormatter
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pantalla principal de configuración y preferencias del usuario.
 *
 * Administra el diagnóstico de rendimiento (FPS), la transferencia offline por códigos QR,
 * los permisos del sistema y la sincronización con cifrado de extremo a extremo (E2EE) en Google Drive.
 *
 * Principio de diseño:
 * - Vista puramente declarativa desacoplada de la lógica de negocio mediante [SettingsViewModel] (Directiva 8 y 13).
 * - Centralización tipográfica, espaciados y cadenas en español estándar (Directivas 1, 2 y 4).
 * - Gestión de modales unificada sin superposición de diálogos mediante [SettingsDialogContainer] (Directiva 14).
 *
 * @param onNavigateBack Callback invocado al presionar el botón de retorno.
 * @param onNavigateToScanQr Callback invocado para navegar hacia el escáner de importación QR.
 * @param viewModel Instancia del [SettingsViewModel] para gestionar el estado.
 * @param modifier Modificador de diseño Compose opcional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val appHaptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val authClient = remember { GoogleDriveManager.getAuthorizationClient(context) }

    val appLockManager = remember { AuthenticatorApp.instance.appLockManager }
    val isUnlocked by appLockManager.isUnlocked.collectAsStateWithLifecycle()
    val biometricAuthManager = remember { AuthenticatorApp.instance.biometricAuthManager }

    val driveErrorText = stringResource(R.string.settings_drive_error)
    val driveConnectedSuccessText = stringResource(R.string.settings_drive_connected_success)
    val servicesDeletedAfterExportText = stringResource(R.string.settings_services_deleted_after_export)
    val exportAuthTitle = stringResource(R.string.settings_transfer_export_auth_title)
    val exportAuthSubtitle = stringResource(R.string.settings_transfer_export_auth_subtitle)
    val importAuthTitle = stringResource(R.string.settings_transfer_import_auth_title)
    val importAuthSubtitle = stringResource(R.string.settings_transfer_import_auth_subtitle)
    val transferAuthFailedText = stringResource(R.string.settings_transfer_auth_failed)

    // Launchers de actividades para permisos y OAuth2 de Google
    var pendingAuthAction by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            try {
                val authResult = authClient.getAuthorizationResultFromIntent(activityResult.data)
                val token = authResult.accessToken
                if (token != null) {
                    GoogleDriveManager.currentAccessToken = token
                    val action = pendingAuthAction
                    pendingAuthAction = null
                    if (action != null) {
                        action(token)
                    } else {
                        viewModel.onGoogleDriveConnected(token)
                    }
                }
            } catch (_: ApiException) {
                pendingAuthAction = null
                scope.launch { snackbarHostState.showSnackbar(driveErrorText) }
            }
        } else {
            pendingAuthAction = null
        }
    }

    val requestGoogleAuthorization: ((String) -> Unit) -> Unit = { onAuthorized ->
        pendingAuthAction = onAuthorized
        authClient.authorize(GoogleDriveManager.getAuthorizationRequest())
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        authLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }
                } else {
                    val token = result.accessToken
                    if (token != null) {
                        val action = pendingAuthAction
                        pendingAuthAction = null
                        action?.invoke(token)
                    }
                }
            }
            .addOnFailureListener {
                pendingAuthAction = null
                scope.launch { snackbarHostState.showSnackbar(driveErrorText) }
            }
    }

    var showQrScannerDialog by remember { mutableStateOf(false) }

    val coordinator = rememberDriveDialogCoordinator(
        context = context,
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
        onRequestAuth = requestGoogleAuthorization
    )

    // Cierre defensivo de todos los diálogos al bloquearse la bóveda
    LaunchedEffect(isUnlocked) {
        if (!isUnlocked) {
            coordinator.closeAllDialogs()
            showQrScannerDialog = false
        }
    }

    // Diagnóstico en tiempo real de permisos: inicialización síncrona con el estado real
    // para garantizar que desde el fotograma 0 se muestre el color correcto (sin parpadeos rojo -> verde)
    val initialCamera = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val initialNotification = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
    val initialBattery = remember {
        BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    var isCameraPermissionGranted by remember { mutableStateOf(initialCamera) }
    var isNotificationPermissionGranted by remember { mutableStateOf(initialNotification) }
    var isBatteryOptimizationIgnored by remember { mutableStateOf(initialBattery) }

    // Proceso 1: Actualización de permisos al regresar de Configuración del sistema (ON_RESUME)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isCameraPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                isNotificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else {
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
                }
                isBatteryOptimizationIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Proceso 2: Reloj aislado de actualización de tiempo relativo de sincronización (cada minuto)
    var currentTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(uiState.isDriveConnected, uiState.lastSyncTimestamp) {
        if (uiState.isDriveConnected && uiState.lastSyncTimestamp > 0L) {
            while (isActive) {
                val now = System.currentTimeMillis()
                currentTick = now
                val millisUntilNextMinute = 60_000L - (now % 60_000L)
                delay(millisUntilNextMinute.coerceAtLeast(1_000L).milliseconds)
            }
        }
    }

    val formattedLastSync = remember(uiState.lastSyncTimestamp, currentTick) {
        if (uiState.lastSyncTimestamp == 0L) {
            null
        } else {
            DateTimeFormatter.formatRelativeSyncTime(context, uiState.lastSyncTimestamp)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> isCameraPermissionGranted = granted }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> isNotificationPermissionGranted = granted }

    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isBatteryOptimizationIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        if (isBatteryOptimizationIgnored) {
            appHaptics.success()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            GoogleDriveManager.clearDownloadCache()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Dimensions.Spacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))

            // 1. Tarjeta de Permisos de la Aplicación
            PermissionsSettingsCard(
                isCameraGranted = isCameraPermissionGranted,
                isNotificationGranted = isNotificationPermissionGranted,
                isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                onRequestCameraPermission = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                },
                onRequestBatteryOptimization = {
                    try {
                        batteryOptimizationLauncher.launch(
                            BatteryOptimizationHelper.createIgnoreBatteryOptimizationIntent(context)
                        )
                    } catch (_: Exception) {
                        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                    }
                }
            )

            // 2. Tarjeta de Rendimiento y Diagnóstico
            PerformanceSettingsCard(
                isFpsOverlayEnabled = uiState.isFpsOverlayEnabled,
                onFpsOverlayChanged = { enabled -> viewModel.setFpsOverlayEnabled(enabled) }
            )

            // 3. Tarjeta de Transferencia Offline por Código QR
            TransferSettingsCard(
                accounts = uiState.accounts,
                onExportClick = {
                    val activity = context as? FragmentActivity
                    if (activity != null) {
                        biometricAuthManager.authenticate(
                            activity = activity,
                            title = exportAuthTitle,
                            subtitle = exportAuthSubtitle,
                            onSuccess = {
                                appHaptics.success()
                                coordinator.showExportDialog = true
                            },
                            onError = { errorCode, _ ->
                                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                    errorCode != BiometricPrompt.ERROR_CANCELED
                                ) {
                                    appHaptics.error()
                                    scope.launch { snackbarHostState.showSnackbar(transferAuthFailedText) }
                                }
                            },
                            onFailed = { appHaptics.error() }
                        )
                    } else {
                        coordinator.showExportDialog = true
                    }
                },
                onImportClick = {
                    val activity = context as? FragmentActivity
                    if (activity != null) {
                        biometricAuthManager.authenticate(
                            activity = activity,
                            title = importAuthTitle,
                            subtitle = importAuthSubtitle,
                            onSuccess = {
                                appHaptics.success()
                                showQrScannerDialog = true
                            },
                            onError = { errorCode, _ ->
                                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                    errorCode != BiometricPrompt.ERROR_CANCELED
                                ) {
                                    appHaptics.error()
                                    scope.launch { snackbarHostState.showSnackbar(transferAuthFailedText) }
                                }
                            },
                            onFailed = { appHaptics.error() }
                        )
                    } else {
                        showQrScannerDialog = true
                    }
                }
            )

            // 4. Tarjeta de Copia de Seguridad y Sincronización en Google Drive
            DriveSyncSettingsCard(
                isDriveConnected = uiState.isDriveConnected,
                isDriveLoading = uiState.isSyncActive,
                formattedLastSync = formattedLastSync,
                driveBackupExists = uiState.driveBackupExists,
                hasUnsyncedChanges = uiState.hasUnsyncedChanges,
                lastSyncTimestamp = uiState.lastSyncTimestamp,
                isAutoSyncEnabled = uiState.isAutoSyncEnabled,
                isSyncMobileDataAllowed = uiState.isSyncMobileDataAllowed,
                hasLocalAccounts = uiState.accounts.isNotEmpty(),
                onConnectClick = {
                    requestGoogleAuthorization { token ->
                        viewModel.onGoogleDriveConnected(token)
                        scope.launch { snackbarHostState.showSnackbar(driveConnectedSuccessText) }
                    }
                },
                onManualSyncClick = {
                    coordinator.executeWithAuth { token ->
                        appHaptics.click()
                        viewModel.executeManualSync(context, token)
                    }
                },
                onCreateBackupClick = {
                    if (uiState.driveBackupExists) {
                        coordinator.showOverwriteWarningDialog = true
                    } else {
                        coordinator.showDriveProtectDialog = true
                    }
                },
                onBackupDetailsClick = {
                    coordinator.openBackupDetails(uiState)
                },
                onDisconnectClick = { coordinator.showDisconnectConfirmDialog = true },
                onAutoSyncToggle = { enabled -> viewModel.setAutoSyncEnabled(enabled, context) },
                onMobileDataToggle = { allowed -> viewModel.setSyncMobileDataAllowed(allowed, context) }
            )

            Spacer(modifier = Modifier.height(Dimensions.Spacing.sm))
        }
    }

    // Orquestación modular de diálogos modales
    SettingsDialogContainer(
        isUnlocked = isUnlocked,
        uiState = uiState,
        formattedLastSync = formattedLastSync,
        showDisconnectConfirmDialog = coordinator.showDisconnectConfirmDialog,
        onDismissDisconnectConfirm = { coordinator.showDisconnectConfirmDialog = false },
        onConfirmDisconnect = { coordinator.confirmDisconnect() },
        showExportDialog = coordinator.showExportDialog,
        onDismissExport = { coordinator.showExportDialog = false },
        onExportBatchesPayload = { selectedIds, pinChars -> viewModel.exportAccountsInBatches(selectedIds, pinChars) },
        onCompleteExport = { exportedIds, keepOnDevice ->
            if (!keepOnDevice && exportedIds.isNotEmpty()) {
                viewModel.deleteExportedAccounts(exportedIds)
                scope.launch { snackbarHostState.showSnackbar(servicesDeletedAfterExportText) }
            }
            coordinator.showExportDialog = false
        },
        showDriveProtectDialog = coordinator.showDriveProtectDialog,
        onDismissDriveProtect = { coordinator.showDriveProtectDialog = false },
        onProtectAndSync = { primary, mnemonic -> coordinator.protectAndSync(primary, mnemonic) },
        showDriveDecryptDialog = coordinator.showDriveDecryptDialog,
        onDismissDriveDecrypt = { coordinator.showDriveDecryptDialog = false },
        onRestoreDriveDecrypt = { passChars -> coordinator.restoreDriveDecrypt(passChars) },
        showBackupDetailsDialog = coordinator.showBackupDetailsDialog,
        isBackupHistoryLoading = coordinator.isHistoryLoadingSynchronous,
        onDismissBackupDetails = {
            coordinator.showBackupDetailsDialog = false
            coordinator.isHistoryLoadingSynchronous = false
        },
        onForceRefreshBackupHistory = { coordinator.forceRefreshHistory() },
        onRestoreBackupHistoryItem = { item, passChars -> coordinator.restoreBackupHistoryItem(item, passChars) },
        onDeleteBackupHistoryItem = { item, passChars -> coordinator.deleteBackupHistoryItem(item, passChars) },
        showOverwriteWarningDialog = coordinator.showOverwriteWarningDialog,
        onDismissOverwriteWarning = { coordinator.showOverwriteWarningDialog = false },
        onConfirmOverwrite = {
            coordinator.showOverwriteWarningDialog = false
            coordinator.showDriveProtectDialog = true
        },
        onRestoreInstead = {
            coordinator.showOverwriteWarningDialog = false
            coordinator.executeWithAuth { coordinator.showDriveDecryptDialog = true }
        }
    )

    if (isUnlocked) {
        if (showQrScannerDialog) {
            QrScannerDialog(
                mode = QrScannerMode.TRANSFER_MIGRATION,
                title = stringResource(R.string.scan_import_title),
                onDismiss = { showQrScannerDialog = false },
                onScanSuccess = {
                    showQrScannerDialog = false
                }
            )
        }
    }
}
