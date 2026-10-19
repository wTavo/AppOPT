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
        previewColor = Color(0xFF2563EB),
        primaryLight = Color(0xFF2563EB),
        primaryDark = Color(0xFF4B82DB),
        primaryContainerLight = Color(0xFFDBEAFE),
        onPrimaryContainerLight = Color(0xFF1E40AF),
        primaryContainerDark = Color(0xFF1E3A8A),
        onPrimaryContainerDark = Color(0xFFDBEAFE),
        secondaryLight = Color(0xFF06B6D4),
        secondaryDark = Color(0xFF06B6D4)
    ),

    /** Verde esmeralda (tono formal de seguridad y estabilidad). */
    GREEN(
        titleRes = R.string.accent_color_green,
        previewColor = Color(0xFF10B981),
        primaryLight = Color(0xFF059669),
        primaryDark = Color(0xFF34D399),
        primaryContainerLight = Color(0xFFD1FAE5),
        onPrimaryContainerLight = Color(0xFF065F46),
        primaryContainerDark = Color(0xFF064E3B),
        onPrimaryContainerDark = Color(0xFFA7F3D0),
        secondaryLight = Color(0xFF10B981),
        secondaryDark = Color(0xFF34D399)
    ),

    /** Púrpura amatista (diseño moderno y distinguido). */
    PURPLE(
        titleRes = R.string.accent_color_purple,
        previewColor = Color(0xFF7C3AED),
        primaryLight = Color(0xFF7C3AED),
        primaryDark = Color(0xFFA78BFA),
        primaryContainerLight = Color(0xFFEDE9FE),
        onPrimaryContainerLight = Color(0xFF5B21B6),
        primaryContainerDark = Color(0xFF4C1D95),
        onPrimaryContainerDark = Color(0xFFDDD6FE),
        secondaryLight = Color(0xFF8B5CF6),
        secondaryDark = Color(0xFFA78BFA)
    ),

    /** Ámbar cálido (tono dorado y enérgico). */
    ORANGE(
        titleRes = R.string.accent_color_orange,
        previewColor = Color(0xFFF59E0B),
        primaryLight = Color(0xFFD97706),
        primaryDark = Color(0xFFFBBF24),
        primaryContainerLight = Color(0xFFFEF3C7),
        onPrimaryContainerLight = Color(0xFF92400E),
        primaryContainerDark = Color(0xFF78350F),
        onPrimaryContainerDark = Color(0xFFFDE68A),
        secondaryLight = Color(0xFFF59E0B),
        secondaryDark = Color(0xFFFBBF24)
    ),

    /** Carmesí coral (acento llamativo y de alta intensidad). */
    RED(
        titleRes = R.string.accent_color_red,
        previewColor = Color(0xFFEF4444),
        primaryLight = Color(0xFFDC2626),
        primaryDark = Color(0xFFF87171),
        primaryContainerLight = Color(0xFFFEE2E2),
        onPrimaryContainerLight = Color(0xFF991B1B),
        primaryContainerDark = Color(0xFF7F1D1D),
        onPrimaryContainerDark = Color(0xFFFECACA),
        secondaryLight = Color(0xFFEF4444),
        secondaryDark = Color(0xFFF87171)
    ),

    /** Turquesa cian (fresco, tecnológico y luminoso). */
    TEAL(
        titleRes = R.string.accent_color_teal,
        previewColor = Color(0xFF0D9488),
        primaryLight = Color(0xFF0D9488),
        primaryDark = Color(0xFF2DD4BF),
        primaryContainerLight = Color(0xFFCCFBF1),
        onPrimaryContainerLight = Color(0xFF115E59),
        primaryContainerDark = Color(0xFF134E4A),
        onPrimaryContainerDark = Color(0xFF99F6E4),
        secondaryLight = Color(0xFF14B8A6),
        secondaryDark = Color(0xFF2DD4BF)
    ),

    /** Color dinámico extraído del sistema operativo Android (Material You en Android 12+). */
    DYNAMIC(
        titleRes = R.string.accent_color_dynamic,
        previewColor = Color(0xFF6750A4),
        primaryLight = Color(0xFF2563EB),
        primaryDark = Color(0xFF4B82DB),
        primaryContainerLight = Color(0xFFDBEAFE),
        onPrimaryContainerLight = Color(0xFF1E40AF),
        primaryContainerDark = Color(0xFF1E3A8A),
        onPrimaryContainerDark = Color(0xFFDBEAFE),
        secondaryLight = Color(0xFF06B6D4),
        secondaryDark = Color(0xFF06B6D4)
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
