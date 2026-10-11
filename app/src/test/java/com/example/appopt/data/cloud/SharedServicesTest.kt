package com.example.appopt.data.cloud

import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.domain.model.ShareBatchDuration
import com.example.appopt.domain.model.SharedOtpBatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para el sistema de Servicios Compartidos Zero-Secret y Lotes Temporales (Directivas 16 y 31).
 */
class SharedServicesTest {

    /**
     * Verifica que [SharedOtpBatch] devuelva el código exacto para el paso de tiempo activo
     * y retorne `null` una vez alcanzada la marca de expiración.
     */
    @Test
    fun `SharedOtpBatch returns code within valid window and null when expired`() {
        val baseSeconds = 1_700_000_020L
        val step0 = baseSeconds / 30L
        val step1 = step0 + 1L
        val expiresAtMillis = (baseSeconds + 60L) * 1000L

        val batch = SharedOtpBatch(
            grantId = "grant-123",
            periodSeconds = 30,
            expiresAtMillis = expiresAtMillis,
            codesByStep = mapOf(
                step0 to "123456",
                step1 to "654321"
            )
        )

        assertFalse(batch.isExpired(baseSeconds * 1000L))
        assertEquals("123456", batch.getCodeAt(baseSeconds * 1000L))
        assertEquals("654321", batch.getCodeAt((baseSeconds + 30L) * 1000L))
        assertEquals(20, batch.getRemainingSecondsAt(baseSeconds * 1000L))

        // Una vez alcanzada la expiración absoluta, el lote debe invalidarse por completo
        assertTrue(batch.isExpired(expiresAtMillis))
        assertNull(batch.getCodeAt(expiresAtMillis))
    }

    /**
     * Verifica la serialización y deserialización simétrica de un lote temporal en [SharedBatchKeyStore].
     */
    @Test
    fun `SharedBatchKeyStore serializes and deserializes batch accurately`() {
        val original = SharedOtpBatch(
            grantId = "grant-abc",
            periodSeconds = 30,
            expiresAtMillis = 1_800_000_000_000L,
            codesByStep = mapOf(
                56000000L to "112233",
                56000001L to "445566"
            )
        )

        val json = SharedBatchKeyStore.serializeBatch(original)
        val restored = SharedBatchKeyStore.deserializeBatch(json)

        assertEquals(original.grantId, restored.grantId)
        assertEquals(original.periodSeconds, restored.periodSeconds)
        assertEquals(original.expiresAtMillis, restored.expiresAtMillis)
        assertEquals(original.codesByStep, restored.codesByStep)
    }

    /**
     * Verifica el parseo seguro de permisos salientes y servicios entrantes sin exposición de claves secretas.
     */
    @Test
    fun `SharedServicesCloudClient parses outgoing grants and incoming services without secrets`() {
        val outgoingJson = """
            {
              "grants": [
                {
                  "grantId": "g-1",
                  "serviceId": "acc-1",
                  "issuer": "GitHub",
                  "accountName": "dev@example.com",
                  "guestEmail": "guest@example.com",
                  "accessMode": "OFFLINE_BATCH",
                  "batchHours": 6,
                  "createdAtMillis": 1700000000000
                }
              ]
            }
        """.trimIndent()

        val grants = SharedServicesCloudClient.parseOutgoingGrantsJson(outgoingJson)
        assertEquals(1, grants.size)
        assertEquals("g-1", grants[0].grantId)
        assertEquals("acc-1", grants[0].accountId)
        assertEquals("GitHub", grants[0].issuer)
        assertEquals("guest@example.com", grants[0].guestEmail)
        assertEquals(ShareAccessMode.OFFLINE_BATCH, grants[0].accessMode)
        assertEquals(6, grants[0].batchHours)

        val incomingJson = """
            {
              "services": [
                {
                  "grantId": "g-2",
                  "ownerEmail": "owner@example.com",
                  "issuer": "AWS",
                  "accountName": "prod-root",
                  "digits": 6,
                  "period": 30,
                  "accessMode": "ONLINE_ONLY",
                  "batchHours": 24
                }
              ]
            }
        """.trimIndent()

        val services = SharedServicesCloudClient.parseIncomingServicesJson(incomingJson)
        assertEquals(1, services.size)
        assertEquals("g-2", services[0].grantId)
        assertEquals("owner@example.com", services[0].ownerEmail)
        assertEquals("AWS", services[0].issuer)
        assertEquals(ShareAccessMode.ONLINE_ONLY, services[0].accessMode)
    }

    /**
     * Verifica la conversión defensiva de modos y duraciones de lotes.
     */
    @Test
    fun `ShareAccessMode and ShareBatchDuration resolve wire values safely`() {
        assertEquals(ShareAccessMode.ONLINE_ONLY, ShareAccessMode.fromWireValue(null))
        assertEquals(ShareAccessMode.ONLINE_ONLY, ShareAccessMode.fromWireValue("INVALID"))
        assertEquals(ShareAccessMode.OFFLINE_BATCH, ShareAccessMode.fromWireValue("offline_batch"))

        assertEquals(ShareBatchDuration.ONE_HOUR, ShareBatchDuration.fromHours(1))
        assertEquals(ShareBatchDuration.SIX_HOURS, ShareBatchDuration.fromHours(6))
        assertEquals(ShareBatchDuration.TWENTY_FOUR_HOURS, ShareBatchDuration.fromHours(24))
        assertEquals(ShareBatchDuration.TWENTY_FOUR_HOURS, ShareBatchDuration.fromHours(99))
    }
}
