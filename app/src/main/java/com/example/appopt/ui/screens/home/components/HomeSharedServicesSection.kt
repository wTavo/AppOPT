package com.example.appopt.ui.screens.home.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.example.appopt.R
import com.example.appopt.data.cloud.SharedBatchKeyStore
import com.example.appopt.data.cloud.SharedServicesCloudClient
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.domain.model.SharedOtpBatch
import com.example.appopt.security.SecurityConfig
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
 * - Visualización directa de servicios compartidos recibidos («Compartidos conmigo») y enviados («Compartidos por mí»).
 * - Persistencia cifrada de la brecha activa de 3 códigos en [SharedBatchKeyStore].
 * - Reutiliza las operaciones de red centralizadas en [com.example.appopt.ui.screens.home.coordinator.HomeCardsCoordinator] (DRY).
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
    loadingSharedGrantId: String? = null,
    onConnectClick: () -> Unit = {},
    onSharedCardClick: (IncomingSharedService) -> Unit = {},
    onRequestSharedCode: (IncomingSharedService) -> Unit = {},
    onAcceptSharedInvitation: (IncomingSharedService) -> Unit = {},
    onRejectSharedInvitation: (IncomingSharedService) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appHaptics = rememberAppHaptics()
    val currentSecondEpoch by TotpClock.currentSecondEpoch.collectAsStateWithLifecycle()
    val cacheVersion by SharedBatchKeyStore.cacheVersion.collectAsStateWithLifecycle()

    var incomingList by remember {
        mutableStateOf(SharedBatchKeyStore.getCachedIncomingServices(context))
    }
    var outgoingList by remember {
        mutableStateOf(SharedBatchKeyStore.getCachedOutgoingGrants(context))
    }
    var errorMessageResId by remember { mutableStateOf<Int?>(null) }
    val errorMessage = errorMessageResId?.let { stringResource(it) }
    val revokeSuccessMsg = stringResource(R.string.share_revoke_success_msg)

    val cachedBatchesMap = remember {
        mutableStateMapOf<String, SharedOtpBatch>().apply {
            SharedBatchKeyStore.getCachedIncomingServices(context).forEach { svc ->
                SharedBatchKeyStore.getValidBatch(context, svc.grantId)?.let { batch ->
                    put(svc.grantId, batch)
                }
            }
        }
    }

    // Sincroniza cambios locales y remotos cuando cambia cacheVersion
    LaunchedEffect(cacheVersion) {
        incomingList = SharedBatchKeyStore.getCachedIncomingServices(context)
        outgoingList = SharedBatchKeyStore.getCachedOutgoingGrants(context)
        val activeIds = incomingList.map { it.grantId }.toSet()
        cachedBatchesMap.keys.toList().forEach { id ->
            if (id !in activeIds) cachedBatchesMap.remove(id)
        }
        incomingList.forEach { svc ->
            val validBatch = SharedBatchKeyStore.getValidBatch(context, svc.grantId)
            if (validBatch != null) {
                cachedBatchesMap[svc.grantId] = validBatch
            } else {
                cachedBatchesMap.remove(svc.grantId)
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
        onAuthError = { errorMessageResId = R.string.share_error_not_signed_in }
    )

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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
            if (showEmptyStateAndHeader) Dimensions.Spacing.sm else Dimensions.Spacing.md
        )
    ) {
        if (errorMessage != null && showEmptyStateAndHeader) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = UrgentRed,
                modifier = Modifier.padding(horizontal = Dimensions.Spacing.xs)
            )
        }

        when {
            !isDriveConnected && showEmptyStateAndHeader -> {
                SharedServicesConnectPromptCard(
                    isConnectingDrive = isConnectingDrive,
                    onConnectClick = {
                        appHaptics.click()
                        onConnectClick()
                    }
                )
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
                            isItemLoading = loadingSharedGrantId == item.grantId,
                            onCopyCode = { code -> onCopyCode(code, item.issuer) },
                            onRequestAction = { onRequestSharedCode(item) },
                            onToggleFavorite = { grantId ->
                                SharedBatchKeyStore.toggleIncomingFavorite(context, grantId)
                            },
                            onAcceptInvitation = { onAcceptSharedInvitation(item) },
                            onRejectInvitation = { onRejectSharedInvitation(item) },
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
                                    errorMessageResId = R.string.share_error_no_internet
                                    return@OutgoingShareGrantCard
                                }
                                errorMessageResId = null
                                requestOAuthToken { token ->
                                    scope.launch {
                                        SharedServicesCloudClient.revokeGrant(token, grant.grantId)
                                            .onSuccess {
                                                val updated = outgoingList.filterNot { it.grantId == grant.grantId }
                                                outgoingList = updated
                                                SharedBatchKeyStore.saveCachedOutgoingGrants(context, updated)
                                                appHaptics.success()
                                                Toast.makeText(context, revokeSuccessMsg, Toast.LENGTH_SHORT).show()
                                            }
                                            .onFailure {
                                                appHaptics.error()
                                                errorMessageResId = if (!SharedServicesCloudClient.isNetworkAvailable(context)) {
                                                    R.string.share_error_no_internet
                                                } else {
                                                    R.string.share_error_generic
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

/**
 * Tarjeta de bienvenida e inicio de sesión con Google cuando la cuenta aún no está vinculada en la pestaña «Compartidos».
 */
@Composable
private fun SharedServicesConnectPromptCard(
    isConnectingDrive: Boolean,
    onConnectClick: () -> Unit
) {
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
                onClick = onConnectClick,
                enabled = !isConnectingDrive,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
            ) {
                if (isConnectingDrive) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimensions.IconSize.small),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = Dimensions.Stroke.regular
                        )
                    }
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
