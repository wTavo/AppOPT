package com.example.appopt.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite de pruebas unitarias para el gestor de diagnósticos y crasheos [AppCrashTracker].
 *
 * Valida:
 * - Sanitización estricta de secretos (*Zero-Leakage* - Directivas 9, 15 y 18).
 * - Enmascaramiento de claves Base32, URIs OTP, tokens y contraseñas.
 * - Comportamiento del buffer circular de errores no fatales.
 */
class AppCrashTrackerTest {

    @Before
    fun setUp() {
        AppCrashTracker.isEnabled = true
    }

    @Test
    fun logNonFatal_whenDisabled_doesNotRecordLogs() {
        AppCrashTracker.isEnabled = false
        val logsBefore = AppCrashTracker.logsFlow.value.size

        AppCrashTracker.logNonFatal(
            tag = "DisabledTest",
            message = "Este mensaje no debería guardarse"
        )

        val logsAfter = AppCrashTracker.logsFlow.value.size
        assertEquals(logsBefore, logsAfter)
    }

    @Test
    fun sanitize_base32Secret_masksCorrectly() {
        val rawInput = "Error al procesar clave JBSWY3DPEHPK3PXP en el algoritmo TOTP"
        val sanitized = AppCrashTracker.sanitize(rawInput)

        assertFalse(sanitized.contains("JBSWY3DPEHPK3PXP"))
        assertTrue(sanitized.contains("[SECRETO_ENMASCARADO]"))
    }

    @Test
    fun sanitize_otpauthUri_masksCorrectly() {
        val rawUri = "otpauth://totp/Google:user@mail.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=Google"
        val sanitized = AppCrashTracker.sanitize(rawUri)

        assertFalse(sanitized.contains("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ"))
        assertTrue(sanitized.contains("[SECRETO_ENMASCARADO]"))
    }

    @Test
    fun sanitize_tokenAndPassParameters_masksCorrectly() {
        val rawLog = "Fallo en autenticación: token=ya29.a0AfH6SMB_secretToken12345 y pass=MyMasterPassword123"
        val sanitized = AppCrashTracker.sanitize(rawLog)

        assertFalse(sanitized.contains("ya29.a0AfH6SMB_secretToken12345"))
        assertFalse(sanitized.contains("MyMasterPassword123"))
        assertTrue(sanitized.contains("[SECRETO_ENMASCARADO]"))
    }

    @Test
    fun sanitize_normalTextWithoutSecrets_preservedUnchanged() {
        val normalText = "Error de conexión HTTP 503: Servicio no disponible temporalmente."
        val sanitized = AppCrashTracker.sanitize(normalText)

        assertEquals(normalText, sanitized)
    }

    @Test
    fun sanitize_emptyString_returnsEmpty() {
        assertEquals("", AppCrashTracker.sanitize(""))
        assertEquals("   ", AppCrashTracker.sanitize("   "))
    }

    @Test
    fun logNonFatal_sanitizesMessageAndAddsToBuffer() {
        val secretMessage = "Excepción con clave JBSWY3DPEHPK3PXP"
        AppCrashTracker.logNonFatal(
            tag = "TestComponent",
            message = secretMessage,
            severity = DiagnosticSeverity.WARNING
        )

        val latestLogs = AppCrashTracker.logsFlow.value
        assertTrue(latestLogs.isNotEmpty())
        val firstLog = latestLogs.first()
        assertEquals("TestComponent", firstLog.tag)
        assertEquals(DiagnosticSeverity.WARNING, firstLog.severity)
        assertFalse(firstLog.message.contains("JBSWY3DPEHPK3PXP"))
        assertTrue(firstLog.message.contains("[SECRETO_ENMASCARADO]"))
    }

    @Test
    fun logNonFatal_circularBuffer_limitsToMaxEntries() {
        for (i in 1..25) {
            AppCrashTracker.logNonFatal(
                tag = "LoopTag",
                message = "Mensaje de prueba número $i"
            )
        }

        val logs = AppCrashTracker.logsFlow.value
        assertTrue(logs.size <= 20)
        assertEquals("Mensaje de prueba número 25", logs.first().message)
    }
}
