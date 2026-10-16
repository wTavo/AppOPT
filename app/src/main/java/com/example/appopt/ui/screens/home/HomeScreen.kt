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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.domain.model.ShareGrantStatus
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.common.UiState
import com.example.appopt.ui.components.AccountDetailsDialog
import com.example.appopt.ui.components.OtpCodeCard
import com.example.appopt.ui.screens.add.AddAccountDialog
import com.example.appopt.ui.screens.home.components.AddAccountSpeedDialOverlay
import com.example.appopt.ui.screens.home.components.EmptyAccountsState
import com.example.appopt.ui.screens.home.components.HomeCategoryFilter
import com.example.appopt.ui.screens.home.components.HomeCategoryFilterBar
import com.example.appopt.ui.screens.home.components.HomeFloatingDock
import com.example.appopt.ui.screens.home.components.HomeSharedServicesAccordion
import com.example.appopt.ui.screens.home.components.HomeTopHeader
import com.example.appopt.ui.screens.home.components.IncomingSharedServiceDetailsDialog
import com.example.appopt.ui.screens.scan.QrScannerDialog
import com.example.appopt.ui.screens.scan.QrScannerMode
import com.example.appopt.ui.screens.settings.components.AppUpdateSettingsCard
import com.example.appopt.ui.screens.settings.coordinator.rememberGoogleOAuthRequester
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.performance.TrackJankMetrics
import com.example.appopt.performance.TrackJankOperationState
import com.example.appopt.util.TotpClock
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Modela un elemento visual de tarjeta OTP para la pantalla principal unificando cuentas propias y compartidas (Directivas 8 y DRY).
 */
private sealed interface HomeCardItem {
    val key: String
    val isFavorite: Boolean
    val issuer: String

    data class Own(val item: AccountWithCode) : HomeCardItem {
        override val key: String get() = item.account.id
        override val isFavorite: Boolean get() = item.account.isFavorite
        override val issuer: String get() = item.account.issuer
    }

    data class Shared(val service: IncomingSharedService) : HomeCardItem {
        override val key: String get() = "shared_${service.grantId}"
        override val isFavorite: Boolean get() = service.isFavorite
        override val issuer: String get() = service.issuer
    }
}

