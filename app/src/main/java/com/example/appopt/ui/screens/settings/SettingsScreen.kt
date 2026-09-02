package com.example.appopt.ui.screens.settings

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.ui.components.ServiceBrandAvatar
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.util.QrCodeGenerator
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

/**
 * Pantalla de configuración de seguridad, transferencia offline de servicios y sincronización en la nube con Google Drive.
 *
 * Características de seguridad y diseño:
 * - Diagnóstico del estado criptográfico de la bóveda local (AES-256-GCM + Android Keystore TEE).
 * - Transferencia directa e interoperable entre dispositivos mediante Códigos QR con selección granular de servicios.
 * - Sincronización en la nube con Google Drive (`appDataFolder`) utilizando tokens OAuth2 efímeros.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToScanQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val repository = AuthenticatorApp.instance.accountRepository

    val accounts by repository.getAccounts().collectAsStateWithLifecycle(initialValue = emptyList())

    // Estados para el flujo de exportación por QR
    var showSelectServicesDialog by remember { mutableStateOf(false) }
    val selectedServiceIds = remember { mutableStateListOf<String>() }
    var keepServicesOnDevice by remember { mutableStateOf(true) }
    var exportedServiceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var transferQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showExportQrDialog by remember { mutableStateOf(false) }

    // Estados para la sincronización con Google Drive
    val googleSignInClient = remember { GoogleDriveManager.getGoogleSignInClient(context) }
    var googleAccount by remember { mutableStateOf(GoogleDriveManager.getLastSignedInAccount(context)) }
    var isDriveLoading by remember { mutableStateOf(false) }
    var showDriveRestoreConfirmDialog by remember { mutableStateOf(false) }

    val emptyServicesMsg = stringResource(R.string.settings_export_services_empty)
    val servicesDeletedMsg = stringResource(R.string.settings_services_deleted_after_export)

    // Launcher para el flujo interactivo de Google Sign-In
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            googleAccount = account
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.settings_drive_connected_as, account.email ?: "")
                )
            }
        } catch (e: ApiException) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.settings_drive_error, e.localizedMessage ?: "Error de autenticación (${e.statusCode})")
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                    ) {
                        Button(
                            onClick = {
                                if (accounts.isEmpty()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(emptyServicesMsg)
                                    }
                                } else {
                                    selectedServiceIds.clear()
                                    selectedServiceIds.addAll(accounts.map { it.id })
                                    keepServicesOnDevice = true
                                    showSelectServicesDialog = true
                                }
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
                Column(modifier = Modifier.padding(Dimensions.Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (googleAccount != null) Icons.Filled.CloudDone else Icons.Filled.Sync,
                                contentDescription = null,
                                tint = if (googleAccount != null) SafeGreen else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Dimensions.IconSize.medium)
                            )
                            Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                            Text(
                                text = stringResource(R.string.settings_drive_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                            color = if (googleAccount != null) SafeGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (googleAccount != null) {
                                    stringResource(R.string.settings_drive_status_synced)
                                } else {
                                    stringResource(R.string.settings_drive_status_not_synced)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (googleAccount != null) SafeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Dimensions.Spacing.sm, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))

                    Text(
                        text = stringResource(R.string.settings_drive_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Muestra el correo de la cuenta de Google vinculada
                    googleAccount?.email?.let { email ->
                        Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                        Text(
                            text = stringResource(R.string.settings_drive_connected_as, email),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimensions.Spacing.md))

                    if (googleAccount == null) {
                        Button(
                            onClick = {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_brand_google),
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.IconSize.small)
                            )
                            Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                            Text(stringResource(R.string.settings_drive_connect_button), style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isDriveLoading = true
                                        val payload = repository.exportAccountsForTransfer()
                                        if (payload.isBlank()) {
                                            snackbarHostState.showSnackbar(emptyServicesMsg)
                                            isDriveLoading = false
                                        } else {
                                            val uploadResult = GoogleDriveManager.uploadBackup(context, googleAccount!!, payload)
                                            uploadResult.onSuccess {
                                                snackbarHostState.showSnackbar(context.getString(R.string.settings_drive_sync_success))
                                            }.onFailure { error ->
                                                snackbarHostState.showSnackbar(context.getString(R.string.settings_drive_error, error.localizedMessage ?: ""))
                                            }
                                            isDriveLoading = false
                                        }
                                    }
                                },
                                enabled = !isDriveLoading,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                            ) {
                                if (isDriveLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(Dimensions.IconSize.small),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(stringResource(R.string.settings_drive_sync_button), style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            OutlinedButton(
                                onClick = { showDriveRestoreConfirmDialog = true },
                                enabled = !isDriveLoading,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                            ) {
                                Text(stringResource(R.string.settings_drive_restore_button), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal 1: Selección de Servicios y Opción de Retención
    if (showSelectServicesDialog) {
        AlertDialog(
            onDismissRequest = { showSelectServicesDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_export_services_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
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
                            .heightIn(max = 240.dp)
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
                                    size = 36.dp
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
                            onCheckedChange = { keepServicesOnDevice = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idsToExport = selectedServiceIds.toSet()
                        scope.launch {
                            val payload = repository.exportAccountsForTransfer(idsToExport)
                            transferQrBitmap = QrCodeGenerator.generateQrBitmap(payload, size = 600)
                            exportedServiceIds = idsToExport
                            showSelectServicesDialog = false
                            showExportQrDialog = true
                        }
                    },
                    enabled = selectedServiceIds.isNotEmpty(),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Text(stringResource(R.string.settings_generate_qr_button), style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSelectServicesDialog = false }) {
                    Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    // Modal 2: Visualizar Código QR generado y Confirmar Transferencia
    if (showExportQrDialog && transferQrBitmap != null) {
        AlertDialog(
            onDismissRequest = { showExportQrDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_export_qr_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
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
                                    .size(240.dp)
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
            },
            confirmButton = {
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
                        showExportQrDialog = false
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
            },
            dismissButton = {
                if (!keepServicesOnDevice) {
                    TextButton(onClick = { showExportQrDialog = false }) {
                        Text(stringResource(R.string.account_modal_close_button), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        )
    }

    // Modal 3: Confirmación de Restauración desde Google Drive
    if (showDriveRestoreConfirmDialog && googleAccount != null) {
        AlertDialog(
            onDismissRequest = { showDriveRestoreConfirmDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_drive_restore_confirm_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_drive_restore_confirm_msg),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDriveRestoreConfirmDialog = false
                        scope.launch {
                            isDriveLoading = true
                            val downloadResult = GoogleDriveManager.downloadBackup(context, googleAccount!!)
                            downloadResult.onSuccess { jsonPayload ->
                                val importResult = repository.importAccountsFromTransfer(jsonPayload)
                                importResult.onSuccess { count ->
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.settings_drive_restore_success, count)
                                    )
                                }.onFailure { error ->
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.settings_drive_error, error.localizedMessage ?: "")
                                    )
                                }
                            }.onFailure { error ->
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.settings_drive_error, error.localizedMessage ?: "")
                                )
                            }
                            isDriveLoading = false
                        }
                    },
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Text(stringResource(R.string.settings_drive_restore_button), style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDriveRestoreConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}
