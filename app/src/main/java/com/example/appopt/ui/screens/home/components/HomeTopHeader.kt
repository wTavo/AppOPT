package com.example.appopt.ui.screens.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.screens.home.model.CloudSyncUiState
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Cabecera superior interactiva de la pantalla principal (Directiva 29).
 *
 * Alterna suavemente entre:
 * 1. Modo normal: Botón de búsqueda a la izquierda, título estilizado central con sincronización ([HomeHeaderTitle]), y alternador de visibilidad a la derecha.
 * 2. Modo búsqueda: Barra de texto en píldora con botón de limpieza y foco directo ([HomeSearchBar]).
 *
 * @param isSearchActive Indica si el campo de búsqueda está desplegado.
 * @param searchQuery Texto de búsqueda actual.
 * @param isHideCodesEnabled Estado del modo de privacidad para ocultar dígitos OTP.
 * @param cloudSyncState Estado visual de la sincronización en la nube ([CloudSyncUiState]).
 * @param isVaultSynced Indica si la bóveda local está sincronizada al 100% con la copia remota.
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
    modifier: Modifier = Modifier,
    isVaultSynced: Boolean = false
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

    Box(modifier = modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = {
                (fadeIn(animationSpec = Motion.Spec.quickFadeSpec()) togetherWith
                        fadeOut(animationSpec = Motion.Spec.quickFadeSpec()))
                    .using(SizeTransform(clip = false))
            },
            label = "headerSearchOverlayAnimation"
        ) { searchOpen ->
            if (searchOpen) {
                HomeSearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onCloseSearch = {
                        onSearchActiveChange(false)
                        onSearchQueryChange("")
                    }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .heightIn(min = Dimensions.ComponentHeight.buttonDefault)
                        .padding(vertical = Dimensions.Spacing.xs),
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
                    HomeHeaderTitle(
                        cloudSyncState = cloudSyncState,
                        isVaultSynced = isVaultSynced
                    )

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
                                    contentDescription = if (hideEnabled) {
                                        stringResource(R.string.action_show_codes)
                                    } else {
                                        stringResource(R.string.action_hide_codes)
                                    },
                                    tint = if (hideEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
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
