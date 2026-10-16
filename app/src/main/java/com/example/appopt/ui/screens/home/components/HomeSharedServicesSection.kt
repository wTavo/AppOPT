package com.example.appopt.ui.screens.home.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.SharedBatchKeyStore
import com.example.appopt.data.cloud.SharedServicesCloudClient
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.domain.model.ShareGrantStatus
import com.example.appopt.domain.model.SharedOtpBatch
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.components.OtpCodeCard
import com.example.appopt.ui.screens.settings.coordinator.rememberGoogleOAuthRequester
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.TotpClock
import kotlinx.coroutines.launch

/**
 * Vista de «Servicios compartidos» integrada bajo el filtro superior «Compartidos» en la pantalla de Inicio (Directivas 4, 29 y 31).
 *
 * Características:
 * - Visualización directa de servicios compartidos recibidos («Compartidos conmigo») y enviados («Compartidos por mí») sin acordeones intermedios.
 * - Persistencia cifrada de la brecha activa de 3 códigos en [SharedBatchKeyStore] para conservar el paso exacto
 *   (`Código 1 de 3`, `2 de 3`, `3 de 3`) aunque el usuario cierre y vuelva a abrir la aplicación antes de que concluya el 3er código.
 * - Soporte para permisos de un solo uso ([ShareAccessMode.SINGLE_USE]) que se eliminan automáticamente al expirar el 3er código.
 * - Diálogo modal de detalles ([IncomingSharedServiceDetailsDialog]) al tocar una tarjeta recibida.
 *
 * @param searchQuery Consulta activa del buscador superior para filtrar servicios compartidos.
 * @param hideCodes Estado del modo privacidad para ocultar dígitos OTP.
 * @param onCopyCode Callback para copiar códigos con limpieza automática del portapapeles.
 * @param modifier Modificador de diseño Compose opcional.
 * @param showEmptyStateAndHeader Si es `true` (filtro «Compartidos»), muestra la barra de resumen/actualizar y el estado vacío ilustrado; si es `false` (filtro «Todos» o «Favoritos»), muestra directamente las tarjetas compartidas cuando existen y se oculta si no hay ninguna.
 * @param onlyFavorites Si es `true`, filtra los servicios entrantes para mostrar únicamente los marcados como favoritos y oculta los salientes.
 */
