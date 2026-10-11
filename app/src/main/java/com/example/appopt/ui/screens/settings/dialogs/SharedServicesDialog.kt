package com.example.appopt.ui.screens.settings.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.data.cloud.LiveSharedCodeResult
import com.example.appopt.data.cloud.SharedBatchKeyStore
import com.example.appopt.data.cloud.SharedServicesCloudClient
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.domain.model.OutgoingShareGrant
import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.domain.model.SharedOtpBatch
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.components.AppDialogActionButtons
import com.example.appopt.ui.components.AppModalDialog
import com.example.appopt.ui.components.CircularTimeProgress
import com.example.appopt.ui.components.ServiceBrandAvatar
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.rememberAppHaptics
import com.example.appopt.util.AccessibilityUtils
import com.example.appopt.util.TotpClock
import kotlinx.coroutines.launch

/**
 * Pestañas de navegación interna del diálogo de servicios compartidos.
 */
private enum class SharedServicesTab {
    INCOMING,
    OUTGOING
}

/**
 * Diálogo modal unificado para consultar códigos compartidos por otros usuarios (en línea o por lote temporal)
 * y revocar al instante los accesos otorgados a terceros (Directivas 11, 14, 29 y 31).
 *
 * @param accessToken Token OAuth2 activo de Google Identity Services.
 * @param onDismiss Callback invocado para cerrar el diálogo modal.
 */
