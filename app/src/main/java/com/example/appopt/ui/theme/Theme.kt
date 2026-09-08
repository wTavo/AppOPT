package com.example.appopt.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = BackgroundDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = AccentCyan,
    onSecondary = BackgroundDark,
    secondaryContainer = PrimaryContainerDark,
    onSecondaryContainer = OnPrimaryContainerDark,
    tertiary = AccentCyan,
    onTertiary = BackgroundDark,
    tertiaryContainer = PrimaryContainerDark,
    onTertiaryContainer = OnPrimaryContainerDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    error = UrgentRed,
    onError = Color.White,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = AccentCyan,
    onSecondary = Color.White,
    secondaryContainer = PrimaryContainerLight,
    onSecondaryContainer = OnPrimaryContainerLight,
    tertiary = AccentCyan,
    onTertiary = Color.White,
    tertiaryContainer = PrimaryContainerLight,
    onTertiaryContainer = OnPrimaryContainerLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    error = UrgentRed,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

/**
 * Tema principal de la aplicación con soporte para Material You (Dynamic Color) y temas Claro/Oscuro de alto contraste.
 *
 * @param darkTheme Determina si se aplica la paleta oscura (por defecto según el sistema operativo).
 * @param dynamicColor Habilita colores dinámicos del sistema (falso por defecto para garantizar contraste de seguridad).
 * @param content Contenido composable a envolver en el tema.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

/**
 * Colores de alto contraste adaptativos para los interruptores [androidx.compose.material3.Switch] de la aplicación.
 *
 * - En modo oscuro (*Dark Theme*): El botón deslizante apagado es blanco puro ([androidx.compose.ui.graphics.Color.White]) para máxima visibilidad.
 * - En modo claro (*Light Theme*): El botón deslizante apagado es gris pizarra de alto contraste para destacar claramente sobre superficies claras.
 */
@Composable
fun appSwitchColors(): androidx.compose.material3.SwitchColors {
    val isDark = MaterialTheme.colorScheme.surface == SurfaceDark || MaterialTheme.colorScheme.background == BackgroundDark
    return androidx.compose.material3.SwitchDefaults.colors(
        uncheckedThumbColor = if (isDark) androidx.compose.ui.graphics.Color.White else TextSecondaryLight,
        uncheckedTrackColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else SurfaceVariantLight,
        uncheckedBorderColor = if (isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f) else OutlineLight,
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        checkedBorderColor = androidx.compose.ui.graphics.Color.Transparent
    )
}
