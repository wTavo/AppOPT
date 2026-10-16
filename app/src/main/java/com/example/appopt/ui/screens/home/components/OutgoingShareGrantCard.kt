package com.example.appopt.ui.screens.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.appopt.R
import com.example.appopt.domain.model.OutgoingShareGrant
import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.domain.model.ShareGrantStatus
import com.example.appopt.ui.components.ServiceBrandAvatar
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.UrgentRed
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Tarjeta visual para un servicio que el usuario propietario ha compartido con un tercero,
 * mostrando si la invitación sigue pendiente o activa y permitiendo revocar en tiempo real (Directiva 31).
 *
 * @param grant Permiso o invitación otorgado por el propietario.
 * @param onRevoke Callback invocado al presionar el botón «Revocar».
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun OutgoingShareGrantCard(
    grant: OutgoingShareGrant,
    onRevoke: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.cardDefault),
        border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                ServiceBrandAvatar(
                    issuer = grant.issuer,
                    size = Dimensions.IconSize.hero
                )
                Spacer(modifier = Modifier.width(Dimensions.Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = grant.issuer.ifEmpty { stringResource(R.string.home_default_issuer) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (grant.accountName.isNotBlank()) {
                        Text(
                            text = grant.accountName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Mail,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.IconSize.small)
                        )
                        Spacer(modifier = Modifier.width(Dimensions.Spacing.xs))
                        Text(
                            text = grant.guestEmail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                    val modeBadge = when (grant.accessMode) {
                        ShareAccessMode.OFFLINE_BATCH ->
                            stringResource(R.string.share_mode_badge_batch, grant.batchHours)
                        ShareAccessMode.SINGLE_USE ->
                            stringResource(R.string.share_mode_badge_single_use)
                        ShareAccessMode.ONLINE_ONLY ->
                            stringResource(R.string.share_mode_badge_online)
                    }
                    val isPending = grant.status == ShareGrantStatus.PENDING
                    val statusBadge = if (isPending) {
                        stringResource(R.string.share_status_pending_badge, modeBadge)
                    } else {
                        stringResource(R.string.share_status_accepted_badge, modeBadge)
                    }
                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                    Text(
                        text = statusBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPending) MaterialTheme.colorScheme.tertiary else SafeGreen
                    )
                }
            }

            Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))

            TextButton(
                onClick = {
                    appHaptics.click()
                    onRevoke()
                }
            ) {
                Text(
                    text = stringResource(R.string.share_revoke_action),
                    style = MaterialTheme.typography.labelLarge,
                    color = UrgentRed
                )
            }
        }
    }
}
