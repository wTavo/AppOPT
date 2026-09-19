package com.example.appopt.ui.screens.home.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.example.appopt.ui.theme.Motion

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import com.example.appopt.ui.navigation.NavigationOriginTracker

/**
 * Barra dock de control flotante ergonómica e interactiva para la pantalla principal.
 *
 * Contiene accesos rápidos con respuesta háptica y captura dinámica de coordenadas para:
 * 1. Bloquear bóveda.
 * 2. Gestor de contraseñas.
 * 3. Botón Hero (+) para desplegar el menú de adición de cuentas (con rotación animada a 'x').
 * 4. Papelera de reciclaje y servicios eliminados recientemente (con badge).
 * 5. Navegación hacia Ajustes.
 *
 * @param onLockVault Callback invocado para bloquear manualmente la aplicación.
 * @param onAddAccountClick Callback invocado al presionar el botón Hero (+) de adición.
 * @param onNavigateToRecentlyDeleted Callback invocado al presionar la papelera de reciclaje.
 * @param onNavigateToSettings Callback invocado al presionar el botón de ajustes.
 * @param isAddMenuOpen Indica si el menú de adición Speed Dial está abierto para rotar el botón (+).
 * @param deletedAccountsCount Cantidad de servicios en papelera de reciclaje para mostrar badge.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun HomeFloatingDock(
    onLockVault: () -> Unit,
    onAddAccountClick: () -> Unit,
    onNavigateToRecentlyDeleted: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    isAddMenuOpen: Boolean = false,
    deletedAccountsCount: Int = 0
) {
    val appHaptics = rememberAppHaptics()
    val context: Context = LocalContext.current

    var fabCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var trashCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var settingsCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val fabRotationAngle by animateFloatAsState(
        targetValue = if (isAddMenuOpen) 45f else 0f,
        animationSpec = tween(
            durationMillis = Motion.Duration.MEDIUM,
            easing = Motion.EasingCurve.Emphasized
        ),
        label = "fab_rotation"
    )

    val dockRestAlpha by animateFloatAsState(
        targetValue = if (isAddMenuOpen) 0.25f else 1.0f,
        animationSpec = tween(
            durationMillis = Motion.Duration.FAST,
            easing = Motion.EasingCurve.Standard
        ),
        label = "dock_rest_alpha"
    )

    Surface(
        shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isAddMenuOpen) 0.60f else 1.0f),
        tonalElevation = if (isAddMenuOpen) Dimensions.Elevation.none else Dimensions.Elevation.cardDefault,
        shadowElevation = if (isAddMenuOpen) Dimensions.Elevation.none else Dimensions.Elevation.cardDefault,
        border = BorderStroke(
            Dimensions.Stroke.thin,
            MaterialTheme.colorScheme.outline.copy(alpha = if (isAddMenuOpen) 0.05f else 0.15f)
        ),
        modifier = modifier.wrapContentWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimensions.Spacing.md, vertical = Dimensions.Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Bloquear bóveda manualmente (atenuado si el menú está abierto)
            IconButton(
                onClick = {
                    if (isAddMenuOpen) {
                        onAddAccountClick()
                    } else {
                        appHaptics.click()
                        onLockVault()
                    }
                },
                modifier = Modifier.graphicsLayer { alpha = dockRestAlpha }
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.home_lock_vault),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 2. Gestor de Contraseñas (Próximamente, atenuado si el menú está abierto)
            val passwordsComingSoonText = stringResource(R.string.passwords_coming_soon)
            IconButton(
                onClick = {
                    if (isAddMenuOpen) {
                        onAddAccountClick()
                    } else {
                        appHaptics.click()
                        Toast.makeText(
                            context,
                            passwordsComingSoonText,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.graphicsLayer { alpha = dockRestAlpha }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_key),
                    contentDescription = stringResource(R.string.passwords_nav_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 3. Hero (+) / (X) FAB: 100% brillante y elevado en primer plano
            FloatingActionButton(
                onClick = {
                    NavigationOriginTracker.updateModalOrigin(fabCoordinates)
                    appHaptics.click()
                    onAddAccountClick()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = Dimensions.Elevation.none,
                    pressedElevation = Dimensions.Elevation.cardDefault
                ),
                modifier = Modifier
                    .size(Dimensions.ComponentSize.heroFab)
                    .onPlaced { fabCoordinates = it }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(if (isAddMenuOpen) R.string.action_close else R.string.home_add_account),
                    modifier = Modifier
                        .size(Dimensions.IconSize.large)
                        .graphicsLayer { rotationZ = fabRotationAngle }
                )
            }

            // 4. Papelera de reciclaje (atenuado si el menú está abierto)
            IconButton(
                onClick = {
                    if (isAddMenuOpen) {
                        onAddAccountClick()
                    } else {
                        NavigationOriginTracker.updateRecentlyDeletedOrigin(trashCoordinates)
                        appHaptics.click()
                        onNavigateToRecentlyDeleted()
                    }
                },
                modifier = Modifier
                    .onPlaced { trashCoordinates = it }
                    .graphicsLayer { alpha = dockRestAlpha }
            ) {
                BadgedBox(
                    badge = {
                        if (deletedAccountsCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ) {
                                Text(
                                    text = deletedAccountsCount.toString(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.trash_nav_title),
                        tint = if (deletedAccountsCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 5. Ajustes y Configuración (atenuado si el menú está abierto)
            IconButton(
                onClick = {
                    if (isAddMenuOpen) {
                        onAddAccountClick()
                    } else {
                        NavigationOriginTracker.updateSettingsOrigin(settingsCoordinates)
                        appHaptics.click()
                        onNavigateToSettings()
                    }
                },
                modifier = Modifier
                    .onPlaced { settingsCoordinates = it }
                    .graphicsLayer { alpha = dockRestAlpha }
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
