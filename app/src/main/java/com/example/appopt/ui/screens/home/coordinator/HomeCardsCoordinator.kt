package com.example.appopt.ui.screens.home.coordinator

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import com.example.appopt.R
import com.example.appopt.data.cloud.SharedBatchKeyStore
import com.example.appopt.data.cloud.SharedServicesCloudClient
import com.example.appopt.domain.model.AccountWithCode
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.domain.model.ShareGrantStatus
import com.example.appopt.ui.screens.home.HomeCardItem
import com.example.appopt.ui.screens.home.HomeViewModel
import com.example.appopt.ui.theme.AppHaptics
import com.example.appopt.ui.theme.rememberAppHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Coordinador de estado y lógica de negocio para las tarjetas unificadas en la pantalla principal (Directivas 8, 29 y DRY).
 *
 * Responsabilidades:
 * - Sincroniza reactivamente las cuentas locales de Room y servicios compartidos en una lista unificada sin saltos visuales.
 * - Gestiona el reordenamiento manual mediante arrastrar y soltar (drag & drop) y su persistencia en base de datos.
 * - Implementa alternancia optimista de favoritos para cuentas propias y compartidas con recolocación inmediata.
 * - Orquesta las peticiones a la nube de servicios compartidos (solicitud de código, aceptación y revocación/abandono).
 */
class HomeCardsCoordinator(
    val localCards: SnapshotStateList<HomeCardItem>,
    val context: Context,
    val scope: CoroutineScope,
    val appHaptics: AppHaptics,
    val viewModel: HomeViewModel,
    val requestOAuthToken: ((String) -> Unit) -> Unit
) {
    var isDraggingAny by mutableStateOf(false)
    var loadingSharedGrantId by mutableStateOf<String?>(null)

    /**
     * Sincroniza la lista unificada local con las emisiones de Room y servicios compartidos.
     */
    fun syncCards(
        currentSuccessAccounts: List<AccountWithCode>,
        incomingServices: List<IncomingSharedService>
    ) {
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

    /**
     * Persiste el orden actual de las cuentas propias en base de datos y de los servicios compartidos en caché.
     */
    fun commitAllCardsOrder() {
        val ownOrderedIds = localCards.filterIsInstance<HomeCardItem.Own>().map { it.item.account.id }
        if (ownOrderedIds.isNotEmpty()) {
            viewModel.commitReorder(ownOrderedIds)
        }
        val sharedOrderedIds = localCards.filterIsInstance<HomeCardItem.Shared>().map { it.service.grantId }
        if (sharedOrderedIds.isNotEmpty()) {
            val currentServices = SharedBatchKeyStore.getCachedIncomingServices(context)
            val serviceMap = currentServices.associateBy { it.grantId }
            val reordered = sharedOrderedIds.mapNotNull { serviceMap[it] } + currentServices.filter { it.grantId !in sharedOrderedIds }
            SharedBatchKeyStore.saveCachedIncomingServices(context, reordered)
        }
    }

    /**
     * Alterna optimistamente el estado de favorito para una cuenta propia y la reubica de inmediato.
     */
    fun onToggleFavoriteOwn(id: String) {
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

    /**
     * Alterna optimistamente el estado de favorito para un servicio compartido y lo reubica de inmediato.
     */
    fun onToggleFavoriteShared(grantId: String) {
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

    /**
     * Solicita un código en tiempo real o descarga un lote sin conexión para el servicio compartido.
     */
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
                            val current = SharedBatchKeyStore.getCachedIncomingServices(context)
                            SharedBatchKeyStore.saveCachedIncomingServices(context, current.filterNot { it.grantId == service.grantId })
                            localCards.removeAll { it is HomeCardItem.Shared && it.service.grantId == service.grantId }
                            Toast.makeText(context, context.getString(R.string.settings_shared_revoked_error), Toast.LENGTH_SHORT).show()
                            scope.launch { SharedBatchKeyStore.syncIncomingServicesFromCloud(context, token, forceRefresh = true) }
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
                            val current = SharedBatchKeyStore.getCachedIncomingServices(context)
                            SharedBatchKeyStore.saveCachedIncomingServices(context, current.filterNot { it.grantId == service.grantId })
                            localCards.removeAll { it is HomeCardItem.Shared && it.service.grantId == service.grantId }
                            Toast.makeText(context, context.getString(R.string.settings_shared_revoked_error), Toast.LENGTH_SHORT).show()
                            scope.launch { SharedBatchKeyStore.syncIncomingServicesFromCloud(context, token, forceRefresh = true) }
                        }
                }
                loadingSharedGrantId = null
            }
        }
    }

    /**
     * Acepta una invitación entrante a un servicio compartido.
     */
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
                        scope.launch { SharedBatchKeyStore.syncIncomingServicesFromCloud(context, token, forceRefresh = true) }
                    }
                loadingSharedGrantId = null
            }
        }
    }

    /**
     * Rechaza una invitación pendiente o abandona un servicio compartido aceptado.
     */
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
                        localCards.removeAll { it is HomeCardItem.Shared && it.service.grantId == item.grantId }
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
                        scope.launch { SharedBatchKeyStore.syncIncomingServicesFromCloud(context, token, forceRefresh = true) }
                    }
                loadingSharedGrantId = null
            }
        }
    }
}

/**
 * Crea y recuerda una instancia de [HomeCardsCoordinator] vinculada al ciclo de vida del Composable.
 */
@Composable
fun rememberHomeCardsCoordinator(
    viewModel: HomeViewModel,
    requestOAuthToken: ((String) -> Unit) -> Unit
): HomeCardsCoordinator {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appHaptics = rememberAppHaptics()
    val localCards = remember { mutableStateListOf<HomeCardItem>() }

    return remember(viewModel, context) {
        HomeCardsCoordinator(
            localCards = localCards,
            context = context,
            scope = scope,
            appHaptics = appHaptics,
            viewModel = viewModel,
            requestOAuthToken = requestOAuthToken
        )
    }
}
