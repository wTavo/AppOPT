package com.example.appopt.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Pruebas unitarias para la configuración y resolución de temas y colores de acento.
 */
class ThemeConfigTest {

    @Test
    fun themeMode_fromKey_resolvesValidKeys() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("SYSTEM"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("Dark"))
    }

    @Test
    fun themeMode_fromKey_defaultsToSystemOnInvalidOrNull() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("unknown_value"))
    }

    @Test
    fun accentColor_fromKey_resolvesValidKeys() {
        assertEquals(AccentColor.BLUE, AccentColor.fromKey("BLUE"))
        assertEquals(AccentColor.GREEN, AccentColor.fromKey("green"))
        assertEquals(AccentColor.PURPLE, AccentColor.fromKey("Purple"))
        assertEquals(AccentColor.ORANGE, AccentColor.fromKey("ORANGE"))
        assertEquals(AccentColor.RED, AccentColor.fromKey("red"))
        assertEquals(AccentColor.TEAL, AccentColor.fromKey("teal"))
        assertEquals(AccentColor.DYNAMIC, AccentColor.fromKey("dynamic"))
    }

    @Test
    fun accentColor_fromKey_defaultsToBlueOnInvalidOrNull() {
        assertEquals(AccentColor.BLUE, AccentColor.fromKey(null))
        assertEquals(AccentColor.BLUE, AccentColor.fromKey(""))
        assertEquals(AccentColor.BLUE, AccentColor.fromKey("random_accent"))
    }

    @Test
    fun colorSchemes_builtCorrectly_forAccentColor() {
        val greenDark = buildDarkColorScheme(AccentColor.GREEN)
        val greenLight = buildLightColorScheme(AccentColor.GREEN)

        assertNotNull(greenDark)
        assertNotNull(greenLight)
        assertEquals(AccentColor.GREEN.primaryDark, greenDark.primary)
        assertEquals(AccentColor.GREEN.primaryLight, greenLight.primary)
    }
}
