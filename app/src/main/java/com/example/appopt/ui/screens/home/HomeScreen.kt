package com.example.appopt.ui.screens.home

import android.widget.Toast
import kotlin.math.pow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.R
import com.example.appopt.domain.repository.AccountWithCode
import com.example.appopt.ui.common.UiState
import com.example.appopt.ui.components.AccountDetailsDialog
import com.example.appopt.ui.components.OtpCodeCard
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics
import java.util.Collections

/**
 * Pantalla principal que visualiza las cuentas 2FA registradas con dock de control flotante ergonómico (Floating Pill Bar).
 *
 * Características de seguridad, diseño e interacción:
 * - Barra superior: Búsqueda interactiva y alternador de modo de privacidad con animaciones fluidas.
 * - Lista reactiva: Cuentas 2FA con arrastre, favoritos, filtrado y actualización en tiempo real de códigos OTP.
 * - Dock flotante: Acciones rápidas ergonómicas inferiores accesibles con una sola mano.
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
    val context = LocalContext.current

    // Lista de renderizado local activa exclusivamente durante sesiones de arrastre
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

    // Durante el arrastre se usa la lista local (mutable); fuera de él, la lista del ViewModel
    val accountsToDisplay by remember(draggingAccountId) {
        derivedStateOf {
            if (draggingAccountId != null && localAccounts.isNotEmpty()) localAccounts
            else currentSuccessAccounts
        }
    }

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
            val minScrollStepPx = with(density) { 3.dp.toPx() }
            val maxScrollStepPx = with(density) { 45.dp.toPx() }

            while (true) {
                val layoutInfo = listState.layoutInfo
                val viewportStart = layoutInfo.viewportStartOffset.toFloat()
                val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
                val totalHeight = (viewportEnd - viewportStart).coerceAtLeast(1f)

                // Zonas de auto-scroll amplio (35% superior e inferior del viewport)
                val topZoneLimit = viewportStart + (totalHeight * 0.35f)
                val bottomZoneLimit = viewportEnd - (totalHeight * 0.35f)
                val now = System.currentTimeMillis()

                // Auto-scroll hacia arriba dinámico
                if (pointerViewportY < topZoneLimit && listState.canScrollBackward) {
                    val distanceInside = topZoneLimit - pointerViewportY
                    val normalizedProgress = (distanceInside / (topZoneLimit - viewportStart).coerceAtLeast(1f)).coerceIn(0f, 2.5f)
                    val dynamicFactor = normalizedProgress.toDouble().pow(1.8).toFloat()
                    val scrollStep = (minScrollStepPx + dynamicFactor * (maxScrollStepPx - minScrollStepPx)).coerceIn(minScrollStepPx, maxScrollStepPx * 1.5f)

                    listState.scrollBy(-scrollStep)

                    // Verificar swap continuo mientras sube
                    if (now - lastSwapTime >= Motion.Duration.DragDebounce.toLong()) {
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
                }
                // Auto-scroll hacia abajo dinámico
                else if (pointerViewportY > bottomZoneLimit && listState.canScrollForward) {
                    val distanceInside = pointerViewportY - bottomZoneLimit
                    val normalizedProgress = (distanceInside / (viewportEnd - bottomZoneLimit).coerceAtLeast(1f)).coerceIn(0f, 2.5f)
                    val dynamicFactor = normalizedProgress.toDouble().pow(1.8).toFloat()
                    val scrollStep = (minScrollStepPx + dynamicFactor * (maxScrollStepPx - minScrollStepPx)).coerceIn(minScrollStepPx, maxScrollStepPx * 1.5f)

                    listState.scrollBy(scrollStep)

                    // Verificar swap continuo mientras baja
                    if (now - lastSwapTime >= Motion.Duration.DragDebounce.toLong()) {
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

                kotlinx.coroutines.delay(16L)
            }
        }
    }

    // Cuenta actualmente seleccionada para el popup modal (reactiva a los ticks en vivo de accountsToDisplay)
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
                            Modifier
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
                                    localAccounts.clear()
                                    localAccounts.addAll(currentSuccessAccounts)
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
                                    if (now - lastSwapTime >= Motion.Duration.DragDebounce.toLong()) {
                                        val floatingCenterY = pointerViewportY
                                        val currentIndex = localAccounts.indexOfFirst { it.account.id == item.account.id }
                                        if (currentIndex != -1) {
                                            val currentAccount = localAccounts[currentIndex]
                                            val layoutInfo = listState.layoutInfo

                                            // Swap hacia abajo
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
                                            // Swap hacia arriba
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
                                    onCommitReorder(localAccounts.map { it.account.id })
                                    draggingAccountId = null
                                    pointerViewportY = 0f
                                    touchOffsetYInCard = 0f
                                    localAccounts.clear()
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
                // Estado de carga inicial (UiState.Loading): No renderiza nada prematuramente evitando parpadeos
            }
        }

        // Dock Flotante Inferior Ergonómico (Overlay moderno con elevación)
        Surface(
            shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = Dimensions.Elevation.modal,
            shadowElevation = Dimensions.Elevation.cardDragging,
            border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = Dimensions.Spacing.lg)
                .zIndex(20f)
                .wrapContentWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Dimensions.Spacing.lg, vertical = Dimensions.Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Bloquear bóveda manualmente
                IconButton(
                    onClick = {
                        appHaptics.click()
                        viewModel.lockVault()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.home_lock_vault),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 2. Gestor de Contraseñas (Módulo Próximamente)
                IconButton(
                    onClick = {
                        appHaptics.click()
                        Toast.makeText(
                            context,
                            context.getString(R.string.passwords_coming_soon),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_key),
                        contentDescription = stringResource(R.string.passwords_nav_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 3. Hero (+) FAB para agregar cuentas
                FloatingActionButton(
                    onClick = {
                        appHaptics.click()
                        showAddOptionsDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = Dimensions.Elevation.cardDefault,
                        pressedElevation = Dimensions.Elevation.cardDragging
                    ),
                    modifier = Modifier.size(Dimensions.ComponentSize.heroFab)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.home_add_account),
                        modifier = Modifier.size(Dimensions.IconSize.large)
                    )
                }

                // 4. Ajustes y Configuración
                IconButton(onClick = {
                    appHaptics.click()
                    onNavigateToSettings()
                }) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. Header Flotante Superior (Overlay Moderno con Título Centrado y Lupa a la Izquierda)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = Dimensions.Spacing.lg, vertical = Dimensions.Spacing.sm)
                .zIndex(20f)
        ) {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) togetherWith
                            fadeOut(animationSpec = Motion.Spec.quickFadeSpec())
                },
                label = "headerSearchOverlayAnimation"
            ) { searchOpen ->
                if (searchOpen) {
                    // Barra de búsqueda flotante en píldora
                    Surface(
                        shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = Dimensions.Elevation.cardDefault,
                        shadowElevation = Dimensions.Elevation.cardDefault,
                        border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimensions.ComponentHeight.buttonDefault)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = Dimensions.Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Dimensions.IconSize.medium)
                            )

                            TextField(
                                value = searchQuery,
                                onValueChange = viewModel::onSearchQueryChanged,
                                placeholder = {
                                    Text(
                                        stringResource(R.string.home_search_placeholder),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                textStyle = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = {
                                    appHaptics.click()
                                    isSearchActive = false
                                    viewModel.onSearchQueryChanged("")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_close_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // Header Overlay: Lupa Izquierda, Título Estilizado Central, Privacidad Derecha
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimensions.ComponentHeight.buttonDefault),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 1. Botón Lupa Flotante a la Izquierda
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = Dimensions.Elevation.cardDefault,
                            shadowElevation = Dimensions.Elevation.cardDefault,
                            border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton + Dimensions.Spacing.sm)
                        ) {
                            IconButton(
                                onClick = {
                                    appHaptics.click()
                                    isSearchActive = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.action_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 2. Título Central con Diseño Estilizado (Píldora Amplia con Tipografía de Alto Impacto)
                        Surface(
                            shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            tonalElevation = Dimensions.Elevation.cardDefault,
                            shadowElevation = Dimensions.Elevation.cardDefault,
                            border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.home_title),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(
                                    horizontal = Dimensions.Spacing.xl,
                                    vertical = Dimensions.Spacing.sm
                                )
                            )
                        }

                        // 3. Botón Privacidad (Ojo) Flotante a la Derecha
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = Dimensions.Elevation.cardDefault,
                            shadowElevation = Dimensions.Elevation.cardDefault,
                            border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton + Dimensions.Spacing.sm)
                        ) {
                            IconButton(
                                onClick = {
                                    appHaptics.click()
                                    viewModel.toggleHideCodes()
                                }
                            ) {
                                AnimatedContent(
                                    targetState = isHideCodesEnabled,
                                    transitionSpec = {
                                        fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) togetherWith
                                                fadeOut(animationSpec = Motion.Spec.quickFadeSpec())
                                    },
                                    label = "hideCodesIconAnimation"
                                ) { hideEnabled ->
                                    Icon(
                                        imageVector = if (hideEnabled) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = if (hideEnabled) stringResource(R.string.action_show_codes) else stringResource(R.string.action_hide_codes),
                                        tint = if (hideEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
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