@Composable
fun HomeSharedServicesAccordion(
    searchQuery: String,
    hideCodes: Boolean,
    onCopyCode: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    showEmptyStateAndHeader: Boolean = true,
    onlyFavorites: Boolean = false,
    isDriveConnected: Boolean = false,
    isConnectingDrive: Boolean = false,
    onConnectClick: () -> Unit = {},
    onSharedCardClick: (IncomingSharedService) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appHaptics = rememberAppHaptics()
    val currentSecondEpoch by TotpClock.currentSecondEpoch.collectAsStateWithLifecycle()
    val cacheVersion by SharedBatchKeyStore.cacheVersion.collectAsStateWithLifecycle()
    val isUnlocked by AuthenticatorApp.instance.appLockManager.isUnlocked.collectAsStateWithLifecycle()

    var isLoading by remember { mutableStateOf(false) }
    var needsGoogleSignIn by remember { mutableStateOf(false) }
    var incomingList by remember {
        mutableStateOf(SharedBatchKeyStore.getCachedIncomingServices(context))
    }
    var outgoingList by remember {
        mutableStateOf(SharedBatchKeyStore.getCachedOutgoingGrants(context))
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val cachedBatchesMap = remember {
        mutableStateMapOf<String, SharedOtpBatch>().apply {
            SharedBatchKeyStore.getCachedIncomingServices(context).forEach { svc ->
                SharedBatchKeyStore.getValidBatch(context, svc.grantId)?.let { batch ->
                    put(svc.grantId, batch)
                }
            }
        }
    }
    var loadingGrantId by remember { mutableStateOf<String?>(null) }

    // Sincroniza cambios locales (p. ej. cuando el propietario comparte un servicio desde AccountDetailsDialog)
    LaunchedEffect(cacheVersion) {
        incomingList = SharedBatchKeyStore.getCachedIncomingServices(context)
        outgoingList = SharedBatchKeyStore.getCachedOutgoingGrants(context)
        incomingList.forEach { svc ->
            val validBatch = SharedBatchKeyStore.getValidBatch(context, svc.grantId)
            if (validBatch != null) {
                cachedBatchesMap[svc.grantId] = validBatch
            }
        }
    }

    // Expira en tiempo real cualquier brecha de 3 códigos o lote cuyo bloque de tiempo haya concluido
    val nowMillis = currentSecondEpoch * 1000L
    LaunchedEffect(currentSecondEpoch) {
        val expiredEntries = cachedBatchesMap.entries
            .filter { (_, batch) -> batch.isExpired(nowMillis) || batch.getCodeAt(nowMillis) == null }
            .toList()
        if (expiredEntries.isNotEmpty()) {
            var updatedIncoming = incomingList
            expiredEntries.forEach { (grantId, batch) ->
                SharedBatchKeyStore.removeBatch(context, grantId)
                cachedBatchesMap.remove(grantId)
                if (batch.isSingleUseWindow) {
                    updatedIncoming = updatedIncoming.filterNot { it.grantId == grantId }
                }
            }
            if (updatedIncoming != incomingList) {
                incomingList = updatedIncoming
                SharedBatchKeyStore.saveCachedIncomingServices(context, updatedIncoming)
            }
        }
    }

    val requestOAuthToken = rememberGoogleOAuthRequester(
        onAuthError = {
            isLoading = false
            needsGoogleSignIn = true
            errorMessage = context.getString(R.string.share_error_not_signed_in)
        }
    )

    fun refreshSharedServices() {
        if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
            val cachedIn = SharedBatchKeyStore.getCachedIncomingServices(context)
            incomingList = cachedIn
            outgoingList = SharedBatchKeyStore.getCachedOutgoingGrants(context)
            cachedIn.forEach { svc ->
                val validBatch = SharedBatchKeyStore.getValidBatch(context, svc.grantId)
                if (validBatch != null) {
                    cachedBatchesMap[svc.grantId] = validBatch
                } else {
                    cachedBatchesMap.remove(svc.grantId)
                }
            }
            isLoading = false
            errorMessage = context.getString(R.string.share_error_no_internet_offline_mode)
            return
        }

        isLoading = true
        errorMessage = null
        requestOAuthToken { token ->
            needsGoogleSignIn = false
            scope.launch {
                SharedBatchKeyStore.syncSharedServicesFromCloud(context, token)
                    .onSuccess {
                        val currentNow = System.currentTimeMillis()
                        val mergedList = SharedBatchKeyStore.getCachedIncomingServices(context)
                        incomingList = mergedList
                        outgoingList = SharedBatchKeyStore.getCachedOutgoingGrants(context)
                        val activeIds = mergedList.map { it.grantId }.toSet()
                        cachedBatchesMap.keys.toList().forEach { id ->
                            if (id !in activeIds) {
                                SharedBatchKeyStore.removeBatch(context, id)
                                cachedBatchesMap.remove(id)
                            }
                        }
                        mergedList.forEach { svc ->
                            SharedBatchKeyStore.getValidBatch(context, svc.grantId, currentNow)?.let { batch ->
                                cachedBatchesMap[svc.grantId] = batch
                            }
                        }
                    }
                    .onFailure {
                        errorMessage = if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
                            context.getString(R.string.share_error_no_internet_offline_mode)
                        } else {
                            context.getString(R.string.share_error_generic)
                        }
                    }
                isLoading = false
            }
        }
    }

    LaunchedEffect(isUnlocked, isDriveConnected) {
        if (isUnlocked && isDriveConnected && SharedServicesCloudClient.isNetworkAvailable(context) && !isLoading) {
            refreshSharedServices()
        }
    }

    fun requestCodeOrOfflineBatch(item: IncomingSharedService) {
        if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
            appHaptics.error()
            errorMessage = context.getString(R.string.share_error_no_internet)
            return
        }
        loadingGrantId = item.grantId
        errorMessage = null
        requestOAuthToken { token ->
            scope.launch {
                if (item.accessMode == ShareAccessMode.OFFLINE_BATCH) {
                    SharedServicesCloudClient.downloadPrecomputedBatch(token, item.grantId)
                        .onSuccess { batch ->
                            SharedBatchKeyStore.saveBatch(context, batch)
                            cachedBatchesMap[item.grantId] = batch
                            appHaptics.success()
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_shared_batch_downloaded_msg),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .onFailure {
                            appHaptics.error()
                            if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
                                errorMessage = context.getString(R.string.share_error_no_internet)
                            } else {
                                SharedBatchKeyStore.removeBatch(context, item.grantId)
                                cachedBatchesMap.remove(item.grantId)
                                errorMessage = context.getString(R.string.settings_shared_revoked_error)
                            }
                        }
                } else {
                    SharedServicesCloudClient.requestLiveCode(token, item.grantId)
                        .onSuccess { live ->
                            val liveBatch = live.toSharedOtpBatch(item.grantId)
                            SharedBatchKeyStore.saveBatch(context, liveBatch)
                            cachedBatchesMap[item.grantId] = liveBatch
                            appHaptics.success()
                        }
                        .onFailure {
                            appHaptics.error()
                            if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
                                errorMessage = context.getString(R.string.share_error_no_internet)
                            } else {
                                SharedBatchKeyStore.removeBatch(context, item.grantId)
                                cachedBatchesMap.remove(item.grantId)
                                errorMessage = context.getString(R.string.settings_shared_revoked_error)
                            }
                        }
                }
                loadingGrantId = null
            }
        }
    }

    fun rejectOrLeaveIncomingService(item: IncomingSharedService) {
        if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
            appHaptics.error()
            errorMessage = context.getString(R.string.share_error_no_internet)
            return
        }
        loadingGrantId = item.grantId
        errorMessage = null
        val wasPending = item.status == ShareGrantStatus.PENDING
        requestOAuthToken { token ->
            scope.launch {
                SharedServicesCloudClient.revokeGrant(token, item.grantId)
                    .onSuccess {
                        val updatedList = incomingList.filterNot { it.grantId == item.grantId }
                        incomingList = updatedList
                        SharedBatchKeyStore.saveCachedIncomingServices(context, updatedList)
                        SharedBatchKeyStore.removeBatch(context, item.grantId)
                        cachedBatchesMap.remove(item.grantId)
                        appHaptics.success()
                        val msgRes = if (wasPending) {
                            R.string.settings_shared_reject_success_msg
                        } else {
                            R.string.settings_shared_leave_success_msg
                        }
                        Toast.makeText(
                            context,
                            context.getString(msgRes),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .onFailure {
                        appHaptics.error()
                        errorMessage = if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
                            context.getString(R.string.share_error_no_internet)
                        } else {
                            context.getString(R.string.share_error_generic)
                        }
                    }
                loadingGrantId = null
            }
        }
    }

    val filteredIncoming = remember(incomingList, searchQuery, onlyFavorites) {
        val baseList = if (onlyFavorites) incomingList.filter { it.isFavorite } else incomingList
        if (searchQuery.isBlank()) baseList
        else baseList.filter {
            it.issuer.contains(searchQuery, ignoreCase = true) ||
                it.accountName.contains(searchQuery, ignoreCase = true) ||
                it.ownerEmail.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredOutgoing = remember(outgoingList, searchQuery, onlyFavorites) {
        if (onlyFavorites) {
            emptyList()
        } else if (searchQuery.isBlank()) {
            outgoingList
        } else {
            outgoingList.filter {
                it.issuer.contains(searchQuery, ignoreCase = true) ||
                    it.accountName.contains(searchQuery, ignoreCase = true) ||
                    it.guestEmail.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    if (!showEmptyStateAndHeader && filteredIncoming.isEmpty()) {
        return
    }

    val pendingInvitationsCount = remember(incomingList) {
        incomingList.count { it.status == ShareGrantStatus.PENDING }
    }

    val summaryText = when {
        pendingInvitationsCount > 0 ->
            stringResource(R.string.home_shared_accordion_summary_pending, pendingInvitationsCount)
        else ->
            stringResource(R.string.home_shared_accordion_summary_counts, incomingList.size, outgoingList.size)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
            if (showEmptyStateAndHeader) Dimensions.Spacing.sm else Dimensions.Spacing.md
        )
    ) {
        if (showEmptyStateAndHeader) {
            // Barra compacta de resumen y botón de actualización manual en la pestaña «Compartidos»
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (pendingInvitationsCount > 0) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (pendingInvitationsCount > 0) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                IconButton(
                    onClick = {
                        appHaptics.click()
                        refreshSharedServices()
                    },
                    modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.action_refresh),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimensions.IconSize.medium)
                    )
                }
            }
        }

        if (errorMessage != null && showEmptyStateAndHeader) {
            Text(
                text = errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = UrgentRed,
                modifier = Modifier.padding(horizontal = Dimensions.Spacing.xs)
            )
        }

        when {
            isLoading && filteredIncoming.isEmpty() && filteredOutgoing.isEmpty() && showEmptyStateAndHeader -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimensions.Spacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(Dimensions.IconSize.large))
                }
            }

            (!isDriveConnected || needsGoogleSignIn) && showEmptyStateAndHeader -> {
                Surface(
                    shape = RoundedCornerShape(Dimensions.CornerRadius.large),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(Dimensions.Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.IconSize.hero)
                        )
                        Text(
                            text = stringResource(R.string.home_shared_accordion_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.home_empty_shared_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                appHaptics.click()
                                onConnectClick()
                            },
                            enabled = !isConnectingDrive && !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        ) {
                            if (isConnectingDrive || isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Dimensions.IconSize.small),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = Dimensions.Stroke.regular
                                )
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.ic_brand_google),
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.IconSize.small)
                                )
                                Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                                Text(
                                    text = stringResource(R.string.settings_drive_connect_button),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            filteredIncoming.isEmpty() && filteredOutgoing.isEmpty() -> {
                if (showEmptyStateAndHeader) {
                    EmptyAccountsState(
                        icon = Icons.Filled.Group,
                        title = stringResource(R.string.home_empty_shared_title),
                        description = stringResource(R.string.home_empty_shared_description),
                        modifier = Modifier.padding(top = Dimensions.Spacing.md)
                    )
                }
            }

            else -> {
                if (filteredIncoming.isNotEmpty()) {
                    if (showEmptyStateAndHeader) {
                        Text(
                            text = stringResource(R.string.settings_shared_tab_incoming),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = Dimensions.Spacing.xs)
                        )
                    }
                    filteredIncoming.forEach { item ->
                        val cachedBatch = cachedBatchesMap[item.grantId]
                        val activeCode = cachedBatch?.getCodeAt(nowMillis)
                        val liveWindowProgress = cachedBatch?.getWindowStepProgress(
                            nowMillis = nowMillis,
                            maxLiveWindowSteps = SecurityConfig.SHARED_LIVE_CODE_WINDOW_STEPS
                        )

                        OtpCodeCard(
                            service = item,
                            activeCode = activeCode,
                            liveWindowProgress = liveWindowProgress,
                            hideCodes = hideCodes,
                            isItemLoading = loadingGrantId == item.grantId,
                            onCopyCode = { code -> onCopyCode(code, item.issuer) },
                            onRequestAction = { requestCodeOrOfflineBatch(item) },
                            onToggleFavorite = { grantId ->
                                SharedBatchKeyStore.toggleIncomingFavorite(context, grantId)
                            },
                            onAcceptInvitation = {
                                if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
                                    appHaptics.error()
                                    errorMessage = context.getString(R.string.share_error_no_internet)
                                    return@OtpCodeCard
                                }
                                loadingGrantId = item.grantId
                                errorMessage = null
                                requestOAuthToken { token ->
                                    scope.launch {
                                        SharedServicesCloudClient.acceptInvitation(token, item.grantId)
                                            .onSuccess {
                                                val updatedList = incomingList.map {
                                                    if (it.grantId == item.grantId) {
                                                        it.copy(status = ShareGrantStatus.ACCEPTED)
                                                    } else {
                                                        it
                                                    }
                                                }
                                                incomingList = updatedList
                                                SharedBatchKeyStore.saveCachedIncomingServices(context, updatedList)
                                                if (item.accessMode == ShareAccessMode.OFFLINE_BATCH) {
                                                    SharedServicesCloudClient.downloadPrecomputedBatch(token, item.grantId)
                                                        .onSuccess { batch ->
                                                            SharedBatchKeyStore.saveBatch(context, batch)
                                                            cachedBatchesMap[item.grantId] = batch
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
                                                errorMessage = if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
                                                    context.getString(R.string.share_error_no_internet)
                                                } else {
                                                    context.getString(R.string.share_error_generic)
                                                }
                                            }
                                        loadingGrantId = null
                                    }
                                }
                            },
                            onRejectInvitation = { rejectOrLeaveIncomingService(item) },
                            onCardClick = { onSharedCardClick(item) }
                        )
                    }
                }

                if (showEmptyStateAndHeader && filteredOutgoing.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                    Text(
                        text = stringResource(R.string.settings_shared_tab_outgoing),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = Dimensions.Spacing.xs)
                    )
                    filteredOutgoing.forEach { grant ->
                        OutgoingShareGrantCard(
                            grant = grant,
                            onRevoke = {
                                if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
                                    appHaptics.error()
                                    errorMessage = context.getString(R.string.share_error_no_internet)
                                    return@OutgoingShareGrantCard
                                }
                                errorMessage = null
                                requestOAuthToken { token ->
                                    scope.launch {
                                        SharedServicesCloudClient.revokeGrant(token, grant.grantId)
                                            .onSuccess {
                                                val updated = outgoingList.filterNot { it.grantId == grant.grantId }
                                                outgoingList = updated
                                                SharedBatchKeyStore.saveCachedOutgoingGrants(context, updated)
                                                appHaptics.success()
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.share_revoke_success_msg),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            .onFailure {
                                                appHaptics.error()
                                                errorMessage = if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
                                                    context.getString(R.string.share_error_no_internet)
                                                } else {
                                                    context.getString(R.string.share_error_generic)
                                                }
                                            }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

