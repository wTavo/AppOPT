package com.example.appopt.ui.screens.home.components

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.appopt.data.cloud.SharedBatchKeyStore
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.components.OtpCodeCard
import com.example.appopt.ui.screens.home.HomeCardItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState

/**
 * Elemento unificado de lista reordenable mediante arrastre que renderiza tarjetas de cuentas propias o servicios compartidos (Directivas 8, 29 y DRY).
 *
 * @param item Elemento visual [HomeCardItem] (Propio o Compartido).
 * @param reorderableState Estado del controlador de arrastre de la lista.
 * @param isHideCodesEnabled Estado del modo de privacidad para ocultar dígitos.
 * @param isSearchActive Indica si el usuario está realizando una búsqueda activa.
 * @param searchQuery Texto de búsqueda actual.
 * @param nowMillis Marca de tiempo UNIX actual en milisegundos para sincronización temporal.
 * @param loadingSharedGrantId Identificador del permiso compartido con petición asíncrona en curso (si aplica).
 * @param onDragStarted Callback invocado al iniciar el arrastre táctil.
 * @param onDragStopped Callback invocado al soltar el elemento arrastrado.
 * @param onOwnCardClick Callback al pulsar una tarjeta de cuenta propia (abrir detalles).
 * @param onSharedCardClick Callback al pulsar una tarjeta de servicio compartido (abrir detalles).
 * @param onCopyCode Callback para copiar el código numérico al portapapeles.
 * @param onToggleFavoriteOwn Callback para alternar favorito en cuentas propias.
 * @param onToggleFavoriteShared Callback para alternar favorito en servicios compartidos.
 * @param onNextHotpCode Callback para avanzar contador HOTP.
 * @param onRequestSharedCode Callback para solicitar código en tiempo real o descargar lote sin conexión.
 * @param onAcceptSharedInvitation Callback para aceptar invitación compartida.
 * @param onRejectSharedInvitation Callback para rechazar o abandonar servicio compartido.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun LazyItemScope.HomeReorderableCardItem(
    item: HomeCardItem,
    reorderableState: ReorderableLazyListState,
    isHideCodesEnabled: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    nowMillis: Long,
    loadingSharedGrantId: String?,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
    onOwnCardClick: (String) -> Unit,
    onSharedCardClick: (IncomingSharedService) -> Unit,
    onCopyCode: (String, String) -> Unit,
    onToggleFavoriteOwn: (String) -> Unit,
    onToggleFavoriteShared: (String) -> Unit,
    onNextHotpCode: (String) -> Unit,
    onRequestSharedCode: (IncomingSharedService) -> Unit,
    onAcceptSharedInvitation: (IncomingSharedService) -> Unit,
    onRejectSharedInvitation: (IncomingSharedService) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    when (item) {
        is HomeCardItem.Own -> {
            ReorderableItem(reorderableState, key = item.key, modifier = modifier) { isDragging ->
                OtpCodeCard(
                    accountWithCode = item.item,
                    hideCodes = isHideCodesEnabled,
                    isDragging = isDragging,
                    modifier = Modifier.longPressDraggableHandle(
                        enabled = !isSearchActive && searchQuery.isBlank(),
                        onDragStarted = { onDragStarted() },
                        onDragStopped = onDragStopped
                    ),
                    onCardClick = { onOwnCardClick(item.item.account.id) },
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

            ReorderableItem(reorderableState, key = item.key, modifier = modifier) { isDragging ->
                OtpCodeCard(
                    service = service,
                    activeCode = activeCode,
                    hideCodes = isHideCodesEnabled,
                    liveWindowProgress = liveWindowProgress,
                    isItemLoading = loadingSharedGrantId == service.grantId,
                    isDragging = isDragging,
                    modifier = Modifier.longPressDraggableHandle(
                        enabled = !isSearchActive && searchQuery.isBlank(),
                        onDragStarted = { onDragStarted() },
                        onDragStopped = onDragStopped
                    ),
                    onCopyCode = { code -> onCopyCode(code, service.issuer) },
                    onRequestAction = { onRequestSharedCode(service) },
                    onToggleFavorite = { onToggleFavoriteShared(service.grantId) },
                    onCardClick = { onSharedCardClick(service) },
                    onAcceptInvitation = { onAcceptSharedInvitation(service) },
                    onRejectInvitation = { onRejectSharedInvitation(service) }
                )
            }
        }
    }
}
