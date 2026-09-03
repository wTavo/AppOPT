package com.example.appopt.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas unitarias para las utilidades de accesibilidad [AccessibilityUtils].
 */
class AccessibilityUtilsTest {

    @Test
    fun toAccessibleSpokenOtp_sixDigits_splitsCorrectly() {
        val result = AccessibilityUtils.toAccessibleSpokenOtp("123456")
        assertEquals("1 2 3 , 4 5 6", result)
    }

    @Test
    fun toAccessibleSpokenOtp_eightDigits_splitsCorrectly() {
        val result = AccessibilityUtils.toAccessibleSpokenOtp("12345678")
        assertEquals("1 2 3 4 , 5 6 7 8", result)
    }

    @Test
    fun toAccessibleSpokenOtp_blank_returnsEmpty() {
        val result = AccessibilityUtils.toAccessibleSpokenOtp("")
        assertEquals("", result)
    }
}
