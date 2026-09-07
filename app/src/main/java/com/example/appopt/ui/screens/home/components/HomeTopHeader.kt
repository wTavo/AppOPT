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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.appopt.ui.screens.home.model.CloudSyncUiState
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.SafeGreen
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Cabecera superior interactiva de la pantalla principal.
 *
 * Alterna suavemente entre:
 * 1. Modo normal: Botón de búsqueda a la izquierda, título de alto impacto estilizado al centro con indicador animado de sincronización en la nube, y alternador de visibilidad a la derecha.
 * 2. Modo búsqueda: Barra de texto en píldora con botón de limpieza y foco directo.
 *
 * Estados visuales del título central:
 * - [CloudSyncUiState.IDLE]: Muestra el título estándar "Authenticator".
 * - [CloudSyncUiState.SYNCING]: Muestra icono de sincronización giratorio y texto descriptivo de carga.
 * - [CloudSyncUiState.SUCCESS]: Muestra contorno verde, icono de verificación y texto "Authenticator" con animación de brillo verde (*shine*).
 * - [CloudSyncUiState.ERROR]: Muestra contorno rojo e icono de advertencia de error.
 *
 * @param isSearchActive Indica si el campo de búsqueda está desplegado.
 * @param searchQuery Texto de búsqueda actual.
 * @param isHideCodesEnabled Estado del modo de privacidad para ocultar dígitos OTP.
 * @param cloudSyncState Estado visual de la sincronización en la nube ([CloudSyncUiState]).
 * @param onSearchActiveChange Callback invocado al activar/desactivar el modo búsqueda.
 * @param onSearchQueryChange Callback invocado al escribir en el campo de búsqueda.
 * @param onToggleHideCodes Callback invocado al alternar el botón de privacidad de códigos.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun HomeTopHeader(
    isSearchActive: Boolean,
    searchQuery: String,
    isHideCodesEnabled: Boolean,
    cloudSyncState: CloudSyncUiState,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleHideCodes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()

    // Respuesta háptica semántica al cambiar a estados terminales de sincronización
    LaunchedEffect(cloudSyncState) {
        when (cloudSyncState) {
            CloudSyncUiState.SUCCESS -> appHaptics.success()
            CloudSyncUiState.ERROR -> appHaptics.error()
            else -> {}
        }
    }

    // Animación infinita para rotación de sincronización y barrido de brillo (*shine*)
    val infiniteTransition = rememberInfiniteTransition(label = "headerSyncInfiniteTransition")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "syncRotation"
    )

    val shineOffset by infiniteTransition.animateFloat(
        initialValue = -150f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shineOffsetAnimation"
    )

    val successShineBrush = Brush.linearGradient(
        colors = listOf(
            SafeGreen,
            Color(0xFF86EFAC),
            SafeGreen
        ),
        start = Offset(shineOffset, 0f),
        end = Offset(shineOffset + 120f, 0f)
    )

    val targetBorderColor = when (cloudSyncState) {
        CloudSyncUiState.IDLE -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        CloudSyncUiState.SYNCING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        CloudSyncUiState.SUCCESS -> SafeGreen
        CloudSyncUiState.ERROR -> MaterialTheme.colorScheme.error
    }

    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = Motion.Spec.buttonColorSpec(),
        label = "headerBorderColorAnimation"
    )

    Box(modifier = modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = {
                fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) togetherWith
                        fadeOut(animationSpec = Motion.Spec.quickFadeSpec())
            },
            label = "headerSearchOverlayAnimation"
        ) { searchOpen ->
            if (searchOpen) {
                Surface(
                    shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = Dimensions.Elevation.cardDefault,
                    shadowElevation = Dimensions.Elevation.cardDefault,
                    border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
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
                            onValueChange = onSearchQueryChange,
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
                                onSearchActiveChange(false)
                                onSearchQueryChange("")
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
                                onSearchActiveChange(true)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.action_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 2. Título Central con Diseño Estilizado e Indicador de Sincronización
                    Surface(
                        shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = Dimensions.Elevation.cardDefault,
                        shadowElevation = Dimensions.Elevation.cardDefault,
                        border = BorderStroke(Dimensions.Stroke.thin, animatedBorderColor),
                        modifier = Modifier.wrapContentWidth()
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
                                    horizontal = Dimensions.Spacing.lg,
                                    vertical = Dimensions.Spacing.sm
                                )
                            ) {
                                when (syncState) {
                                    CloudSyncUiState.IDLE -> {
                                        Text(
                                            text = stringResource(R.string.home_title),
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontWeight = FontWeight.ExtraBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    CloudSyncUiState.SYNCING -> {
                                        Icon(
                                            imageVector = Icons.Filled.Sync,
                                            contentDescription = stringResource(R.string.home_sync_syncing),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(Dimensions.IconSize.medium)
                                                .graphicsLayer { rotationZ = rotationAngle }
                                        )
                                        Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                                        Text(
                                            text = stringResource(R.string.home_sync_syncing),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    CloudSyncUiState.SUCCESS -> {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = stringResource(R.string.home_sync_success),
                                            tint = SafeGreen,
                                            modifier = Modifier.size(Dimensions.IconSize.medium)
                                        )
                                        Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                                        Text(
                                            text = stringResource(R.string.home_title),
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                brush = successShineBrush
                                            )
                                        )
                                    }
                                    CloudSyncUiState.ERROR -> {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.home_sync_error),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(Dimensions.IconSize.medium)
                                        )
                                        Spacer(modifier = Modifier.width(Dimensions.Spacing.sm))
                                        Text(
                                            text = stringResource(R.string.home_sync_error),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
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
                                onToggleHideCodes()
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
