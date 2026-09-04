package com.example.appopt.ui.screens.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Cabecera superior interactiva de la pantalla principal.
 *
 * Alterna suavemente entre:
 * 1. Modo normal: Botón de búsqueda a la izquierda, título de alto impacto estilizado al centro, y alternador de visibilidad a la derecha.
 * 2. Modo búsqueda: Barra de texto en píldora con botón de limpieza y foco directo.
 *
 * @param isSearchActive Indica si el campo de búsqueda está desplegado.
 * @param searchQuery Texto de búsqueda actual.
 * @param isHideCodesEnabled Estado del modo de privacidad para ocultar dígitos OTP.
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
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleHideCodes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()

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
                    border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
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

                    // 2. Título Central con Diseño Estilizado
                    Surface(
                        shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = Dimensions.Elevation.cardDefault,
                        shadowElevation = Dimensions.Elevation.cardDefault,
                        border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.home_title),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(
                                horizontal = Dimensions.Spacing.xl,
                                vertical = Dimensions.Spacing.sm
                            )
                        )
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