/**
 * Pantalla principal que visualiza las cuentas 2FA con barra de filtros superior («Todos», «Favoritos» y «Compartidos»)
 * y navegación táctil deslizando hacia la izquierda o derecha ([HorizontalPager]).
 *
 * Características de seguridad, diseño e interacción:
 * - Cabecera superior flotante: Búsqueda interactiva, alternador de modo de privacidad ([HomeTopHeader]) y filtros rápidos por categoría ([HomeCategoryFilterBar]).
 * - Paginación horizontal fluida: Permite cambiar entre «Todos», «Favoritos» y «Compartidos» tanto tocando el filtro superior como deslizando lateralmente la pantalla.
 * - Dock flotante inferior: Acciones rápidas ergonómicas inferiores accesibles con una sola mano ([HomeFloatingDock]).
 * - Menú Speed Dial contextual: Despliegue de opciones directamente sobre el botón (+) con rotación animada a 'x'.
 * - Diálogos modales atómicos: Visualización de detalles, compartición y edición de cuentas.
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
    var loadingSharedGrantId by remember { mutableStateOf<String?>(null) }
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

    val appHaptics = rememberAppHaptics()

    // Lista de tarjetas visuales que mantiene el orden unificado determinístico y fluido sin parpadeos (Directivas 8 y DRY)
    val localCards = remember { mutableStateListOf<HomeCardItem>() }
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

    val incomingServices = remember(sharedCacheVersion, isUnlocked) {
        SharedBatchKeyStore.getCachedIncomingServices(context)
    }

    // Sincroniza la lista unificada local con las emisiones de Room y servicios compartidos
    LaunchedEffect(currentSuccessAccounts, incomingServices) {
        if (isDraggingAny) {
            // Durante arrastre: solo actualizar códigos/datos sin alterar el orden visual
            val ownMap = currentSuccessAccounts.associateBy { it.account.id }
            val sharedMap = incomingServices.associateBy { it.grantId }
            for (i in localCards.indices) {
                when (val card = localCards[i]) {
                    is HomeCardItem.Own -> {
                        val updated = ownMap[card.item.account.id]
                        if (updated != null && updated != card.item) {
                            localCards[i] = HomeCardItem.Own(updated)
                        }
                    }
                    is HomeCardItem.Shared -> {
                        val updated = sharedMap[card.service.grantId]
                        if (updated != null && updated != card.service) {
                            localCards[i] = HomeCardItem.Shared(updated)
                        }
                    }
                }
            }
        } else {
            val currentOwnIds = currentSuccessAccounts.map { it.account.id }.toSet()
            val currentSharedGrantIds = incomingServices.map { it.grantId }.toSet()
            val localOwnIds = localCards.filterIsInstance<HomeCardItem.Own>().map { it.item.account.id }.toSet()
            val localSharedGrantIds = localCards.filterIsInstance<HomeCardItem.Shared>().map { it.service.grantId }.toSet()

            if (localCards.isEmpty()) {
                val favOwn = currentSuccessAccounts.filter { it.account.isFavorite }.map { HomeCardItem.Own(it) }
                val favShared = incomingServices.filter { it.isFavorite }.map { HomeCardItem.Shared(it) }
                val nonFavOwn = currentSuccessAccounts.filterNot { it.account.isFavorite }.map { HomeCardItem.Own(it) }
                val nonFavShared = incomingServices.filterNot { it.isFavorite }.map { HomeCardItem.Shared(it) }
                localCards.addAll((favOwn + favShared) + (nonFavOwn + nonFavShared))
            } else {
                val ownMap = currentSuccessAccounts.associateBy { it.account.id }
                val sharedMap = incomingServices.associateBy { it.grantId }

                // Eliminar tarjetas que ya no existen
                localCards.removeAll { card ->
                    when (card) {
                        is HomeCardItem.Own -> card.item.account.id !in currentOwnIds
                        is HomeCardItem.Shared -> card.service.grantId !in currentSharedGrantIds
                    }
                }

                // Actualizar tarjetas existentes
                for (i in localCards.indices) {
                    when (val card = localCards[i]) {
                        is HomeCardItem.Own -> {
                            val updated = ownMap[card.item.account.id]
                            if (updated != null && updated != card.item) {
                                localCards[i] = HomeCardItem.Own(updated)
                            }
                        }
                        is HomeCardItem.Shared -> {
                            val updated = sharedMap[card.service.grantId]
                            if (updated != null && updated != card.service) {
                                localCards[i] = HomeCardItem.Shared(updated)
                            }
                        }
                    }
                }

                // Incorporar nuevas cuentas
                val newOwn = currentSuccessAccounts.filter { it.account.id !in localOwnIds }
                for (item in newOwn) {
                    val newCard = HomeCardItem.Own(item)
                    if (item.account.isFavorite) {
                        localCards.add(0, newCard)
                    } else {
                        localCards.add(newCard)
                    }
                }

                // Incorporar nuevos servicios compartidos
                val newShared = incomingServices.filter { it.grantId !in localSharedGrantIds }
                for (svc in newShared) {
                    val newCard = HomeCardItem.Shared(svc)
                    if (svc.isFavorite) {
                        localCards.add(0, newCard)
                    } else {
                        localCards.add(newCard)
                    }
                }
            }
        }
    }

    val currentSecondEpoch by TotpClock.currentSecondEpoch.collectAsStateWithLifecycle()
    val nowMillis = currentSecondEpoch * 1000L

    val allDisplayItems by remember(searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                localCards.toList()
            } else {
                localCards.filter { card ->
                    when (card) {
                        is HomeCardItem.Own -> {
                            card.item.account.issuer.contains(searchQuery, ignoreCase = true) ||
                                card.item.account.accountName.contains(searchQuery, ignoreCase = true)
                        }
                        is HomeCardItem.Shared -> {
                            card.service.issuer.contains(searchQuery, ignoreCase = true) ||
                                card.service.accountName.contains(searchQuery, ignoreCase = true) ||
                                card.service.ownerEmail.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }
            }
        }
    }

    val favoriteDisplayItems by remember(allDisplayItems) {
        derivedStateOf {
            allDisplayItems.filter { it.isFavorite }
        }
    }

    fun requestSharedCode(service: IncomingSharedService) {
        if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
            appHaptics.error()
            Toast.makeText(context, context.getString(R.string.share_error_no_internet), Toast.LENGTH_SHORT).show()
            return
        }
        loadingSharedGrantId = service.grantId
        requestOAuthToken { token ->
            scope.launch {
                if (service.accessMode == ShareAccessMode.OFFLINE_BATCH) {
                    SharedServicesCloudClient.downloadPrecomputedBatch(token, service.grantId)
                        .onSuccess { batch ->
                            SharedBatchKeyStore.saveBatch(context, batch)
                            appHaptics.success()
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_shared_batch_downloaded_msg),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .onFailure {
                            appHaptics.error()
                            SharedBatchKeyStore.removeBatch(context, service.grantId)
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_shared_revoked_error),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    SharedServicesCloudClient.requestLiveCode(token, service.grantId)
                        .onSuccess { live ->
                            val liveBatch = live.toSharedOtpBatch(service.grantId)
                            SharedBatchKeyStore.saveBatch(context, liveBatch)
                            appHaptics.success()
                        }
                        .onFailure {
                            appHaptics.error()
                            SharedBatchKeyStore.removeBatch(context, service.grantId)
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_shared_revoked_error),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
                loadingSharedGrantId = null
            }
        }
    }

    fun acceptSharedInvitation(item: IncomingSharedService) {
        if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
            appHaptics.error()
            Toast.makeText(context, context.getString(R.string.share_error_no_internet), Toast.LENGTH_SHORT).show()
            return
        }
        loadingSharedGrantId = item.grantId
        requestOAuthToken { token ->
            scope.launch {
                SharedServicesCloudClient.acceptInvitation(token, item.grantId)
                    .onSuccess {
                        val current = SharedBatchKeyStore.getCachedIncomingServices(context)
                        val updated = current.map {
                            if (it.grantId == item.grantId) it.copy(status = ShareGrantStatus.ACCEPTED) else it
                        }
                        SharedBatchKeyStore.saveCachedIncomingServices(context, updated)
                        if (item.accessMode == ShareAccessMode.OFFLINE_BATCH) {
                            SharedServicesCloudClient.downloadPrecomputedBatch(token, item.grantId)
                                .onSuccess { batch ->
                                    SharedBatchKeyStore.saveBatch(context, batch)
                                }
                        }
                        appHaptics.success()
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_shared_accept_success_msg),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .onFailure {
                        appHaptics.error()
                        Toast.makeText(context, context.getString(R.string.share_error_generic), Toast.LENGTH_SHORT).show()
                    }
                loadingSharedGrantId = null
            }
        }
    }

    fun rejectSharedInvitation(item: IncomingSharedService) {
        if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
            appHaptics.error()
            Toast.makeText(context, context.getString(R.string.share_error_no_internet), Toast.LENGTH_SHORT).show()
            return
        }
        loadingSharedGrantId = item.grantId
        val wasPending = item.status == ShareGrantStatus.PENDING
        requestOAuthToken { token ->
            scope.launch {
                SharedServicesCloudClient.revokeGrant(token, item.grantId)
                    .onSuccess {
                        val current = SharedBatchKeyStore.getCachedIncomingServices(context)
                        val updated = current.filterNot { it.grantId == item.grantId }
                        SharedBatchKeyStore.saveCachedIncomingServices(context, updated)
                        SharedBatchKeyStore.removeBatch(context, item.grantId)
                        appHaptics.success()
                        val msgRes = if (wasPending) {
                            R.string.settings_shared_reject_success_msg
                        } else {
                            R.string.settings_shared_leave_success_msg
                        }
                        Toast.makeText(context, context.getString(msgRes), Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        appHaptics.error()
                        Toast.makeText(context, context.getString(R.string.share_error_generic), Toast.LENGTH_SHORT).show()
                    }
                loadingSharedGrantId = null
            }
        }
    }

    // Lambdas estabilizadas y funciones de interacción directa (Directivas 8 y DRY)
    val onCopyCode = remember(viewModel) { { code: String, issuer: String -> viewModel.copyCode(code, issuer) } }
    val onNextHotpCode = remember(viewModel) { { id: String -> viewModel.nextHotpCode(id) } }

    fun commitAllCardsOrder() {
        val ownIds = localCards.filterIsInstance<HomeCardItem.Own>().map { it.item.account.id }
        viewModel.commitReorder(ownIds)

        val currentShared = SharedBatchKeyStore.getCachedIncomingServices(context)
        val sharedPositionMap = localCards.filterIsInstance<HomeCardItem.Shared>()
            .mapIndexed { index, shared -> shared.service.grantId to index }
            .toMap()
        val reorderedShared = currentShared.map { svc ->
            val newIndex = sharedPositionMap[svc.grantId] ?: svc.orderIndex
            svc.copy(orderIndex = newIndex)
        }.sortedWith(
            compareByDescending<IncomingSharedService> { it.isFavorite }
                .thenBy { it.orderIndex }
                .thenBy { it.issuer.lowercase() }
        )
        SharedBatchKeyStore.saveCachedIncomingServices(context, reorderedShared)
    }

    fun onToggleFavoriteOwn(id: String) {
        appHaptics.click()
        val index = localCards.indexOfFirst { it is HomeCardItem.Own && it.item.account.id == id }
        if (index != -1) {
            val card = localCards[index] as HomeCardItem.Own
            val willBeFavorite = !card.isFavorite
            val updated = card.item.copy(account = card.item.account.copy(isFavorite = willBeFavorite))
            localCards.removeAt(index)
            val updatedCard = HomeCardItem.Own(updated)
            if (willBeFavorite) {
                localCards.add(0, updatedCard)
            } else {
                val firstNonFavIndex = localCards.indexOfFirst { !it.isFavorite }
                if (firstNonFavIndex != -1) {
                    localCards.add(firstNonFavIndex, updatedCard)
                } else {
                    localCards.add(updatedCard)
                }
            }
        }
        viewModel.toggleFavorite(id)
    }

    fun onToggleFavoriteShared(grantId: String) {
        appHaptics.click()
        val index = localCards.indexOfFirst { it is HomeCardItem.Shared && it.service.grantId == grantId }
        if (index != -1) {
            val card = localCards[index] as HomeCardItem.Shared
            val willBeFavorite = !card.isFavorite
            val updated = card.service.copy(isFavorite = willBeFavorite)
            localCards.removeAt(index)
            val updatedCard = HomeCardItem.Shared(updated)
            if (willBeFavorite) {
                localCards.add(0, updatedCard)
            } else {
                val firstNonFavIndex = localCards.indexOfFirst { !it.isFavorite }
                if (firstNonFavIndex != -1) {
                    localCards.add(firstNonFavIndex, updatedCard)
                } else {
                    localCards.add(updatedCard)
                }
            }
        }
        SharedBatchKeyStore.toggleIncomingFavorite(context, grantId)
    }

    val selectedAccountWithCode = selectedAccountId?.let { id ->
        localCards.filterIsInstance<HomeCardItem.Own>().find { it.item.account.id == id }?.item
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
                userScrollEnabled = !isDraggingAny,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageFilter = HomeCategoryFilter.entries[page]
                val pageListState = rememberLazyListState()
                val pageReorderableState = rememberReorderableLazyListState(pageListState) { from, to ->
                    val fromIndex = localCards.indexOfFirst { it.key == from.key }
                    val toIndex = localCards.indexOfFirst { it.key == to.key }
                    val fromCard = localCards.getOrNull(fromIndex)
                    val toCard = localCards.getOrNull(toIndex)
                    if (fromCard != null && toCard != null && fromCard.isFavorite == toCard.isFavorite) {
                        val item = localCards.removeAt(fromIndex)
                        localCards.add(toIndex, item)
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
                                    onCopyCode = onCopyCode,
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

                        HomeCategoryFilter.ALL -> {
                            if (allDisplayItems.isNotEmpty()) {
                                items(
                                    items = allDisplayItems,
                                    key = { it.key },
                                    contentType = { "otp_card" }
                                ) { item ->
                                    when (item) {
                                        is HomeCardItem.Own -> {
                                            ReorderableItem(pageReorderableState, key = item.key) { isDragging ->
                                                OtpCodeCard(
                                                    accountWithCode = item.item,
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
                                                                commitAllCardsOrder()
                                                            }
                                                        ),
                                                    onCardClick = { selectedAccountId = item.item.account.id },
                                                    onCopyCode = { code -> onCopyCode(code, item.item.account.issuer) },
                                                    onToggleFavorite = { onToggleFavoriteOwn(item.item.account.id) },
                                                    onNextHotpCode = onNextHotpCode
                                                )
                                            }
                                        }

                                        is HomeCardItem.Shared -> {
                                            val service = item.service
                                            val cachedBatch = SharedBatchKeyStore.getValidBatch(context, service.grantId, nowMillis)
                                            val activeCode = cachedBatch?.getCodeAt(nowMillis)
                                            val liveWindowProgress = cachedBatch?.getWindowStepProgress(
                                                nowMillis = nowMillis,
                                                maxLiveWindowSteps = SecurityConfig.SHARED_LIVE_CODE_WINDOW_STEPS
                                            )
                                            ReorderableItem(pageReorderableState, key = item.key) { isDragging ->
                                                OtpCodeCard(
                                                    service = service,
                                                    activeCode = activeCode,
                                                    hideCodes = isHideCodesEnabled,
                                                    liveWindowProgress = liveWindowProgress,
                                                    isItemLoading = loadingSharedGrantId == service.grantId,
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
                                                                commitAllCardsOrder()
                                                            }
                                                        ),
                                                    onCopyCode = { code -> onCopyCode(code, service.issuer) },
                                                    onRequestAction = { requestSharedCode(service) },
                                                    onToggleFavorite = { onToggleFavoriteShared(service.grantId) },
                                                    onCardClick = { selectedSharedServiceForDetails = service },
                                                    onAcceptInvitation = { acceptSharedInvitation(service) },
                                                    onRejectInvitation = { rejectSharedInvitation(service) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (allDisplayItems.isEmpty() && uiState is UiState.Empty) {
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

                        HomeCategoryFilter.FAVORITES -> {
                            if (favoriteDisplayItems.isNotEmpty()) {
                                items(
                                    items = favoriteDisplayItems,
                                    key = { it.key },
                                    contentType = { "otp_card" }
                                ) { item ->
                                    when (item) {
                                        is HomeCardItem.Own -> {
                                            ReorderableItem(pageReorderableState, key = item.key) { isDragging ->
                                                OtpCodeCard(
                                                    accountWithCode = item.item,
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
                                                                commitAllCardsOrder()
                                                            }
                                                        ),
                                                    onCardClick = { selectedAccountId = item.item.account.id },
                                                    onCopyCode = { code -> onCopyCode(code, item.item.account.issuer) },
                                                    onToggleFavorite = { onToggleFavoriteOwn(item.item.account.id) },
                                                    onNextHotpCode = onNextHotpCode
                                                )
                                            }
                                        }

                                        is HomeCardItem.Shared -> {
                                            val service = item.service
                                            val cachedBatch = SharedBatchKeyStore.getValidBatch(context, service.grantId, nowMillis)
                                            val activeCode = cachedBatch?.getCodeAt(nowMillis)
                                            val liveWindowProgress = cachedBatch?.getWindowStepProgress(
                                                nowMillis = nowMillis,
                                                maxLiveWindowSteps = SecurityConfig.SHARED_LIVE_CODE_WINDOW_STEPS
                                            )
                                            ReorderableItem(pageReorderableState, key = item.key) { isDragging ->
                                                OtpCodeCard(
                                                    service = service,
                                                    activeCode = activeCode,
                                                    hideCodes = isHideCodesEnabled,
                                                    liveWindowProgress = liveWindowProgress,
                                                    isItemLoading = loadingSharedGrantId == service.grantId,
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
                                                                commitAllCardsOrder()
                                                            }
                                                        ),
                                                    onCopyCode = { code -> onCopyCode(code, service.issuer) },
                                                    onRequestAction = { requestSharedCode(service) },
                                                    onToggleFavorite = { onToggleFavoriteShared(service.grantId) },
                                                    onCardClick = { selectedSharedServiceForDetails = service },
                                                    onAcceptInvitation = { acceptSharedInvitation(service) },
                                                    onRejectInvitation = { rejectSharedInvitation(service) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (favoriteDisplayItems.isEmpty()) {
                                item(
                                    key = "empty_favorites_state",
                                    contentType = "empty_state"
                                ) {
                                    EmptyAccountsState(
                                        icon = Icons.Filled.Star,
                                        title = stringResource(R.string.home_empty_favorites_title),
                                        description = stringResource(R.string.home_empty_favorites_description),
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

        // Modal de Detalles para Servicio Compartido recibido
        selectedSharedServiceForDetails?.let { service ->
            val batch = SharedBatchKeyStore.getValidBatch(context, service.grantId, nowMillis)
            IncomingSharedServiceDetailsDialog(
                service = service,
                hasValidBatch = batch != null && !batch.isExpired(nowMillis),
                isLoading = loadingSharedGrantId == service.grantId,
                onDismiss = { selectedSharedServiceForDetails = null },
                onRenewOfflineBatch = { requestSharedCode(service) },
                onConfirmLeave = {
                    rejectSharedInvitation(service)
                    selectedSharedServiceForDetails = null
                }
            )
        }
    }
}
