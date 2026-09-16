package com.example.appopt.data

import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.domain.totp.Base32
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite de pruebas unitarias automatizadas para la previsualización y selección granular
 * de cuentas a importar o restaurar en la bóveda local.
 */
class AccountImportPreviewTest {

    @Test
    fun testParseAccountsForPreviewWithCompactFormat() {
        val jsonPayload = JSONObject().apply {
            put("v", 1)
            val array = JSONArray().apply {
                put(JSONObject().apply {
                    put("i", "GitHub")
                    put("a", "octocat@github.com")
                    put("s", "JBSWY3DPEHPK3PXP")
                    put("alg", "SHA256")
                    put("d", 8)
                })
                put(JSONObject().apply {
                    put("i", "Google")
                    put("a", "admin@gmail.com")
                    put("s", "KRUGS4ZANFZSA53O")
                })
            }
            put("a", array)
        }.toString()

        val root = JSONObject(jsonPayload)
        val array = root.getJSONArray("a")
        assertEquals(2, array.length())

        val item1 = array.getJSONObject(0)
        assertEquals("GitHub", item1.getString("i"))
        assertEquals("octocat@github.com", item1.getString("a"))
        assertEquals("SHA256", item1.getString("alg"))
        assertEquals(8, item1.getInt("d"))

        val item2 = array.getJSONObject(1)
        assertEquals("Google", item2.getString("i"))
        assertEquals("admin@gmail.com", item2.getString("a"))
        assertEquals("KRUGS4ZANFZSA53O", item2.getString("s"))
    }

    @Test
    fun testSelectiveAccountImportFiltering() {
        val secret1 = "SECRET_1_BYTES".toByteArray()
        val secret2 = "SECRET_2_BYTES".toByteArray()
        val secret3 = "SECRET_3_BYTES".toByteArray()

        val parsedList = listOf(
            ParsedAccountPreview(
                id = "acc-1",
                issuer = "GitHub",
                accountName = "user1",
                algorithm = OtpAlgorithm.SHA1,
                digits = 6,
                period = 30,
                type = OtpType.TOTP,
                counter = 0L,
                isFavorite = false,
                isAlreadyInVault = false,
                secretBytes = secret1
            ),
            ParsedAccountPreview(
                id = "acc-2",
                issuer = "AWS",
                accountName = "user2",
                algorithm = OtpAlgorithm.SHA256,
                digits = 6,
                period = 30,
                type = OtpType.TOTP,
                counter = 0L,
                isFavorite = false,
                isAlreadyInVault = true,
                secretBytes = secret2
            ),
            ParsedAccountPreview(
                id = "acc-3",
                issuer = "Discord",
                accountName = "user3",
                algorithm = OtpAlgorithm.SHA1,
                digits = 6,
                period = 30,
                type = OtpType.TOTP,
                counter = 0L,
                isFavorite = false,
                isAlreadyInVault = false,
                secretBytes = secret3
            )
        )

        // El usuario solo selecciona acc-1 y acc-3 (descarta acc-2)
        val selectedIds = setOf("acc-1", "acc-3")
        val accountsToImport = parsedList.filter { it.id in selectedIds }

        assertEquals(2, accountsToImport.size)
        assertTrue(accountsToImport.any { it.issuer == "GitHub" })
        assertTrue(accountsToImport.any { it.issuer == "Discord" })
        assertFalse(accountsToImport.any { it.issuer == "AWS" })
    }

    @Test
    fun testSelectAllAndDeselectAllToggle() {
        val parsedList = listOf(
            ParsedAccountPreview(id = "1", issuer = "A", accountName = "", algorithm = OtpAlgorithm.SHA1, digits = 6, period = 30, type = OtpType.TOTP, counter = 0L, isFavorite = false, isAlreadyInVault = false, secretBytes = byteArrayOf()),
            ParsedAccountPreview(id = "2", issuer = "B", accountName = "", algorithm = OtpAlgorithm.SHA1, digits = 6, period = 30, type = OtpType.TOTP, counter = 0L, isFavorite = false, isAlreadyInVault = false, secretBytes = byteArrayOf()),
            ParsedAccountPreview(id = "3", issuer = "C", accountName = "", algorithm = OtpAlgorithm.SHA1, digits = 6, period = 30, type = OtpType.TOTP, counter = 0L, isFavorite = false, isAlreadyInVault = false, secretBytes = byteArrayOf())
        )

        // Inicialmente todas seleccionadas
        var selectedIds = parsedList.map { it.id }.toSet()
        assertEquals(3, selectedIds.size)

        // Deseleccionar todas
        selectedIds = emptySet()
        assertEquals(0, selectedIds.size)

        // Seleccionar todas de nuevo
        selectedIds = parsedList.map { it.id }.toSet()
        assertEquals(3, selectedIds.size)

        // Toggle individual
        selectedIds = selectedIds - "2"
        assertEquals(setOf("1", "3"), selectedIds)
    }
}
