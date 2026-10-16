package com.example.appopt.data.cloud

import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.domain.model.ShareBatchDuration
import com.example.appopt.domain.model.ShareGrantStatus
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
                  "status": "PENDING",
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
        assertEquals(ShareGrantStatus.PENDING, grants[0].status)

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
                  "batchHours": 24,
                  "status": "ACCEPTED"
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
        assertEquals(ShareGrantStatus.ACCEPTED, services[0].status)
    }

    /**
     * Verifica la conversión defensiva de modos, estados de invitación y duraciones de lotes.
     */
    @Test
    fun `ShareAccessMode, ShareGrantStatus and ShareBatchDuration resolve wire values safely`() {
        assertEquals(ShareAccessMode.ONLINE_ONLY, ShareAccessMode.fromWireValue(null))
        assertEquals(ShareAccessMode.ONLINE_ONLY, ShareAccessMode.fromWireValue("INVALID"))
        assertEquals(ShareAccessMode.OFFLINE_BATCH, ShareAccessMode.fromWireValue("offline_batch"))

        assertEquals(ShareGrantStatus.ACCEPTED, ShareGrantStatus.fromWireValue(null))
        assertEquals(ShareGrantStatus.PENDING, ShareGrantStatus.fromWireValue("pending"))
        assertEquals(ShareGrantStatus.ACCEPTED, ShareGrantStatus.fromWireValue("ACCEPTED"))

        assertEquals(ShareBatchDuration.ONE_HOUR, ShareBatchDuration.fromHours(1))
        assertEquals(ShareBatchDuration.SIX_HOURS, ShareBatchDuration.fromHours(6))
        assertEquals(ShareBatchDuration.TWENTY_FOUR_HOURS, ShareBatchDuration.fromHours(24))
        assertEquals(ShareBatchDuration.TWENTY_FOUR_HOURS, ShareBatchDuration.fromHours(99))
    }

    /**
     * Verifica que [LiveSharedCodeResult] entregue la brecha de 3 códigos consecutivos
     * y retorne `null` automáticamente cuando concluye el tercer código.
     */
    @Test
    fun `LiveSharedCodeResult rotates across 3 codes and expires after third code`() {
        val period = 30
        val step0 = 56_000_000L
        val step1 = step0 + 1L
        val step2 = step0 + 2L
        val expiresAtMillis = (step0 + 3L) * period * 1000L

        val liveWindow = LiveSharedCodeResult(
            code = "111222",
            remainingSeconds = 15,
            periodSeconds = period,
            expiresAtMillis = expiresAtMillis,
            codesByStep = mapOf(
                step0 to "111222",
                step1 to "333444",
                step2 to "555666"
            )
        )

        val t0 = (step0 * period + 15L) * 1000L
        val t1 = (step1 * period + 5L) * 1000L
        val t2 = (step2 * period + 29L) * 1000L

        assertEquals("111222", liveWindow.getCodeAt(t0))
        assertEquals(1 to 3, liveWindow.getWindowStepProgress(t0))

        assertEquals("333444", liveWindow.getCodeAt(t1))
        assertEquals(2 to 3, liveWindow.getWindowStepProgress(t1))

        assertEquals("555666", liveWindow.getCodeAt(t2))
        assertEquals(3 to 3, liveWindow.getWindowStepProgress(t2))

        // Al terminar el 3er código, desaparece y retorna null
        assertNull(liveWindow.getCodeAt(expiresAtMillis))
        assertNull(liveWindow.getWindowStepProgress(expiresAtMillis))
    }

    /**
     * Verifica que [IncomingSharedService] mantenga `isFavorite = false` por defecto y permita
     * su actualización inmutable preservando todos sus campos.
     */
    @Test
    fun `IncomingSharedService defaults isFavorite to false and supports copy`() {
        val service = IncomingSharedService(
            grantId = "grant-fav-1",
            ownerEmail = "alice@example.com",
            issuer = "GitHub",
            accountName = "alice@corp",
            digits = 6,
            period = 30,
            accessMode = ShareAccessMode.ONLINE_ONLY,
            batchHours = 24,
            status = ShareGrantStatus.ACCEPTED
        )

        assertFalse(service.isFavorite)

        val favoriteService = service.copy(isFavorite = true)
        assertTrue(favoriteService.isFavorite)
        assertEquals("grant-fav-1", favoriteService.grantId)
        assertEquals("GitHub", favoriteService.issuer)
    }

    /**
     * Verifica que la serialización y deserialización JSON en [SharedBatchKeyStore]
     * conserve fielmente el estado [IncomingSharedService.isFavorite].
     */
    @Test
    fun `SharedBatchKeyStore preserves isFavorite across JSON serialization`() {
        val list = listOf(
            IncomingSharedService(
                grantId = "g-fav",
                ownerEmail = "owner1@example.com",
                issuer = "Slack",
                accountName = "work",
                digits = 6,
                period = 30,
                accessMode = ShareAccessMode.ONLINE_ONLY,
                batchHours = 24,
                status = ShareGrantStatus.ACCEPTED,
                isFavorite = true
            ),
            IncomingSharedService(
                grantId = "g-non-fav",
                ownerEmail = "owner2@example.com",
                issuer = "Google",
                accountName = "personal",
                digits = 6,
                period = 30,
                accessMode = ShareAccessMode.OFFLINE_BATCH,
                batchHours = 6,
                status = ShareGrantStatus.PENDING,
                isFavorite = false
            )
        )

        val json = SharedBatchKeyStore.serializeIncomingServicesJson(list)
        val restored = SharedBatchKeyStore.deserializeIncomingServicesJson(json)

        assertEquals(2, restored.size)
        assertTrue(restored[0].isFavorite)
        assertEquals("g-fav", restored[0].grantId)
        assertFalse(restored[1].isFavorite)
        assertEquals("g-non-fav", restored[1].grantId)
    }

    /**
     * Verifica que [SharedBatchKeyStore.deserializeIncomingServicesJson] ordene los servicios
     * entrantes colocando primero los favoritos (`isFavorite == true`) y luego por emisor.
     */
    @Test
    fun `deserializeIncomingServicesJson sorts favorites first then by issuer`() {
        val list = listOf(
            IncomingSharedService(
                grantId = "g-1",
                ownerEmail = "owner@example.com",
                issuer = "Zoom",
                accountName = "work",
                isFavorite = false
            ),
            IncomingSharedService(
                grantId = "g-2",
                ownerEmail = "owner@example.com",
                issuer = "Slack",
                accountName = "team",
                isFavorite = true
            ),
            IncomingSharedService(
                grantId = "g-3",
                ownerEmail = "owner@example.com",
                issuer = "Amazon",
                accountName = "aws",
                isFavorite = false
            ),
            IncomingSharedService(
                grantId = "g-4",
                ownerEmail = "owner@example.com",
                issuer = "GitHub",
                accountName = "repo",
                isFavorite = true
            )
        )

        val json = SharedBatchKeyStore.serializeIncomingServicesJson(list)
        val restored = SharedBatchKeyStore.deserializeIncomingServicesJson(json)

        assertEquals(4, restored.size)
        // Favoritos primero, ordenados alfabéticamente: GitHub, luego Slack
        assertTrue(restored[0].isFavorite)
        assertEquals("GitHub", restored[0].issuer)
        assertTrue(restored[1].isFavorite)
        assertEquals("Slack", restored[1].issuer)

        // No favoritos después, ordenados alfabéticamente: Amazon, luego Zoom
        assertFalse(restored[2].isFavorite)
        assertEquals("Amazon", restored[2].issuer)
        assertFalse(restored[3].isFavorite)
        assertEquals("Zoom", restored[3].issuer)
    }
}
