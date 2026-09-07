package com.example.appopt.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.CloudVaultSyncManager
import com.example.appopt.data.cloud.DriveBackupInfo
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.data.cloud.SyncFrequency
import com.example.appopt.ui.screens.settings.components.DriveSyncSettingsCard
import com.example.appopt.ui.screens.settings.components.PerformanceSettingsCard
import com.example.appopt.ui.screens.settings.components.TransferSettingsCard
import com.example.appopt.ui.screens.settings.dialogs.DriveBackupDetailsDialog
import com.example.appopt.ui.screens.settings.dialogs.DriveDecryptDialog
import com.example.appopt.ui.screens.settings.dialogs.DriveDisconnectConfirmDialog
import com.example.appopt.ui.screens.settings.dialogs.DriveOverwriteWarningDialog
import com.example.appopt.ui.screens.settings.dialogs.DriveProtectDialog
import com.example.appopt.ui.screens.settings.dialogs.ExportServicesDialog
import com.example.appopt.ui.screens.settings.dialogs.SyncFrequencyDialog
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.BatteryOptimizationHelper
import com.example.appopt.util.DateTimeFormatter
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

    // Estados de sincronización con Google Identity Services
    val authClient = remember { GoogleDriveManager.getAuthorizationClient(context) }
    var isDriveConnected by remember { mutableStateOf(prefsManager.isGoogleDriveConnected()) }
    var syncFrequency by remember { mutableStateOf(prefsManager.getSyncFrequency()) }
    var isSyncMobileDataAllowed by remember { mutableStateOf(prefsManager.isSyncMobileDataAllowed()) }
    var lastSyncTimestamp by remember { mutableLongStateOf(prefsManager.getLastSyncTimestamp()) }
    var driveAccessToken by remember { mutableStateOf<String?>(null) }
    var isDriveLoading by remember { mutableStateOf(false) }
    var isCheckingDriveBackup by remember { mutableStateOf(false) }
    var driveBackupExists by remember { mutableStateOf(false) }
    var driveBackupInfo by remember { mutableStateOf<DriveBackupInfo?>(null) }
    var lastSyncedHash by remember { mutableStateOf(prefsManager.getLastSyncedVaultHash()) }

    // Estados para control de modales
    var showExportDialog by remember { mutableStateOf(false) }
    var showFrequencyDialog by remember { mutableStateOf(false) }
    var showBackupDetailsDialog by remember { mutableStateOf(false) }
    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }
    var showDriveProtectDialog by remember { mutableStateOf(false) }
    var showDriveDecryptDialog by remember { mutableStateOf(false) }
    var showOverwriteWarningDialog by remember { mutableStateOf(false) }

    // Mensajes de retroalimentación centralizados
    val driveErrorText = stringResource(R.string.settings_drive_error)
    val driveConnectedSuccessText = stringResource(R.string.settings_drive_connected_success)
    val driveDisconnectedSuccessText = stringResource(R.string.settings_drive_disconnected_success)
    val servicesDeletedAfterExportText = stringResource(R.string.settings_services_deleted_after_export)
    val driveSyncSuccessText = stringResource(R.string.settings_drive_sync_success)
    val driveDecryptErrorText = stringResource(R.string.settings_drive_decrypt_error)
    val driveDeleteSuccessText = stringResource(R.string.settings_drive_delete_success)

    val lifecycleOwner = LocalLifecycleOwner.current
    var currentTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isBatteryOptimizationIgnored by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isBatteryOptimizationIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
            while (isActive) {
                val now = System.currentTimeMillis()
                currentTick = now
                val millisUntilNextMinute = 60_000L - (now % 60_000L)
                delay(millisUntilNextMinute.coerceAtLeast(1_000L).milliseconds)
            }
        }
    }

    val formattedLastSync = remember(lastSyncTimestamp, currentTick) {
        if (lastSyncTimestamp == 0L) {
            null
        } else {
            DateTimeFormatter.formatRelativeSyncTime(context, lastSyncTimestamp)
        }
    }

    val currentVaultHash = remember(accounts) {
        if (accounts.isEmpty()) "" else CloudVaultSyncManager.computeAccountsSignature(accounts)
    }
    val hasUnsyncedChanges = remember(currentVaultHash, lastSyncedHash) {
        if (accounts.isEmpty()) {
            false
        } else {
            lastSyncedHash.isNullOrEmpty() || currentVaultHash != lastSyncedHash
        }
    }

    LaunchedEffect(Unit) {
        if (prefsManager.isGoogleDriveConnected() && lastSyncTimestamp == 0L) {
            isCheckingDriveBackup = true
            authClient.authorize(GoogleDriveManager.getAuthorizationRequest())
                .addOnSuccessListener { result ->
                    if (!result.hasResolution() && result.accessToken != null) {
                        driveAccessToken = result.accessToken
                        isDriveConnected = true
                        scope.launch {
                            try {
                                val info = GoogleDriveManager.fetchBackupDetails(result.accessToken!!)
                                driveBackupExists = info != null
                                driveBackupInfo = info
                                if (info != null && accounts.isNotEmpty()) {
                                    val syncTime = info.modifiedTimeMillis
                                    lastSyncTimestamp = syncTime
                                    prefsManager.setLastSyncTimestamp(syncTime)
                                    prefsManager.setLastSyncedVaultHash(currentVaultHash)
                                    lastSyncedHash = currentVaultHash
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
                    val action = pendingAuthAction
                    pendingAuthAction = null
                    if (action != null) {
                        action(token)
                    } else {
                        isDriveConnected = true
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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permiso otorgado o denegado por el usuario */ }

    fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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
                val payload = repository.exportAccountsForTransfer()
                val autoSyncKey = com.example.appopt.security.SecurityConfig.AUTO_SYNC_VAULT_KEY.toCharArray()
                try {
                    val uploadResult = GoogleDriveManager.uploadBackup(token, payload, autoSyncKey)
                    uploadResult.onSuccess {
                        val now = System.currentTimeMillis()
                        val currentHash = CloudVaultSyncManager.computeAccountsSignature(accounts)
                        prefsManager.setLastSyncTimestamp(now)
                        prefsManager.setLastSyncedVaultHash(currentHash)
                        withContext(Dispatchers.Main) {
                            lastSyncTimestamp = now
                            lastSyncedHash = currentHash
                            driveBackupExists = true
                            appHaptics.success()
                        }
                    }.onFailure { _ ->
                        withContext(Dispatchers.Main) {
                            appHaptics.error()
                            snackbarHostState.showSnackbar(driveErrorText)
                        }
                    }
                } finally {
                    autoSyncKey.fill('0')
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

            // 1. Tarjeta de Rendimiento y Diagnóstico
            PerformanceSettingsCard(
                isFpsOverlayEnabled = isFpsOverlayEnabled,
                onFpsOverlayChanged = { enabled -> prefsManager.setFpsOverlayEnabled(enabled) }
            )

            // 2. Tarjeta de Transferencia Offline por Código QR
            TransferSettingsCard(
                accounts = accounts,
                onExportClick = { showExportDialog = true },
                onImportClick = onNavigateToScanQr
            )

            // 3. Tarjeta de Copia de Seguridad y Sincronización en Google Drive
            DriveSyncSettingsCard(
                isDriveConnected = isDriveConnected,
                isDriveLoading = isDriveLoading,
                isCheckingDriveBackup = isCheckingDriveBackup,
                formattedLastSync = formattedLastSync,
                driveBackupExists = driveBackupExists,
                hasUnsyncedChanges = hasUnsyncedChanges,
                lastSyncTimestamp = lastSyncTimestamp,
                syncFrequency = syncFrequency,
                isSyncMobileDataAllowed = isSyncMobileDataAllowed,
                isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                onConnectClick = {
                    requestGoogleAuthorization { token ->
                        isDriveConnected = true
                        prefsManager.setGoogleDriveConnected(true)
                        isCheckingDriveBackup = true
                        scope.launch {
                            try {
                                val info = GoogleDriveManager.fetchBackupDetails(token)
                                driveBackupExists = info != null
                                driveBackupInfo = info
                                if (info != null && accounts.isNotEmpty()) {
                                    val syncTime = info.modifiedTimeMillis
                                    lastSyncTimestamp = syncTime
                                    prefsManager.setLastSyncTimestamp(syncTime)
                                    prefsManager.setLastSyncedVaultHash(currentVaultHash)
                                    lastSyncedHash = currentVaultHash
                                }
                                 snackbarHostState.showSnackbar(driveConnectedSuccessText)
                                checkAndRequestNotificationPermission()
                            } finally {
                                isCheckingDriveBackup = false
                            }
                        }
                    }
                },
                onManualSyncClick = {
                    if (isDriveLoading) return@DriveSyncSettingsCard
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
                onBackupDetailsClick = { showBackupDetailsDialog = true },
                onDisconnectClick = { showDisconnectConfirmDialog = true },
                onFrequencyClick = { showFrequencyDialog = true },
                onMobileDataToggle = { allowed ->
                    isSyncMobileDataAllowed = allowed
                    prefsManager.setSyncMobileDataAllowed(allowed)
                    CloudVaultSyncManager.schedulePeriodicSync(context, syncFrequency, allowed)
                },
                onRequestBatteryOptimizationClick = {
                    BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                }
            )
        }
    }

    // Modal: Confirmación de Desvinculación de Google Drive
    if (showDisconnectConfirmDialog) {
        DriveDisconnectConfirmDialog(
            onConfirm = {
                showDisconnectConfirmDialog = false
                isDriveConnected = false
                driveAccessToken = null
                driveBackupExists = false
                lastSyncTimestamp = 0L
                prefsManager.setGoogleDriveConnected(false)
                prefsManager.setLastSyncTimestamp(0L)
                prefsManager.setLastSyncedVaultHash("")
                lastSyncedHash = ""
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
                        val payload = repository.exportAccountsForTransfer()
                        val uploadResult = GoogleDriveManager.uploadBackup(
                            accessToken = driveAccessToken!!,
                            rawBackupJson = payload,
                            secretKeyPass = primaryPassChars,
                            emergencyMnemonic = emergencyMnemonicChars
                        )
                        uploadResult.onSuccess {
                            val now = System.currentTimeMillis()
                            val currentHash = CloudVaultSyncManager.computeVaultHash(payload)
                            isDriveConnected = true
                            driveBackupExists = true
                            lastSyncTimestamp = now
                            prefsManager.setGoogleDriveConnected(true)
                            prefsManager.setLastSyncTimestamp(now)
                            prefsManager.setLastSyncedVaultHash(currentHash)
                            lastSyncedHash = currentHash
                            CloudVaultSyncManager.schedulePeriodicSync(context, syncFrequency, isSyncMobileDataAllowed)
                            snackbarHostState.showSnackbar(driveSyncSuccessText)
                        }.onFailure { _ ->
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

    // Modal: Descifrado y Restauración desde Google Drive
    if (showDriveDecryptDialog) {
        DriveDecryptDialog(
            onRestore = { passChars ->
                showDriveDecryptDialog = false
                scope.launch {
                    isDriveLoading = true
                    try {
                        val downloadResult = GoogleDriveManager.downloadBackup(driveAccessToken!!, passChars)
                        downloadResult.onSuccess { jsonPayload ->
                            val importResult = repository.importAccountsFromTransfer(jsonPayload)
                            importResult.onSuccess { count ->
                                isDriveConnected = true
                                prefsManager.setGoogleDriveConnected(true)
                                val now = System.currentTimeMillis()
                                lastSyncTimestamp = now
                                prefsManager.setLastSyncTimestamp(now)
                                val currentHash = CloudVaultSyncManager.computeVaultHash(jsonPayload)
                                prefsManager.setLastSyncedVaultHash(currentHash)
                                lastSyncedHash = currentHash
                                CloudVaultSyncManager.schedulePeriodicSync(context, syncFrequency, isSyncMobileDataAllowed)
                                snackbarHostState.showSnackbar(
                                    context.applicationContext.getString(R.string.settings_drive_restore_success, count)
                                )
                            }.onFailure { _ ->
                                snackbarHostState.showSnackbar(driveErrorText)
                            }
                        }.onFailure {
                            snackbarHostState.showSnackbar(driveDecryptErrorText)
                        }
                    } finally {
                        isDriveLoading = false
                    }
                }
            },
            onDismiss = { showDriveDecryptDialog = false }
        )
    }

    // Modal: Selección de Frecuencia de Sincronización
    if (showFrequencyDialog) {
        SyncFrequencyDialog(
            currentFrequency = syncFrequency,
            onFrequencySelected = { frequencyOption ->
                syncFrequency = frequencyOption
                prefsManager.setSyncFrequency(frequencyOption)
                CloudVaultSyncManager.schedulePeriodicSync(context, frequencyOption, isSyncMobileDataAllowed)
                showFrequencyDialog = false
                if (frequencyOption != SyncFrequency.OFF) {
                    checkAndRequestNotificationPermission()
                }
            },
            onDismiss = { showFrequencyDialog = false }
        )
    }

    // Modal: Detalles de la Copia y Confirmación de Eliminación
    if (showBackupDetailsDialog) {
        DriveBackupDetailsDialog(
            formattedLastSync = formattedLastSync,
            isLoading = isDriveLoading,
            onDeleteConfirmed = {
                showBackupDetailsDialog = false
                val executeDelete: (String) -> Unit = { token ->
                    scope.launch {
                        isDriveLoading = true
                        try {
                            val deleteResult = GoogleDriveManager.deleteBackup(token)
                            deleteResult.onSuccess {
                                prefsManager.setLastSyncTimestamp(0L)
                                prefsManager.setLastSyncedVaultHash("")
                                lastSyncedHash = ""
                                lastSyncTimestamp = 0L
                                driveBackupExists = false
                                snackbarHostState.showSnackbar(driveDeleteSuccessText)
                            }.onFailure { _ ->
                                snackbarHostState.showSnackbar(driveErrorText)
                            }
                        } finally {
                            isDriveLoading = false
                        }
                    }
                }

                if (driveAccessToken == null) {
                    requestGoogleAuthorization { token -> executeDelete(token) }
                } else {
                    executeDelete(driveAccessToken!!)
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
