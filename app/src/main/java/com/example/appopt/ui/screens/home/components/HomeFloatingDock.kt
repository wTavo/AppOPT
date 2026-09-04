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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.appopt.R
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Barra dock de control flotante ergonómica e interactiva para la pantalla principal.
 *
 * Contiene accesos rápidos con respuesta háptica para:
 * 1. Bloquear bóveda.
 * 2. Gestor de contraseñas.
 * 3. Botón Hero (+) para agregar nuevas cuentas OTP.
 * 4. Navegación hacia Ajustes.
 *
 * @param onLockVault Callback invocado para bloquear manualmente la aplicación.
 * @param onAddAccountClick Callback invocado al presionar el botón Hero (+) de adición.
 * @param onNavigateToSettings Callback invocado al presionar el botón de ajustes.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun HomeFloatingDock(
    onLockVault: () -> Unit,
    onAddAccountClick: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()
    val context: Context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(Dimensions.CornerRadius.pill),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Dimensions.Elevation.modal,
        shadowElevation = Dimensions.Elevation.cardDragging,
        border = BorderStroke(Dimensions.Stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = modifier.wrapContentWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimensions.Spacing.lg, vertical = Dimensions.Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Bloquear bóveda manualmente
            IconButton(
                onClick = {
                    appHaptics.click()
                    onLockVault()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.home_lock_vault),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 2. Gestor de Contraseñas (Próximamente)
            val passwordsComingSoonText = stringResource(R.string.passwords_coming_soon)
            IconButton(
                onClick = {
                    appHaptics.click()
                    Toast.makeText(
                        context,
                        passwordsComingSoonText,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_key),
                    contentDescription = stringResource(R.string.passwords_nav_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 3. Hero (+) FAB para agregar cuentas
            FloatingActionButton(
                onClick = {
                    appHaptics.click()
                    onAddAccountClick()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = Dimensions.Elevation.cardDefault,
                    pressedElevation = Dimensions.Elevation.cardDragging
                ),
                modifier = Modifier.size(Dimensions.ComponentSize.heroFab)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.home_add_account),
                    modifier = Modifier.size(Dimensions.IconSize.large)
                )
            }

            // 4. Ajustes y Configuración
            IconButton(
                onClick = {
                    appHaptics.click()
                    onNavigateToSettings()
                }
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
