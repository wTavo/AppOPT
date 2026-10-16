package com.example.appopt.ui.screens.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.SharedBatchKeyStore
import com.example.appopt.data.cloud.SharedServicesCloudClient
import com.example.appopt.data.update.AppUpdateManager
import com.example.appopt.domain.model.AccountWithCode
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.domain.model.ShareGrantStatus
import com.example.appopt.performance.TrackJankMetrics
import com.example.appopt.performance.TrackJankOperationState
import com.example.appopt.ui.common.UiState
import com.example.appopt.ui.screens.home.components.AddAccountSpeedDialOverlay
import com.example.appopt.ui.screens.home.components.EmptyAccountsState
import com.example.appopt.ui.screens.home.components.HomeCategoryFilter
import com.example.appopt.ui.screens.home.components.HomeCategoryFilterBar
import com.example.appopt.ui.screens.home.components.HomeFloatingDock
import com.example.appopt.ui.screens.home.components.HomeReorderableCardItem
import com.example.appopt.ui.screens.home.components.HomeSharedServicesAccordion
import com.example.appopt.ui.screens.home.components.HomeTopHeader
import com.example.appopt.ui.screens.home.coordinator.rememberHomeCardsCoordinator
import com.example.appopt.ui.screens.home.dialogs.HomeDialogContainer
import com.example.appopt.ui.screens.home.model.HomeCardItem
import com.example.appopt.ui.screens.settings.components.AppUpdateSettingsCard
import com.example.appopt.ui.screens.settings.coordinator.rememberGoogleOAuthRequester
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.TotpClock
import kotlinx.coroutines.launch
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Pantalla principal que visualiza las cuentas 2FA con barra de filtros superior («Todos», «Favoritos» y «Compartidos»)
 * y navegación táctil deslizando hacia la izquierda o derecha ([HorizontalPager]).
 *
 * Características de seguridad, diseño e interacción:
 * - Cabecera superior flotante: Búsqueda interactiva, alternador de modo de privacidad ([HomeTopHeader]) y filtros rápidos por categoría ([HomeCategoryFilterBar]).
 * - Paginación horizontal fluida: Permite cambiar entre «Todos», «Favoritos» y «Compartidos» tanto tocando el filtro superior como deslizando lateralmente la pantalla.
 * - Dock flotante inferior: Acciones rápidas ergonómicas inferiores accesibles con una sola mano ([HomeFloatingDock]).
 * - Menú Speed Dial contextual: Despliegue de opciones directamente sobre el botón (+) con rotación animada a 'x'.
 * - Diálogos modales atómicos: Delegados a [HomeDialogContainer].
 * - Lista de tarjetas y ordenamiento por arrastre: Delegados a [HomeReorderableCardItem] y [com.example.appopt.ui.screens.home.coordinator.HomeCardsCoordinator].
 *
 * @param viewModel ViewModel reactivo que suministra el flujo de cuentas y operaciones de bóveda.
 * @param onNavigateToSettings Callback para navegar hacia la pantalla de Ajustes.
 * @param modifier Modificador de diseño Compose opcional.
 * @param onNavigateToRecentlyDeleted Callback para navegar hacia la papelera de reciclaje.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToRecentlyDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appHaptics = rememberAppHaptics()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isHideCodesEnabled by viewModel.isHideCodesEnabled.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
    val isDriveConnected by viewModel.isDriveConnected.collectAsStateWithLifecycle()
    val isDriveConnecting by viewModel.isDriveConnecting.collectAsStateWithLifecycle()
    val isVaultSynced by viewModel.isVaultSynced.collectAsStateWithLifecycle()
    val deletedAccountsCount by viewModel.deletedAccountsCount.collectAsStateWithLifecycle()
    val availableUpdate by AppUpdateManager.availableUpdate.collectAsStateWithLifecycle()
    val sharedCacheVersion by SharedBatchKeyStore.cacheVersion.collectAsStateWithLifecycle()

    TrackJankMetrics(screenName = "HomeScreen")

    var isSearchActive by remember { mutableStateOf(false) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var selectedSharedServiceForDetails by remember { mutableStateOf<IncomingSharedService?>(null) }
    var isAddMenuOpen by remember { mutableStateOf(false) }
    var isQrScannerDialogOpen by remember { mutableStateOf(false) }
    var isManualAddDialogOpen by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = HomeCategoryFilter.ALL.ordinal,
        pageCount = { HomeCategoryFilter.entries.size }
    )
    val selectedFilter = HomeCategoryFilter.entries[pagerState.currentPage]

    val appLockManager = remember { AuthenticatorApp.instance.appLockManager }
    val isUnlocked by appLockManager.isUnlocked.collectAsStateWithLifecycle()

    val requestOAuthToken = rememberGoogleOAuthRequester(onAuthError = {})
    val cardsCoordinator = rememberHomeCardsCoordinator(viewModel, requestOAuthToken)

    // Cierre defensivo de modales al bloquearse la bóveda, búsqueda silenciosa de actualizaciones y sincronización de invitaciones compartidas
    LaunchedEffect(isUnlocked, isDriveConnected) {
        if (!isUnlocked) {
            selectedAccountId = null
            selectedSharedServiceForDetails = null
            isAddMenuOpen = false
            isSearchActive = false
            isQrScannerDialogOpen = false
            isManualAddDialogOpen = false
        } else if (SharedServicesCloudClient.isNetworkAvailable(context)) {
            AppUpdateManager.checkForUpdate(context)
            if (isDriveConnected) {
                requestOAuthToken { token ->
                    scope.launch {
                        SharedBatchKeyStore.syncSharedServicesFromCloud(context, token)
                    }
                }
            }
        }
    }

    val pendingSharedCount = remember(sharedCacheVersion, isUnlocked) {
        SharedBatchKeyStore.getCachedIncomingServices(context)
            .count { it.status == ShareGrantStatus.PENDING }
    }

    TrackJankOperationState(
        key = "Action",
        value = if (cardsCoordinator.isDraggingAny) "DragAndDropCard" else if (isSearchActive) "FilteringSearch" else null
    )

    @Suppress("UNCHECKED_CAST")
    val currentSuccessAccounts by remember(uiState) {
        derivedStateOf {
            (uiState as? UiState.Success<*>)?.data as? List<AccountWithCode> ?: emptyList()
        }
    }

    val incomingServices = remember(sharedCacheVersion, isUnlocked) {
        SharedBatchKeyStore.getCachedIncomingServices(context)
    }

    LaunchedEffect(currentSuccessAccounts, incomingServices) {
        cardsCoordinator.syncCards(currentSuccessAccounts, incomingServices)
    }

    val currentSecondEpoch by TotpClock.currentSecondEpoch.collectAsStateWithLifecycle()
    val nowMillis = currentSecondEpoch * 1000L

    val allDisplayItems by remember(searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                cardsCoordinator.localCards.toList()
            } else {
                cardsCoordinator.localCards.filter { it.matchesQuery(searchQuery) }
            }
        }
    }

    val favoriteDisplayItems by remember(allDisplayItems) {
        derivedStateOf {
            allDisplayItems.filter { it.isFavorite }
        }
    }

    val selectedAccountWithCode = selectedAccountId?.let { id ->
        cardsCoordinator.localCards.filterIsInstance<HomeCardItem.Own>().find { it.item.account.id == id }?.item
            ?: currentSuccessAccounts.find { it.account.id == id }
    }

    // Cálculo reactivo de resguardo vertical para evitar que el header flotante + barra de filtros y el dock tapen las tarjetas
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listTopPadding = statusBarTop +
        Dimensions.ComponentHeight.buttonDefault +
        Dimensions.ComponentHeight.buttonCompact +
        Dimensions.Spacing.xs +
        (Dimensions.Spacing.sm * 2) +
        Dimensions.Spacing.lg
    val listBottomPadding = navBarBottom + Dimensions.Spacing.lg + Dimensions.ComponentSize.heroFab + (Dimensions.Spacing.sm * 2) + Dimensions.Spacing.xl

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Capa de Contenido Principal con deslizamiento horizontal entre Todos, Favoritos y Compartidos
        if (uiState !is UiState.Loading) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !cardsCoordinator.isDraggingAny,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageFilter = HomeCategoryFilter.entries[page]
                val pageListState = rememberLazyListState()
                val pageReorderableState = rememberReorderableLazyListState(pageListState) { from, to ->
                    val fromIndex = cardsCoordinator.localCards.indexOfFirst { it.key == from.key }
                    val toIndex = cardsCoordinator.localCards.indexOfFirst { it.key == to.key }
                    val fromCard = cardsCoordinator.localCards.getOrNull(fromIndex)
                    val toCard = cardsCoordinator.localCards.getOrNull(toIndex)
                    if (fromCard != null && toCard != null && fromCard.isFavorite == toCard.isFavorite) {
                        val item = cardsCoordinator.localCards.removeAt(fromIndex)
                        cardsCoordinator.localCards.add(toIndex, item)
                        appHaptics.dragTick()
                    }
                }

                LazyColumn(
                    state = pageListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimensions.Spacing.lg,
                        end = Dimensions.Spacing.lg,
                        top = listTopPadding,
                        bottom = listBottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                ) {
                    if (availableUpdate != null) {
                        item(
                            key = "app_update_banner_card",
                            contentType = "app_update_card"
                        ) {
                            AppUpdateSettingsCard()
                        }
                    }

                    when (pageFilter) {
                        HomeCategoryFilter.SHARED -> {
                            item(
                                key = "shared_services_view",
                                contentType = "shared_view"
                            ) {
                                HomeSharedServicesAccordion(
                                    searchQuery = searchQuery,
                                    hideCodes = isHideCodesEnabled,
                                    onCopyCode = { code, issuer -> viewModel.copyCode(code, issuer) },
                                    showEmptyStateAndHeader = true,
                                    isDriveConnected = isDriveConnected,
                                    isConnectingDrive = isDriveConnecting,
                                    onConnectClick = {
                                        requestOAuthToken { token ->
                                            viewModel.onGoogleDriveConnected(token) {
                                                appHaptics.success()
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.settings_drive_connected_success),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        HomeCategoryFilter.ALL, HomeCategoryFilter.FAVORITES -> {
                            val displayItems = if (pageFilter == HomeCategoryFilter.ALL) allDisplayItems else favoriteDisplayItems
                            if (displayItems.isNotEmpty()) {
                                items(
                                    items = displayItems,
                                    key = { it.key },
                                    contentType = { "otp_card" }
                                ) { item ->
                                    HomeReorderableCardItem(
                                        item = item,
                                        reorderableState = pageReorderableState,
                                        isHideCodesEnabled = isHideCodesEnabled,
                                        isSearchActive = isSearchActive,
                                        searchQuery = searchQuery,
                                        nowMillis = nowMillis,
                                        loadingSharedGrantId = cardsCoordinator.loadingSharedGrantId,
                                        onDragStarted = {
                                            cardsCoordinator.isDraggingAny = true
                                            appHaptics.dragTick()
                                        },
                                        onDragStopped = {
                                            cardsCoordinator.isDraggingAny = false
                                            cardsCoordinator.commitAllCardsOrder()
                                        },
                                        onOwnCardClick = { selectedAccountId = it },
                                        onSharedCardClick = { selectedSharedServiceForDetails = it },
                                        onCopyCode = { code, issuer -> viewModel.copyCode(code, issuer) },
                                        onToggleFavoriteOwn = { cardsCoordinator.onToggleFavoriteOwn(it) },
                                        onToggleFavoriteShared = { cardsCoordinator.onToggleFavoriteShared(it) },
                                        onNextHotpCode = { viewModel.nextHotpCode(it) },
                                        onRequestSharedCode = { cardsCoordinator.requestSharedCode(it) },
                                        onAcceptSharedInvitation = { cardsCoordinator.acceptSharedInvitation(it) },
                                        onRejectSharedInvitation = { cardsCoordinator.rejectSharedInvitation(it) }
                                    )
                                }
                            } else if (uiState is UiState.Empty && pageFilter == HomeCategoryFilter.ALL) {
                                item(
                                    key = "empty_accounts_state",
                                    contentType = "empty_state"
                                ) {
                                    EmptyAccountsState(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = Dimensions.Spacing.xl)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Menú flotante Speed Dial contextual para agregar cuentas
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

        // 3. Dock Flotante Inferior Ergonómico
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

        // 4. Header Flotante Superior + Barra de Filtros (Todos, Favoritos, Compartidos)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = Dimensions.Spacing.lg, vertical = Dimensions.Spacing.sm)
                .zIndex(20f)
        ) {
            HomeTopHeader(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                isHideCodesEnabled = isHideCodesEnabled,
                cloudSyncState = cloudSyncState,
                isVaultSynced = isVaultSynced,
                onSearchActiveChange = { isSearchActive = it },
                onSearchQueryChange = viewModel::onSearchQueryChanged,
                onToggleHideCodes = { viewModel.toggleHideCodes() },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))

            HomeCategoryFilterBar(
                selectedFilter = selectedFilter,
                onFilterSelected = { filter ->
                    scope.launch {
                        pagerState.animateScrollToPage(filter.ordinal)
                    }
                },
                pendingSharedCount = pendingSharedCount
            )
        }
    }

    // 5. Contenedor de modales desacoplado
    HomeDialogContainer(
        isUnlocked = isUnlocked,
        isManualAddDialogOpen = isManualAddDialogOpen,
        onDismissManualAdd = { isManualAddDialogOpen = false },
        isQrScannerDialogOpen = isQrScannerDialogOpen,
        onDismissQrScanner = { isQrScannerDialogOpen = false },
        selectedAccountWithCode = selectedAccountWithCode,
        onDismissAccountDetails = { selectedAccountId = null },
        onCopyCode = { code, issuer -> viewModel.copyCode(code, issuer) },
        onUpdateAccount = { id, issuer, name -> viewModel.updateAccount(id, issuer, name) },
        onDeleteAccount = {
            viewModel.deleteAccount(it)
            selectedAccountId = null
        },
        selectedSharedServiceForDetails = selectedSharedServiceForDetails,
        loadingSharedGrantId = cardsCoordinator.loadingSharedGrantId,
        nowMillis = nowMillis,
        onDismissSharedDetails = { selectedSharedServiceForDetails = null },
        onRequestSharedCode = { cardsCoordinator.requestSharedCode(it) },
        onRejectSharedInvitation = {
            cardsCoordinator.rejectSharedInvitation(it)
            selectedSharedServiceForDetails = null
        }
    )
}
