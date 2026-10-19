package com.example.appopt.ui.theme

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.appopt.R

/**
 * Modos de tema visual soportados por la aplicación.
 *
 * @property titleRes Identificador del recurso de texto localizado para el nombre del modo.
 */
enum class ThemeMode(@StringRes val titleRes: Int) {
    /** Se sincroniza automáticamente con la configuración del sistema operativo. */
    SYSTEM(R.string.theme_mode_system),

    /** Fuerza el tema claro con fondo luminoso y alto contraste. */
    LIGHT(R.string.theme_mode_light),

    /** Fuerza el tema oscuro con fondos profundos para pantallas OLED. */
    DARK(R.string.theme_mode_dark);

    companion object {
        /**
         * Resuelve una instancia de [ThemeMode] a partir de su clave persistida en disco.
         *
         * @param key Nombre serializado del modo.
         * @return Instancia coincidente o [SYSTEM] por defecto.
         */
        fun fromKey(key: String?): ThemeMode {
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: SYSTEM
        }
    }
}

/**
 * Paleta de colores de acento personalizables de la aplicación.
 *
 * Provee tonalidades armónicas de alto contraste tanto para modo claro como para modo oscuro,
 * garantizando coherencia visual según los lineamientos de Material Design 3.
 *
 * @property titleRes Identificador del recurso de texto para el nombre en pantalla.
 * @property previewColor Color representativo mostrado en el círculo de selección visual.
 * @property primaryLight Tonalidad primaria en tema claro.
 * @property primaryDark Tonalidad primaria luminosa en tema oscuro.
 * @property primaryContainerLight Fondo suave para contenedores en tema claro.
 * @property onPrimaryContainerLight Color de texto sobre contenedor primario en tema claro.
 * @property primaryContainerDark Fondo oscuro para contenedores en tema oscuro.
 * @property onPrimaryContainerDark Color de texto sobre contenedor primario en tema oscuro.
 * @property secondaryLight Color secundario armónico en tema claro.
 * @property secondaryDark Color secundario armónico en tema oscuro.
 */
enum class AccentColor(
    @StringRes val titleRes: Int,
    val previewColor: Color,
    val primaryLight: Color,
    val primaryDark: Color,
    val primaryContainerLight: Color,
    val onPrimaryContainerLight: Color,
    val primaryContainerDark: Color,
    val onPrimaryContainerDark: Color,
    val secondaryLight: Color,
    val secondaryDark: Color
) {
    /** Azul zafiro (color predeterminado clásico de la aplicación). */
    BLUE(
        titleRes = R.string.accent_color_blue,
        previewColor = PrimaryBlue,
        primaryLight = PrimaryBlue,
        primaryDark = PrimaryBlueDark,
        primaryContainerLight = PrimaryContainerLight,
        onPrimaryContainerLight = OnPrimaryContainerLight,
        primaryContainerDark = PrimaryContainerDark,
        onPrimaryContainerDark = OnPrimaryContainerDark,
        secondaryLight = AccentCyan,
        secondaryDark = AccentCyan
    ),

    /** Verde esmeralda (tono formal de seguridad y estabilidad). */
    GREEN(
        titleRes = R.string.accent_color_green,
        previewColor = AccentGreenPreview,
        primaryLight = AccentGreenPrimaryLight,
        primaryDark = AccentGreenPrimaryDark,
        primaryContainerLight = AccentGreenContainerLight,
        onPrimaryContainerLight = AccentGreenOnContainerLight,
        primaryContainerDark = AccentGreenContainerDark,
        onPrimaryContainerDark = AccentGreenOnContainerDark,
        secondaryLight = SafeGreen,
        secondaryDark = AccentGreenPrimaryDark
    ),

    /** Púrpura amatista (diseño moderno y distinguido). */
    PURPLE(
        titleRes = R.string.accent_color_purple,
        previewColor = AccentPurplePreview,
        primaryLight = AccentPurplePrimaryLight,
        primaryDark = AccentPurplePrimaryDark,
        primaryContainerLight = AccentPurpleContainerLight,
        onPrimaryContainerLight = AccentPurpleOnContainerLight,
        primaryContainerDark = AccentPurpleContainerDark,
        onPrimaryContainerDark = AccentPurpleOnContainerDark,
        secondaryLight = AccentPurpleSecondaryLight,
        secondaryDark = AccentPurplePrimaryDark
    ),

    /** Ámbar cálido (tono dorado y enérgico). */
    ORANGE(
        titleRes = R.string.accent_color_orange,
        previewColor = AccentOrangePreview,
        primaryLight = AccentOrangePrimaryLight,
        primaryDark = AccentOrangePrimaryDark,
        primaryContainerLight = AccentOrangeContainerLight,
        onPrimaryContainerLight = AccentOrangeOnContainerLight,
        primaryContainerDark = AccentOrangeContainerDark,
        onPrimaryContainerDark = AccentOrangeOnContainerDark,
        secondaryLight = WarningOrange,
        secondaryDark = AccentOrangePrimaryDark
    ),

    /** Carmesí coral (acento llamativo y de alta intensidad). */
    RED(
        titleRes = R.string.accent_color_red,
        previewColor = AccentRedPreview,
        primaryLight = AccentRedPrimaryLight,
        primaryDark = AccentRedPrimaryDark,
        primaryContainerLight = AccentRedContainerLight,
        onPrimaryContainerLight = AccentRedOnContainerLight,
        primaryContainerDark = AccentRedContainerDark,
        onPrimaryContainerDark = AccentRedOnContainerDark,
        secondaryLight = UrgentRed,
        secondaryDark = AccentRedPrimaryDark
    ),

    /** Turquesa cian (fresco, tecnológico y luminoso). */
    TEAL(
        titleRes = R.string.accent_color_teal,
        previewColor = AccentTealPreview,
        primaryLight = AccentTealPrimaryLight,
        primaryDark = AccentTealPrimaryDark,
        primaryContainerLight = AccentTealContainerLight,
        onPrimaryContainerLight = AccentTealOnContainerLight,
        primaryContainerDark = AccentTealContainerDark,
        onPrimaryContainerDark = AccentTealOnContainerDark,
        secondaryLight = AccentTealSecondaryLight,
        secondaryDark = AccentTealPrimaryDark
    ),

    /** Color dinámico extraído del sistema operativo Android (Material You en Android 12+). */
    DYNAMIC(
        titleRes = R.string.accent_color_dynamic,
        previewColor = AccentDynamicPreview,
        primaryLight = PrimaryBlue,
        primaryDark = PrimaryBlueDark,
        primaryContainerLight = PrimaryContainerLight,
        onPrimaryContainerLight = OnPrimaryContainerLight,
        primaryContainerDark = PrimaryContainerDark,
        onPrimaryContainerDark = OnPrimaryContainerDark,
        secondaryLight = AccentCyan,
        secondaryDark = AccentCyan
    );

    companion object {
        /**
         * Resuelve una instancia de [AccentColor] a partir de su clave persistida en disco.
         *
         * @param key Nombre serializado del color de acento.
         * @return Instancia coincidente o [BLUE] por defecto.
         */
        fun fromKey(key: String?): AccentColor {
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: BLUE
        }

        /**
         * Determina si el soporte para colores dinámicos (Material You) está disponible en el dispositivo.
         *
         * @return `true` en Android 12 (API 31) o superior.
         */
        fun isDynamicAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}