@Composable
fun SharedServicesDialog(
    accessToken: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appHaptics = rememberAppHaptics()
    val clipboardManager = remember { AuthenticatorApp.instance.secureClipboardManager }
    val currentSecondEpoch by TotpClock.currentSecondEpoch.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(SharedServicesTab.INCOMING) }
    var isLoading by remember { mutableStateOf(true) }
    var incomingList by remember { mutableStateOf<List<IncomingSharedService>>(emptyList()) }
    var outgoingList by remember { mutableStateOf<List<OutgoingShareGrant>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val liveCodesMap = remember { mutableStateMapOf<String, LiveSharedCodeResult>() }
    val cachedBatchesMap = remember { mutableStateMapOf<String, SharedOtpBatch>() }
    var loadingGrantId by remember { mutableStateOf<String?>(null) }

    fun loadAllSharedData() {
        scope.launch {
            isLoading = true
            errorMessage = null
            val incomingRes = SharedServicesCloudClient.fetchIncomingServices(accessToken)
            val outgoingRes = SharedServicesCloudClient.fetchOutgoingGrants(accessToken)

            incomingRes.onSuccess { list ->
                incomingList = list
                list.forEach { service ->
                    if (service.accessMode == ShareAccessMode.OFFLINE_BATCH) {
                        SharedBatchKeyStore.getValidBatch(context, service.grantId)?.let { batch ->
                            cachedBatchesMap[service.grantId] = batch
                        }
                    }
                }
            }.onFailure {
                errorMessage = context.getString(R.string.share_error_generic)
            }

            outgoingRes.onSuccess { list ->
                outgoingList = list
            }
            isLoading = false
        }
    }

    LaunchedEffect(accessToken) {
        loadAllSharedData()
    }

    AppModalDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
        ) {
            // 1. Cabecera fija superior
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.settings_shared_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = {
                        appHaptics.click()
                        loadAllSharedData()
                    },
                    enabled = !isLoading,
                    modifier = Modifier.size(Dimensions.ComponentSize.actionIconButton)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.action_refresh),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Selector de pestañas (Compartidos conmigo / Compartidos por mí)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
            ) {
                FilterChip(
                    selected = selectedTab == SharedServicesTab.INCOMING,
                    onClick = {
                        appHaptics.click()
                        selectedTab = SharedServicesTab.INCOMING
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.settings_shared_tab_incoming),
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = selectedTab == SharedServicesTab.OUTGOING,
                    onClick = {
                        appHaptics.click()
                        selectedTab = SharedServicesTab.OUTGOING
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.settings_shared_tab_outgoing),
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // 2. Cuerpo central scrolleable aislado
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
            ) {
                AnimatedVisibility(
                    visible = !errorMessage.isNullOrBlank(),
                    enter = fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) + expandVertically(),
                    exit = fadeOut(animationSpec = Motion.Spec.quickFadeSpec()) + shrinkVertically()
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                AnimatedContent(
                    targetState = Pair(isLoading, selectedTab),
                    transitionSpec = { Motion.Spec.dialogStepContentTransform() },
                    label = "sharedServicesTabTransition"
                ) { (loading, tab) ->
                    if (loading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Dimensions.Spacing.xl),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Dimensions.IconSize.large),
                                strokeWidth = Dimensions.Stroke.regular
                            )
                        }
                    } else if (tab == SharedServicesTab.INCOMING) {
                        if (incomingList.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_shared_incoming_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)) {
                                incomingList.forEach { service ->
                                    val batch = cachedBatchesMap[service.grantId]
                                    val activeCode = if (service.accessMode == ShareAccessMode.OFFLINE_BATCH) {
                                        batch?.getCodeAt(currentSecondEpoch * 1000L)
                                    } else {
                                        liveCodesMap[service.grantId]?.code
                                    }

                                    IncomingServiceItemCard(
                                        service = service,
                                        activeCode = activeCode,
                                        hasValidBatch = batch != null && !batch.isExpired(currentSecondEpoch * 1000L),
                                        isItemLoading = loadingGrantId == service.grantId,
                                        onCopyCode = { code ->
                                            appHaptics.copy()
                                            clipboardManager.copyToClipboard(
                                                label = service.issuer,
                                                text = code,
                                                autoClearSeconds = SecurityConfig.CLIPBOARD_OTP_AUTO_CLEAR_SECONDS
                                            )
                                        },
                                        onRequestAction = {
                                            scope.launch {
                                                loadingGrantId = service.grantId
                                                errorMessage = null
                                                if (service.accessMode == ShareAccessMode.ONLINE_ONLY) {
                                                    SharedServicesCloudClient.requestLiveCode(accessToken, service.grantId)
                                                        .onSuccess { res ->
                                                            appHaptics.success()
                                                            liveCodesMap[service.grantId] = res
                                                        }
                                                        .onFailure {
                                                            appHaptics.error()
                                                            errorMessage = context.getString(R.string.settings_shared_revoked_error)
                                                        }
                                                } else {
                                                    SharedServicesCloudClient.downloadPrecomputedBatch(accessToken, service.grantId)
                                                        .onSuccess { newBatch ->
                                                            appHaptics.success()
                                                            SharedBatchKeyStore.saveBatch(context, newBatch)
                                                            cachedBatchesMap[service.grantId] = newBatch
                                                        }
                                                        .onFailure {
                                                            appHaptics.error()
                                                            errorMessage = context.getString(R.string.settings_shared_revoked_error)
                                                        }
                                                }
                                                loadingGrantId = null
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        if (outgoingList.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_shared_outgoing_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)) {
                                outgoingList.forEach { grant ->
                                    OutgoingGrantItemCard(
                                        grant = grant,
                                        onRevoke = {
                                            scope.launch {
                                                SharedServicesCloudClient.revokeGrant(accessToken, grant.grantId)
                                                    .onSuccess {
                                                        appHaptics.success()
                                                        outgoingList = outgoingList.filterNot { it.grantId == grant.grantId }
                                                    }
                                                    .onFailure {
                                                        appHaptics.error()
                                                        errorMessage = context.getString(R.string.share_error_generic)
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

            // 3. Pie fijo inferior
            AppDialogActionButtons(onDismiss = onDismiss)
        }
    }
}

/**
 * Tarjeta individual de un servicio compartido con el usuario actual (Zero-Secret).
 */
@Composable
private fun IncomingServiceItemCard(
    service: IncomingSharedService,
    activeCode: String?,
    hasValidBatch: Boolean,
    isItemLoading: Boolean,
    onCopyCode: (String) -> Unit,
    onRequestAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
            ) {
                ServiceBrandAvatar(issuer = service.issuer, size = Dimensions.IconSize.large)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = service.issuer,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (service.accountName.isNotBlank()) {
                        Text(
                            text = service.accountName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_shared_owner_format, service.ownerEmail),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!activeCode.isNullOrBlank()) {
                val formattedCode = if (activeCode.length == 6) {
                    "${activeCode.substring(0, 3)} ${activeCode.substring(3, 6)}"
                } else {
                    activeCode
                }
                val spokenOtp = AccessibilityUtils.toAccessibleSpokenOtp(activeCode)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                        .clickable { onCopyCode(activeCode) }
                        .padding(vertical = Dimensions.Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics { contentDescription = spokenOtp }
                    ) {
                        Text(
                            text = formattedCode,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.IconSize.small)
                        )
                    }
                    CircularTimeProgress(
                        period = service.period,
                        modifier = Modifier.size(Dimensions.IconSize.large)
                    )
                }
            }

            val buttonLabelRes = when {
                service.accessMode == ShareAccessMode.ONLINE_ONLY -> R.string.settings_shared_tap_to_fetch
                hasValidBatch -> R.string.settings_shared_batch_ready
                else -> R.string.settings_shared_download_batch_action
            }

            OutlinedButton(
                onClick = onRequestAction,
                enabled = !isItemLoading,
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(buttonLabelRes),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * Tarjeta individual de un permiso otorgado por el usuario propietario con botón de revocación instantánea.
 */
@Composable
private fun OutgoingGrantItemCard(
    grant: OutgoingShareGrant,
    onRevoke: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = grant.issuer,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = grant.guestEmail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val modeBadge = if (grant.accessMode == ShareAccessMode.OFFLINE_BATCH) {
                    stringResource(R.string.share_mode_badge_batch, grant.batchHours)
                } else {
                    stringResource(R.string.share_mode_badge_online)
                }
                Text(
                    text = modeBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            TextButton(onClick = onRevoke) {
                Text(
                    text = stringResource(R.string.share_revoke_action),
                    style = MaterialTheme.typography.labelLarge,
                    color = UrgentRed
                )
            }
        }
    }
}
