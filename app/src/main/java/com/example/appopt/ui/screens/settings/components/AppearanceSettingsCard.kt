package com.example.appopt.ui.screens.settings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight

import com.example.appopt.R
import com.example.appopt.ui.components.SettingsSectionCard
import com.example.appopt.ui.theme.AccentColor
import com.example.appopt.ui.theme.Dimensions
import com.example.appopt.ui.theme.Motion
import com.example.appopt.ui.theme.ThemeMode
import com.example.appopt.ui.theme.rememberAppHaptics

/**
 * Tarjeta de ajustes para la personalización del tema visual (Claro / Oscuro / Sistema)
 * y la selección de la paleta cromática de acento (Directivas 1, 2, 3, 4, 5, 7 y 10).
 *
 * @param currentThemeMode Modo de tema activo en la aplicación.
 * @param currentAccentColor Paleta de color de acento activa.
 * @param onThemeModeChanged Callback invocado al seleccionar un nuevo modo de tema.
 * @param onAccentColorChanged Callback invocado al seleccionar una nueva paleta de acento.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun AppearanceSettingsCard(
    currentThemeMode: ThemeMode,
    currentAccentColor: AccentColor,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onAccentColorChanged: (AccentColor) -> Unit,
    modifier: Modifier = Modifier
) {
    val appHaptics = rememberAppHaptics()
    val availableAccents = remember {
        AccentColor.entries.filter { it != AccentColor.DYNAMIC || AccentColor.isDynamicAvailable() }
    }

    SettingsSectionCard(
        title = stringResource(R.string.settings_appearance_card_title),
        description = stringResource(R.string.settings_appearance_card_desc),
        icon = Icons.Filled.Palette,
        iconTint = MaterialTheme.colorScheme.primary,
        modifier = modifier
    ) {
        // 1. Selector de modo de tema (Sistema / Claro / Oscuro)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.settings_appearance_theme_mode_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            ThemeModeSegmentedControl(
                selectedMode = currentThemeMode,
                onModeSelected = { mode ->
                    if (mode != currentThemeMode) {
                        appHaptics.click()
                        onThemeModeChanged(mode)
                    }
                }
            )
        }

        // 2. Selector de paleta de color de acento
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.settings_appearance_accent_color_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                availableAccents.forEach { accent ->
                    val isSelected = accent == currentAccentColor
                    val title = stringResource(accent.titleRes)

                    AccentColorSwatch(
                        accent = accent,
                        isSelected = isSelected,
                        contentDescription = title,
                        onClick = {
                            if (!isSelected) {
                                appHaptics.click()
                                onAccentColorChanged(accent)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Control segmentado para la selección del modo de tema visual.
 *
 * @param selectedMode Modo de tema actualmente seleccionado.
 * @param onModeSelected Callback invocado al pulsar sobre un segmento.
 */
@Composable
private fun ThemeModeSegmentedControl(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = mode == selectedMode
                val itemTitle = stringResource(mode.titleRes)
                val icon: ImageVector = when (mode) {
                    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                    ThemeMode.DARK -> Icons.Filled.DarkMode
                }

                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = tween(
                        durationMillis = Motion.Duration.FAST,
                        easing = Motion.EasingCurve.Standard
                    ),
                    label = "themeSegmentBg"
                )

                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(
                        durationMillis = Motion.Duration.FAST,
                        easing = Motion.EasingCurve.Standard
                    ),
                    label = "themeSegmentContent"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimensions.ComponentSize.themeSegmentHeight)
                        .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                        .background(backgroundColor)
                        .clickable(role = Role.RadioButton) { onModeSelected(mode) }
                        .semantics {
                            this.role = Role.RadioButton
                            this.selected = isSelected
                            this.contentDescription = itemTitle
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.IconSize.small),
                            tint = contentColor
                        )
                        Text(
                            text = itemTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Muestra visual circular para seleccionar un color de acento específico.
 *
 * @param accent Paleta de color representada.
 * @param isSelected Indica si esta paleta es la actualmente activa.
 * @param contentDescription Descripción accesible para lectores de pantalla.
 * @param onClick Callback invocado al presionar la muestra.
 */
@Composable
private fun AccentColorSwatch(
    accent: AccentColor,
    isSelected: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    val isDynamic = accent == AccentColor.DYNAMIC
    val swatchColor = if (isDynamic) MaterialTheme.colorScheme.primary else accent.previewColor

    Box(
        modifier = Modifier
            .size(Dimensions.ComponentSize.colorSwatchSize)
            .clip(CircleShape)
            .background(swatchColor)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = Dimensions.Stroke.selectionIndicator,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape
                    )
                } else {
                    Modifier.border(
                        width = Dimensions.Stroke.thin,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
                }
            )
            .clickable(role = Role.RadioButton) { onClick() }
            .semantics {
                this.role = Role.RadioButton
                this.selected = isSelected
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center
    ) {
        if (isDynamic) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.IconSize.small),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        } else if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.IconSize.small),
                tint = Color.White
            )
        }
    }
}
