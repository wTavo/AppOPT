package com.example.appopt.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Pruebas unitarias para el formateador centralizado [DateTimeFormatter].
 */
class DateTimeFormatterTest {

    @Test
    fun formatAbsoluteDateTime_zero_returnsEmpty() {
        val result = DateTimeFormatter.formatAbsoluteDateTime(0L)
        assertEquals("", result)
    }

    @Test
    fun formatAbsoluteDateTime_validTimestamp_returnsFormattedString() {
        // 1700000000000L = 14/11/2023 22:13:20 UTC
        val result = DateTimeFormatter.formatAbsoluteDateTime(1700000000000L, Locale.US)
        assertTrue(result.contains("2023") || result.contains("11") || result.contains("14"))
    }
}
