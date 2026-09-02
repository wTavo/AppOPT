package com.example.appopt.ui.screens.settings

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.util.QrCodeGenerator
import kotlinx.coroutines.launch

/**
 * Pantalla de configuración de seguridad, transferencia offline de cuentas y respaldo en la nube.
 *
 * Características:
 * - Diagnóstico del estado criptográfico de la bóveda local (AES-256-GCM + Android Keystore TEE).
 * - Transferencia directa e interoperable entre dispositivos mediante Códigos QR.
 * - Preparación de copia de seguridad en Google Drive sin archivos ni contraseñas manuales.
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

    var transferQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showExportQrDialog by remember { mutableStateOf(false) }

    val emptyAccountsMsg = stringResource(R.string.settings_export_qr_empty)
    val driveInfoMsg = stringResource(R.string.settings_drive_feature_info)

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
                                scope.launch {
                                    val payload = repository.exportAccountsForTransfer()
                                    if (payload.isBlank()) {
                                        snackbarHostState.showSnackbar(emptyAccountsMsg)
                                    } else {
                                        val bitmap = QrCodeGenerator.generateQrBitmap(payload, size = 600)
                                        transferQrBitmap = bitmap
                                        showExportQrDialog = true
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        ) {
                            Text(stringResource(R.string.settings_export_qr_button), style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = onNavigateToScanQr,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        ) {
                            Text(stringResource(R.string.settings_import_qr_button), style = MaterialTheme.typography.labelLarge)
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
                                imageVector = Icons.Filled.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
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
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = stringResource(R.string.settings_drive_status_not_synced),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                    Spacer(modifier = Modifier.height(Dimensions.Spacing.md))

                    Button(
                        onClick = {
                            Toast.makeText(context, driveInfoMsg, Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.small))
                        Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                        Text(stringResource(R.string.settings_drive_sync_button), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    // Modal para visualizar el Código QR de Transferencia directa
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
                            color = androidx.compose.ui.graphics.Color.White,
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
                    onClick = { showExportQrDialog = false },
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                ) {
                    Text(stringResource(R.string.account_modal_close_button), style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}
