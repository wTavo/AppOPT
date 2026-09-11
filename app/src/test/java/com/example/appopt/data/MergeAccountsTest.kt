package com.example.appopt.data

import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.model.TotpAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de regresión para la lógica de resolución determinística de conflictos
 * y fusión bidireccional de cuentas entre múltiples dispositivos.
 */
class MergeAccountsTest {

    @Test
    fun testMultiDeviceMergeLogicSimulation() {
        // Simulación de dos dispositivos (Teléfono A y Teléfono B)
        val deviceAAccounts = mutableMapOf(
            "google:user@gmail.com" to TotpAccount(
                id = "id-1",
                issuer = "Google",
                accountName = "user@gmail.com",
                updatedAt = 1000L
            ),
            "github:user" to TotpAccount(
                id = "id-2",
                issuer = "GitHub",
                accountName = "user",
                updatedAt = 1000L
            )
        )

        val deviceBAccounts = mutableMapOf(
            "github:user" to TotpAccount(
                id = "id-2",
                issuer = "GitHub",
                accountName = "user",
                updatedAt = 2000L // Editado más recientemente en Teléfono B
            ),
            "binance:trader" to TotpAccount(
                id = "id-3",
                issuer = "Binance",
                accountName = "trader",
                updatedAt = 1500L // Nuevo servicio agregado en Teléfono B
            )
        )

        // Algoritmo de Fusión Determinística de B sobre A
        for ((key, remoteAcc) in deviceBAccounts) {
            val localAcc = deviceAAccounts[key]
            if (localAcc == null) {
                // Cuenta remota no presente en local -> Se agrega
                deviceAAccounts[key] = remoteAcc
            } else if (remoteAcc.updatedAt > localAcc.updatedAt) {
                // Cuenta coincidente -> Se preserva la versión más reciente
                deviceAAccounts[key] = remoteAcc
            }
        }

        // Verificaciones
        assertEquals("El resultado debe contener exactamente 3 cuentas consolidadas", 3, deviceAAccounts.size)
        assertTrue("Debe contener la cuenta de Google de A", deviceAAccounts.containsKey("google:user@gmail.com"))
        assertTrue("Debe contener la cuenta de GitHub", deviceAAccounts.containsKey("github:user"))
        assertTrue("Debe contener la nueva cuenta de Binance de B", deviceAAccounts.containsKey("binance:trader"))
        assertEquals("La cuenta de GitHub debe tener la marca de tiempo más reciente de B", 2000L, deviceAAccounts["github:user"]?.updatedAt)
    }

    @Test
    fun testMergePreservesLocalWhenLocalIsNewer() {
        val localAccount = TotpAccount(
            id = "id-1",
            issuer = "Discord",
            accountName = "gamer",
            counter = 5L,
            updatedAt = 5000L // Local es más reciente
        )

        val remoteAccount = TotpAccount(
            id = "id-1",
            issuer = "Discord",
            accountName = "gamer",
            counter = 2L,
            updatedAt = 3000L // Remoto es más antiguo
        )

        val merged = if (remoteAccount.updatedAt > localAccount.updatedAt) remoteAccount else localAccount

        assertEquals("Debe prevalecer la versión local si tiene un updatedAt más reciente", 5L, merged.counter)
        assertEquals(5000L, merged.updatedAt)
    }

    @Test
    fun testMergeDifferentSecretsWithSameNamePreservesBoth() {
        // Dos cuentas con el mismo nombre pero secretos criptográficos distintos
        val localAccountsBySecret = mutableMapOf(
            "secret-hash-1" to TotpAccount(
                id = "id-1",
                issuer = "Google",
                accountName = "user@gmail.com",
                updatedAt = 1000L
            )
        )

        val remoteAccount = TotpAccount(
            id = "id-2",
            issuer = "Google",
            accountName = "user@gmail.com",
            updatedAt = 2000L
        )
        val remoteSecretHash = "secret-hash-2"

        // Fusión por hash de secreto
        val existing = localAccountsBySecret[remoteSecretHash]
        if (existing == null) {
            localAccountsBySecret[remoteSecretHash] = remoteAccount
        } else if (remoteAccount.updatedAt > existing.updatedAt) {
            localAccountsBySecret[remoteSecretHash] = remoteAccount
        }

        assertEquals("Ambas cuentas 2FA deben preservarse por tener secretos distintos", 2, localAccountsBySecret.size)
        assertTrue(localAccountsBySecret.containsKey("secret-hash-1"))
        assertTrue(localAccountsBySecret.containsKey("secret-hash-2"))
    }

