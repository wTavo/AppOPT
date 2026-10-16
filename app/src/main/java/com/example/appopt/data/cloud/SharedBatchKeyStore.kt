package com.example.appopt.data.cloud

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.example.appopt.AuthenticatorApp
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.domain.model.OutgoingShareGrant
import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.domain.model.ShareBatchDuration
import com.example.appopt.domain.model.SharedOtpBatch
import com.example.appopt.security.SecurityConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /**
     * Contador reactivo de revisiones para sincronizar en tiempo real la sección de Inicio
     * cuando se agregan, aceptan o revocan permisos compartidos.
     */
    val cacheVersion: StateFlow<Long> = _cacheVersion.asStateFlow()

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(SecurityConfig.SHARED_BATCH_PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Guarda un lote precalculado cifrándolo con la clave maestra del Android Keystore.
     *
     * @param context Contexto de la aplicación.
     * @param batch Lote de códigos temporales a resguardar.
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
     *
     * @param context Contexto de la aplicación.
     * @param grantId Identificador del permiso compartido.
     * @param nowMillis Marca de tiempo actual en milisegundos.
     * @return [SharedOtpBatch] vigente, o `null` si no existe o caducó.
     */
    fun getValidBatch(
        context: Context,
        grantId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): SharedOtpBatch? {
        memoryCache[grantId]?.let { cached ->
            if (!cached.isExpired(nowMillis)) {
                return cached
            }
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
     *
     * @param context Contexto de la aplicación.
     * @param grantId Identificador del permiso revocado o caducado.
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
     *
     * @param context Contexto de la aplicación.
     */
    fun clearAllBatches(context: Context) {
        memoryCache.clear()
        getPrefs(context).edit { clear() }
        _cacheVersion.value++
    }

    /**
     * Guarda en caché local la lista de metadatos de servicios compartidos recibidos (sin secretos)
     * para permitir la consulta de lotes sin conexión vigentes cuando el dispositivo no tiene internet.
     *
     * @param context Contexto de la aplicación.
     * @param services Lista de [IncomingSharedService] recibidos.
     */
    fun saveCachedIncomingServices(context: Context, services: List<IncomingSharedService>) {
        val json = serializeIncomingServicesJson(services)
        getPrefs(context).edit {
            putString(SecurityConfig.SHARED_CACHED_INCOMING_KEY, json)
        }
        _cacheVersion.value++
    }

    /**
     * Serializa una lista de [IncomingSharedService] a una cadena JSON.
     *
     * @param services Lista de servicios entrantes.
     * @return Cadena JSON que representa la lista.
     */
    internal fun serializeIncomingServicesJson(services: List<IncomingSharedService>): String {
        val arr = JSONArray()
        for (s in services) {
            arr.put(
                JSONObject().apply {
                    put("grantId", s.grantId)
                    put("ownerEmail", s.ownerEmail)
                    put("issuer", s.issuer)
                    put("accountName", s.accountName)
                    put("digits", s.digits)
                    put("period", s.period)
                    put("accessMode", s.accessMode.name)
                    put("batchHours", s.batchHours)
                    put("status", s.status.name)
                    put("isFavorite", s.isFavorite)
                    put("orderIndex", s.orderIndex)
                }
            )
        }
        return arr.toString()
    }

    /**
     * Deserializa una cadena JSON a una lista inmutable de [IncomingSharedService].
     *
     * @param raw Cadena JSON cruda.
     * @return Lista parseada o lista vacía ante error.
     */
    internal fun deserializeIncomingServicesJson(raw: String): List<IncomingSharedService> {
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<IncomingSharedService>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    IncomingSharedService(
                        grantId = obj.getString("grantId"),
                        ownerEmail = obj.getString("ownerEmail"),
                        issuer = obj.optString("issuer", ""),
                        accountName = obj.optString("accountName", ""),
                        digits = obj.optInt("digits", SecurityConfig.DEFAULT_OTP_DIGITS),
                        period = obj.optInt("period", SecurityConfig.DEFAULT_TOTP_PERIOD_SECONDS),
                        accessMode = ShareAccessMode.fromWireValue(obj.optString("accessMode")),
                        batchHours = obj.optInt("batchHours", ShareBatchDuration.TWENTY_FOUR_HOURS.hours),
                        status = com.example.appopt.domain.model.ShareGrantStatus.fromWireValue(obj.optString("status")),
                        isFavorite = obj.optBoolean("isFavorite", false),
                        orderIndex = obj.optInt("orderIndex", 0)
                    )
                )
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
     *
     * @param context Contexto de la aplicación.
     * @return Lista de [IncomingSharedService] almacenada o lista vacía.
     */
    fun getCachedIncomingServices(context: Context): List<IncomingSharedService> {
        val raw = getPrefs(context).getString(SecurityConfig.SHARED_CACHED_INCOMING_KEY, null) ?: return emptyList()
        return deserializeIncomingServicesJson(raw)
    }

    /**
     * Alterna el estado de favorito de un servicio compartido recibido.
     *
     * @param context Contexto de la aplicación.
     * @param grantId Identificador único del permiso de servicio compartido.
     */
    fun toggleIncomingFavorite(context: Context, grantId: String) {
        val current = getCachedIncomingServices(context)
        val updated = current.map {
            if (it.grantId == grantId) it.copy(isFavorite = !it.isFavorite) else it
        }.sortedWith(
            compareByDescending<IncomingSharedService> { it.isFavorite }
                .thenBy { it.orderIndex }
                .thenBy { it.issuer.lowercase() }
        )
        saveCachedIncomingServices(context, updated)
    }

    /**
     * Guarda en caché local la lista de permisos otorgados por el propietario.
     *
     * @param context Contexto de la aplicación.
     * @param grants Lista de [OutgoingShareGrant] activos.
     */
    fun saveCachedOutgoingGrants(context: Context, grants: List<OutgoingShareGrant>) {
        val arr = JSONArray()
        for (g in grants) {
            arr.put(
                JSONObject().apply {
                    put("grantId", g.grantId)
                    put("accountId", g.accountId)
                    put("issuer", g.issuer)
                    put("accountName", g.accountName)
                    put("guestEmail", g.guestEmail)
                    put("accessMode", g.accessMode.name)
                    put("batchHours", g.batchHours)
                    put("createdAtMillis", g.createdAtMillis)
                    put("status", g.status.name)
                }
            )
        }
        getPrefs(context).edit {
            putString(SecurityConfig.SHARED_CACHED_OUTGOING_KEY, arr.toString())
        }
        _cacheVersion.value++
    }

    /**
     * Recupera de la caché local la lista de permisos otorgados por el propietario.
     *
     * @param context Contexto de la aplicación.
     * @return Lista de [OutgoingShareGrant] almacenada o lista vacía.
     */
    fun getCachedOutgoingGrants(context: Context): List<OutgoingShareGrant> {
        val raw = getPrefs(context).getString(SecurityConfig.SHARED_CACHED_OUTGOING_KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<OutgoingShareGrant>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    OutgoingShareGrant(
                        grantId = obj.getString("grantId"),
                        accountId = obj.optString("accountId", ""),
                        issuer = obj.optString("issuer", ""),
                        accountName = obj.optString("accountName", ""),
                        guestEmail = obj.getString("guestEmail"),
                        accessMode = ShareAccessMode.fromWireValue(obj.optString("accessMode")),
                        batchHours = obj.optInt("batchHours", ShareBatchDuration.TWENTY_FOUR_HOURS.hours),
                        createdAtMillis = obj.optLong("createdAtMillis", 0L),
                        status = com.example.appopt.domain.model.ShareGrantStatus.fromWireValue(obj.optString("status"))
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Serializa un [SharedOtpBatch] a una cadena JSON compacta.
     *
     * @param batch Lote de códigos temporales.
     * @return Representación JSON.
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
     *
     * @param jsonString Cadena JSON con la estructura del lote.
     * @return Instancia reconstruida de [SharedOtpBatch].
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
     * Sincroniza desde la nube los servicios compartidos entrantes y salientes, unificando el estado
     * con la caché local y preservando favoritos, índices de ordenación y ventanas de un solo uso activas.
     *
     * @param context Contexto de la aplicación.
     * @param token Token OAuth2 de Google con autorización vigente.
     * @return [Result] que confirma la sincronización exitosa o reporta el fallo de red.
     */
    suspend fun syncSharedServicesFromCloud(context: Context, token: String): Result<Unit> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val incomingRes = SharedServicesCloudClient.fetchIncomingServices(token)
                val outgoingRes = SharedServicesCloudClient.fetchOutgoingGrants(token)

                incomingRes.onSuccess { remoteList ->
                    val currentNow = System.currentTimeMillis()
                    val cachedIn = getCachedIncomingServices(context)
                    val existingFavoritesMap = cachedIn.associate { it.grantId to it.isFavorite }
                    val existingOrderMap = cachedIn.associate { it.grantId to it.orderIndex }
                    val activeSingleUse = cachedIn.filter { localSvc ->
                        val batch = getValidBatch(context, localSvc.grantId, currentNow)
                        batch != null && batch.isSingleUseWindow && !batch.isExpired(currentNow) &&
                            remoteList.none { it.grantId == localSvc.grantId }
                    }
                    val remoteMerged = remoteList.map { remoteSvc ->
                        val isFav = existingFavoritesMap[remoteSvc.grantId] ?: false
                        val orderIdx = existingOrderMap[remoteSvc.grantId] ?: remoteSvc.orderIndex
                        remoteSvc.copy(isFavorite = isFav, orderIndex = orderIdx)
                    }
                    saveCachedIncomingServices(context, remoteMerged + activeSingleUse)
                }

                outgoingRes.onSuccess { list ->
                    saveCachedOutgoingGrants(context, list)
                }

                if (incomingRes.isFailure) {
                    Result.failure(incomingRes.exceptionOrNull() ?: RuntimeException("Error al consultar servicios compartidos"))
                } else {
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
