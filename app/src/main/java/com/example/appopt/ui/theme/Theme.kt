package com.example.appopt.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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

/**
 * Construye el esquema de colores oscuros adaptado al color de acento seleccionado.
 *
 * @param accent Paleta de acento seleccionada.
 * @return [ColorScheme] de Material 3 para tema oscuro.
 */
fun buildDarkColorScheme(accent: AccentColor = AccentColor.BLUE): ColorScheme = darkColorScheme(
    primary = accent.primaryDark,
    onPrimary = Color.White,
    primaryContainer = accent.primaryContainerDark,
    onPrimaryContainer = accent.onPrimaryContainerDark,
    secondary = accent.secondaryDark,
    onSecondary = Color.White,
    secondaryContainer = accent.primaryContainerDark,
    onSecondaryContainer = accent.onPrimaryContainerDark,
    tertiary = accent.secondaryDark,
    onTertiary = Color.White,
    tertiaryContainer = accent.primaryContainerDark,
    onTertiaryContainer = accent.onPrimaryContainerDark,
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

/**
 * Construye el esquema de colores claros adaptado al color de acento seleccionado.
 *
 * @param accent Paleta de acento seleccionada.
 * @return [ColorScheme] de Material 3 para tema claro.
 */
fun buildLightColorScheme(accent: AccentColor = AccentColor.BLUE): ColorScheme = lightColorScheme(
    primary = accent.primaryLight,
    onPrimary = Color.White,
    primaryContainer = accent.primaryContainerLight,
    onPrimaryContainer = accent.onPrimaryContainerLight,
    secondary = accent.secondaryLight,
    onSecondary = Color.White,
    secondaryContainer = accent.primaryContainerLight,
    onSecondaryContainer = accent.onPrimaryContainerLight,
    tertiary = accent.secondaryLight,
    onTertiary = Color.White,
    tertiaryContainer = accent.primaryContainerLight,
    onTertiaryContainer = accent.onPrimaryContainerLight,
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

val DarkColorScheme = buildDarkColorScheme(AccentColor.BLUE)

private val LightColorScheme = buildLightColorScheme(AccentColor.BLUE)

/**
 * Construye el esquema de colores oscuros adaptando dinámicamente los acentos desde Material You
 * pero preservando inmutables los fondos y superficies azabache de alto contraste de la aplicación.
 *
 * @param context Contexto de Android para consultar el motor dinámico del sistema.
 * @return [ColorScheme] de Material 3 con acentos dinámicos y superficies de seguridad intactas.
 */
fun buildDynamicDarkColorScheme(context: Context): ColorScheme {
    val dynamic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        DarkColorScheme
    }
    return darkColorScheme(
        primary = dynamic.primary,
        onPrimary = dynamic.onPrimary,
        primaryContainer = dynamic.primaryContainer,
        onPrimaryContainer = dynamic.onPrimaryContainer,
        secondary = dynamic.secondary,
        onSecondary = dynamic.onSecondary,
        secondaryContainer = dynamic.secondaryContainer,
        onSecondaryContainer = dynamic.onSecondaryContainer,
        tertiary = dynamic.tertiary,
        onTertiary = dynamic.onTertiary,
        tertiaryContainer = dynamic.tertiaryContainer,
        onTertiaryContainer = dynamic.onTertiaryContainer,
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
}

/**
 * Construye el esquema de colores claros adaptando dinámicamente los acentos desde Material You
 * pero preservando inmutables los fondos y superficies blanco y hielo de alto contraste de la aplicación.
 *
 * @param context Contexto de Android para consultar el motor dinámico del sistema.
 * @return [ColorScheme] de Material 3 con acentos dinámicos y superficies de seguridad intactas.
 */
fun buildDynamicLightColorScheme(context: Context): ColorScheme {
    val dynamic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicLightColorScheme(context)
    } else {
        LightColorScheme
    }
    return lightColorScheme(
        primary = dynamic.primary,
        onPrimary = dynamic.onPrimary,
        primaryContainer = dynamic.primaryContainer,
        onPrimaryContainer = dynamic.onPrimaryContainer,
        secondary = dynamic.secondary,
        onSecondary = dynamic.onSecondary,
        secondaryContainer = dynamic.secondaryContainer,
        onSecondaryContainer = dynamic.onSecondaryContainer,
        tertiary = dynamic.tertiary,
        onTertiary = dynamic.onTertiary,
        tertiaryContainer = dynamic.tertiaryContainer,
        onTertiaryContainer = dynamic.onTertiaryContainer,
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
}

/**
 * Tema principal de la aplicación con soporte para personalización de tema (Claro/Oscuro/Sistema),
 * colores de acento dinámicos o personalizados de alto contraste.
 *
 * @param darkTheme Determina si se aplica la paleta oscura.
 * @param accentColor Color de acento seleccionado por el usuario.
 * @param dynamicColor Habilita colores dinámicos del sistema si accentColor es [AccentColor.DYNAMIC].
 * @param content Contenido composable a envolver en el tema.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: AccentColor = AccentColor.BLUE,
    dynamicColor: Boolean = (accentColor == AccentColor.DYNAMIC),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) buildDynamicDarkColorScheme(context) else buildDynamicLightColorScheme(context)
        }
        darkTheme -> buildDarkColorScheme(accentColor)
        else -> buildLightColorScheme(accentColor)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
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
        uncheckedThumbColor = if (isDark) Color.White else TextSecondaryLight,
        uncheckedTrackColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else SurfaceVariantLight,
        uncheckedBorderColor = if (isDark) Color.White.copy(alpha = 0.5f) else OutlineLight,
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        checkedBorderColor = Color.Transparent
    )
}