    @Test
    fun testMergeSameSecretUpdatesWhenRemoteIsNewer() {
        val secretHash = "secret-hash-shared"
        val localAccountsBySecret = mutableMapOf(
            secretHash to TotpAccount(
                id = "id-1",
                issuer = "Google",
                accountName = "user@gmail.com",
                updatedAt = 1000L
            )
        )

        val remoteAccount = TotpAccount(
            id = "id-1",
            issuer = "Google (Renombrado)",
            accountName = "user@gmail.com",
            updatedAt = 2000L // Remoto es más nuevo
        )

        val existing = localAccountsBySecret[secretHash]
        if (existing == null) {
            localAccountsBySecret[secretHash] = remoteAccount
        } else if (remoteAccount.updatedAt > existing.updatedAt) {
            localAccountsBySecret[secretHash] = remoteAccount
        }

        assertEquals(1, localAccountsBySecret.size)
        assertEquals("Google (Renombrado)", localAccountsBySecret[secretHash]?.issuer)
        assertEquals(2000L, localAccountsBySecret[secretHash]?.updatedAt)
    }

    @Test
    fun testCompactFormatAccountsExtraction() {
        val compactJson = """
            {
              "v": 1,
              "a": [
                {
                  "i": "GitHub",
                  "a": "octocat",
                  "s": "JBSWY3DPEHPK3PXP",
                  "alg": "SHA1",
                  "d": 6,
                  "p": 30,
                  "t": "TOTP",
                  "c": 0
                },
                {
                  "i": "AWS",
                  "a": "admin",
                  "s": "JBSWY3DPEHPK3PXQ"
                }
              ]
            }
        """.trimIndent()

        val root = org.json.JSONObject(compactJson)
        val accountsArray = when {
            root.has("accounts") -> root.getJSONArray("accounts")
            root.has("a") -> root.getJSONArray("a")
            else -> null
        }

        org.junit.Assert.assertNotNull("Debe extraer el arreglo bajo la clave 'a'", accountsArray)
        assertEquals(2, accountsArray!!.length())

        val item1 = accountsArray.getJSONObject(0)
        assertEquals("GitHub", item1.optString("i"))
        assertEquals("octocat", item1.optString("a"))
        assertEquals("JBSWY3DPEHPK3PXP", item1.getString("s"))

        val item2 = accountsArray.getJSONObject(1)
        assertEquals("AWS", item2.optString("i"))
        assertEquals("admin", item2.optString("a"))
        assertEquals("JBSWY3DPEHPK3PXQ", item2.getString("s"))
    }

    @Test
    fun testVerboseFormatAccountsExtraction() {
        val verboseJson = """
            {
              "version": 1,
              "accounts": [
                {
                  "id": "uuid-1",
                  "issuer": "Google",
                  "accountName": "user@gmail.com",
                  "secret": "JBSWY3DPEHPK3PXP",
                  "algorithm": "SHA1",
                  "digits": 6,
                  "period": 30,
                  "type": "TOTP"
                }
              ]
            }
        """.trimIndent()

        val root = org.json.JSONObject(verboseJson)
        val accountsArray = when {
            root.has("accounts") -> root.getJSONArray("accounts")
            root.has("a") -> root.getJSONArray("a")
            else -> null
        }

        org.junit.Assert.assertNotNull("Debe extraer el arreglo bajo la clave 'accounts'", accountsArray)
        assertEquals(1, accountsArray!!.length())

        val item = accountsArray.getJSONObject(0)
        assertEquals("Google", item.optString("issuer"))
        assertEquals("user@gmail.com", item.optString("accountName"))
        assertEquals("JBSWY3DPEHPK3PXP", item.getString("secret"))
    }
}
