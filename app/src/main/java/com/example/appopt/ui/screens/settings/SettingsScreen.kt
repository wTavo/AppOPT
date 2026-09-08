package com.example.appopt.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appopt.R
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.ui.screens.settings.components.DriveSyncSettingsCard
import com.example.appopt.ui.screens.settings.components.PerformanceSettingsCard
import com.example.appopt.ui.screens.settings.components.PermissionsSettingsCard
import com.example.appopt.ui.screens.settings.components.TransferSettingsCard
import com.example.appopt.ui.screens.settings.dialogs.DriveBackupDetailsDialog
import com.example.appopt.ui.screens.settings.dialogs.DriveDecryptDialog
import com.example.appopt.ui.screens.settings.dialogs.DriveDisconnectConfirmDialog
import com.example.appopt.ui.screens.settings.dialogs.DriveOverwriteWarningDialog
import com.example.appopt.ui.screens.settings.dialogs.DriveProtectDialog
import com.example.appopt.ui.screens.settings.dialogs.ExportServicesDialog
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
 * - Gestión de modales unificada sin superposición de diálogos (Directiva 14).
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
    onNavigateToScanQr: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appHaptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val authClient = remember { GoogleDriveManager.getAuthorizationClient(context) }
    var driveAccessToken by remember { mutableStateOf<String?>(null) }

    // Control de visibilidad de modales
    var showExportDialog by remember { mutableStateOf(false) }
    var showBackupDetailsDialog by remember { mutableStateOf(false) }
    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }
    var showDriveProtectDialog by remember { mutableStateOf(false) }
    var showDriveDecryptDialog by remember { mutableStateOf(false) }
    var showOverwriteWarningDialog by remember { mutableStateOf(false) }

    // Mensajes centralizados de retroalimentación
    val driveErrorText = stringResource(R.string.settings_drive_error)
    val driveConnectedSuccessText = stringResource(R.string.settings_drive_connected_success)
    val driveDisconnectedSuccessText = stringResource(R.string.settings_drive_disconnected_success)
    val servicesDeletedAfterExportText = stringResource(R.string.settings_services_deleted_after_export)
    val driveSyncSuccessText = stringResource(R.string.settings_drive_sync_success)
    val driveDecryptErrorText = stringResource(R.string.settings_drive_decrypt_error)
    val driveDeleteSingleSuccessText = stringResource(R.string.settings_drive_delete_single_success)
    val driveDeleteAllSuccessText = stringResource(R.string.settings_drive_delete_all_success)

    // Diagnóstico en tiempo real de permisos y reloj del sistema
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isCameraPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var isNotificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        )
    }
    var isBatteryOptimizationIgnored by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isCameraPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            isNotificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
            isBatteryOptimizationIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
            while (isActive) {
                val now = System.currentTimeMillis()
                currentTick = now
                val millisUntilNextMinute = 60_000L - (now % 60_000L)
                delay(millisUntilNextMinute.coerceAtLeast(1_000L).milliseconds)
            }
        }
    }

    val formattedLastSync = remember(uiState.effectiveLastSyncTimestamp, currentTick) {
        if (uiState.effectiveLastSyncTimestamp == 0L) {
            null
        } else {
            DateTimeFormatter.formatRelativeSyncTime(context, uiState.effectiveLastSyncTimestamp)
        }
    }

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
                    driveAccessToken = token
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
                scope.launch {
                    snackbarHostState.showSnackbar(driveErrorText)
                }
            }
        } else {
            pendingAuthAction = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> isCameraPermissionGranted = granted }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> isNotificationPermissionGranted = granted }

    fun requestGoogleAuthorization(onAuthorized: (String) -> Unit) {
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
                        driveAccessToken = token
                        val action = pendingAuthAction
                        pendingAuthAction = null
                        action?.invoke(token)
                    }
                }
            }
            .addOnFailureListener {
                pendingAuthAction = null
                scope.launch {
                    snackbarHostState.showSnackbar(driveErrorText)
                }
            }
    }

    LaunchedEffect(Unit) {
        if (uiState.isDriveConnected) {
            val token = driveAccessToken ?: GoogleDriveManager.currentAccessToken
            if (token != null) {
                driveAccessToken = token
                viewModel.checkRemoteBackupOnStartup(token)
            } else {
                requestGoogleAuthorization { newToken ->
                    viewModel.checkRemoteBackupOnStartup(newToken)
                }
            }
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
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                },
                onRequestBatteryOptimization = {
                    BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
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
                onExportClick = { showExportDialog = true },
                onImportClick = onNavigateToScanQr
            )

            // 4. Tarjeta de Copia de Seguridad y Sincronización en Google Drive
            DriveSyncSettingsCard(
                isDriveConnected = uiState.isDriveConnected,
                isDriveLoading = uiState.isSyncActive,
                isCheckingDriveBackup = uiState.isCheckingDriveBackup,
                formattedLastSync = formattedLastSync,
                driveBackupExists = uiState.driveBackupExists,
                hasUnsyncedChanges = uiState.hasUnsyncedChanges,
                lastSyncTimestamp = uiState.effectiveLastSyncTimestamp,
                isAutoSyncEnabled = uiState.isAutoSyncEnabled,
                isSyncMobileDataAllowed = uiState.isSyncMobileDataAllowed,
                onConnectClick = {
                    requestGoogleAuthorization { token ->
                        viewModel.onGoogleDriveConnected(token)
                        scope.launch { snackbarHostState.showSnackbar(driveConnectedSuccessText) }
                    }
                },
                onManualSyncClick = {
                    val executeSync: (String) -> Unit = { token ->
                        viewModel.executeManualSync(context, token) { success ->
                            if (success) {
                                appHaptics.success()
                            } else {
                                appHaptics.error()
                                scope.launch { snackbarHostState.showSnackbar(driveErrorText) }
                            }
                        }
                    }
                    val token = driveAccessToken ?: GoogleDriveManager.currentAccessToken
                    if (token == null) {
                        requestGoogleAuthorization { executeSync(it) }
                    } else {
                        executeSync(token)
                    }
                },
                onRestoreClick = {
                    val token = driveAccessToken ?: GoogleDriveManager.currentAccessToken
                    if (token == null) {
                        requestGoogleAuthorization { showDriveDecryptDialog = true }
                    } else {
                        showDriveDecryptDialog = true
                    }
                },
                onCreateBackupClick = {
                    if (uiState.driveBackupExists) {
                        showOverwriteWarningDialog = true
                    } else {
                        showDriveProtectDialog = true
                    }
                },
                onBackupDetailsClick = {
                    showBackupDetailsDialog = true
                    val executeFetch: (String) -> Unit = { token ->
                        viewModel.fetchBackupHistoryIfNeeded(
                            token = token,
                            onAuthExpired = {
                                driveAccessToken = null
                                GoogleDriveManager.currentAccessToken = null
                                requestGoogleAuthorization { freshToken ->
                                    viewModel.fetchBackupHistoryIfNeeded(freshToken) {}
                                }
                            }
                        )
                    }
                    val token = driveAccessToken ?: GoogleDriveManager.currentAccessToken
                    if (token == null) {
                        requestGoogleAuthorization { executeFetch(it) }
                    } else {
                        executeFetch(token)
                    }
                },
                onDisconnectClick = { showDisconnectConfirmDialog = true },
                onAutoSyncToggle = { enabled -> viewModel.setAutoSyncEnabled(enabled, context) },
                onMobileDataToggle = { allowed -> viewModel.setSyncMobileDataAllowed(allowed, context) }
            )
        }
    }

    // Modal: Confirmación de Desvinculación de Google Drive
    if (showDisconnectConfirmDialog) {
        DriveDisconnectConfirmDialog(
            onConfirm = {
                showDisconnectConfirmDialog = false
                driveAccessToken = null
                viewModel.disconnectGoogleDrive(context)
                scope.launch { snackbarHostState.showSnackbar(driveDisconnectedSuccessText) }
            },
            onDismiss = { showDisconnectConfirmDialog = false }
        )
    }

    // Modal: Selección y Exportación por Código QR
    if (showExportDialog) {
        ExportServicesDialog(
            accounts = uiState.accounts,
            onExportPayload = { selectedIds -> viewModel.exportAccounts(selectedIds) },
            onCompleteExport = { exportedIds, keepOnDevice ->
                if (!keepOnDevice && exportedIds.isNotEmpty()) {
                    viewModel.deleteExportedAccounts(exportedIds)
                    scope.launch { snackbarHostState.showSnackbar(servicesDeletedAfterExportText) }
                }
                showExportDialog = false
            },
            onDismiss = { showExportDialog = false }
        )
    }

    // Modal: Configuración de Protección E2EE (3 Pasos)
    if (showDriveProtectDialog) {
        DriveProtectDialog(
            onProtectAndSync = { primaryPassChars, emergencyMnemonicChars ->
                showDriveProtectDialog = false
                val token = driveAccessToken ?: GoogleDriveManager.currentAccessToken
                if (token != null) {
                    viewModel.createProtectedBackup(context, token, primaryPassChars, emergencyMnemonicChars) { success ->
                        scope.launch {
                            snackbarHostState.showSnackbar(if (success) driveSyncSuccessText else driveErrorText)
                        }
                    }
                }
            },
            onDismiss = { showDriveProtectDialog = false }
        )
    }

    // Modal: Descifrado y Restauración Directa desde la Tarjeta Principal
    if (showDriveDecryptDialog) {
        DriveDecryptDialog(
            backupDateMillis = uiState.driveBackupInfo?.modifiedTimeMillis,
            deviceName = uiState.driveBackupInfo?.deviceName,
            isMostRecent = true,
            onRestore = { passChars ->
                showDriveDecryptDialog = false
                val token = driveAccessToken ?: GoogleDriveManager.currentAccessToken
                if (token != null) {
                    viewModel.restoreFromBackup(context, token, passChars) { result ->
                        result.onSuccess { count ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.applicationContext.getString(R.string.settings_drive_restore_success, count)
                                )
                            }
                        }.onFailure { error ->
                            val isDecryptFailure = error.message?.contains("clave", ignoreCase = true) == true ||
                                    error.message?.contains("descargar", ignoreCase = true) == true
                            scope.launch {
                                snackbarHostState.showSnackbar(if (isDecryptFailure) driveDecryptErrorText else driveErrorText)
                            }
                        }
                    }
                }
            },
            onDismiss = { showDriveDecryptDialog = false }
        )
    }

    // Modal: Historial de Versiones (Point-in-Time), Restauración In-Situ y Borrado Granular / Total
    if (showBackupDetailsDialog) {
        DriveBackupDetailsDialog(
            backupItems = uiState.backupHistoryList,
            isLoading = uiState.isFetchingBackupHistory || uiState.isRefreshingBackupHistory,
            lastFetchTimestamp = uiState.lastHistoryFetchTimestamp,
            lastSyncTimestamp = uiState.effectiveLastSyncTimestamp,
            hasUnsyncedChanges = uiState.hasUnsyncedChanges,
            onForceRefresh = {
                val token = driveAccessToken ?: GoogleDriveManager.currentAccessToken
                if (token == null) {
                    requestGoogleAuthorization { freshToken ->
                        viewModel.forceRefreshBackupHistory(freshToken) {
                            driveAccessToken = null
                            GoogleDriveManager.currentAccessToken = null
                        }
                    }
                } else {
                    viewModel.forceRefreshBackupHistory(
                        token = token,
                        onAuthExpired = {
                            driveAccessToken = null
                            GoogleDriveManager.currentAccessToken = null
                            requestGoogleAuthorization { freshToken ->
                                viewModel.forceRefreshBackupHistory(freshToken) {}
                            }
                        }
                    )
                }
            },
            onRestoreBackup = { item, passChars ->
                showBackupDetailsDialog = false
                val token = driveAccessToken ?: GoogleDriveManager.currentAccessToken
                if (token != null) {
                    viewModel.restoreSpecificBackup(context, token, item.fileId, passChars) { result ->
                        result.onSuccess { count ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.applicationContext.getString(R.string.settings_drive_restore_success, count)
                                )
                            }
                        }.onFailure { error ->
                            val isDecryptFailure = error.message?.contains("clave", ignoreCase = true) == true ||
                                    error.message?.contains("descargar", ignoreCase = true) == true
                            scope.launch {
                                snackbarHostState.showSnackbar(if (isDecryptFailure) driveDecryptErrorText else driveErrorText
                                )
                            }
                        }
                    }
                }
            },
            onDeleteSpecificBackup = { item ->
                val token = driveAccessToken ?: GoogleDriveManager.currentAccessToken
                if (token != null) {
                    viewModel.deleteSpecificBackup(token, item.fileId) { success ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (success) driveDeleteSingleSuccessText else driveErrorText
                            )
                        }
                    }
                }
            },
            onDeleteAllConfirmed = {
                showBackupDetailsDialog = false
                val token = driveAccessToken ?: GoogleDriveManager.currentAccessToken
                if (token != null) {
                    viewModel.deleteAllBackups(token) { success ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (success) driveDeleteAllSuccessText else driveErrorText
                            )
                        }
                    }
                }
            },
            onDismiss = { showBackupDetailsDialog = false }
        )
    }

    // Modal: Advertencia de Sobrescritura de Respaldo Remoto
    if (showOverwriteWarningDialog) {
        DriveOverwriteWarningDialog(
            backupInfo = uiState.driveBackupInfo,
            formattedLastSync = formattedLastSync,
            onConfirmOverwrite = {
                showOverwriteWarningDialog = false
                showDriveProtectDialog = true
            },
            onRestoreInstead = {
                showOverwriteWarningDialog = false
                val token = driveAccessToken ?: GoogleDriveManager.currentAccessToken
                if (token == null) {
                    requestGoogleAuthorization { showDriveDecryptDialog = true }
                } else {
                    showDriveDecryptDialog = true
                }
            },
            onDismiss = { showOverwriteWarningDialog = false }
        )
    }
}
