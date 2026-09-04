package com.example.appopt.ui.screens.home

import kotlin.math.pow
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.domain.repository.AccountWithCode
import com.example.appopt.ui.common.UiState
import com.example.appopt.ui.components.AccountDetailsDialog
import com.example.appopt.ui.components.OtpCodeCard
import com.example.appopt.ui.screens.home.components.EmptyAccountsState
import com.example.appopt.ui.screens.home.components.HomeFloatingDock
import com.example.appopt.ui.screens.home.components.HomeTopHeader
import com.example.appopt.ui.screens.home.dialogs.AddAccountOptionsDialog
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics
import java.util.Collections
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pantalla principal que visualiza las cuentas 2FA registradas con dock de control flotante ergonómico (Floating Pill Bar).
 *
 * Características de seguridad, diseño e interacción:
 * - Barra superior modular: Búsqueda interactiva y alternador de modo de privacidad con animaciones fluidas ([HomeTopHeader]).
 * - Lista reactiva: Cuentas 2FA con arrastre, favoritos, filtrado y actualización en tiempo real de códigos OTP.
 * - Dock flotante inferior: Acciones rápidas ergonómicas inferiores accesibles con una sola mano ([HomeFloatingDock]).
 * - Diálogos modales atómicos: Visualización de detalles, edición y selector de adición de cuentas.
 *
 * @param viewModel ViewModel reactivo que suministra el flujo de cuentas y operaciones de bóveda.
 * @param onNavigateToScanQr Callback para navegar hacia la cámara para escanear QR.
 * @param onNavigateToAddManual Callback para navegar hacia el formulario manual.
 * @param onNavigateToSettings Callback para navegar hacia la pantalla de Ajustes.
 * @param modifier Modificador de diseño Compose opcional.
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isHideCodesEnabled by viewModel.isHideCodesEnabled.collectAsStateWithLifecycle()

    var isSearchActive by remember { mutableStateOf(false) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var showAddOptionsDialog by remember { mutableStateOf(false) }

    val appHaptics = rememberAppHaptics()

    // Lista de renderizado local que mantiene el orden visual determinístico y fluido sin parpadeos
    val localAccounts = remember { mutableStateListOf<AccountWithCode>() }
    var draggingAccountId by remember { mutableStateOf<String?>(null) }
    var pointerViewportY by remember { mutableFloatStateOf(0f) }
    var touchOffsetYInCard by remember { mutableFloatStateOf(0f) }
    var lastSwapTime by remember { mutableLongStateOf(0L) }

    // Resuelve las cuentas directamente desde el UiState inmutable para máxima velocidad de renderizado
    @Suppress("UNCHECKED_CAST")
    val currentSuccessAccounts by remember(uiState) {
        derivedStateOf {
            (uiState as? UiState.Success<*>)?.data as? List<AccountWithCode> ?: emptyList()
        }
    }

    // Sincroniza la lista local con las emisiones del ViewModel evitando rebotes o parpadeos
    LaunchedEffect(currentSuccessAccounts) {
        if (draggingAccountId == null) {
            localAccounts.clear()
            localAccounts.addAll(currentSuccessAccounts)
        } else {
            val codeMap = currentSuccessAccounts.associate { it.account.id to it.code }
            for (i in localAccounts.indices) {
                val item = localAccounts[i]
                val updatedCode = codeMap[item.account.id]
                if (updatedCode != null && updatedCode != item.code) {
                    localAccounts[i] = item.copy(code = updatedCode)
                }
            }
        }
    }

    val accountsToDisplay = localAccounts.ifEmpty { currentSuccessAccounts }
    val listState = rememberLazyListState()

    // Lambdas estabilizadas: se fijan en la primera composición y no cambian mientras el ViewModel sea el mismo
    val onCopyCode = remember(viewModel) { { code: String, issuer: String -> viewModel.copyCode(code, issuer) } }
    val onToggleFavorite = remember(viewModel) { viewModel::toggleFavorite }
    val onNextHotpCode = remember(viewModel) { viewModel::nextHotpCode }
    val onCommitReorder = remember(viewModel) { viewModel::commitReorder }

    val density = LocalDensity.current

    // Motor de auto-scroll continuo con aceleración dinámica exponencial según proximidad al borde
    LaunchedEffect(draggingAccountId) {
        if (draggingAccountId != null) {
            val minScrollStepPx = with(density) { Dimensions.Spacing.xs.toPx() }
            val maxScrollStepPx = with(density) { (Dimensions.Spacing.xxl + Dimensions.Spacing.md).toPx() }

            while (true) {
                val layoutInfo = listState.layoutInfo
                val viewportStart = layoutInfo.viewportStartOffset.toFloat()
                val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
                val totalHeight = (viewportEnd - viewportStart).coerceAtLeast(1f)

                val topZoneLimit = viewportStart + (totalHeight * 0.35f)
                val bottomZoneLimit = viewportEnd - (totalHeight * 0.35f)
                val now = System.currentTimeMillis()

                if (pointerViewportY < topZoneLimit && listState.canScrollBackward) {
                    val distanceInside = topZoneLimit - pointerViewportY
                    val normalizedProgress = (distanceInside / (topZoneLimit - viewportStart).coerceAtLeast(1f)).coerceIn(0f, 2.5f)
                    val dynamicFactor = normalizedProgress.toDouble().pow(1.8).toFloat()
                    val scrollStep = (minScrollStepPx + dynamicFactor * (maxScrollStepPx - minScrollStepPx)).coerceIn(minScrollStepPx, maxScrollStepPx * 1.5f)

                    listState.scrollBy(-scrollStep)

                    if (now - lastSwapTime >= Motion.Duration.DRAG_DEBOUNCE.toLong()) {
                        val currentIndex = localAccounts.indexOfFirst { it.account.id == draggingAccountId }
                        if (currentIndex > 0) {
                            val currentAccount = localAccounts[currentIndex]
                            val prevAccount = localAccounts[currentIndex - 1]
                            if (currentAccount.account.isFavorite == prevAccount.account.isFavorite) {
                                val prevItem = layoutInfo.visibleItemsInfo.find { it.key == prevAccount.account.id }
                                if (prevItem != null) {
                                    val prevCenterY = prevItem.offset + (prevItem.size / 2f)
                                    if (pointerViewportY < prevCenterY) {
                                        Collections.swap(localAccounts, currentIndex, currentIndex - 1)
                                        lastSwapTime = now
                                        appHaptics.dragTick()
                                    }
                                }
                            }
                        }
                    }
                } else if (pointerViewportY > bottomZoneLimit && listState.canScrollForward) {
                    val distanceInside = pointerViewportY - bottomZoneLimit
                    val normalizedProgress = (distanceInside / (viewportEnd - bottomZoneLimit).coerceAtLeast(1f)).coerceIn(0f, 2.5f)
                    val dynamicFactor = normalizedProgress.toDouble().pow(1.8).toFloat()
                    val scrollStep = (minScrollStepPx + dynamicFactor * (maxScrollStepPx - minScrollStepPx)).coerceIn(minScrollStepPx, maxScrollStepPx * 1.5f)

                    listState.scrollBy(scrollStep)

                    if (now - lastSwapTime >= Motion.Duration.DRAG_DEBOUNCE.toLong()) {
                        val currentIndex = localAccounts.indexOfFirst { it.account.id == draggingAccountId }
                        if (currentIndex != -1 && currentIndex < localAccounts.lastIndex) {
                            val currentAccount = localAccounts[currentIndex]
                            val nextAccount = localAccounts[currentIndex + 1]
                            if (currentAccount.account.isFavorite == nextAccount.account.isFavorite) {
                                val nextItem = layoutInfo.visibleItemsInfo.find { it.key == nextAccount.account.id }
                                if (nextItem != null) {
                                    val nextCenterY = nextItem.offset + (nextItem.size / 2f)
                                    if (pointerViewportY > nextCenterY) {
                                        Collections.swap(localAccounts, currentIndex, currentIndex + 1)
                                        lastSwapTime = now
                                        appHaptics.dragTick()
                                    }
                                }
                            }
                        }
                    }
                }

                delay(16L.milliseconds)
            }
        }
    }

    val selectedAccountWithCode = selectedAccountId?.let { id ->
        accountsToDisplay.find { it.account.id == id }
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
                        top = Dimensions.Spacing.xxl * 2 + Dimensions.Spacing.md,
                        bottom = Dimensions.Spacing.xxl * 3
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                ) {
                    items(
                        items = accountsToDisplay,
                        key = { it.account.id },
                        contentType = { "otp_card" }
                    ) { item ->
                        val isDragging = draggingAccountId == item.account.id

                        val cardModifier = if (isDragging) {
                            Modifier
                                .zIndex(10f)
                                .graphicsLayer {
                                    val renderedItem = listState.layoutInfo.visibleItemsInfo.find { it.key == item.account.id }
                                    val currentItemTop = renderedItem?.offset?.toFloat() ?: (pointerViewportY - touchOffsetYInCard)
                                    translationY = (pointerViewportY - touchOffsetYInCard) - currentItemTop
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                    shadowElevation = 16f
                                }
                        } else {
                            Modifier.animateItem(
                                fadeInSpec = Motion.Spec.staggerItemSpec(),
                                placementSpec = Motion.Spec.springFeedbackSpec()
                            )
                        }

                        OtpCodeCard(
                            accountWithCode = item,
                            hideCodes = isHideCodesEnabled,
                            isDragging = isDragging,
                            isReorderEnabled = !isSearchActive && searchQuery.isBlank(),
                            modifier = cardModifier,
                            onCardClick = { selectedAccountId = item.account.id },
                            onCopyCode = { code -> onCopyCode(code, item.account.issuer) },
                            onToggleFavorite = onToggleFavorite,
                            onNextHotpCode = onNextHotpCode,
                            onStartDrag = {
                                if (searchQuery.isBlank()) {
                                    if (localAccounts.isEmpty() && currentSuccessAccounts.isNotEmpty()) {
                                        localAccounts.addAll(currentSuccessAccounts)
                                    }
                                    draggingAccountId = item.account.id
                                    val layoutInfo = listState.layoutInfo
                                    val draggedItem = layoutInfo.visibleItemsInfo.find { it.key == item.account.id }
                                    val itemTop = draggedItem?.offset?.toFloat() ?: 0f
                                    val itemHeight = draggedItem?.size?.toFloat() ?: 120f
                                    touchOffsetYInCard = itemHeight / 2f
                                    pointerViewportY = itemTop + touchOffsetYInCard
                                    lastSwapTime = System.currentTimeMillis()
                                }
                            },
                            onDragDelta = { deltaY, _ ->
                                if (draggingAccountId == item.account.id) {
                                    pointerViewportY += deltaY

                                    val now = System.currentTimeMillis()
                                    if (now - lastSwapTime >= Motion.Duration.DRAG_DEBOUNCE.toLong()) {
                                        val floatingCenterY = pointerViewportY
                                        val currentIndex = localAccounts.indexOfFirst { it.account.id == item.account.id }
                                        if (currentIndex != -1) {
                                            val currentAccount = localAccounts[currentIndex]
                                            val layoutInfo = listState.layoutInfo

                                            if (currentIndex < localAccounts.lastIndex) {
                                                val nextAccount = localAccounts[currentIndex + 1]
                                                if (currentAccount.account.isFavorite == nextAccount.account.isFavorite) {
                                                    val nextItem = layoutInfo.visibleItemsInfo.find { it.key == nextAccount.account.id }
                                                    if (nextItem != null) {
                                                        val nextCenterY = nextItem.offset + (nextItem.size / 2f)
                                                        if (floatingCenterY > nextCenterY) {
                                                            Collections.swap(localAccounts, currentIndex, currentIndex + 1)
                                                            lastSwapTime = now
                                                            appHaptics.dragTick()
                                                        }
                                                    }
                                                }
                                            }
                                            if (currentIndex > 0) {
                                                val prevAccount = localAccounts[currentIndex - 1]
                                                if (currentAccount.account.isFavorite == prevAccount.account.isFavorite) {
                                                    val prevItem = layoutInfo.visibleItemsInfo.find { it.key == prevAccount.account.id }
                                                    if (prevItem != null) {
                                                        val prevCenterY = prevItem.offset + (prevItem.size / 2f)
                                                        if (floatingCenterY < prevCenterY) {
                                                            Collections.swap(localAccounts, currentIndex, currentIndex - 1)
                                                            lastSwapTime = now
                                                            appHaptics.dragTick()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            onEndDrag = {
                                if (draggingAccountId != null) {
                                    val finalOrderedIds = localAccounts.map { it.account.id }
                                    onCommitReorder(finalOrderedIds)
                                    draggingAccountId = null
                                    pointerViewportY = 0f
                                    touchOffsetYInCard = 0f
                                }
                            }
                        )
                    }
                }
            }
            uiState is UiState.Empty -> {
                EmptyAccountsState(
                    onScanQr = onNavigateToScanQr,
                    onAddManual = onNavigateToAddManual
                )
            }
            else -> {
                // Estado de carga inicial (UiState.Loading)
            }
        }

        // 2. Dock Flotante Inferior Ergonómico
        HomeFloatingDock(
            onLockVault = { viewModel.lockVault() },
            onAddAccountClick = { showAddOptionsDialog = true },
            onNavigateToSettings = onNavigateToSettings,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = Dimensions.Spacing.lg)
                .zIndex(20f)
        )

        // 3. Header Flotante Superior
        HomeTopHeader(
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            isHideCodesEnabled = isHideCodesEnabled,
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
        AddAccountOptionsDialog(
            onScanQr = {
                showAddOptionsDialog = false
                onNavigateToScanQr()
            },
            onAddManual = {
                showAddOptionsDialog = false
                onNavigateToAddManual()
            },
            onDismiss = { showAddOptionsDialog = false }
        )
    }
}
