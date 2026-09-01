package com.example.appopt.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.R
import com.example.appopt.domain.repository.AccountWithCode
import com.example.appopt.ui.components.AccountDetailsDialog
import com.example.appopt.ui.components.OtpCodeCard
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import java.util.Collections

/**
 * Pantalla principal que visualiza las cuentas 2FA registradas con soporte de reordenamiento fluido por pulsación prolongada (Long Press).
 *
 * Características de seguridad e interacción:
 * - Pulsación corta (*Tap*): Desencadena el efecto de resaltado nativo (Ripple) y abre el modal de detalle y edición.
 * - Pulsación prolongada (*Long Press*): Activa vibración háptica y modo de arrastre con banda de histéresis matemática.
 * - Intercambio in-place mediante [Collections.swap] preservando el 100% de la sincronización en vivo de los contadores TOTP.
 * - Consumo de [Dimensions] y [Motion] para un diseño unificado y estandarizado.
 * - Restricción estricta de ordenamiento: Las cuentas favoritas solo se reordenan entre favoritas, y las normales entre normales.
 * - Barra de búsqueda en tiempo real.
 * - Alternador de modo privacidad para ocultar/mostrar códigos persistido.
 * - Acceso a escaneo de códigos QR y entrada manual de secretos.
 * - Acceso a ajustes de respaldo, generación de recovery keys y bloqueo manual de la bóveda.
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

    var isSearchActive by remember { mutableStateOf(false) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var showAddOptionsDialog by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    // Lista de renderizado local para swaps instantáneos sin animaciones residuales de reacomodo
    val localAccounts = remember { mutableStateListOf<AccountWithCode>() }
    var draggingAccountId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var lastSwapTime by remember { mutableLongStateOf(0L) }

    // Sincronización continua de datos en vivo (los contadores y códigos continúan actualizándose en tiempo real)
    LaunchedEffect(accounts) {
        val currentIds = localAccounts.map { it.account.id }
        val newIds = accounts.map { it.account.id }

        // Si se agregó, eliminó o cambió la cantidad de cuentas
        if (currentIds.toSet() != newIds.toSet() || currentIds.size != newIds.size || localAccounts.isEmpty()) {
            if (draggingAccountId == null) {
                localAccounts.clear()
                localAccounts.addAll(accounts)
            }
        } else {
            // Actualizar en vivo los dígitos y el contador circular segundo a segundo sin pausar durante el arrastre
            localAccounts.indices.forEach { i ->
                val localItem = localAccounts[i]
                val freshItem = accounts.find { it.account.id == localItem.account.id }
                if (freshItem != null && (localItem.code != freshItem.code || localItem.remainingSeconds != freshItem.remainingSeconds || localItem.progress != freshItem.progress || localItem.account.isFavorite != freshItem.account.isFavorite || localItem.account.issuer != freshItem.account.issuer || localItem.account.accountName != freshItem.account.accountName)) {
                    localAccounts[i] = localItem.copy(
                        account = freshItem.account,
                        code = freshItem.code,
                        remainingSeconds = freshItem.remainingSeconds,
                        progress = freshItem.progress
                    )
                }
            }
        }
    }

    // Cuenta actualmente seleccionada para el popup modal
    val selectedAccountWithCode = remember(localAccounts, selectedAccountId) {
        localAccounts.find { it.account.id == selectedAccountId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = viewModel::onSearchQueryChanged,
                            placeholder = { Text(stringResource(R.string.home_search_placeholder), style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.home_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                },
                actions = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.onSearchQueryChanged("")
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close_search))
                        }
                    } else {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                        }

                        IconButton(onClick = { viewModel.toggleHideCodes() }) {
                            Icon(
                                imageVector = if (isHideCodesEnabled) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (isHideCodesEnabled) stringResource(R.string.action_show_codes) else stringResource(R.string.action_hide_codes)
                            )
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
                shape = RoundedCornerShape(Dimensions.CornerRadius.large)
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
            if (localAccounts.isEmpty()) {
                EmptyAccountsState(
                    onScanQr = onNavigateToScanQr,
                    onAddManual = onNavigateToAddManual
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Dimensions.Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                ) {
                    itemsIndexed(
                        items = localAccounts,
                        key = { _, item -> item.account.id }
                    ) { index, item ->
                        val isDragging = draggingAccountId == item.account.id

                        val cardModifier = Modifier
                            .zIndex(if (isDragging) 10f else 1f)
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = dragOffsetY
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                    shadowElevation = 16f
                                }
                            }

                        OtpCodeCard(
                            accountWithCode = item,
                            hideCodes = isHideCodesEnabled,
                            isDragging = isDragging,
                            modifier = cardModifier,
                            onCardClick = { selectedAccountId = item.account.id },
                            onCopyCode = { code -> viewModel.copyCode(code, item.account.issuer) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onNextHotpCode = viewModel::nextHotpCode,
                            onStartDrag = {
                                draggingAccountId = item.account.id
                                dragOffsetY = 0f
                                lastSwapTime = System.currentTimeMillis()
                            },
                            onDragDelta = { deltaY, cardHeightPx ->
                                if (draggingAccountId == item.account.id) {
                                    dragOffsetY += deltaY
                                    val now = System.currentTimeMillis()

                                    // Banda de histéresis (70%): previene oscilaciones rápidas cuando el dedo se queda en el centro
                                    val threshold = cardHeightPx * 0.70f

                                    if (now - lastSwapTime >= Motion.Duration.DragDebounce.toLong()) {
                                        val currentIndex = localAccounts.indexOfFirst { it.account.id == item.account.id }
                                        if (currentIndex != -1) {
                                            // Arrastre hacia abajo con intercambio in-place
                                            if (dragOffsetY > threshold && currentIndex < localAccounts.lastIndex) {
                                                val currentAccount = localAccounts[currentIndex]
                                                val nextAccount = localAccounts[currentIndex + 1]
                                                if (currentAccount.account.isFavorite == nextAccount.account.isFavorite) {
                                                    Collections.swap(localAccounts, currentIndex, currentIndex + 1)
                                                    dragOffsetY -= cardHeightPx
                                                    lastSwapTime = now
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                            // Arrastre hacia arriba con intercambio in-place
                                            else if (dragOffsetY < -threshold && currentIndex > 0) {
                                                val currentAccount = localAccounts[currentIndex]
                                                val prevAccount = localAccounts[currentIndex - 1]
                                                if (currentAccount.account.isFavorite == prevAccount.account.isFavorite) {
                                                    Collections.swap(localAccounts, currentIndex, currentIndex - 1)
                                                    dragOffsetY += cardHeightPx
                                                    lastSwapTime = now
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            onEndDrag = {
                                if (draggingAccountId != null) {
                                    viewModel.commitReorder(localAccounts.map { it.account.id })
                                    draggingAccountId = null
                                    dragOffsetY = 0f
                                }
                            }
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
            title = { Text(stringResource(R.string.home_add_dialog_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                ) {
                    Button(
                        onClick = {
                            showAddOptionsDialog = false
                            onNavigateToScanQr()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.medium))
                        Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                        Text(stringResource(R.string.home_scan_qr_option), style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = {
                            showAddOptionsDialog = false
                            onNavigateToAddManual()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                    ) {
                        Icon(Icons.Filled.Keyboard, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.medium))
                        Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                        Text(stringResource(R.string.home_add_manual_option), style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddOptionsDialog = false }) {
                    Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}

/**
 * Estado visual cuando no existen cuentas registradas en la bóveda local.
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
            .padding(Dimensions.Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.size(Dimensions.IconSize.illustration)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.IconSize.hero),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.Spacing.xl))

        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(Dimensions.Spacing.sm))

        Text(
            text = stringResource(R.string.home_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimensions.Spacing.xxl))

        Button(
            onClick = onScanQr,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimensions.ComponentHeight.buttonDefault),
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.medium))
            Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
            Text(stringResource(R.string.home_scan_qr_option), style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(Dimensions.Spacing.md))

        OutlinedButton(
            onClick = onAddManual,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimensions.ComponentHeight.buttonDefault),
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
        ) {
            Icon(Icons.Filled.Keyboard, contentDescription = null, modifier = Modifier.size(Dimensions.IconSize.medium))
            Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
            Text(stringResource(R.string.home_add_manual_option), style = MaterialTheme.typography.labelLarge)
        }
    }
}
