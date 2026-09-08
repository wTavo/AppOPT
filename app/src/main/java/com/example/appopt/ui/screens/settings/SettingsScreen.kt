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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.flow.first
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.CloudVaultSyncManager
import com.example.appopt.data.cloud.DriveBackupInfo
import com.example.appopt.data.cloud.DriveBackupItem
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.data.cloud.ManualSyncManager
import com.example.appopt.data.cloud.SyncFrequency
import com.example.appopt.security.SecurityConfig
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
import com.example.appopt.util.SyncNotificationHelper
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pantalla principal de configuración y preferencias del usuario.
 *
 * Administra el diagnóstico de rendimiento (FPS), la transferencia offline por códigos QR
 * y la sincronización con cifrado de extremo a extremo (E2EE) en Google Drive mediante Google Identity Services.
 *
 * @param onNavigateBack Callback invocado al presionar el botón de retorno.
 * @param onNavigateToScanQr Callback invocado para navegar hacia el escáner de importación QR.
 * @param modifier Modificador de diseño Compose opcional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToScanQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appHaptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val repository = AuthenticatorApp.instance.accountRepository
    val prefsManager = remember { AuthenticatorApp.instance.preferencesManager }

    val accounts by repository.getAccounts().collectAsStateWithLifecycle(initialValue = emptyList())
    val isFpsOverlayEnabled by prefsManager.isFpsOverlayEnabledFlow.collectAsStateWithLifecycle()

    // Estados reactivos de sincronización con Google Identity Services
    val authClient = remember { GoogleDriveManager.getAuthorizationClient(context) }
    val isDriveConnected by prefsManager.isGoogleDriveConnectedFlow.collectAsStateWithLifecycle()
    val isAutoSyncEnabled by prefsManager.isAutoSyncEnabledFlow.collectAsStateWithLifecycle()
    val isSyncMobileDataAllowed by prefsManager.isSyncMobileDataAllowedFlow.collectAsStateWithLifecycle()
    val lastSyncTimestamp by prefsManager.lastSyncTimestampFlow.collectAsStateWithLifecycle()
    val lastSyncedHash by prefsManager.lastSyncedVaultHashFlow.collectAsStateWithLifecycle()
    var driveAccessToken by remember { mutableStateOf<String?>(null) }
    var isDriveLoading by remember { mutableStateOf(false) }
    var isCheckingDriveBackup by remember { mutableStateOf(false) }
    var driveBackupExists by remember { mutableStateOf(false) }
    var driveBackupInfo by remember { mutableStateOf<DriveBackupInfo?>(null) }

    // Observación en tiempo real del estado de tareas WorkManager de sincronización en segundo plano
    val reactiveWorkInfos by remember(context) {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(CloudVaultSyncManager.REACTIVE_WORK_NAME)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val periodicWorkInfos by remember(context) {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(CloudVaultSyncManager.PERIODIC_WORK_NAME)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val isGlobalSyncing by ManualSyncManager.isSyncing.collectAsStateWithLifecycle()

    val isAutoSyncRunning = remember(reactiveWorkInfos, periodicWorkInfos) {
        reactiveWorkInfos.any { it.state == WorkInfo.State.RUNNING } ||
                periodicWorkInfos.any { it.state == WorkInfo.State.RUNNING }
    }

    val isSyncActive = isDriveLoading || isGlobalSyncing || isAutoSyncRunning

    // Estados para control de modales
    var showExportDialog by remember { mutableStateOf(false) }
    var showBackupDetailsDialog by remember { mutableStateOf(false) }
    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }
    var showDriveProtectDialog by remember { mutableStateOf(false) }
    var showDriveDecryptDialog by remember { mutableStateOf(false) }
    var showOverwriteWarningDialog by remember { mutableStateOf(false) }
    var backupHistoryList by remember { mutableStateOf<List<DriveBackupItem>>(emptyList()) }
    var lastBackupHistoryFetchTimestamp by remember { mutableLongStateOf(0L) }
    var selectedBackupToRestore by remember { mutableStateOf<DriveBackupItem?>(null) }
    var isFetchingBackupHistory by remember { mutableStateOf(false) }

    // Mensajes de retroalimentación centralizados
    val driveErrorText = stringResource(R.string.settings_drive_error)
    val driveConnectedSuccessText = stringResource(R.string.settings_drive_connected_success)
    val driveDisconnectedSuccessText = stringResource(R.string.settings_drive_disconnected_success)
    val servicesDeletedAfterExportText = stringResource(R.string.settings_services_deleted_after_export)
    val driveSyncSuccessText = stringResource(R.string.settings_drive_sync_success)
    val driveDecryptErrorText = stringResource(R.string.settings_drive_decrypt_error)
    val driveDeleteSuccessText = stringResource(R.string.settings_drive_delete_success)
    val driveDeleteSingleSuccessText = stringResource(R.string.settings_drive_delete_single_success)
    val driveDeleteAllSuccessText = stringResource(R.string.settings_drive_delete_all_success)

    // Notificación visual de error en pantalla si una tarea de sincronización en segundo plano falla
    LaunchedEffect(reactiveWorkInfos, periodicWorkInfos) {
        val hasFailure = reactiveWorkInfos.any { it.state == WorkInfo.State.FAILED } ||
                periodicWorkInfos.any { it.state == WorkInfo.State.FAILED }
        if (hasFailure) {
            appHaptics.error()
            snackbarHostState.showSnackbar(driveErrorText)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var currentTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isCameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
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

    val effectiveLastSyncTimestamp = remember(lastSyncTimestamp, backupHistoryList, driveBackupInfo) {
        if (lastSyncTimestamp > 0L) {
            lastSyncTimestamp
        } else {
            backupHistoryList.firstOrNull()?.modifiedTimeMillis
                ?: driveBackupInfo?.modifiedTimeMillis
                ?: 0L
        }
    }

    val formattedLastSync = remember(effectiveLastSyncTimestamp, currentTick) {
        if (effectiveLastSyncTimestamp == 0L) {
            null
        } else {
            DateTimeFormatter.formatRelativeSyncTime(context, effectiveLastSyncTimestamp)
        }
    }

    val currentVaultHash = remember(accounts) {
        CloudVaultSyncManager.computeAccountsSignature(accounts)
    }
    val hasUnsyncedChanges = remember(currentVaultHash, lastSyncedHash, isDriveConnected, effectiveLastSyncTimestamp) {
        if (!isDriveConnected || effectiveLastSyncTimestamp == 0L) {
            false
        } else {
            !lastSyncedHash.isNullOrEmpty() && currentVaultHash != lastSyncedHash
        }
    }

    LaunchedEffect(Unit) {
        if (prefsManager.isGoogleDriveConnected()) {
            val token = driveAccessToken ?: GoogleDriveManager.currentAccessToken
            if (token != null) {
                driveAccessToken = token
            } else {
                isCheckingDriveBackup = true
                authClient.authorize(GoogleDriveManager.getAuthorizationRequest())
                    .addOnSuccessListener { result ->
                        if (!result.hasResolution() && result.accessToken != null) {
                            val newToken = result.accessToken!!
                            driveAccessToken = newToken
                            GoogleDriveManager.currentAccessToken = newToken
                            scope.launch {
                                try {
                                    val historyResult = ManualSyncManager.fetchBackupHistory(newToken)
                                    if (historyResult.isSuccess) {
                                        val items = historyResult.getOrNull().orEmpty()
                                        backupHistoryList = items
                                        lastBackupHistoryFetchTimestamp = System.currentTimeMillis()
                                        driveBackupExists = items.isNotEmpty()
                                        val mostRecent = items.firstOrNull()
                                        if (mostRecent != null) {
                                            driveBackupInfo = DriveBackupInfo(
                                                fileId = mostRecent.fileId,
                                                modifiedTimeMillis = mostRecent.modifiedTimeMillis,
                                                deviceName = mostRecent.deviceName
                                            )
                                            if (accounts.isNotEmpty() && lastSyncTimestamp == 0L) {
                                                prefsManager.setLastSyncTimestamp(mostRecent.modifiedTimeMillis)
                                                prefsManager.setLastSyncedVaultHash(currentVaultHash)
                                            }
                                        }
                                    }
                                } finally {
                                    isCheckingDriveBackup = false
                                }
                            }
                        } else {
                            isCheckingDriveBackup = false
                        }
                    }.addOnFailureListener {
                        isCheckingDriveBackup = false
                    }
            }
        }
    }

    // Observar actualizaciones reactivas de sincronización en segundo plano
    LaunchedEffect(lastSyncTimestamp) {
        if (lastSyncTimestamp > 0L && prefsManager.isGoogleDriveConnected()) {
            driveBackupExists = true
            val token = driveAccessToken ?: GoogleDriveManager.currentAccessToken
            if (token != null) {
                val historyResult = ManualSyncManager.fetchBackupHistory(token)
                if (historyResult.isSuccess) {
                    val items = historyResult.getOrNull().orEmpty()
                    backupHistoryList = items
                    lastBackupHistoryFetchTimestamp = System.currentTimeMillis()
                    val mostRecent = items.firstOrNull()
                    if (mostRecent != null) {
                        driveBackupInfo = DriveBackupInfo(
                            fileId = mostRecent.fileId,
                            modifiedTimeMillis = mostRecent.modifiedTimeMillis,
                            deviceName = mostRecent.deviceName
                        )
                    }
                }
            }
        }
    }

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
                        prefsManager.setGoogleDriveConnected(true)
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
            isDriveLoading = false
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isCameraPermissionGranted = granted
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationPermissionGranted = granted
    }

    fun openAppSystemSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            openAppSystemSettings()
        }
    }

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
            .addOnFailureListener { _ ->
                pendingAuthAction = null
                isDriveLoading = false
                scope.launch {
                    snackbarHostState.showSnackbar(driveErrorText)
                }
            }
    }

    fun executeManualSync(token: String) {
        AuthenticatorApp.instance.applicationScope.launch {
            try {
                val result = ManualSyncManager.syncNow(context, token)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        driveBackupExists = true
                        appHaptics.success()
                        scope.launch {
                            val historyResult = ManualSyncManager.fetchBackupHistory(token)
                            val items = historyResult.getOrNull().orEmpty()
                            backupHistoryList = items
                            lastBackupHistoryFetchTimestamp = System.currentTimeMillis()
                            val mostRecent = items.firstOrNull()
                            if (mostRecent != null) {
                                driveBackupInfo = DriveBackupInfo(
                                    fileId = mostRecent.fileId,
                                    modifiedTimeMillis = mostRecent.modifiedTimeMillis,
                                    deviceName = mostRecent.deviceName
                                )
                            }
                        }
                    } else {
                        appHaptics.error()
                        snackbarHostState.showSnackbar(driveErrorText)
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isDriveLoading = false
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

            // 1. Tarjeta de Permisos de la Aplicación (Al inicio)
            PermissionsSettingsCard(
                isCameraGranted = isCameraPermissionGranted,
                isNotificationGranted = isNotificationPermissionGranted,
                isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                onRequestCameraPermission = { requestCameraPermission() },
                onRequestNotificationPermission = { checkAndRequestNotificationPermission() },
                onRequestBatteryOptimization = {
                    BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                }
            )

            // 2. Tarjeta de Rendimiento y Diagnóstico
            PerformanceSettingsCard(
                isFpsOverlayEnabled = isFpsOverlayEnabled,
                onFpsOverlayChanged = { enabled -> prefsManager.setFpsOverlayEnabled(enabled) }
            )

            // 3. Tarjeta de Transferencia Offline por Código QR
            TransferSettingsCard(
                accounts = accounts,
                onExportClick = { showExportDialog = true },
                onImportClick = onNavigateToScanQr
            )

            // 4. Tarjeta de Copia de Seguridad y Sincronización en Google Drive
            DriveSyncSettingsCard(
                isDriveConnected = isDriveConnected,
                isDriveLoading = isSyncActive,
                isCheckingDriveBackup = isCheckingDriveBackup,
                formattedLastSync = formattedLastSync,
                driveBackupExists = driveBackupExists,
                hasUnsyncedChanges = hasUnsyncedChanges,
                lastSyncTimestamp = effectiveLastSyncTimestamp,
                isAutoSyncEnabled = isAutoSyncEnabled,
                isSyncMobileDataAllowed = isSyncMobileDataAllowed,
                onConnectClick = {
                    isDriveLoading = true
                    requestGoogleAuthorization { token ->
                        prefsManager.setGoogleDriveConnected(true)
                        driveAccessToken = token
                        GoogleDriveManager.currentAccessToken = token
                        isDriveLoading = false
                        scope.launch {
                            val historyResult = ManualSyncManager.fetchBackupHistory(token)
                            val items = historyResult.getOrNull().orEmpty()
                            backupHistoryList = items
                            lastBackupHistoryFetchTimestamp = System.currentTimeMillis()
                            driveBackupExists = items.isNotEmpty()
                            val mostRecent = items.firstOrNull()
                            if (mostRecent != null) {
                                driveBackupInfo = DriveBackupInfo(
                                    fileId = mostRecent.fileId,
                                    modifiedTimeMillis = mostRecent.modifiedTimeMillis,
                                    deviceName = mostRecent.deviceName
                                )
                            }
                            snackbarHostState.showSnackbar(driveConnectedSuccessText)
                        }
                    }
                },
                onManualSyncClick = {
                    isDriveLoading = true
                    if (driveAccessToken == null) {
                        requestGoogleAuthorization { token -> executeManualSync(token) }
                    } else {
                        executeManualSync(driveAccessToken!!)
                    }
                },
                onRestoreClick = {
                    if (driveAccessToken == null) {
                        requestGoogleAuthorization { showDriveDecryptDialog = true }
                    } else {
                        showDriveDecryptDialog = true
                    }
                },
                onCreateBackupClick = {
                    if (driveBackupExists) {
                        showOverwriteWarningDialog = true
                    } else {
                        showDriveProtectDialog = true
                    }
                },
                onBackupDetailsClick = {
                    showBackupDetailsDialog = true
                    val now = System.currentTimeMillis()
                    val isCacheStale = backupHistoryList.isEmpty() || (now - lastBackupHistoryFetchTimestamp > SecurityConfig.BACKUP_HISTORY_CACHE_TTL_MILLIS)

                    if (isCacheStale) {
                        val fetchAndOpenDetails: (String) -> Unit = { token ->
                            isFetchingBackupHistory = true
                            scope.launch {
                                try {
                                    val historyResult = ManualSyncManager.fetchBackupHistory(token)
                                    if (historyResult.isSuccess) {
                                        val items = historyResult.getOrNull().orEmpty()
                                        backupHistoryList = items
                                        lastBackupHistoryFetchTimestamp = System.currentTimeMillis()
                                        driveBackupExists = items.isNotEmpty()
                                        val mostRecent = items.firstOrNull()
                                        if (mostRecent != null) {
                                            driveBackupInfo = DriveBackupInfo(
                                                fileId = mostRecent.fileId,
                                                modifiedTimeMillis = mostRecent.modifiedTimeMillis,
                                                deviceName = mostRecent.deviceName
                                            )
                                        }
                                    } else {
                                        // Token expirado o error de autenticación: solicitar nuevo token silencioso y reintentar
                                        driveAccessToken = null
                                        GoogleDriveManager.currentAccessToken = null
                                        requestGoogleAuthorization { freshToken ->
                                            scope.launch {
                                                isFetchingBackupHistory = true
                                                try {
                                                    val retryResult = ManualSyncManager.fetchBackupHistory(freshToken)
                                                    if (retryResult.isSuccess) {
                                                        val items = retryResult.getOrNull().orEmpty()
                                                        backupHistoryList = items
                                                        lastBackupHistoryFetchTimestamp = System.currentTimeMillis()
                                                        driveBackupExists = items.isNotEmpty()
                                                        val mostRecent = items.firstOrNull()
                                                        if (mostRecent != null) {
                                                            driveBackupInfo = DriveBackupInfo(
                                                                fileId = mostRecent.fileId,
                                                                modifiedTimeMillis = mostRecent.modifiedTimeMillis,
                                                                deviceName = mostRecent.deviceName
                                                            )
                                                        }
                                                    }
                                                } finally {
                                                    isFetchingBackupHistory = false
                                                }
                                            }
                                        }
                                    }
                                } finally {
                                    isFetchingBackupHistory = false
                                }
                            }
                        }
                        if (driveAccessToken == null) {
                            requestGoogleAuthorization { token -> fetchAndOpenDetails(token) }
                        } else {
                            fetchAndOpenDetails(driveAccessToken!!)
                        }
                    }
                },
                onDisconnectClick = { showDisconnectConfirmDialog = true },
                onAutoSyncToggle = { enabled ->
                    prefsManager.setAutoSyncEnabled(enabled)
                    if (enabled) {
                        CloudVaultSyncManager.triggerReactiveSync(context, 0L)
                    }
                },
                onMobileDataToggle = { allowed ->
                    prefsManager.setSyncMobileDataAllowed(allowed)
                    if (isAutoSyncEnabled) {
                        CloudVaultSyncManager.triggerReactiveSync(context, 0L)
                    }
                }
            )
        }
    }

    // Modal: Confirmación de Desvinculación de Google Drive
    if (showDisconnectConfirmDialog) {
        DriveDisconnectConfirmDialog(
            onConfirm = {
                showDisconnectConfirmDialog = false
                driveAccessToken = null
                GoogleDriveManager.clearSession()
                driveBackupExists = false
                driveBackupInfo = null
                backupHistoryList = emptyList()
                lastBackupHistoryFetchTimestamp = 0L
                prefsManager.setGoogleDriveConnected(false)
                prefsManager.setLastSyncTimestamp(0L)
                prefsManager.setLastSyncedVaultHash("")
                CloudVaultSyncManager.schedulePeriodicSync(context, SyncFrequency.OFF, false)
                scope.launch {
                    snackbarHostState.showSnackbar(driveDisconnectedSuccessText)
                }
            },
            onDismiss = { showDisconnectConfirmDialog = false }
        )
    }

    // Modal: Selección y Exportación por Código QR
    if (showExportDialog) {
        ExportServicesDialog(
            accounts = accounts,
            onExportPayload = { selectedIds -> repository.exportAccountsForTransfer(selectedIds) },
            onCompleteExport = { exportedIds, keepOnDevice ->
                if (!keepOnDevice && exportedIds.isNotEmpty()) {
                    scope.launch {
                        exportedIds.forEach { id -> repository.deleteAccount(id) }
                        snackbarHostState.showSnackbar(servicesDeletedAfterExportText)
                    }
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
                scope.launch {
                    isDriveLoading = true
                    try {
                        val result = ManualSyncManager.createProtectedBackup(
                            context = context,
                            accessToken = driveAccessToken!!,
                            secretKeyPass = primaryPassChars,
                            emergencyMnemonic = emergencyMnemonicChars
                        )
                        result.onSuccess {
                            driveBackupExists = true
                            scope.launch {
                                val historyResult = ManualSyncManager.fetchBackupHistory(driveAccessToken!!)
                                val items = historyResult.getOrNull().orEmpty()
                                backupHistoryList = items
                                lastBackupHistoryFetchTimestamp = System.currentTimeMillis()
                                val mostRecent = items.firstOrNull()
                                if (mostRecent != null) {
                                    driveBackupInfo = DriveBackupInfo(
                                        fileId = mostRecent.fileId,
                                        modifiedTimeMillis = mostRecent.modifiedTimeMillis,
                                        deviceName = mostRecent.deviceName
                                    )
                                }
                            }
                            if (isAutoSyncEnabled) {
                                CloudVaultSyncManager.triggerReactiveSync(context, 0L)
                            }
                            snackbarHostState.showSnackbar(driveSyncSuccessText)
                        }.onFailure {
                            snackbarHostState.showSnackbar(driveErrorText)
                        }
                    } finally {
                        isDriveLoading = false
                    }
                }
            },
            onDismiss = { showDriveProtectDialog = false }
        )
    }

    // Modal: Descifrado y Restauración desde Google Drive (Acceso Directo desde Tarjeta Principal)
    if (showDriveDecryptDialog) {
        val targetDateMillis = driveBackupInfo?.modifiedTimeMillis
        val targetDeviceName = driveBackupInfo?.deviceName

        DriveDecryptDialog(
            backupDateMillis = targetDateMillis,
            deviceName = targetDeviceName,
            isMostRecent = true,
            onRestore = { passChars ->
                showDriveDecryptDialog = false
                scope.launch {
                    isDriveLoading = true
                    try {
                        val result = ManualSyncManager.restoreFromBackup(
                            context = context,
                            accessToken = driveAccessToken!!,
                            secretKeyPass = passChars
                        )
                        result.onSuccess { count ->
                            lastBackupHistoryFetchTimestamp = 0L
                            if (isAutoSyncEnabled) {
                                CloudVaultSyncManager.triggerReactiveSync(context, 0L)
                            }
                            snackbarHostState.showSnackbar(
                                context.applicationContext.getString(R.string.settings_drive_restore_success, count)
                            )
                        }.onFailure { error ->
                            val isDecryptFailure = error.message?.contains("clave", ignoreCase = true) == true ||
                                    error.message?.contains("descargar", ignoreCase = true) == true
                            snackbarHostState.showSnackbar(
                                if (isDecryptFailure) driveDecryptErrorText else driveErrorText
                            )
                        }
                    } finally {
                        isDriveLoading = false
                    }
                }
            },
            onDismiss = {
                showDriveDecryptDialog = false
            }
        )
    }

    // Modal: Detalles de la Copia, Historial (Point-in-Time Recovery) y Eliminación Granular / Total
    if (showBackupDetailsDialog) {
        DriveBackupDetailsDialog(
            backupItems = backupHistoryList,
            isLoading = isFetchingBackupHistory,
            onRestoreBackup = { item, passChars ->
                showBackupDetailsDialog = false
                val executeRestore: (String) -> Unit = { token ->
                    scope.launch {
                        isDriveLoading = true
                        try {
                            val result = ManualSyncManager.restoreSpecificBackup(
                                context = context,
                                accessToken = token,
                                fileId = item.fileId,
                                secretKeyPass = passChars
                            )
                            result.onSuccess { count ->
                                lastBackupHistoryFetchTimestamp = 0L
                                if (isAutoSyncEnabled) {
                                    CloudVaultSyncManager.triggerReactiveSync(context, 0L)
                                }
                                snackbarHostState.showSnackbar(
                                    context.applicationContext.getString(R.string.settings_drive_restore_success, count)
                                )
                            }.onFailure { error ->
                                val isDecryptFailure = error.message?.contains("clave", ignoreCase = true) == true ||
                                        error.message?.contains("descargar", ignoreCase = true) == true
                                snackbarHostState.showSnackbar(
                                    if (isDecryptFailure) driveDecryptErrorText else driveErrorText
                                )
                            }
                        } finally {
                            isDriveLoading = false
                        }
                    }
                }
                if (driveAccessToken == null) {
                    requestGoogleAuthorization { token -> executeRestore(token) }
                } else {
                    executeRestore(driveAccessToken!!)
                }
            },
            onDeleteSpecificBackup = { item ->
                val executeDeleteSpecific: (String) -> Unit = { token ->
                    scope.launch {
                        isFetchingBackupHistory = true
                        try {
                            val deleteResult = ManualSyncManager.deleteSpecificBackup(token, item.fileId)
                            deleteResult.onSuccess {
                                val updated = backupHistoryList.filterNot { it.fileId == item.fileId }
                                backupHistoryList = updated
                                lastBackupHistoryFetchTimestamp = System.currentTimeMillis()
                                driveBackupExists = updated.isNotEmpty()
                                val mostRecent = updated.firstOrNull()
                                driveBackupInfo = if (mostRecent != null) {
                                    DriveBackupInfo(
                                        fileId = mostRecent.fileId,
                                        modifiedTimeMillis = mostRecent.modifiedTimeMillis,
                                        deviceName = mostRecent.deviceName
                                    )
                                } else {
                                    null
                                }
                                snackbarHostState.showSnackbar(driveDeleteSingleSuccessText)
                            }.onFailure {
                                snackbarHostState.showSnackbar(driveErrorText)
                            }
                        } finally {
                            isFetchingBackupHistory = false
                        }
                    }
                }
                if (driveAccessToken == null) {
                    requestGoogleAuthorization { token -> executeDeleteSpecific(token) }
                } else {
                    executeDeleteSpecific(driveAccessToken!!)
                }
            },
            onDeleteAllConfirmed = {
                showBackupDetailsDialog = false
                val executeDeleteAll: (String) -> Unit = { token ->
                    scope.launch {
                        isFetchingBackupHistory = true
                        try {
                            val deleteResult = ManualSyncManager.deleteAllBackups(token)
                            deleteResult.onSuccess {
                                backupHistoryList = emptyList()
                                lastBackupHistoryFetchTimestamp = 0L
                                driveBackupExists = false
                                driveBackupInfo = null
                                prefsManager.setLastSyncTimestamp(0L)
                                prefsManager.setLastSyncedVaultHash("")
                                snackbarHostState.showSnackbar(driveDeleteAllSuccessText)
                            }.onFailure {
                                snackbarHostState.showSnackbar(driveErrorText)
                            }
                        } finally {
                            isFetchingBackupHistory = false
                        }
                    }
                }

                if (driveAccessToken == null) {
                    requestGoogleAuthorization { token -> executeDeleteAll(token) }
                } else {
                    executeDeleteAll(driveAccessToken!!)
                }
            },
            onDismiss = { showBackupDetailsDialog = false }
        )
    }

    // Modal: Advertencia de Sobrescritura de Respaldo Remoto
    if (showOverwriteWarningDialog) {
        DriveOverwriteWarningDialog(
            backupInfo = driveBackupInfo,
            formattedLastSync = formattedLastSync,
            onConfirmOverwrite = {
                showOverwriteWarningDialog = false
                showDriveProtectDialog = true
            },
            onRestoreInstead = {
                showOverwriteWarningDialog = false
                if (driveAccessToken == null) {
                    requestGoogleAuthorization { showDriveDecryptDialog = true }
                } else {
                    showDriveDecryptDialog = true
                }
            },
            onDismiss = { showOverwriteWarningDialog = false }
        )
    }
}
