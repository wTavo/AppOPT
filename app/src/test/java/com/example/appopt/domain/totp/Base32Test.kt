package com.example.appopt.domain.totp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Pruebas unitarias de codificación, decodificación y validación estricta de Base32
 * contra los vectores de prueba oficiales del RFC 4648 sección 10.
 */
class Base32Test {

    /**
     * Valida que todos los vectores estándar de prueba del RFC 4648 se codifiquen y decodifiquen sin pérdidas.
     */
    @Test
    fun testRfc4648Base32Vectors() {
        assertEquals("", Base32.encode("".toByteArray(StandardCharsets.US_ASCII), withPadding = true))
        assertEquals("MY======", Base32.encode("f".toByteArray(StandardCharsets.US_ASCII), withPadding = true))
        assertEquals("MZXQ====", Base32.encode("fo".toByteArray(StandardCharsets.US_ASCII), withPadding = true))
        assertEquals("MZXW6===", Base32.encode("foo".toByteArray(StandardCharsets.US_ASCII), withPadding = true))
        assertEquals("MZXW6YQ=", Base32.encode("foob".toByteArray(StandardCharsets.US_ASCII), withPadding = true))
        assertEquals("MZXW6YTB", Base32.encode("fooba".toByteArray(StandardCharsets.US_ASCII), withPadding = true))
        assertEquals("MZXW6YTBOI======", Base32.encode("foobar".toByteArray(StandardCharsets.US_ASCII), withPadding = true))

        assertArrayEquals("".toByteArray(), Base32.decode(""))
        assertArrayEquals("f".toByteArray(), Base32.decode("MY======"))
        assertArrayEquals("fo".toByteArray(), Base32.decode("MZXQ===="))
        assertArrayEquals("foo".toByteArray(), Base32.decode("MZXW6==="))
        assertArrayEquals("foob".toByteArray(), Base32.decode("MZXW6YQ="))
        assertArrayEquals("fooba".toByteArray(), Base32.decode("MZXW6YTB"))
        assertArrayEquals("foobar".toByteArray(), Base32.decode("MZXW6YTBOI======"))
    }

    /**
     * Valida la tolerancia de caracteres comunes como espacios y guiones en el saneamiento.
     */
    @Test
    fun testDecodingWithoutPaddingAndWithSpaces() {
        assertArrayEquals("foobar".toByteArray(), Base32.decode("MZXW 6YTB OI"))
        assertArrayEquals("foobar".toByteArray(), Base32.decode("mzxw-6ytb-oi"))
    }

    /**
     * Valida la detección de caracteres no permitidos en el alfabeto Base32 canónico.
     */
    @Test
    fun testValidation() {
        assertTrue(Base32.isValid("JBSWY3DPEHPK3PXP"))
        assertTrue(Base32.isValid("jbsw y3dp ehpk 3pxp"))
        assertFalse(Base32.isValid("1890InvalidChars!"))
        assertFalse(Base32.isValid(""))
    }
}
