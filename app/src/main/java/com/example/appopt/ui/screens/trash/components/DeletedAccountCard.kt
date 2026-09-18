package com.example.appopt.ui.screens.trash.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.security.SecurityConfig
import com.example.appopt.ui.components.AppAnimatedButton
import com.example.appopt.ui.components.ServiceBrandAvatar
import com.example.appopt.ui.navigation.NavigationOriginTracker
import com.example.appopt.ui.theme.Dimensions

/**
 * Tarjeta individual representativa de una cuenta en la papelera de reciclaje.
 *
 * Muestra el emisor, nombre de cuenta, avatar de marca, conteo regresivo de retención de 30 días
 * y acciones para restaurar o eliminar definitivamente.
 *
 * @param account Modelo de la cuenta eliminada.
 * @param onRestore Callback para restaurar la cuenta.
 * @param onPermanentDelete Callback para solicitar la eliminación definitiva.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DeletedAccountCard(
    account: TotpAccount,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = remember { System.currentTimeMillis() }
    val deletedTime = account.deletedAt ?: account.updatedAt
    val elapsedMillis = (now - deletedTime).coerceAtLeast(0L)
    val remainingMillis = (SecurityConfig.TRASH_RETENTION_MILLIS - elapsedMillis).coerceAtLeast(0L)
    val remainingDays = (remainingMillis / (24L * 60L * 60L * 1000L)).toInt()

    val countdownText = if (remainingDays > 0) {
        stringResource(R.string.trash_days_remaining, remainingDays)
    } else {
        stringResource(R.string.trash_hours_remaining)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.cardDefault),
        border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ServiceBrandAvatar(
                    issuer = account.issuer,
                    size = Dimensions.IconSize.hero
                )

                Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.issuer.ifEmpty { stringResource(R.string.home_default_issuer) },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (account.accountName.isNotBlank()) {
                        Text(
                            text = account.accountName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(Dimensions.Spacing.xs))
                    Text(
                        text = countdownText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            var deleteCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

            // Acciones: Eliminar definitivamente y Restaurar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        NavigationOriginTracker.updateFromCoordinates(deleteCoordinates)
                        onPermanentDelete()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                        .onPlaced { deleteCoordinates = it },
                    contentPadding = PaddingValues(horizontal = Dimensions.Spacing.xs, vertical = Dimensions.Spacing.xs),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(
                        Dimensions.Stroke.thin,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.trash_permanent_delete_button),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }

                AppAnimatedButton(
                    text = stringResource(R.string.trash_restore_button),
                    height = Dimensions.ComponentHeight.buttonDefault,
                    onClick = {
                        onRestore()
                        true
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
