package com.example.appopt.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import kotlinx.coroutines.launch

/**
 * Pantalla de configuración de seguridad, respaldos cifrados y recuperación offline.
 *
 * Características:
 * - Generación de Recovery Key de 256 bits de entropía.
 * - Exportación de la bóveda a un archivo cifrado con contraseña (PBKDF2 + AES-GCM).
 * - Importación y restauración de cuentas desde archivos de respaldo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val cryptoManager = AuthenticatorApp.instance.cryptoManager
    val repository = AuthenticatorApp.instance.accountRepository
    val clipboardManager = AuthenticatorApp.instance.secureClipboardManager

    var recoveryKey by remember { mutableStateOf<String?>(null) }
    var copiedKey by remember { mutableStateOf(false) }
    var showGenerateDialog by remember { mutableStateOf(false) }

    // Estados de diálogo de exportación / importación
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    // Export Launcher (Storage Access Framework)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            pendingExportUri = uri
            showExportDialog = true
        }
    }

    // Import Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportDialog = true
        }
    }

    val exportSuccessMsg = stringResource(R.string.settings_export_success)
    val importSuccessFormat = stringResource(R.string.settings_import_success)
    val importErrorMsg = stringResource(R.string.settings_import_error)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Tarjeta de estado y diagnóstico de seguridad
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = SafeGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_vault_status_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(R.string.settings_vault_status_details),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }

            // Sección de Copia de Seguridad y Restauración
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.SaveAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_backup_section_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.settings_backup_section_description),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                exportLauncher.launch("authenticator_backup_${System.currentTimeMillis()}.enc")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_export_backup_button), fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_import_backup_button), fontSize = 13.sp)
                        }
                    }
                }
            }

            // Sección de Clave de Recuperación
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_recovery_key_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.settings_recovery_key_description),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (recoveryKey == null) {
                        Button(
                            onClick = { showGenerateDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.settings_generate_recovery_button))
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = recoveryKey ?: "",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedButton(
                                    onClick = {
                                        recoveryKey?.let {
                                            clipboardManager.copyToClipboard("Recovery Key", it, 60)
                                            copiedKey = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = if (copiedKey) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (copiedKey) stringResource(R.string.action_copied_recovery)
                                        else stringResource(R.string.action_copy_key)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Modal para Exportar Respaldo Cifrado con Contraseña
    if (showExportDialog) {
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        val mismatchError = stringResource(R.string.settings_export_password_mismatch)
        val shortError = stringResource(R.string.settings_export_password_too_short)

        AlertDialog(
            onDismissRequest = {
                showExportDialog = false
                pendingExportUri = null
            },
            title = { Text(stringResource(R.string.settings_export_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text(stringResource(R.string.settings_export_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMessage = null },
                        label = { Text(stringResource(R.string.settings_export_password_confirm_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Text(text = errorMessage ?: "", color = UrgentRed, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (password.length < 8) {
                            errorMessage = shortError
                            return@Button
                        }
                        if (password != confirmPassword) {
                            errorMessage = mismatchError
                            return@Button
                        }

                        val uri = pendingExportUri ?: return@Button
                        val pwdArray = password.toCharArray()

                        scope.launch {
                            try {
                                val encryptedBytes = repository.exportVault(pwdArray)
                                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                    outputStream.write(encryptedBytes)
                                }
                                showExportDialog = false
                                pendingExportUri = null
                                snackbarHostState.showSnackbar(exportSuccessMsg)
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage
                            } finally {
                                pwdArray.fill('0')
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    pendingExportUri = null
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Modal para Importar Respaldo Cifrado con Contraseña
    if (showImportDialog) {
        var importPassword by remember { mutableStateOf("") }
        var importError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                pendingImportUri = null
            },
            title = { Text(stringResource(R.string.settings_import_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it; importError = null },
                        label = { Text(stringResource(R.string.settings_import_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (importError != null) {
                        Text(text = importError ?: "", color = UrgentRed, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingImportUri ?: return@Button
                        val pwdArray = importPassword.toCharArray()

                        scope.launch {
                            try {
                                val bytes = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                    inputStream.readBytes()
                                } ?: throw Exception("No se pudo leer el archivo seleccionado")

                                val result = repository.importVault(bytes, pwdArray)
                                if (result.isSuccess) {
                                    val count = result.getOrThrow()
                                    showImportDialog = false
                                    pendingImportUri = null
                                    snackbarHostState.showSnackbar(String.format(importSuccessFormat, count))
                                } else {
                                    importError = importErrorMsg
                                }
                            } catch (e: Exception) {
                                importError = importErrorMsg
                            } finally {
                                pwdArray.fill('0')
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_unlock))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    pendingImportUri = null
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Modal de confirmación para generación de Recovery Key
    if (showGenerateDialog) {
        AlertDialog(
            onDismissRequest = { showGenerateDialog = false },
            title = { Text(stringResource(R.string.settings_recovery_dialog_title)) },
            text = {
                Text(stringResource(R.string.settings_recovery_dialog_message))
            },
            confirmButton = {
                Button(
                    onClick = {
                        recoveryKey = cryptoManager.generateRecoveryKey()
                        showGenerateDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_generate))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
