package com.example.appopt.data.cloud

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.example.appopt.AuthenticatorApp
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.domain.model.OutgoingShareGrant
import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.domain.model.ShareBatchDuration
import com.example.appopt.domain.model.ShareGrantStatus
import com.example.appopt.domain.model.SharedOtpBatch
import com.example.appopt.security.SecurityConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Almacén seguro de lotes temporales de códigos OTP precalculados para servicios compartidos (Directivas 6, 9 y 31).
 *
 * Garantías de seguridad:
 * - Cifra cada lote en reposo mediante [com.example.appopt.security.CryptoManager] respaldado por el Android Keystore (AES-256-GCM).
 * - Jamás almacena semillas Base32: únicamente pares temporales `(timeStep -> código)` que caducan irreversiblemente.
 * - Destruye automáticamente lotes vencidos o revocados y purga su caché en RAM ante transiciones a segundo plano.
 */
object SharedBatchKeyStore {

    private val memoryCache = ConcurrentHashMap<String, SharedOtpBatch>()
    private val _cacheVersion = MutableStateFlow(0L)

    @Volatile
    private var lastIncomingSyncMillis = 0L

    /**
     * Contador reactivo de revisiones para sincronizar en tiempo real la sección de Inicio
     * cuando se agregan, aceptan o revocan permisos compartidos.
     */
    val cacheVersion: StateFlow<Long> = _cacheVersion.asStateFlow()

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(SecurityConfig.SHARED_BATCH_PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Guarda un lote precalculado cifrándolo con la clave maestra del Android Keystore.
     */
    fun saveBatch(context: Context, batch: SharedOtpBatch) {
        memoryCache[batch.grantId] = batch
        val plainBytes = serializeBatch(batch).toByteArray(StandardCharsets.UTF_8)
        try {
            val encrypted = AuthenticatorApp.instance.cryptoManager.encrypt(plainBytes)
            val cipherB64 = Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP)
            val ivB64 = Base64.encodeToString(encrypted.iv, Base64.NO_WRAP)
            getPrefs(context).edit {
                putString("${SecurityConfig.SHARED_BATCH_KEY_PREFIX}${batch.grantId}_ct", cipherB64)
                putString("${SecurityConfig.SHARED_BATCH_KEY_PREFIX}${batch.grantId}_iv", ivB64)
            }
            _cacheVersion.value++
        } finally {
            plainBytes.fill(0)
        }
    }

    /**
     * Recupera un lote precalculado válido para el permiso indicado, o lo destruye si ha expirado.
     */
    fun getValidBatch(
        context: Context,
        grantId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): SharedOtpBatch? {
        memoryCache[grantId]?.let { cached ->
            if (!cached.isExpired(nowMillis)) return cached
            removeBatch(context, grantId)
            return null
        }

        val prefs = getPrefs(context)
        val cipherB64 = prefs.getString("${SecurityConfig.SHARED_BATCH_KEY_PREFIX}${grantId}_ct", null) ?: return null
        val ivB64 = prefs.getString("${SecurityConfig.SHARED_BATCH_KEY_PREFIX}${grantId}_iv", null) ?: return null

        return try {
            val decryptedBytes = AuthenticatorApp.instance.cryptoManager.decrypt(
                ciphertext = Base64.decode(cipherB64, Base64.NO_WRAP),
                iv = Base64.decode(ivB64, Base64.NO_WRAP)
            )
            val jsonString = String(decryptedBytes, StandardCharsets.UTF_8)
            decryptedBytes.fill(0)

            val batch = deserializeBatch(jsonString)
            if (batch.isExpired(nowMillis)) {
                removeBatch(context, grantId)
                null
            } else {
                memoryCache[grantId] = batch
                batch
            }
        } catch (_: Exception) {
            removeBatch(context, grantId)
            null
        }
    }

    /**
     * Elimina de forma inmediata y permanente el lote asociado a un permiso (ej. tras revocación).
     */
    fun removeBatch(context: Context, grantId: String) {
        memoryCache.remove(grantId)
        getPrefs(context).edit {
            remove("${SecurityConfig.SHARED_BATCH_KEY_PREFIX}${grantId}_ct")
            remove("${SecurityConfig.SHARED_BATCH_KEY_PREFIX}${grantId}_iv")
        }
        _cacheVersion.value++
    }

    /**
     * Limpia la caché volátil en memoria RAM cuando la aplicación pasa a segundo plano (Directiva 9).
     */
    fun clearMemoryCache() {
        memoryCache.clear()
    }

    /**
     * Elimina todos los lotes y metadatos almacenados tanto en memoria como en disco al desvincular la cuenta.
     */
    fun clearAllBatches(context: Context) {
        memoryCache.clear()
        getPrefs(context).edit { clear() }
        _cacheVersion.value++
    }

    /**
     * Guarda en caché local la lista de metadatos de servicios compartidos recibidos aplicando Diff-Before-Write.
     */
    fun saveCachedIncomingServices(context: Context, services: List<IncomingSharedService>) {
        if (getCachedIncomingServices(context) == services) return
        val json = serializeIncomingServicesJson(services)
        getPrefs(context).edit { putString(SecurityConfig.SHARED_CACHED_INCOMING_KEY, json) }
        _cacheVersion.value++
    }

