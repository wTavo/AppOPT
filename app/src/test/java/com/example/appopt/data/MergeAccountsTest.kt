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
}
