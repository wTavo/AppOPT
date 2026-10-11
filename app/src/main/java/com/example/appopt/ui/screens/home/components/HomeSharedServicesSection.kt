package com.example.appopt.ui.screens.home.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.R
import com.example.appopt.data.cloud.LiveSharedCodeResult
import com.example.appopt.data.cloud.SharedBatchKeyStore
import com.example.appopt.data.cloud.SharedServicesCloudClient
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.domain.model.OutgoingShareGrant
import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.domain.model.SharedOtpBatch
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.screens.settings.coordinator.rememberGoogleOAuthRequester
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.TotpClock
import kotlinx.coroutines.launch

/**
 * Sección desplegable (acordeón) de «Servicios compartidos» integrada en la lista principal de Inicio (Directivas 4, 29 y 31).
 *
 * Permite consultar y gestionar en la misma lista de Inicio tanto los servicios que terceros han compartido
 * con el usuario (en línea o por lotes sin conexión) como los permisos otorgados por el propietario,
 * manteniendo la pantalla principal limpia cuando la sección está plegada.
 *
 * @param searchQuery Consulta activa del buscador superior para filtrar servicios compartidos.
 * @param hideCodes Estado del modo privacidad para ocultar dígitos OTP.
 * @param onCopyCode Callback para copiar códigos con limpieza automática del portapapeles.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun HomeSharedServicesAccordion(
    searchQuery: String,
    hideCodes: Boolean,
    onCopyCode: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appHaptics = rememberAppHaptics()
    val currentSecondEpoch by TotpClock.currentSecondEpoch.collectAsStateWithLifecycle()

    var isExpanded by remember { mutableStateOf(false) }
    var hasFetchedOnce by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var needsGoogleSignIn by remember { mutableStateOf(false) }
    var incomingList by remember { mutableStateOf<List<IncomingSharedService>>(emptyList()) }
    var outgoingList by remember { mutableStateOf<List<OutgoingShareGrant>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val liveCodesMap = remember { mutableStateMapOf<String, LiveSharedCodeResult>() }
    val cachedBatchesMap = remember { mutableStateMapOf<String, SharedOtpBatch>() }
    var loadingGrantId by remember { mutableStateOf<String?>(null) }

    val requestOAuthToken = rememberGoogleOAuthRequester(
        onAuthError = {
            isLoading = false
            needsGoogleSignIn = true
            errorMessage = context.getString(R.string.share_error_not_signed_in)
        }
    )

    fun refreshSharedServices() {
        isLoading = true
        errorMessage = null
        requestOAuthToken { token ->
            needsGoogleSignIn = false
            scope.launch {
                val incomingRes = SharedServicesCloudClient.fetchIncomingServices(token)
                val outgoingRes = SharedServicesCloudClient.fetchOutgoingGrants(token)

                incomingRes.onSuccess { list ->
                    incomingList = list
                    list.forEach { svc ->
                        SharedBatchKeyStore.getValidBatch(context, svc.grantId)?.let { batch ->
                            cachedBatchesMap[svc.grantId] = batch
                        }
                    }
                }.onFailure {
                    errorMessage = context.getString(R.string.share_error_generic)
                }

                outgoingRes.onSuccess { list ->
                    outgoingList = list
                }

                hasFetchedOnce = true
                isLoading = false
            }
        }
    }

    LaunchedEffect(isExpanded) {
        if (isExpanded && !hasFetchedOnce && !isLoading) {
            refreshSharedServices()
        }
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(
            durationMillis = Motion.Duration.FAST,
            easing = Motion.EasingCurve.Standard
        ),
        label = "sharedAccordionChevron"
    )

    val filteredIncoming = remember(incomingList, searchQuery) {
        if (searchQuery.isBlank()) incomingList
        else incomingList.filter {
            it.issuer.contains(searchQuery, ignoreCase = true) ||
                it.accountName.contains(searchQuery, ignoreCase = true) ||
                it.ownerEmail.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredOutgoing = remember(outgoingList, searchQuery) {
        if (searchQuery.isBlank()) outgoingList
        else outgoingList.filter {
            it.issuer.contains(searchQuery, ignoreCase = true) ||
                it.accountName.contains(searchQuery, ignoreCase = true) ||
                it.guestEmail.contains(searchQuery, ignoreCase = true)
        }
    }

    val summaryText = when {
        !hasFetchedOnce -> stringResource(R.string.home_shared_accordion_summary_tap)
        incomingList.isEmpty() && outgoingList.isEmpty() -> stringResource(R.string.home_shared_accordion_summary_empty)
        else -> stringResource(R.string.home_shared_accordion_summary_counts, incomingList.size, outgoingList.size)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Cabecera compacta tipo tarjeta desplegable
        Surface(
            shape = RoundedCornerShape(Dimensions.CornerRadius.large),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = Dimensions.Elevation.cardDefault,
            shadowElevation = Dimensions.Elevation.cardDefault,
            border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
                .clickable {
                    appHaptics.click()
                    isExpanded = !isExpanded
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.Spacing.lg, vertical = Dimensions.Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.size(Dimensions.IconSize.large)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Dimensions.IconSize.medium)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(Dimensions.Spacing.md))

                    Column {
                        Text(
                            text = stringResource(R.string.home_shared_accordion_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isExpanded) {
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

                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(Dimensions.IconSize.medium)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                }
            }
        }

        // Contenido desplegable de servicios compartidos
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) + expandVertically(),
            exit = fadeOut(animationSpec = Motion.Spec.quickFadeSpec()) + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimensions.Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = UrgentRed,
                        modifier = Modifier.padding(horizontal = Dimensions.Spacing.xs)
                    )
                }

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Dimensions.Spacing.lg),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(Dimensions.IconSize.large))
                        }
                    }

                    needsGoogleSignIn -> {
                        Surface(
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(Dimensions.Spacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                            ) {
                                Text(
                                    text = stringResource(R.string.share_error_not_signed_in),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                AppAnimatedButton(
                                    text = stringResource(R.string.home_shared_connect_button),
                                    onClick = {
                                        refreshSharedServices()
                                        true
                                    }
                                )
                            }
                        }
                    }

                    filteredIncoming.isEmpty() && filteredOutgoing.isEmpty() -> {
                        Surface(
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.settings_shared_incoming_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(Dimensions.Spacing.md)
                            )
                        }
                    }

                    else -> {
                        if (filteredIncoming.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_shared_tab_incoming),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = Dimensions.Spacing.xs)
                            )
                            val nowMillis = currentSecondEpoch * 1000L
                            filteredIncoming.forEach { item ->
                                val cachedBatch = cachedBatchesMap[item.grantId]
                                val batchCode = cachedBatch?.getCodeAt(nowMillis)
                                val liveCodeResult = liveCodesMap[item.grantId]
                                val activeCode = batchCode ?: liveCodeResult?.code

                                IncomingSharedOtpCard(
                                    service = item,
                                    activeCode = activeCode,
                                    hideCodes = hideCodes,
                                    hasValidBatch = cachedBatch != null && !cachedBatch.isExpired(nowMillis),
                                    isItemLoading = loadingGrantId == item.grantId,
                                    onCopyCode = { code -> onCopyCode(code, item.issuer) },
                                    onRequestAction = {
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
                                                            SharedBatchKeyStore.removeBatch(context, item.grantId)
                                                            cachedBatchesMap.remove(item.grantId)
                                                            appHaptics.error()
                                                            errorMessage = context.getString(R.string.settings_shared_revoked_error)
                                                        }
                                                } else {
                                                    SharedServicesCloudClient.requestLiveCode(token, item.grantId)
                                                        .onSuccess { live ->
                                                            liveCodesMap[item.grantId] = live
                                                            appHaptics.success()
                                                        }
                                                        .onFailure {
                                                            liveCodesMap.remove(item.grantId)
                                                            appHaptics.error()
                                                            errorMessage = context.getString(R.string.settings_shared_revoked_error)
                                                        }
                                                }
                                                loadingGrantId = null
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        if (filteredOutgoing.isNotEmpty()) {
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
                                        errorMessage = null
                                        requestOAuthToken { token ->
                                            scope.launch {
                                                SharedServicesCloudClient.revokeGrant(token, grant.grantId)
                                                    .onSuccess {
                                                        outgoingList = outgoingList.filterNot { it.grantId == grant.grantId }
                                                        appHaptics.success()
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.share_revoke_success_msg),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                    .onFailure {
                                                        appHaptics.error()
                                                        errorMessage = context.getString(R.string.share_error_generic)
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
    }
}
