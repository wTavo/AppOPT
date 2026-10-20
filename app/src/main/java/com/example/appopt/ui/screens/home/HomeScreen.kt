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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.StarOutline
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.data.cloud.SharedBatchKeyStore
import com.example.appopt.data.cloud.SharedServicesCloudClient
import com.example.appopt.data.cloud.UnauthorizedException
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
import com.example.appopt.ui.screens.settings.coordinator.rememberGoogleOAuthRequester
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.TotpClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Pantalla principal que visualiza las cuentas 2FA con barra de filtros superior («Todos», «Favoritos» y «Compartidos»)
 * y navegación táctil deslizando hacia la izquierda o derecha ([HorizontalPager]).
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
    val driveConnectedSuccessText = stringResource(R.string.settings_drive_connected_success)

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

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(isUnlocked, isDriveConnected) {
        if (!isUnlocked) {
            selectedAccountId = null
            selectedSharedServiceForDetails = null
            isAddMenuOpen = false
            isSearchActive = false
            isQrScannerDialogOpen = false
            isManualAddDialogOpen = false
        } else {
            if (SharedServicesCloudClient.isNetworkAvailable(context)) AppUpdateManager.checkForUpdate(context)
            if (isDriveConnected) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    while (true) {
                        if (SharedServicesCloudClient.isNetworkAvailable(context) && !cardsCoordinator.isDraggingAny) {
                            val cachedToken = GoogleDriveManager.currentAccessToken
                            if (!cachedToken.isNullOrBlank()) {
                                val syncRes = SharedBatchKeyStore.syncIncomingServicesFromCloud(context, cachedToken)
                                if (syncRes.isFailure && syncRes.exceptionOrNull() is UnauthorizedException) {
                                    requestOAuthToken { token ->
                                        scope.launch { SharedBatchKeyStore.syncIncomingServicesFromCloud(context, token, forceRefresh = true) }
                                    }
                                }
                            } else {
                                requestOAuthToken { token ->
                                    scope.launch { SharedBatchKeyStore.syncIncomingServicesFromCloud(context, token) }
                                }
                            }
                        }
                        delay(SecurityConfig.SHARED_SERVICES_POLL_INTERVAL_MILLIS)
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedFilter) {
        if (selectedFilter == HomeCategoryFilter.SHARED && isUnlocked && isDriveConnected && SharedServicesCloudClient.isNetworkAvailable(context)) {
            val token = GoogleDriveManager.currentAccessToken
            if (!token.isNullOrBlank()) {
                SharedBatchKeyStore.syncIncomingServicesFromCloud(context, token)
            } else {
                requestOAuthToken { tok -> scope.launch { SharedBatchKeyStore.syncIncomingServicesFromCloud(context, tok) } }
            }
        }
    }

    val pendingSharedCount = remember(sharedCacheVersion, isUnlocked) {
        SharedBatchKeyStore.getCachedIncomingServices(context).count { it.status == ShareGrantStatus.PENDING }
    }

    TrackJankOperationState(
        key = "Action",
        value = if (cardsCoordinator.isDraggingAny) "DragAndDropCard" else if (isSearchActive) "FilteringSearch" else null
    )

    @Suppress("UNCHECKED_CAST")
    val currentSuccessAccounts by remember(uiState) {
        derivedStateOf { (uiState as? UiState.Success<*>)?.data as? List<AccountWithCode> ?: emptyList() }
    }

    val incomingServices = remember(sharedCacheVersion, isUnlocked) {
        SharedBatchKeyStore.getCachedIncomingServices(context)
    }

    LaunchedEffect(incomingServices) {
        if (selectedSharedServiceForDetails != null && incomingServices.none { it.grantId == selectedSharedServiceForDetails?.grantId }) {
            selectedSharedServiceForDetails = null
        }
    }

    LaunchedEffect(currentSuccessAccounts, incomingServices) {
        cardsCoordinator.syncCards(currentSuccessAccounts, incomingServices)
    }

    val currentSecondEpoch by TotpClock.currentSecondEpoch.collectAsStateWithLifecycle()
    val nowMillis = currentSecondEpoch * 1000L

    val allDisplayItems by remember(searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) cardsCoordinator.localCards.toList()
            else cardsCoordinator.localCards.filter { it.matchesQuery(searchQuery) }
        }
    }

    val favoriteDisplayItems by remember(allDisplayItems) {
        derivedStateOf { allDisplayItems.filter { it.isFavorite } }
    }

    val selectedAccountWithCode = selectedAccountId?.let { id ->
        cardsCoordinator.localCards.filterIsInstance<HomeCardItem.Own>().find { it.item.account.id == id }?.item
            ?: currentSuccessAccounts.find { it.account.id == id }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listTopPadding = statusBarTop + Dimensions.ComponentHeight.buttonDefault + Dimensions.ComponentHeight.buttonCompact +
        Dimensions.Spacing.md + (Dimensions.Spacing.sm * 2) + Dimensions.Spacing.lg
    val listBottomPadding = navBarBottom + Dimensions.Spacing.lg + Dimensions.ComponentSize.heroFab +
        (Dimensions.Spacing.sm * 2) + Dimensions.Spacing.xl

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                    contentPadding = PaddingValues(start = Dimensions.Spacing.lg, end = Dimensions.Spacing.lg, top = listTopPadding, bottom = listBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                ) {
                    when (pageFilter) {
                        HomeCategoryFilter.SHARED -> {
                            item(key = "shared_services_view", contentType = "shared_view") {
                                HomeSharedServicesAccordion(
                                    searchQuery = searchQuery,
                                    hideCodes = isHideCodesEnabled,
                                    onCopyCode = { code, issuer -> viewModel.copyCode(code, issuer) },
                                    showEmptyStateAndHeader = true,
                                    isDriveConnected = isDriveConnected,
                                    isConnectingDrive = isDriveConnecting,
                                    loadingSharedGrantId = cardsCoordinator.loadingSharedGrantId,
                                    onConnectClick = {
                                        requestOAuthToken { token ->
                                            viewModel.onGoogleDriveConnected(token) {
                                                appHaptics.success()
                                                Toast.makeText(context, driveConnectedSuccessText, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onSharedCardClick = { selectedSharedServiceForDetails = it },
                                    onRequestSharedCode = { cardsCoordinator.requestSharedCode(it) },
                                    onAcceptSharedInvitation = { cardsCoordinator.acceptSharedInvitation(it) },
                                    onRejectSharedInvitation = { cardsCoordinator.rejectSharedInvitation(it) }
                                )
                            }
                        }

                        HomeCategoryFilter.ALL, HomeCategoryFilter.FAVORITES -> {
                            val displayItems = if (pageFilter == HomeCategoryFilter.ALL) allDisplayItems else favoriteDisplayItems
                            if (displayItems.isNotEmpty()) {
                                items(items = displayItems, key = { it.key }, contentType = { "otp_card" }) { item ->
                                    HomeReorderableCardItem(
                                        item = item,
                                        reorderableState = pageReorderableState,
                                        isHideCodesEnabled = isHideCodesEnabled,
                                        isSearchActive = isSearchActive,
                                        searchQuery = searchQuery,
                                        nowMillis = nowMillis,
                                        loadingSharedGrantId = cardsCoordinator.loadingSharedGrantId,
                                        onDragStarted = { cardsCoordinator.isDraggingAny = true; appHaptics.dragTick() },
                                        onDragStopped = { cardsCoordinator.isDraggingAny = false; cardsCoordinator.commitAllCardsOrder() },
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
                            } else {
                                item(key = "empty_state_${pageFilter.name}", contentType = "empty_state") {
                                    val emptyMod = Modifier.fillMaxSize().padding(top = Dimensions.Spacing.xl)
                                    when {
                                        searchQuery.isNotBlank() -> EmptyAccountsState(
                                            icon = Icons.Filled.SearchOff,
                                            title = stringResource(R.string.home_search_empty_title),
                                            description = stringResource(R.string.home_search_empty_description, searchQuery),
                                            modifier = emptyMod
                                        )
                                        pageFilter == HomeCategoryFilter.FAVORITES -> EmptyAccountsState(
                                            icon = Icons.Filled.StarOutline,
                                            title = stringResource(R.string.home_empty_favorites_title),
                                            description = stringResource(R.string.home_empty_favorites_description),
                                            modifier = emptyMod
                                        )
                                        else -> EmptyAccountsState(modifier = emptyMod)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isUnlocked) {
            AddAccountSpeedDialOverlay(
                isOpen = isAddMenuOpen, onDismiss = { isAddMenuOpen = false },
                onScanQr = { isAddMenuOpen = false; isQrScannerDialogOpen = true },
                onAddManual = { isAddMenuOpen = false; isManualAddDialogOpen = true }
            )
        }

        HomeFloatingDock(
            onLockVault = { viewModel.lockVault() },
            onAddAccountClick = { isAddMenuOpen = !isAddMenuOpen },
            onNavigateToRecentlyDeleted = onNavigateToRecentlyDeleted,
            onNavigateToSettings = onNavigateToSettings,
            isAddMenuOpen = isAddMenuOpen,
            deletedAccountsCount = deletedAccountsCount,
            hasUpdateAvailable = availableUpdate != null,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Dimensions.Spacing.lg).zIndex(20f)
        )

        Column(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
                .padding(horizontal = Dimensions.Spacing.lg, vertical = Dimensions.Spacing.sm).zIndex(20f)
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

            Spacer(modifier = Modifier.height(Dimensions.Spacing.md))

            HomeCategoryFilterBar(
                selectedFilter = selectedFilter,
                onFilterSelected = { filter -> scope.launch { pagerState.animateScrollToPage(filter.ordinal) } },
                pendingSharedCount = pendingSharedCount
            )
        }
    }

    HomeDialogContainer(
        isUnlocked = isUnlocked,
        isManualAddDialogOpen = isManualAddDialogOpen, onDismissManualAdd = { isManualAddDialogOpen = false },
        isQrScannerDialogOpen = isQrScannerDialogOpen, onDismissQrScanner = { isQrScannerDialogOpen = false },
        selectedAccountWithCode = selectedAccountWithCode,
        onDismissAccountDetails = { selectedAccountId = null },
        onCopyCode = { code, issuer -> viewModel.copyCode(code, issuer) },
        onUpdateAccount = { id, issuer, name -> viewModel.updateAccount(id, issuer, name) },
        onDeleteAccount = { viewModel.deleteAccount(it); selectedAccountId = null },
        selectedSharedServiceForDetails = selectedSharedServiceForDetails,
        loadingSharedGrantId = cardsCoordinator.loadingSharedGrantId,
        nowMillis = nowMillis,
        onDismissSharedDetails = { selectedSharedServiceForDetails = null },
        onRequestSharedCode = { cardsCoordinator.requestSharedCode(it) },
        onRejectSharedInvitation = { cardsCoordinator.rejectSharedInvitation(it); selectedSharedServiceForDetails = null }
    )
}
