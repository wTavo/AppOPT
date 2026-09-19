package com.example.appopt.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.AuthenticatorApp
import com.example.appopt.domain.model.AccountWithCode
import com.example.appopt.ui.common.UiState
import com.example.appopt.ui.components.AccountDetailsDialog
import com.example.appopt.ui.components.OtpCodeCard
import com.example.appopt.ui.screens.add.AddAccountDialog
import com.example.appopt.ui.screens.home.components.AddAccountSpeedDialOverlay
import com.example.appopt.ui.screens.home.components.EmptyAccountsState
import com.example.appopt.ui.screens.home.components.HomeFloatingDock
import com.example.appopt.ui.screens.home.components.HomeTopHeader
import com.example.appopt.ui.screens.scan.QrScannerDialog
import com.example.appopt.ui.screens.scan.QrScannerMode
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.performance.TrackJankMetrics
import com.example.appopt.performance.TrackJankOperationState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Pantalla principal que visualiza las cuentas 2FA registradas con dock de control flotante ergonómico (Floating Pill Bar).
 *
 * Características de seguridad, diseño e interacción:
 * - Barra superior modular: Búsqueda interactiva y alternador de modo de privacidad con animaciones fluidas ([HomeTopHeader]).
 * - Lista reactiva: Cuentas 2FA con arrastre, favoritos, filtrado y actualización en tiempo real de códigos OTP.
 * - Dock flotante inferior: Acciones rápidas ergonómicas inferiores accesibles con una sola mano ([HomeFloatingDock]).
 * - Menú Speed Dial contextual: Despliegue de opciones directamente sobre el botón (+) con rotación animada a 'x'.
 * - Diálogos modales atómicos: Visualización de detalles y edición de cuentas.
 *
 * @param viewModel ViewModel reactivo que suministra el flujo de cuentas y operaciones de bóveda.
 * @param onNavigateToSettings Callback para navegar hacia la pantalla de Ajustes.
 * @param modifier Modificador de diseño Compose opcional.
 * @param onNavigateToRecentlyDeleted Callback para navegar hacia la papelera de reciclaje.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToRecentlyDeleted: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isHideCodesEnabled by viewModel.isHideCodesEnabled.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
    val isVaultSynced by viewModel.isVaultSynced.collectAsStateWithLifecycle()
    val deletedAccountsCount by viewModel.deletedAccountsCount.collectAsStateWithLifecycle()

    TrackJankMetrics(screenName = "HomeScreen")

    var isSearchActive by remember { mutableStateOf(false) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var isAddMenuOpen by remember { mutableStateOf(false) }
    var isQrScannerDialogOpen by remember { mutableStateOf(false) }
    var isManualAddDialogOpen by remember { mutableStateOf(false) }

    val appLockManager = remember { AuthenticatorApp.instance.appLockManager }
    val isUnlocked by appLockManager.isUnlocked.collectAsStateWithLifecycle()

    // Cierre defensivo de modales y estados transitorios al bloquearse la bóveda
    LaunchedEffect(isUnlocked) {
        if (!isUnlocked) {
            selectedAccountId = null
            isAddMenuOpen = false
            isSearchActive = false
            isQrScannerDialogOpen = false
            isManualAddDialogOpen = false
        }
    }

    val appHaptics = rememberAppHaptics()

    // Lista de renderizado local que mantiene el orden visual determinístico y fluido sin parpadeos
    val localAccounts = remember { mutableStateListOf<AccountWithCode>() }
    var isDraggingAny by remember { mutableStateOf(false) }

    TrackJankOperationState(
        key = "Action",
        value = if (isDraggingAny) "DragAndDropCard" else if (isSearchActive) "FilteringSearch" else null
    )

    // Resuelve las cuentas directamente desde el UiState inmutable para máxima velocidad de renderizado
    @Suppress("UNCHECKED_CAST")
    val currentSuccessAccounts by remember(uiState) {
        derivedStateOf {
            (uiState as? UiState.Success<*>)?.data as? List<AccountWithCode> ?: emptyList()
        }
    }

    // Sincroniza la lista local con las emisiones del ViewModel mediante diff incremental:
    // Solo actualiza los elementos que realmente cambiaron, evitando que LazyColumn redibuje
    // todas las tarjetas en cada emisión del ticker (cada ~30s al rotar el código OTP).
    LaunchedEffect(currentSuccessAccounts) {
        if (isDraggingAny) {
            // Durante arrastre: solo actualizar códigos sin alterar el orden visual
            val codeMap = currentSuccessAccounts.associate { it.account.id to it.code }
            for (i in localAccounts.indices) {
                val item = localAccounts[i]
                val updatedCode = codeMap[item.account.id]
                if (updatedCode != null && updatedCode != item.code) {
                    localAccounts[i] = item.copy(code = updatedCode)
                }
            }
        } else {
            // Fuera de arrastre: diff incremental por ID para minimizar invalidaciones en LazyColumn
            val newById = currentSuccessAccounts.associateBy { it.account.id }
            val localIds = localAccounts.map { it.account.id }.toSet()
            val newIds = newById.keys

            // 1. Actualizar elementos existentes cuyo código o datos hayan cambiado
            for (i in localAccounts.indices) {
                val item = localAccounts[i]
                val updated = newById[item.account.id]
                if (updated != null && updated != item) {
                    localAccounts[i] = updated
                }
            }

            // 2. Eliminar elementos que ya no existen (eliminaciones de cuenta)
            localAccounts.removeAll { it.account.id !in newIds }

            // 3. Añadir elementos nuevos que no estaban (altas de cuenta)
            val toAdd = currentSuccessAccounts.filter { it.account.id !in localIds }
            localAccounts.addAll(toAdd)
        }
    }

    val accountsToDisplay = localAccounts.ifEmpty { currentSuccessAccounts }
    val listState = rememberLazyListState()

    // Lambdas estabilizadas: se fijan en la primera composición y no cambian mientras el ViewModel sea el mismo
    val onCopyCode = remember(viewModel) { { code: String, issuer: String -> viewModel.copyCode(code, issuer) } }
    val onToggleFavorite = remember(viewModel) { { id: String -> viewModel.toggleFavorite(id) } }
    val onNextHotpCode = remember(viewModel) { { id: String -> viewModel.nextHotpCode(id) } }
    val onCommitReorder = remember(viewModel) { { ids: List<String> -> viewModel.commitReorder(ids) } }

    // Estado del motor de reordenamiento estándar (sh.calvin.reorderable)
    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        val fromAccount = localAccounts.getOrNull(from.index)
        val toAccount = localAccounts.getOrNull(to.index)
        if (fromAccount != null && toAccount != null && fromAccount.account.isFavorite == toAccount.account.isFavorite) {
            val item = localAccounts.removeAt(from.index)
            localAccounts.add(to.index, item)
            appHaptics.dragTick()
        }
    }

    val selectedAccountWithCode = selectedAccountId?.let { id ->
        accountsToDisplay.find { it.account.id == id }
    }

    // Cálculo dinámico de resguardo vertical para evitar que el header flotante y el dock tapen las tarjetas en cualquier dispositivo
    val density = LocalDensity.current
    val statusBars = WindowInsets.statusBars
    val navigationBars = WindowInsets.navigationBars
    val (listTopPadding, listBottomPadding) = remember(density, statusBars, navigationBars) {
        val statusBarTop = statusBars.asPaddingValues(density).calculateTopPadding()
        val navBarBottom = navigationBars.asPaddingValues(density).calculateBottomPadding()
        val top = statusBarTop + Dimensions.ComponentHeight.buttonDefault + (Dimensions.Spacing.sm * 2) + Dimensions.Spacing.md
        val bottom = navBarBottom + Dimensions.Spacing.lg + Dimensions.ComponentSize.heroFab + (Dimensions.Spacing.sm * 2) + Dimensions.Spacing.lg
        top to bottom
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Capa de Contenido Principal (LazyColumn de Cuentas)
        when {
            accountsToDisplay.isNotEmpty() -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimensions.Spacing.lg,
                        end = Dimensions.Spacing.lg,
                        top = listTopPadding,
                        bottom = listBottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                ) {
                    items(
                        items = accountsToDisplay,
                        key = { it.account.id },
                        contentType = { "otp_card" }
                    ) { item ->
                        ReorderableItem(reorderableLazyListState, key = item.account.id) { isDragging ->
                            OtpCodeCard(
                                accountWithCode = item,
                                hideCodes = isHideCodesEnabled,
                                isDragging = isDragging,
                                modifier = Modifier
                                    .longPressDraggableHandle(
                                        enabled = !isSearchActive && searchQuery.isBlank(),
                                        onDragStarted = {
                                            isDraggingAny = true
                                            appHaptics.dragTick()
                                        },
                                        onDragStopped = {
                                            isDraggingAny = false
                                            onCommitReorder(localAccounts.map { it.account.id })
                                        }
                                    ),
                                onCardClick = { selectedAccountId = item.account.id },
                                onCopyCode = { code -> onCopyCode(code, item.account.issuer) },
                                onToggleFavorite = onToggleFavorite,
                                onNextHotpCode = onNextHotpCode
                            )
                        }
                    }
                }
            }
            uiState is UiState.Empty -> {
                EmptyAccountsState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = listTopPadding,
                            bottom = listBottomPadding
                        )
                )
            }
            else -> {
                // Estado de carga inicial (UiState.Loading)
            }
        }

        // 2. Menú flotante Speed Dial contextual para agregar cuentas (scrim en z=10f, tarjeta en z=25f)
        if (isUnlocked) {
            AddAccountSpeedDialOverlay(
                isOpen = isAddMenuOpen,
                onDismiss = { isAddMenuOpen = false },
                onScanQr = {
                    isAddMenuOpen = false
                    isQrScannerDialogOpen = true
                },
                onAddManual = {
                    isAddMenuOpen = false
                    isManualAddDialogOpen = true
                }
            )
        }

        // 3. Dock Flotante Inferior Ergonómico (z=20f, por encima del scrim para que el botón (X) permanezca brillante e interactivo)
        HomeFloatingDock(
            onLockVault = { viewModel.lockVault() },
            onAddAccountClick = { isAddMenuOpen = !isAddMenuOpen },
            onNavigateToRecentlyDeleted = onNavigateToRecentlyDeleted,
            onNavigateToSettings = onNavigateToSettings,
            isAddMenuOpen = isAddMenuOpen,
            deletedAccountsCount = deletedAccountsCount,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = Dimensions.Spacing.lg)
                .zIndex(20f)
        )

        // 4. Header Flotante Superior
        HomeTopHeader(
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            isHideCodesEnabled = isHideCodesEnabled,
            cloudSyncState = cloudSyncState,
            isVaultSynced = isVaultSynced,
            onSearchActiveChange = { isSearchActive = it },
            onSearchQueryChange = viewModel::onSearchQueryChanged,
            onToggleHideCodes = { viewModel.toggleHideCodes() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = Dimensions.Spacing.lg, vertical = Dimensions.Spacing.sm)
                .zIndex(20f)
        )
    }

    // Modales interactivos en la bóveda
    if (isUnlocked) {
        // Modal de Ingreso Manual
        if (isManualAddDialogOpen) {
            AddAccountDialog(
                onDismiss = { isManualAddDialogOpen = false }
            )
        }

        // Modal de Escaneo de Códigos QR (Alta individual de cuentas)
        if (isQrScannerDialogOpen) {
            QrScannerDialog(
                mode = QrScannerMode.SINGLE_ACCOUNT,
                onDismiss = { isQrScannerDialogOpen = false }
            )
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
    }
}
