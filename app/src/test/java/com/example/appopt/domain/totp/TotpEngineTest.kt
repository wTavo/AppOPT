package com.example.appopt.domain.totp

import com.example.appopt.domain.model.OtpAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Pruebas unitarias para [TotpEngine] utilizando los vectores de prueba oficiales
 * definidos en el Apéndice B del estándar RFC 6238.
 */
class TotpEngineTest {

    // Vectores oficiales del RFC 6238:
    // Semilla para SHA1: "12345678901234567890" (20 bytes)
    private val seedSha1 = "12345678901234567890".toByteArray(StandardCharsets.US_ASCII)
    // Semilla para SHA256: 32 bytes
    private val seedSha256 = "12345678901234567890123456789012".toByteArray(StandardCharsets.US_ASCII)
    // Semilla para SHA512: 64 bytes
    private val seedSha512 = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray(StandardCharsets.US_ASCII)

    /**
     * Valida que HMAC-SHA1 genere exactamente los códigos esperados en los timestamps de prueba del RFC.
     */
    @Test
    fun testRfc6238TestVectors_SHA1() {
        assertEquals("94287082", TotpEngine.generateTotp(seedSha1, 59L * 1000, periodSeconds = 30, digits = 8, algorithm = OtpAlgorithm.SHA1))
        assertEquals("07081804", TotpEngine.generateTotp(seedSha1, 1111111109L * 1000, periodSeconds = 30, digits = 8, algorithm = OtpAlgorithm.SHA1))
        assertEquals("14050471", TotpEngine.generateTotp(seedSha1, 1111111111L * 1000, periodSeconds = 30, digits = 8, algorithm = OtpAlgorithm.SHA1))
        assertEquals("89005924", TotpEngine.generateTotp(seedSha1, 1234567890L * 1000, periodSeconds = 30, digits = 8, algorithm = OtpAlgorithm.SHA1))
        assertEquals("69279037", TotpEngine.generateTotp(seedSha1, 2000000000L * 1000, periodSeconds = 30, digits = 8, algorithm = OtpAlgorithm.SHA1))
    }

    /**
     * Valida que HMAC-SHA256 genere exactamente los códigos esperados en los timestamps de prueba del RFC.
     */
    @Test
    fun testRfc6238TestVectors_SHA256() {
        assertEquals("46119246", TotpEngine.generateTotp(seedSha256, 59L * 1000, periodSeconds = 30, digits = 8, algorithm = OtpAlgorithm.SHA256))
        assertEquals("68084774", TotpEngine.generateTotp(seedSha256, 1111111109L * 1000, periodSeconds = 30, digits = 8, algorithm = OtpAlgorithm.SHA256))
        assertEquals("67062674", TotpEngine.generateTotp(seedSha256, 1111111111L * 1000, periodSeconds = 30, digits = 8, algorithm = OtpAlgorithm.SHA256))
        assertEquals("91819424", TotpEngine.generateTotp(seedSha256, 1234567890L * 1000, periodSeconds = 30, digits = 8, algorithm = OtpAlgorithm.SHA256))
        assertEquals("90698825", TotpEngine.generateTotp(seedSha256, 2000000000L * 1000, periodSeconds = 30, digits = 8, algorithm = OtpAlgorithm.SHA256))
    }

    /**
     * Valida que HMAC-SHA512 genere exactamente los códigos esperados en los timestamps de prueba del RFC.
     */
    @Test
    fun testRfc6238TestVectors_SHA512() {
        assertEquals("90693936", TotpEngine.generateTotp(seedSha512, 59L * 1000, periodSeconds = 30, digits = 8, algorithm = OtpAlgorithm.SHA512))
        assertEquals("25091201", TotpEngine.generateTotp(seedSha512, 1111111109L * 1000, periodSeconds = 30, digits = 8, algorithm = OtpAlgorithm.SHA512))
        assertEquals("99943326", TotpEngine.generateTotp(seedSha512, 1111111111L * 1000, periodSeconds = 30, digits = 8, algorithm = OtpAlgorithm.SHA512))
        assertEquals("93441116", TotpEngine.generateTotp(seedSha512, 1234567890L * 1000, periodSeconds = 30, digits = 8, algorithm = OtpAlgorithm.SHA512))
        assertEquals("38618901", TotpEngine.generateTotp(seedSha512, 2000000000L * 1000, periodSeconds = 30, digits = 8, algorithm = OtpAlgorithm.SHA512))
    }

    /**
     * Valida el cálculo de segundos restantes y progreso porcentual dentro de la ventana de 30s.
     */
    @Test
    fun testRemainingSecondsAndProgress() {
        // Al inicio de la ventana (segundo 0)
        val t0 = 30_000L
        assertEquals(30, TotpEngine.getRemainingSeconds(t0, 30))
        assertEquals(1.0f, TotpEngine.getProgress(t0, 30), 0.01f)

        // A la mitad de la ventana (segundo 15)
        val t15 = 45_000L
        assertEquals(15, TotpEngine.getRemainingSeconds(t15, 30))
        assertEquals(0.5f, TotpEngine.getProgress(t15, 30), 0.01f)

        // Al final de la ventana (segundo 29)
        val t29 = 59_000L
        assertEquals(1, TotpEngine.getRemainingSeconds(t29, 30))
        assertEquals(0.033f, TotpEngine.getProgress(t29, 30), 0.05f)
    }
}