    /**
     * Parsea un objeto JSON individual hacia [IncomingSharedService].
     */
    internal fun parseIncomingServiceJsonObject(obj: JSONObject): IncomingSharedService {
        return IncomingSharedService(
            grantId = obj.getString("grantId"),
            ownerEmail = obj.getString("ownerEmail"),
            issuer = obj.optString("issuer", ""),
            accountName = obj.optString("accountName", ""),
            digits = obj.optInt("digits", SecurityConfig.DEFAULT_OTP_DIGITS),
            period = obj.optInt("period", SecurityConfig.DEFAULT_TOTP_PERIOD_SECONDS),
            accessMode = ShareAccessMode.fromWireValue(obj.optString("accessMode")),
            batchHours = obj.optInt("batchHours", ShareBatchDuration.TWENTY_FOUR_HOURS.hours),
            status = ShareGrantStatus.fromWireValue(obj.optString("status")),
            isFavorite = obj.optBoolean("isFavorite", false),
            orderIndex = obj.optInt("orderIndex", 0)
        )
    }

    /**
     * Parsea un objeto JSON individual hacia [OutgoingShareGrant].
     */
    internal fun parseOutgoingGrantJsonObject(obj: JSONObject): OutgoingShareGrant {
        return OutgoingShareGrant(
            grantId = obj.getString("grantId"),
            accountId = obj.optString("accountId", obj.optString("serviceId", "")),
            issuer = obj.optString("issuer", ""),
            accountName = obj.optString("accountName", ""),
            guestEmail = obj.getString("guestEmail"),
            accessMode = ShareAccessMode.fromWireValue(obj.optString("accessMode")),
            batchHours = obj.optInt("batchHours", ShareBatchDuration.TWENTY_FOUR_HOURS.hours),
            createdAtMillis = obj.optLong("createdAtMillis", 0L),
            status = ShareGrantStatus.fromWireValue(obj.optString("status"))
        )
    }

    /**
     * Serializa una lista de [IncomingSharedService] a una cadena JSON.
     */
    internal fun serializeIncomingServicesJson(services: List<IncomingSharedService>): String {
        val arr = JSONArray()
        for (s in services) {
            arr.put(
                JSONObject().apply {
                    put("grantId", s.grantId); put("ownerEmail", s.ownerEmail); put("issuer", s.issuer)
                    put("accountName", s.accountName); put("digits", s.digits); put("period", s.period)
                    put("accessMode", s.accessMode.name); put("batchHours", s.batchHours); put("status", s.status.name)
                    put("isFavorite", s.isFavorite); put("orderIndex", s.orderIndex)
                }
            )
        }
        return arr.toString()
    }

