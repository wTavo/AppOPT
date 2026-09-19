package com.example.appopt.ui.screens.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.appopt.R
import com.example.appopt.ui.screens.home.CloudSyncUiState
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.WarningYellow
import com.example.appopt.ui.theme.WarningYellowLight

/**
 * Título central de la pantalla principal con indicador reactivo de sincronización en la nube (Directivas 27 y 29).
 *
 * @param cloudSyncState Estado visual de la sincronización en Google Drive.
 * @param isVaultSynced Indica si la bóveda local está sincronizada al 100%.
 * @param modifier Modificador de diseño.
 */
@Composable
fun HomeHeaderTitle(
    cloudSyncState: CloudSyncUiState,
    isVaultSynced: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "headerSyncInfiniteTransition")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Motion.Duration.SYNC_ROTATION, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "syncRotation"
    )

    val shineOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Motion.Duration.AMBIENT_SHINE_LOOP, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shineOffsetAnimation"
    )

    val successShineBrush = Brush.linearGradient(
        colors = listOf(SafeGreen, Color(0xFF86EFAC), SafeGreen),
        start = Offset(shineOffset, 0f),
        end = Offset(shineOffset + 180f, 0f)
    )

    val pendingShineBrush = Brush.linearGradient(
        colors = listOf(WarningYellow, WarningYellowLight, WarningYellow),
        start = Offset(shineOffset, 0f),
        end = Offset(shineOffset + 180f, 0f)
    )

    val targetBorderColor = when (cloudSyncState) {
        CloudSyncUiState.PENDING -> WarningYellow
        CloudSyncUiState.SYNCING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        CloudSyncUiState.ERROR -> MaterialTheme.colorScheme.error
        CloudSyncUiState.SUCCESS -> SafeGreen
        CloudSyncUiState.IDLE -> if (isVaultSynced) SafeGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    }

    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = Motion.Spec.buttonColorSpec(),
        label = "headerBorderColorAnimation"
    )

    Surface(
        shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Dimensions.Elevation.cardDefault,
        shadowElevation = Dimensions.Elevation.cardDefault,
        border = BorderStroke(Dimensions.Stroke.thin, animatedBorderColor),
        modifier = modifier.wrapContentWidth()
    ) {
        AnimatedContent(
            targetState = cloudSyncState,
            transitionSpec = {
                fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) togetherWith
                        fadeOut(animationSpec = Motion.Spec.quickFadeSpec())
            },
            label = "headerTitleSyncAnimation"
        ) { syncState ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(
                    horizontal = Dimensions.Spacing.xl,
                    vertical = Dimensions.Spacing.sm
                )
            ) {
                when (syncState) {
                    CloudSyncUiState.IDLE -> {
                        Text(
                            text = stringResource(R.string.home_title),
                            style = if (isVaultSynced) {
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    brush = successShineBrush
                                )
                            } else {
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold
                                )
                            },
                            color = if (isVaultSynced) Color.Unspecified else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    CloudSyncUiState.PENDING -> {
                        Text(
                            text = stringResource(R.string.home_title),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                brush = pendingShineBrush
                            ),
                            color = Color.Unspecified
                        )
                    }
                    CloudSyncUiState.SYNCING -> {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = stringResource(R.string.home_sync_syncing),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(Dimensions.IconSize.large)
                                .graphicsLayer { rotationZ = rotationAngle }
                        )
                    }
                    CloudSyncUiState.SUCCESS -> {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.home_sync_success),
                            tint = SafeGreen,
                            modifier = Modifier.size(Dimensions.IconSize.large)
                        )
                    }
                    CloudSyncUiState.ERROR -> {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.home_sync_error),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(Dimensions.IconSize.large)
                        )
                    }
                }
            }
        }
    }
}
