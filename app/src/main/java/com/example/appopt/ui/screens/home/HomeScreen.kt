package com.example.appopt.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.R
import com.example.appopt.ui.components.AccountDetailsDialog
import com.example.appopt.ui.components.OtpCodeCard

/**
 * Pantalla principal de la aplicación.
 *
 * Muestra el listado de cuentas 2FA sincronizadas con el reloj, buscador en tiempo real,
 * botón de privacidad persistente para ocultar/mostrar códigos en todas las tarjetas,
 * botón de bloqueo inmediato de bóveda y accesos rápidos para agregar cuentas o configurar respaldos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToScanQr: () -> Unit,
    onNavigateToAddManual: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isHideCodesEnabled by viewModel.isHideCodesEnabled.collectAsStateWithLifecycle()

    var showSearch by remember { mutableStateOf(false) }
    var accountToDeleteId by remember { mutableStateOf<String?>(null) }
    var showAddOptionsDialog by remember { mutableStateOf(false) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }

    // Cuenta actualmente seleccionada para el popup modal (reactiva a cambios de Room)
    val selectedAccountWithCode = accounts.find { it.account.id == selectedAccountId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::onSearchQueryChanged,
                            placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.onSearchQueryChanged("")
                                    showSearch = false
                                }) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.action_close_search)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            Text(
                                text = stringResource(R.string.home_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    }
                },
                actions = {
                    if (!showSearch) {
                        // Botón de privacidad: Ocultar / Mostrar códigos (Persistente)
                        IconButton(onClick = { viewModel.toggleHideCodes() }) {
                            Icon(
                                imageVector = if (isHideCodesEnabled) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (isHideCodesEnabled) stringResource(R.string.action_show_codes) else stringResource(R.string.action_hide_codes)
                            )
                        }
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                        IconButton(onClick = { viewModel.lockVault() }) {
                            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.home_lock_vault))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddOptionsDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.home_add_account))
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (accounts.isEmpty()) {
                EmptyAccountsState(
                    onScanQr = onNavigateToScanQr,
                    onAddManual = onNavigateToAddManual
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = accounts,
                        key = { it.account.id }
                    ) { item ->
                        OtpCodeCard(
                            accountWithCode = item,
                            hideCodes = isHideCodesEnabled,
                            onCardClick = { selectedAccountId = item.account.id },
                            onCopyCode = { code -> viewModel.copyCode(code, item.account.issuer) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onNextHotpCode = viewModel::nextHotpCode
                        )
                    }
                }
            }
        }
    }

    // Modal / Popup de Edición y Detalles de la Cuenta seleccionada
    selectedAccountWithCode?.let { item ->
        AccountDetailsDialog(
            accountWithCode = item,
            onDismiss = { selectedAccountId = null },
            onCopyCode = { code -> viewModel.copyCode(code, item.account.issuer) },
            onUpdateAccount = { id, issuer, name -> viewModel.updateAccount(id, issuer, name) },
            onDeleteAccount = {
                viewModel.deleteAccount(it)
                selectedAccountId = null
            }
        )
    }

    // Modal de selección de método de adición (QR o Manual)
    if (showAddOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showAddOptionsDialog = false },
            title = { Text(stringResource(R.string.home_add_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            showAddOptionsDialog = false
                            onNavigateToScanQr()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.home_scan_qr_option))
                    }

                    OutlinedButton(
                        onClick = {
                            showAddOptionsDialog = false
                            onNavigateToAddManual()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.home_add_manual_option))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddOptionsDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Diálogo de confirmación de borrado
    if (accountToDeleteId != null) {
        AlertDialog(
            onDismissRequest = { accountToDeleteId = null },
            title = { Text(stringResource(R.string.home_delete_dialog_title)) },
            text = {
                Text(stringResource(R.string.home_delete_dialog_message))
            },
            confirmButton = {
                Button(
                    onClick = {
                        accountToDeleteId?.let { viewModel.deleteAccount(it) }
                        accountToDeleteId = null
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDeleteId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * Estado vacío cuando no existen cuentas configuradas.
 */
@Composable
private fun EmptyAccountsState(
    onScanQr: () -> Unit,
    onAddManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.home_empty_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.home_empty_description),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onScanQr,
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.home_scan_qr_option))
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onAddManual,
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.home_add_manual_option))
        }
    }
}