    /**
     * Deserializa una cadena JSON a una lista inmutable de [IncomingSharedService].
     */
    internal fun deserializeIncomingServicesJson(raw: String): List<IncomingSharedService> {
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<IncomingSharedService>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(parseIncomingServiceJsonObject(obj))
            }
            list.sortedWith(
                compareByDescending<IncomingSharedService> { it.isFavorite }
                    .thenBy { it.orderIndex }
                    .thenBy { it.issuer.lowercase() }
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Recupera de la caché local los metadatos de servicios compartidos recibidos.
     */
    fun getCachedIncomingServices(context: Context): List<IncomingSharedService> {
        val raw = getPrefs(context).getString(SecurityConfig.SHARED_CACHED_INCOMING_KEY, null) ?: return emptyList()
        return deserializeIncomingServicesJson(raw)
    }

    /**
     * Alterna el estado de favorito de un servicio compartido recibido.
     */
    fun toggleIncomingFavorite(context: Context, grantId: String) {
        val updated = getCachedIncomingServices(context).map {
            if (it.grantId == grantId) it.copy(isFavorite = !it.isFavorite) else it
        }.sortedWith(
            compareByDescending<IncomingSharedService> { it.isFavorite }
                .thenBy { it.orderIndex }
                .thenBy { it.issuer.lowercase() }
        )
        saveCachedIncomingServices(context, updated)
    }

    /**
     * Guarda en caché local la lista de permisos otorgados por el propietario aplicando Diff-Before-Write.
     */
    fun saveCachedOutgoingGrants(context: Context, grants: List<OutgoingShareGrant>) {
        if (getCachedOutgoingGrants(context) == grants) return
        val arr = JSONArray()
        for (g in grants) {
            arr.put(
                JSONObject().apply {
                    put("grantId", g.grantId); put("accountId", g.accountId); put("issuer", g.issuer)
                    put("accountName", g.accountName); put("guestEmail", g.guestEmail); put("accessMode", g.accessMode.name)
                    put("batchHours", g.batchHours); put("createdAtMillis", g.createdAtMillis); put("status", g.status.name)
                }
            )
        }
        getPrefs(context).edit { putString(SecurityConfig.SHARED_CACHED_OUTGOING_KEY, arr.toString()) }
        _cacheVersion.value++
    }

    /**
     * Recupera de la caché local la lista de permisos otorgados por el propietario.
     */
    fun getCachedOutgoingGrants(context: Context): List<OutgoingShareGrant> {
        val raw = getPrefs(context).getString(SecurityConfig.SHARED_CACHED_OUTGOING_KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<OutgoingShareGrant>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(parseOutgoingGrantJsonObject(obj))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Serializa un [SharedOtpBatch] a una cadena JSON compacta.
     */
    fun serializeBatch(batch: SharedOtpBatch): String {
        val codesObj = JSONObject()
        for ((step, code) in batch.codesByStep) {
            codesObj.put(step.toString(), code)
        }
        return JSONObject().apply {
            put("grantId", batch.grantId)
            put("periodSeconds", batch.periodSeconds)
            put("expiresAtMillis", batch.expiresAtMillis)
            put("isSingleUseWindow", batch.isSingleUseWindow)
            put("codesByStep", codesObj)
        }.toString()
    }

    /**
     * Deserializa un [SharedOtpBatch] a partir de su representación JSON.
     */
    fun deserializeBatch(jsonString: String): SharedOtpBatch {
        val root = JSONObject(jsonString)
        val grantId = root.getString("grantId")
        val periodSeconds = root.optInt("periodSeconds", SecurityConfig.DEFAULT_TOTP_PERIOD_SECONDS)
        val expiresAtMillis = root.getLong("expiresAtMillis")
        val isSingleUseWindow = root.optBoolean("isSingleUseWindow", false)
        val codesObj = root.optJSONObject("codesByStep") ?: JSONObject()
        val codesMap = HashMap<Long, String>(codesObj.length())

        val keys = codesObj.keys()
        while (keys.hasNext()) {
            val stepKey = keys.next()
            val stepLong = stepKey.toLongOrNull() ?: continue
            val codeValue = codesObj.optString(stepKey, "")
            if (codeValue.isNotBlank()) {
                codesMap[stepLong] = codeValue
            }
        }

        return SharedOtpBatch(
            grantId = grantId,
            periodSeconds = periodSeconds,
            expiresAtMillis = expiresAtMillis,
            codesByStep = codesMap,
            isSingleUseWindow = isSingleUseWindow
        )
    }

    /**
     * Sincroniza exclusivamente los servicios entrantes aplicando Diff-Before-Write, purga de lotes revocados y cooldown.
     *
     * @param context Contexto de la aplicación.
     * @param token Token de acceso a la nube.
     * @param forceRefresh Fuerza la consulta ignorando el cooldown de 5 segundos.
     */
    suspend fun syncIncomingServicesFromCloud(
        context: Context,
        token: String,
        forceRefresh: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && (now - lastIncomingSyncMillis) < SecurityConfig.SHARED_SERVICES_MIN_SYNC_COOLDOWN_MILLIS) {
            return@withContext Result.success(false)
        }
        runCatching {
            val remoteList = SharedServicesCloudClient.fetchIncomingServices(token).getOrThrow()
            lastIncomingSyncMillis = System.currentTimeMillis()
            val cachedIn = getCachedIncomingServices(context)
            val existingFavoritesMap = cachedIn.associate { it.grantId to it.isFavorite }
            val existingOrderMap = cachedIn.associate { it.grantId to it.orderIndex }
            val activeSingleUse = cachedIn.filter { localSvc ->
                val batch = getValidBatch(context, localSvc.grantId, lastIncomingSyncMillis)
                batch != null && batch.isSingleUseWindow && !batch.isExpired(lastIncomingSyncMillis) &&
                    remoteList.none { it.grantId == localSvc.grantId }
            }
            val remoteMerged = remoteList.map { remoteSvc ->
                val isFav = existingFavoritesMap[remoteSvc.grantId] ?: false
                val orderIdx = existingOrderMap[remoteSvc.grantId] ?: remoteSvc.orderIndex
                remoteSvc.copy(isFavorite = isFav, orderIndex = orderIdx)
            }
            val finalIncoming = remoteMerged + activeSingleUse

            // Purgar lotes en memoria y disco para permisos revocados
            val finalGrantIds = finalIncoming.map { it.grantId }.toSet()
            cachedIn.filter { it.grantId !in finalGrantIds }.forEach { removeBatch(context, it.grantId) }

            if (cachedIn != finalIncoming) {
                saveCachedIncomingServices(context, finalIncoming)
                true
            } else {
                false
            }
        }
    }

    /**
     * Sincroniza desde la nube los servicios compartidos entrantes y salientes para sincronización completa.
     */
    suspend fun syncSharedServicesFromCloud(context: Context, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        val incomingResult = syncIncomingServicesFromCloud(context, token, forceRefresh = true)
        val outgoingRes = SharedServicesCloudClient.fetchOutgoingGrants(token)
        outgoingRes.onSuccess { saveCachedOutgoingGrants(context, it) }
        if (incomingResult.isFailure) {
            Result.failure(incomingResult.exceptionOrNull() ?: RuntimeException("Error al consultar servicios compartidos"))
        } else {
            Result.success(Unit)
        }
    }
}
