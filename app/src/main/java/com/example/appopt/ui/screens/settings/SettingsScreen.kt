package com.example.appopt.ui.screens.settings

import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.example.appopt.util.EmergencyKitPdfGenerator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.CloudVaultSyncManager
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.data.cloud.SyncFrequency
import com.example.appopt.data.local.PreferencesManager
import com.example.appopt.security.CryptoManager
import com.example.appopt.security.MnemonicManager
import com.example.appopt.ui.components.ServiceBrandAvatar
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.appSwitchColors
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.ui.util.QrCodeGenerator
import com.example.appopt.util.DateTimeFormatter
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

/**
 * Pantalla de configuración de seguridad, transferencia offline de servicios y sincronización en la nube con Google Drive.
 *
 * Utiliza Google Identity Services (GIS) mediante [GoogleDriveManager] y ofrece protección E2EE dual
 * mediante PIN numérico o Clave de 64 dígitos con AES-256-GCM.
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
    val secureClipboard = remember { AuthenticatorApp.instance.secureClipboardManager }
    val snackbarHostState = remember { SnackbarHostState() }
    val repository = AuthenticatorApp.instance.accountRepository
    val prefsManager = remember { AuthenticatorApp.instance.preferencesManager }

    val accounts by repository.getAccounts().collectAsStateWithLifecycle(initialValue = emptyList())

    // Estados para el flujo de exportación por QR (unificado en un solo modal dinámico)
    var showExportDialog by remember { mutableStateOf(false) }
    var isShowingQrInExportDialog by remember { mutableStateOf(false) }
    val selectedServiceIds = remember { mutableStateListOf<String>() }
    var keepServicesOnDevice by remember { mutableStateOf(true) }
    var exportedServiceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var transferQrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Estados para la sincronización persistente con Google Identity Services
    val authClient = remember { GoogleDriveManager.getAuthorizationClient(context) }
    var isDriveConnected by remember { mutableStateOf(prefsManager.isGoogleDriveConnected()) }
    var syncFrequency by remember { mutableStateOf(prefsManager.getSyncFrequency()) }
    var isSyncMobileDataAllowed by remember { mutableStateOf(prefsManager.isSyncMobileDataAllowed()) }
    var lastSyncTimestamp by remember { mutableStateOf(prefsManager.getLastSyncTimestamp()) }
    var driveAccessToken by remember { mutableStateOf<String?>(null) }
    var isDriveLoading by remember { mutableStateOf(false) }
    var showFrequencyDialog by remember { mutableStateOf(false) }
    var showBackupDetailsDialog by remember { mutableStateOf(false) }
    var isConfirmingDeleteInDialog by remember { mutableStateOf(false) }
    val isFpsOverlayEnabled by prefsManager.isFpsOverlayEnabledFlow.collectAsStateWithLifecycle()

    val formattedLastSync = remember(lastSyncTimestamp) {
        if (lastSyncTimestamp == 0L) {
            null
        } else {
            DateTimeFormatter.formatRelativeSyncTime(context, lastSyncTimestamp)
        }
    }

    // Autorización silenciosa y comprobación de respaldo al abrir la pantalla si ya estaba configurada
    var driveBackupExists by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (prefsManager.isGoogleDriveConnected()) {
            authClient.authorize(GoogleDriveManager.getAuthorizationRequest())
                .addOnSuccessListener { result ->
                    if (!result.hasResolution() && result.accessToken != null) {
                        driveAccessToken = result.accessToken
                        isDriveConnected = true
                        scope.launch {
                            driveBackupExists = GoogleDriveManager.hasExistingBackup(result.accessToken!!)
                        }
                    }
                }
        }
    }

    // Callback pendiente para ejecutar tras resolver el Intent de Google Identity
    var pendingAuthAction by remember { mutableStateOf<((String) -> Unit)?>(null) }

    // Estados para el diálogo de protección E2EE al sincronizar (Flujo de 3 pasos: Método Ppal -> 12 Palabras -> Cuestionario)
    var showDriveProtectDialog by remember { mutableStateOf(false) }
    var driveProtectStep by remember { mutableIntStateOf(1) } // 1: Método Principal, 2: 12 Palabras, 3: Cuestionario
    var selectedProtectionTab by remember { mutableIntStateOf(0) } // 0: Contraseña maestra, 1: Clave 64 dígitos
    var generatedMnemonicWords by remember { mutableStateOf(MnemonicManager.generate12WordPhrase()) }
    var masterPasswordText by remember { mutableStateOf("") }
    var masterPasswordConfirmText by remember { mutableStateOf("") }
    var isMasterPasswordVisible by remember { mutableStateOf(false) }
    var generated64Key by remember { mutableStateOf(GoogleDriveManager.generate64DigitKey()) }
    var quizQuestions by remember { mutableStateOf<List<MnemonicManager.MnemonicQuizQuestion>>(emptyList()) }
    var quizSelectedAnswers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var isQuizError by remember { mutableStateOf(false) }

    // Estados para el diálogo de descifrado al restaurar
    var showDriveDecryptDialog by remember { mutableStateOf(false) }
    var restoreSecretText by remember { mutableStateOf("") }
    var isRestoreSecretVisible by remember { mutableStateOf(false) }
    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }

    val emptyServicesMsg = stringResource(R.string.settings_export_services_empty)
    val servicesDeletedMsg = stringResource(R.string.settings_services_deleted_after_export)
    val keyCopiedMsg = stringResource(R.string.settings_drive_key_copied)
    val wordsCopiedMsg = stringResource(R.string.settings_drive_words_copied)

    // Lanzador moderno para resolver el consentimiento de Google Identity
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
            } catch (e: ApiException) {
                pendingAuthAction = null
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.settings_drive_error, e.localizedMessage ?: "Código (${e.statusCode})")
                    )
                }
            }
        } else {
            pendingAuthAction = null
            isDriveLoading = false
        }
    }

    /**
     * Solicita autorización a Google Identity Services de forma moderna y reactiva.
     */
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
            .addOnFailureListener { error ->
                pendingAuthAction = null
                isDriveLoading = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.settings_drive_error, error.localizedMessage ?: "")
                    )
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

            // 1. Tarjeta de Estado y Diagnóstico de Seguridad
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(Dimensions.CornerRadius.large)
            ) {
                Column(modifier = Modifier.padding(Dimensions.Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = SafeGreen,
                            modifier = Modifier.size(Dimensions.IconSize.medium)
                        )
                        Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                        Text(
                            text = stringResource(R.string.settings_vault_status_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimensions.Spacing.sm))

                    Text(
                        text = stringResource(R.string.settings_vault_status_details),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1.1 Tarjeta de Rendimiento y Diagnósticos (FPS)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(Dimensions.CornerRadius.large)
            ) {
                Column(modifier = Modifier.padding(Dimensions.Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Dimensions.IconSize.medium)
                            )
                            Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_perf_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                                Text(
                                    text = stringResource(R.string.settings_perf_fps_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isFpsOverlayEnabled,
                            onCheckedChange = { enabled ->
                                appHaptics.click()
                                prefsManager.setFpsOverlayEnabled(enabled)
                            },
                            colors = appSwitchColors()
                        )
                    }
                }
            }

            // 2. Tarjeta de Transferencia Directa por Código QR (Offline)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(Dimensions.CornerRadius.large)
            ) {
                Column(modifier = Modifier.padding(Dimensions.Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.IconSize.medium)
                        )
                        Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                        Text(
                            text = stringResource(R.string.settings_transfer_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))

                    Text(
                        text = stringResource(R.string.settings_transfer_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(Dimensions.Spacing.md))

                    if (accounts.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                        ) {
                            Button(
                                onClick = {
                                    selectedServiceIds.clear()
                                    selectedServiceIds.addAll(accounts.map { it.id })
                                    keepServicesOnDevice = true
                                    isShowingQrInExportDialog = false
                                    showExportDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                            ) {
                                Text(stringResource(R.string.settings_export_services_button), style = MaterialTheme.typography.labelLarge)
                            }

                            OutlinedButton(
                                onClick = onNavigateToScanQr,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                            ) {
                                Text(stringResource(R.string.settings_import_services_button), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    } else {
                        Button(
                            onClick = onNavigateToScanQr,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        ) {
                            Text(stringResource(R.string.settings_import_services_button), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // 3. Tarjeta de Copia de Seguridad en la Nube (Google Drive)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(Dimensions.CornerRadius.large)
            ) {
                Column(
                    modifier = Modifier.padding(Dimensions.Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                ) {
                    // 1. Cabecera con icono, título, badge de estado y acción de desvincular
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = if (isDriveConnected) Icons.Filled.CloudDone else Icons.Filled.Sync,
                                contentDescription = null,
                                tint = if (isDriveConnected) SafeGreen else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Dimensions.IconSize.medium)
                            )
                            Text(
                                text = stringResource(R.string.settings_drive_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                                color = if (isDriveConnected) SafeGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (isDriveConnected) {
                                        stringResource(R.string.settings_drive_status_synced)
                                    } else {
                                        stringResource(R.string.settings_drive_status_not_synced)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDriveConnected) SafeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.xs)
                                )
                            }

                            if (isDriveConnected) {
                                IconButton(
                                    onClick = { showDisconnectConfirmDialog = true },
                                    modifier = Modifier.size(Dimensions.IconSize.hero)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SyncDisabled,
                                        contentDescription = stringResource(R.string.settings_drive_disconnect_button),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(Dimensions.IconSize.large)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Descripción clara
                    Text(
                        text = stringResource(R.string.settings_drive_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 3. Estado de última copia (Contenedor interactivo)
                    if (isDriveConnected) {
                        val hasBackupInfo = formattedLastSync != null || driveBackupExists
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (hasBackupInfo) {
                                        Modifier.clickable {
                                            isConfirmingDeleteInDialog = false
                                            showBackupDetailsDialog = true
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CloudDone,
                                        contentDescription = null,
                                        tint = if (hasBackupInfo) SafeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(Dimensions.IconSize.small)
                                    )
                                    Text(
                                        text = when {
                                            formattedLastSync != null -> stringResource(R.string.settings_drive_last_sync, formattedLastSync)
                                            driveBackupExists -> stringResource(R.string.settings_drive_backup_found)
                                            else -> stringResource(R.string.settings_drive_last_sync_never)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (hasBackupInfo) {
                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(Dimensions.IconSize.small)
                                    )
                                }
                            }
                        }
                    }

                    // 4. Bloque de acciones
                    if (!isDriveConnected) {
                        Button(
                            onClick = {
                                requestGoogleAuthorization { token ->
                                    isDriveConnected = true
                                    prefsManager.setGoogleDriveConnected(true)
                                    scope.launch {
                                        driveBackupExists = GoogleDriveManager.hasExistingBackup(token)
                                        snackbarHostState.showSnackbar(context.getString(R.string.settings_drive_connected_success))
                                    }
                                }
                            },
                            enabled = !isDriveLoading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        ) {
                            if (isDriveLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Dimensions.IconSize.small),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = Dimensions.Stroke.regular
                                )
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.ic_brand_google),
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.IconSize.small)
                                )
                                Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                                Text(
                                    text = stringResource(R.string.settings_drive_connect_button),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    } else if (lastSyncTimestamp > 0L) {
                        // Estado: Ya sincronizado (Botón único de "Sincronizar ahora")
                        Button(
                            onClick = {
                                val performSync: (String) -> Unit = { token ->
                                    scope.launch {
                                        isDriveLoading = true
                                        try {
                                            val payload = repository.exportAccountsForTransfer()
                                            val autoSyncKey = "AppOPT_AutoSync_Vault_E2EE_v1".toCharArray()
                                            try {
                                                val uploadResult = GoogleDriveManager.uploadBackup(token, payload, autoSyncKey)
                                                uploadResult.onSuccess {
                                                    val now = System.currentTimeMillis()
                                                    val currentHash = CloudVaultSyncManager.computeVaultHash(payload)
                                                    lastSyncTimestamp = now
                                                    prefsManager.setLastSyncTimestamp(now)
                                                    prefsManager.setLastSyncedVaultHash(currentHash)
                                                    driveBackupExists = true
                                                    snackbarHostState.showSnackbar(context.getString(R.string.settings_drive_sync_success))
                                                }.onFailure { error ->
                                                    snackbarHostState.showSnackbar(context.getString(R.string.settings_drive_error, error.localizedMessage ?: ""))
                                                }
                                            } finally {
                                                autoSyncKey.fill('0')
                                            }
                                        } finally {
                                            isDriveLoading = false
                                        }
                                    }
                                }

                                if (driveAccessToken == null) {
                                    requestGoogleAuthorization { token ->
                                        performSync(token)
                                    }
                                } else {
                                    performSync(driveAccessToken!!)
                                }
                            },
                            enabled = !isDriveLoading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        ) {
                            if (isDriveLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Dimensions.IconSize.small),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = Dimensions.Stroke.regular
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.settings_drive_sync_button),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    } else if (driveBackupExists) {
                        // Estado: Conectado y existe copia previa en Drive (Restaurar o Crear nueva)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                        ) {
                            Button(
                                onClick = {
                                    restoreSecretText = ""
                                    if (driveAccessToken == null) {
                                        requestGoogleAuthorization {
                                            showDriveDecryptDialog = true
                                        }
                                    } else {
                                        showDriveDecryptDialog = true
                                    }
                                },
                                enabled = !isDriveLoading,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_restore_button),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    driveProtectStep = 1
                                    generatedMnemonicWords = MnemonicManager.generate12WordPhrase()
                                    masterPasswordText = ""
                                    masterPasswordConfirmText = ""
                                    generated64Key = GoogleDriveManager.generate64DigitKey()
                                    showDriveProtectDialog = true
                                },
                                enabled = !isDriveLoading,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_drive_create_button),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    } else {
                        // Estado: Conectado sin copia en Drive (Botón único de "Crear copia de seguridad")
                        Button(
                            onClick = {
                                driveProtectStep = 1
                                generatedMnemonicWords = MnemonicManager.generate12WordPhrase()
                                masterPasswordText = ""
                                masterPasswordConfirmText = ""
                                generated64Key = GoogleDriveManager.generate64DigitKey()
                                showDriveProtectDialog = true
                            },
                            enabled = !isDriveLoading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_drive_create_button),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                        // 5. Contenedor de configuración de automatización
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        ) {
                            Column(
                                modifier = Modifier.padding(Dimensions.Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                            ) {
                                // Fila: Frecuencia de la copia
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showFrequencyDialog = true }
                                        .padding(vertical = Dimensions.Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f).padding(end = Dimensions.Spacing.sm),
                                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_drive_frequency_title),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        val frequencyLabel = when (syncFrequency) {
                                            SyncFrequency.DAILY -> stringResource(R.string.settings_drive_frequency_daily)
                                            SyncFrequency.WEEKLY -> stringResource(R.string.settings_drive_frequency_weekly)
                                            SyncFrequency.MONTHLY -> stringResource(R.string.settings_drive_frequency_monthly)
                                            SyncFrequency.OFF -> stringResource(R.string.settings_drive_frequency_off)
                                        }
                                        Text(
                                            text = frequencyLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Fila: Uso de datos móviles (solo si no está desactivada)
                                if (syncFrequency != SyncFrequency.OFF) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val newState = !isSyncMobileDataAllowed
                                                isSyncMobileDataAllowed = newState
                                                prefsManager.setSyncMobileDataAllowed(newState)
                                                CloudVaultSyncManager.schedulePeriodicSync(context, syncFrequency, newState)
                                            }
                                            .padding(vertical = Dimensions.Spacing.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f).padding(end = Dimensions.Spacing.sm),
                                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.settings_drive_mobile_data_label),
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = stringResource(R.string.settings_drive_mobile_data_description),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = isSyncMobileDataAllowed,
                                            onCheckedChange = {
                                                isSyncMobileDataAllowed = it
                                                prefsManager.setSyncMobileDataAllowed(it)
                                                CloudVaultSyncManager.schedulePeriodicSync(context, syncFrequency, it)
                                            },
                                            colors = appSwitchColors()
                                        )
                                    }
                                }

                                // Fila: Eliminar copia de seguridad (si existe alguna registrada)
                                if (formattedLastSync != null || driveBackupExists) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.Spacing.xs))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                isConfirmingDeleteInDialog = true
                                                showBackupDetailsDialog = true
                                            }
                                            .padding(vertical = Dimensions.Spacing.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(Dimensions.IconSize.small)
                                            )
                                            Text(
                                                text = stringResource(R.string.settings_drive_delete_button),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    // Modal: Confirmación de Desvinculación de Google Drive
    if (showDisconnectConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_drive_disconnect_confirm_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_drive_disconnect_confirm_msg),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectConfirmDialog = false
                        isDriveConnected = false
                        driveAccessToken = null
                        driveBackupExists = false
                        lastSyncTimestamp = 0L
                        prefsManager.setGoogleDriveConnected(false)
                        prefsManager.setLastSyncTimestamp(0L)
                        prefsManager.setLastSyncedVaultHash("")
                        CloudVaultSyncManager.schedulePeriodicSync(context, SyncFrequency.OFF, false)
                        com.google.android.gms.auth.api.identity.Identity.getSignInClient(context).signOut()
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.settings_drive_disconnected_success))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Text(
                        text = stringResource(R.string.settings_drive_disconnect_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    // Modal Unificado: Selección de Servicios y Visualización de Código QR para Transferencia
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = {
                if (isShowingQrInExportDialog) {
                    isShowingQrInExportDialog = false
                } else {
                    showExportDialog = false
                }
            },
            title = {
                Text(
                    text = if (isShowingQrInExportDialog) {
                        stringResource(R.string.settings_export_qr_dialog_title)
                    } else {
                        stringResource(R.string.settings_export_services_dialog_title)
                    },
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                if (!isShowingQrInExportDialog) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_export_services_dialog_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = Dimensions.ComponentSize.modalListMaxHeight)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                        ) {
                            accounts.forEach { account ->
                                val isSelected = account.id in selectedServiceIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isSelected) {
                                                selectedServiceIds.remove(account.id)
                                            } else {
                                                selectedServiceIds.add(account.id)
                                            }
                                        }
                                        .padding(vertical = Dimensions.Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ServiceBrandAvatar(
                                        issuer = account.issuer,
                                        size = Dimensions.ComponentSize.actionIconButton
                                    )
                                    Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = account.issuer,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        if (account.accountName.isNotBlank()) {
                                            Text(
                                                text = account.accountName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                selectedServiceIds.add(account.id)
                                            } else {
                                                selectedServiceIds.remove(account.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.Spacing.xs))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { keepServicesOnDevice = !keepServicesOnDevice }
                                .padding(vertical = Dimensions.Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = Dimensions.Spacing.sm)) {
                                Text(
                                    text = stringResource(R.string.settings_keep_services_label),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = stringResource(R.string.settings_keep_services_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = keepServicesOnDevice,
                                onCheckedChange = { keepServicesOnDevice = it },
                                colors = appSwitchColors()
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        transferQrBitmap?.let { bitmap ->
                            Surface(
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                color = Color.White,
                                modifier = Modifier.padding(Dimensions.Spacing.sm)
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(Dimensions.ComponentSize.qrCodeDisplay)
                                        .padding(Dimensions.Spacing.sm)
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.settings_export_qr_dialog_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                if (!isShowingQrInExportDialog) {
                    Button(
                        onClick = {
                            val idsToExport = selectedServiceIds.toSet()
                            scope.launch {
                                val payload = repository.exportAccountsForTransfer(idsToExport)
                                transferQrBitmap = QrCodeGenerator.generateQrBitmap(payload, size = 600)
                                exportedServiceIds = idsToExport
                                isShowingQrInExportDialog = true
                            }
                        },
                        enabled = selectedServiceIds.isNotEmpty(),
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(stringResource(R.string.settings_generate_qr_button), style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = {
                            if (!keepServicesOnDevice && exportedServiceIds.isNotEmpty()) {
                                scope.launch {
                                    exportedServiceIds.forEach { id ->
                                        repository.deleteAccount(id)
                                    }
                                    snackbarHostState.showSnackbar(servicesDeletedMsg)
                                }
                            }
                            showExportDialog = false
                            isShowingQrInExportDialog = false
                        },
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(
                            text = if (!keepServicesOnDevice) {
                                stringResource(R.string.settings_export_confirm_done)
                            } else {
                                stringResource(R.string.account_modal_close_button)
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            },
            dismissButton = {
                if (!isShowingQrInExportDialog) {
                    TextButton(onClick = {
                        showExportDialog = false
                        isShowingQrInExportDialog = false
                    }) {
                        Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    TextButton(onClick = { isShowingQrInExportDialog = false }) {
                        Text(stringResource(R.string.settings_drive_details_back), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        )
    }

    // Modal: Protección E2EE al sincronizar en Google Drive (Máquina de 3 Pasos: Método Principal -> 12 Palabras -> Cuestionario)
    if (showDriveProtectDialog) {
        val isPasswordValid = masterPasswordText.length >= 10 && masterPasswordText == masterPasswordConfirmText
        val isStep1Valid = if (selectedProtectionTab == 0) isPasswordValid else generated64Key.isNotBlank()
        val isQuizAnswered = quizQuestions.isNotEmpty() && quizSelectedAnswers.size == quizQuestions.size

        AlertDialog(
            onDismissRequest = {
                when (driveProtectStep) {
                    3 -> driveProtectStep = 2 // Navegación defensiva hacia atrás
                    2 -> driveProtectStep = 1
                    else -> showDriveProtectDialog = false
                }
            },
            title = {
                Text(
                    text = when (driveProtectStep) {
                        1 -> stringResource(R.string.settings_drive_protect_step1_title)
                        2 -> stringResource(R.string.settings_drive_protect_step2_title)
                        else -> stringResource(R.string.settings_drive_quiz_title)
                    },
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                ) {
                    when (driveProtectStep) {
                        1 -> {
                            // PASO 1: Selección y configuración del Método Principal
                            Text(
                                text = stringResource(R.string.settings_drive_protect_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            PrimaryTabRow(selectedTabIndex = selectedProtectionTab) {
                                Tab(
                                    selected = selectedProtectionTab == 0,
                                    onClick = { selectedProtectionTab = 0 },
                                    text = { Text(stringResource(R.string.settings_drive_method_password), style = MaterialTheme.typography.labelSmall) },
                                    icon = { Icon(Icons.Filled.Password, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small)) }
                                )
                                Tab(
                                    selected = selectedProtectionTab == 1,
                                    onClick = { selectedProtectionTab = 1 },
                                    text = { Text(stringResource(R.string.settings_drive_method_key), style = MaterialTheme.typography.labelSmall) },
                                    icon = { Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small)) }
                                )
                            }

                            if (selectedProtectionTab == 0) {
                                // Opción 1: Contraseña Maestra Fuerte
                                Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)) {
                                    OutlinedTextField(
                                        value = masterPasswordText,
                                        onValueChange = { masterPasswordText = it },
                                        label = { Text(stringResource(R.string.settings_drive_password_label)) },
                                        visualTransformation = if (isMasterPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { isMasterPasswordVisible = !isMasterPasswordVisible }) {
                                                Icon(
                                                    imageVector = if (isMasterPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                    contentDescription = null
                                                )
                                            }
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = masterPasswordConfirmText,
                                        onValueChange = { masterPasswordConfirmText = it },
                                        label = { Text(stringResource(R.string.settings_drive_password_confirm_label)) },
                                        visualTransformation = if (isMasterPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        singleLine = true,
                                        isError = masterPasswordConfirmText.isNotEmpty() && masterPasswordText != masterPasswordConfirmText,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (masterPasswordText.isNotEmpty() && masterPasswordText.length < 10) {
                                        Text(
                                            text = stringResource(R.string.settings_drive_password_too_short),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else if (masterPasswordConfirmText.isNotEmpty() && masterPasswordText != masterPasswordConfirmText) {
                                        Text(
                                            text = stringResource(R.string.settings_drive_password_mismatch),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_drive_password_warning),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(Dimensions.Spacing.sm)
                                        )
                                    }
                                }
                            } else {
                                // Opción 2: Clave generada de 64 dígitos
                                Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)) {
                                    Surface(
                                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(Dimensions.Spacing.sm)) {
                                            Text(
                                                text = generated64Key,
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                appHaptics.copy()
                                                secureClipboard.copyToClipboard(
                                                    label = "AppOPT-BackupKey",
                                                    text = generated64Key,
                                                    autoClearSeconds = 60
                                                )
                                                Toast.makeText(context, keyCopiedMsg, Toast.LENGTH_LONG).show()
                                            }
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small))
                                            Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                                            Text(stringResource(R.string.settings_drive_copy_60s), style = MaterialTheme.typography.labelMedium)
                                        }

                                        TextButton(
                                            onClick = { generated64Key = GoogleDriveManager.generate64DigitKey() }
                                        ) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small))
                                            Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                                            Text(stringResource(R.string.settings_drive_key_regenerate), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_drive_key_warning),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(Dimensions.Spacing.sm)
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            // PASO 2: Kit de Recuperación Consolidado (Método Ppal + 12 Palabras BIP-39)
                            Text(
                                text = stringResource(R.string.settings_drive_emergency_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)) {
                                // 1. Resumen del Método Principal
                                Text(
                                    text = stringResource(R.string.settings_drive_summary_primary_title),
                                    style = MaterialTheme.typography.titleSmall
                                )

                                Surface(
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(Dimensions.Spacing.sm),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (selectedProtectionTab == 0) Icons.Filled.Password else Icons.Filled.Key,
                                            contentDescription = null,
                                            modifier = Modifier.size(Dimensions.IconSize.small),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                                        Text(
                                            text = if (selectedProtectionTab == 0) {
                                                "•••••••••••• (${masterPasswordText.length} caracteres)"
                                            } else {
                                                generated64Key
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))

                                // 2. Resumen de la Frase de Emergencia (12 Palabras BIP-39)
                                Text(
                                    text = stringResource(R.string.settings_drive_summary_emergency_title),
                                    style = MaterialTheme.typography.titleSmall
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                                    ) {
                                        generatedMnemonicWords.take(6).forEachIndexed { index, word ->
                                            Surface(
                                                shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "${index + 1}. $word",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                    modifier = Modifier.padding(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.xs)
                                                )
                                            }
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                                    ) {
                                        generatedMnemonicWords.drop(6).forEachIndexed { index, word ->
                                            Surface(
                                                shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "${index + 7}. $word",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                    modifier = Modifier.padding(horizontal = Dimensions.Spacing.sm, vertical = Dimensions.Spacing.xs)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            appHaptics.copy()
                                            val fullPhrase = generatedMnemonicWords.joinToString(" ")
                                            secureClipboard.copyToClipboard(
                                                label = "AppOPT-MnemonicWords",
                                                text = fullPhrase,
                                                autoClearSeconds = 60
                                            )
                                            Toast.makeText(context, wordsCopiedMsg, Toast.LENGTH_LONG).show()
                                        }
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small))
                                        Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                                        Text(stringResource(R.string.settings_drive_copy_60s), style = MaterialTheme.typography.labelMedium)
                                    }

                                    TextButton(
                                        onClick = { generatedMnemonicWords = MnemonicManager.generate12WordPhrase() }
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small))
                                        Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                                        Text(stringResource(R.string.settings_drive_words_regenerate), style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                // Botón para Imprimir / Guardar Emergency Kit en PDF
                                OutlinedButton(
                                    onClick = {
                                        appHaptics.click()
                                        val methodTitle = if (selectedProtectionTab == 0) {
                                            context.getString(R.string.settings_drive_password_label)
                                        } else {
                                            context.getString(R.string.settings_drive_key_label)
                                        }
                                        val methodValue = if (selectedProtectionTab == 0) {
                                            "•••••••••••• (${masterPasswordText.length} caracteres)"
                                        } else {
                                            generated64Key
                                        }
                                        EmergencyKitPdfGenerator.printEmergencyKit(
                                            context = context,
                                            primaryMethodTitle = methodTitle,
                                            primaryMethodValue = methodValue,
                                            mnemonicWords = generatedMnemonicWords
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                                ) {
                                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small))
                                    Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                                    Text(stringResource(R.string.settings_drive_print_pdf), style = MaterialTheme.typography.labelMedium)
                                }

                                Text(
                                    text = stringResource(R.string.settings_drive_print_pdf_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Surface(
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_drive_words_warning),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(Dimensions.Spacing.sm)
                                    )
                                }
                            }
                        }
                        else -> {
                            // PASO 3: Cuestionario de Verificación BIP-39
                            Text(
                                text = stringResource(R.string.settings_drive_quiz_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)) {
                                quizQuestions.forEach { question ->
                                    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)) {
                                        Text(
                                            text = stringResource(R.string.settings_drive_quiz_question_label, question.position),
                                            style = MaterialTheme.typography.titleSmall
                                        )

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                                        ) {
                                            question.options.forEach { option ->
                                                val isSelected = quizSelectedAnswers[question.position] == option
                                                Surface(
                                                    shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            appHaptics.click()
                                                            isQuizError = false
                                                            quizSelectedAnswers = quizSelectedAnswers + (question.position to option)
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.sm),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = option,
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1
                                                        )
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Filled.CheckCircle,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(Dimensions.IconSize.small),
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (isQuizError) {
                                    Surface(
                                        shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                        color = MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_drive_quiz_error),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(Dimensions.Spacing.sm)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                when (driveProtectStep) {
                    1 -> {
                        Button(
                            onClick = { driveProtectStep = 2 },
                            enabled = isStep1Valid,
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        ) {
                            Text(stringResource(R.string.settings_drive_next_step), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    2 -> {
                        Button(
                            onClick = {
                                quizQuestions = MnemonicManager.generateQuiz(generatedMnemonicWords, 2)
                                quizSelectedAnswers = emptyMap()
                                isQuizError = false
                                driveProtectStep = 3
                            },
                            enabled = generatedMnemonicWords.size == 12,
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        ) {
                            Text(stringResource(R.string.settings_drive_to_quiz_step), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    else -> {
                        Button(
                            onClick = {
                                val isAllCorrect = quizQuestions.all { quizSelectedAnswers[it.position] == it.correctWord }
                                if (!isAllCorrect) {
                                    appHaptics.error()
                                    isQuizError = true
                                    Toast.makeText(context, context.getString(R.string.settings_drive_quiz_error), Toast.LENGTH_LONG).show()
                                    return@Button
                                }

                                val primaryPassChars = if (selectedProtectionTab == 0) {
                                    masterPasswordText.toCharArray()
                                } else {
                                    generated64Key.toCharArray()
                                }
                                val emergencyMnemonicChars = MnemonicManager.normalizePhrase(
                                    generatedMnemonicWords.joinToString(" ")
                                ).toCharArray()

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
                                            CloudVaultSyncManager.schedulePeriodicSync(context, syncFrequency, isSyncMobileDataAllowed)
                                            snackbarHostState.showSnackbar(context.getString(R.string.settings_drive_sync_success))
                                        }.onFailure { error ->
                                            snackbarHostState.showSnackbar(context.getString(R.string.settings_drive_error, error.localizedMessage ?: ""))
                                        }
                                    } finally {
                                        primaryPassChars.fill('0')
                                        emergencyMnemonicChars.fill('0')
                                        isDriveLoading = false
                                    }
                                }
                            },
                            enabled = isQuizAnswered,
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        ) {
                            Text(stringResource(R.string.settings_drive_encrypt_and_sync), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        when (driveProtectStep) {
                            3 -> driveProtectStep = 2
                            2 -> driveProtectStep = 1
                            else -> showDriveProtectDialog = false
                        }
                    }
                ) {
                    Text(
                        text = when (driveProtectStep) {
                            3 -> stringResource(R.string.settings_drive_quiz_review_words)
                            2 -> stringResource(R.string.settings_drive_details_back)
                            else -> stringResource(R.string.action_cancel)
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }

    // Modal: Descifrado y Restauración desde Google Drive
    if (showDriveDecryptDialog) {
        AlertDialog(
            onDismissRequest = { showDriveDecryptDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_drive_decrypt_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.settings_drive_decrypt_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))

                    OutlinedTextField(
                        value = restoreSecretText,
                        onValueChange = { restoreSecretText = it },
                        label = { Text(stringResource(R.string.settings_drive_decrypt_input_label)) },
                        visualTransformation = if (isRestoreSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isRestoreSecretVisible = !isRestoreSecretVisible }) {
                                Icon(
                                    imageVector = if (isRestoreSecretVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val normalizedSecret = if (restoreSecretText.contains(" ")) {
                            MnemonicManager.normalizePhrase(restoreSecretText)
                        } else {
                            restoreSecretText.trim()
                        }
                        val passChars = normalizedSecret.toCharArray()
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
                                        CloudVaultSyncManager.schedulePeriodicSync(context, syncFrequency, isSyncMobileDataAllowed)
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.settings_drive_restore_success, count)
                                        )
                                    }.onFailure { error ->
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.settings_drive_error, error.localizedMessage ?: "")
                                        )
                                    }
                                }.onFailure {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.settings_drive_decrypt_error)
                                    )
                                }
                            } finally {
                                passChars.fill('0')
                                isDriveLoading = false
                            }
                        }
                    },
                    enabled = restoreSecretText.isNotBlank(),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Text(stringResource(R.string.settings_drive_decrypt_and_restore), style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDriveDecryptDialog = false }) {
                    Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    // Modal: Selección de Frecuencia de Copia de Seguridad
    if (showFrequencyDialog) {
        AlertDialog(
            onDismissRequest = { showFrequencyDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_drive_frequency_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                ) {
                    val frequencyOptions = listOf(
                        SyncFrequency.DAILY to R.string.settings_drive_frequency_daily,
                        SyncFrequency.WEEKLY to R.string.settings_drive_frequency_weekly,
                        SyncFrequency.MONTHLY to R.string.settings_drive_frequency_monthly,
                        SyncFrequency.OFF to R.string.settings_drive_frequency_off
                    )

                    frequencyOptions.forEach { (frequencyOption, labelRes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    syncFrequency = frequencyOption
                                    prefsManager.setSyncFrequency(frequencyOption)
                                    CloudVaultSyncManager.schedulePeriodicSync(context, frequencyOption, isSyncMobileDataAllowed)
                                    showFrequencyDialog = false
                                }
                                .padding(vertical = Dimensions.Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = syncFrequency == frequencyOption,
                                onClick = {
                                    syncFrequency = frequencyOption
                                    prefsManager.setSyncFrequency(frequencyOption)
                                    CloudVaultSyncManager.schedulePeriodicSync(context, frequencyOption, isSyncMobileDataAllowed)
                                    showFrequencyDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFrequencyDialog = false }) {
                    Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    // Modal Unificado: Detalles de la Copia de Seguridad y Confirmación de Eliminación
    if (showBackupDetailsDialog) {
        AlertDialog(
            onDismissRequest = {
                if (isConfirmingDeleteInDialog) {
                    isConfirmingDeleteInDialog = false
                } else {
                    showBackupDetailsDialog = false
                }
            },
            title = {
                Text(
                    text = if (isConfirmingDeleteInDialog) {
                        stringResource(R.string.settings_drive_delete_confirm_title)
                    } else {
                        stringResource(R.string.settings_drive_details_title)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isConfirmingDeleteInDialog) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                if (!isConfirmingDeleteInDialog) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_drive_details_date, formattedLastSync ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_drive_details_encryption),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))

                        OutlinedButton(
                            onClick = { isConfirmingDeleteInDialog = true },
                            enabled = !isDriveLoading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.IconSize.small)
                            )
                            Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                            Text(
                                text = stringResource(R.string.settings_drive_delete_button),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.settings_drive_delete_confirm_msg),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                if (isConfirmingDeleteInDialog) {
                    Button(
                        onClick = {
                            showBackupDetailsDialog = false
                            isConfirmingDeleteInDialog = false
                            val executeDelete: (String) -> Unit = { token ->
                                scope.launch {
                                    isDriveLoading = true
                                    try {
                                        val deleteResult = GoogleDriveManager.deleteBackup(token)
                                        deleteResult.onSuccess {
                                            prefsManager.setLastSyncTimestamp(0L)
                                            prefsManager.setLastSyncedVaultHash("")
                                            lastSyncTimestamp = 0L
                                            driveBackupExists = false
                                            snackbarHostState.showSnackbar(context.getString(R.string.settings_drive_delete_success))
                                        }.onFailure { error ->
                                            snackbarHostState.showSnackbar(context.getString(R.string.settings_drive_error, error.localizedMessage ?: ""))
                                        }
                                    } finally {
                                        isDriveLoading = false
                                    }
                                }
                            }

                            if (driveAccessToken == null) {
                                requestGoogleAuthorization { token ->
                                    executeDelete(token)
                                }
                            } else {
                                executeDelete(driveAccessToken!!)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Text(stringResource(R.string.settings_drive_delete_confirm_btn), style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    TextButton(onClick = {
                        showBackupDetailsDialog = false
                        isConfirmingDeleteInDialog = false
                    }) {
                        Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
            dismissButton = {
                if (isConfirmingDeleteInDialog) {
                    TextButton(onClick = { isConfirmingDeleteInDialog = false }) {
                        Text(stringResource(R.string.settings_drive_details_back), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        )
    }
}
