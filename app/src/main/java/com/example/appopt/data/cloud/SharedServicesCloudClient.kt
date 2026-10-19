package com.example.appopt.data.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.appopt.AuthenticatorApp
import com.example.appopt.data.local.AppDatabase
import com.example.appopt.domain.model.IncomingSharedService
import com.example.appopt.domain.model.LiveSharedCodeResult
import com.example.appopt.domain.model.OutgoingShareGrant
import com.example.appopt.domain.model.ShareAccessMode
import com.example.appopt.domain.model.ShareBatchDuration
import com.example.appopt.domain.model.SharedOtpBatch
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.domain.totp.Base32
import com.example.appopt.security.CryptoManager
import com.example.appopt.security.SecurityConfig
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Alias de compatibilidad hacia el modelo de dominio [LiveSharedCodeResult].
 */
typealias LiveSharedCodeResult = LiveSharedCodeResult

/**
 * Cliente HTTP REST seguro para el sistema de Servicios Compartidos Zero-Secret en Cloudflare Workers + D1 (Directivas 8, 9, 15, 18 y 31).
 *
 * Garantías arquitectónicas:
 * - Reutiliza la validación de códigos de estado HTTP de [GoogleDriveManager.validateResponseCode] (Principio DRY).
 * - Envuelve todos los flujos de entrada/salida en bloques `.use { ... }` y cierra la conexión en `finally` (Directiva 9).
 * - El dispositivo invitado jamás recibe ni procesa semillas Base32: únicamente metadatos, dígitos actuales o lotes temporales.
 */
object SharedServicesCloudClient {

    /**
     * Verifica de forma determinística si el dispositivo dispone de una conexión activa y validada a Internet.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Revoca automáticamente en la nube todos los permisos compartidos asociados a un conjunto de servicios propios
     * cuando el propietario los traslada a la papelera o los elimina definitivamente (Principio Zero Trust).
     */
    suspend fun revokeAllGrantsForAccountsIfPossible(
        context: Context,
        accountIds: Set<String>
    ): Unit = withContext(Dispatchers.IO) {
        if (accountIds.isEmpty()) return@withContext
        val prefsManager = AuthenticatorApp.instance.preferencesManager
        if (!prefsManager.isGoogleDriveConnected() || !isNetworkAvailable(context)) {
            return@withContext
        }

        runCatching {
            val token = GoogleDriveManager.currentAccessToken ?: run {
                val authClient = GoogleDriveManager.getAuthorizationClient(context)
                val authResult = Tasks.await(authClient.authorize(GoogleDriveManager.getAuthorizationRequest()))
                if (authResult.hasResolution() || authResult.accessToken == null) {
                    return@runCatching
                }
                authResult.accessToken!!.also { GoogleDriveManager.currentAccessToken = it }
            }

            val outgoing = fetchOutgoingGrants(token).getOrNull().orEmpty()
            val toRevoke = outgoing.filter { it.accountId in accountIds }
            if (toRevoke.isEmpty()) return@runCatching

            toRevoke.forEach { grant ->
                revokeGrant(token, grant.grantId)
            }

            val remaining = outgoing.filterNot { it.accountId in accountIds }
            SharedBatchKeyStore.saveCachedOutgoingGrants(context, remaining)
        }
    }

    /**
     * Comparte un servicio TOTP o actualiza los permisos de acceso para un correo invitado.
     * Descifra el secreto de hardware únicamente en el instante de la petición y limpia el arreglo de bytes en `finally` (Directiva 9).
     */
    suspend fun shareService(
        accessToken: String,
        account: TotpAccount,
        guestEmail: String,
        accessMode: ShareAccessMode,
        batchDuration: ShareBatchDuration
    ): Result<OutgoingShareGrant> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanEmail = guestEmail.trim().lowercase()
            require(cleanEmail.contains("@") && cleanEmail.contains(".")) {
                "Formato de correo inválido"
            }

            val app = AuthenticatorApp.instance
            val entity = AppDatabase.getInstance(app).accountDao().getAccountById(account.id)
                ?: throw IllegalStateException("Cuenta no encontrada en la bóveda local")

            var secretBytes: ByteArray? = null
            val payloadJson = try {
                secretBytes = app.cryptoManager.decrypt(entity.encryptedSecret, entity.iv)
                val secretBase32 = Base32.encode(secretBytes)
                JSONObject().apply {
                    put("serviceId", account.id)
                    put("issuer", account.issuer)
                    put("accountName", account.accountName)
                    put("secretBase32", secretBase32)
                    put("algorithm", account.algorithm.name)
                    put("digits", account.digits)
                    put("period", account.period)
                    put("guestEmail", cleanEmail)
                    put("accessMode", accessMode.name)
                    put("batchHours", batchDuration.hours)
                }.toString()
            } finally {
                secretBytes?.let { CryptoManager.zeroize(it) }
            }

            val responseBody = executeHttpRequest(
                method = "POST",
                path = "/api/shares",
                accessToken = accessToken,
                operationName = "compartir servicio",
                requestBody = payloadJson
            )

            SharedBatchKeyStore.parseOutgoingGrantJsonObject(JSONObject(responseBody))
        }
    }

    /**
     * Consulta la lista de accesos compartidos que el usuario propietario ha otorgado a terceros.
     */
    suspend fun fetchOutgoingGrants(accessToken: String): Result<List<OutgoingShareGrant>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val responseBody = executeHttpRequest(
                    method = "GET",
                    path = "/api/shares/outgoing",
                    accessToken = accessToken,
                    operationName = "consultar accesos compartidos"
                )
                parseOutgoingGrantsJson(responseBody)
            }
        }

    /**
     * Consulta los servicios que otros propietarios han compartido con el usuario actual (sin claves secretas).
     */
    suspend fun fetchIncomingServices(accessToken: String): Result<List<IncomingSharedService>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val responseBody = executeHttpRequest(
                    method = "GET",
                    path = "/api/shares/incoming",
                    accessToken = accessToken,
                    operationName = "consultar servicios recibidos"
                )
                parseIncomingServicesJson(responseBody)
            }
        }

    /**
     * Acepta explícitamente una invitación de servicio compartido en el dispositivo invitado (B).
     */
    suspend fun acceptInvitation(accessToken: String, grantId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedId = URLEncoder.encode(grantId, StandardCharsets.UTF_8.name())
                executeHttpRequest(
                    method = "POST",
                    path = "/api/shares/$encodedId/accept",
                    accessToken = accessToken,
                    operationName = "aceptar invitación compartida"
                )
                Unit
            }
        }

    /**
     * Revoca de forma inmediata el permiso de acceso de un invitado (desde el propietario A)
     * o rechaza/abandona voluntariamente un servicio compartido (desde el invitado B).
     */
    suspend fun revokeGrant(accessToken: String, grantId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedId = URLEncoder.encode(grantId, StandardCharsets.UTF_8.name())
                executeHttpRequest(
                    method = "DELETE",
                    path = "/api/shares/$encodedId",
                    accessToken = accessToken,
                    operationName = "revocar acceso compartido"
                )
                Unit
            }
        }

    /**
     * Solicita bajo demanda la brecha de [SecurityConfig.SHARED_LIVE_CODE_WINDOW_STEPS] códigos OTP consecutivos
     * para un servicio compartido conmigo.
     */
    suspend fun requestLiveCode(accessToken: String, grantId: String): Result<LiveSharedCodeResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedId = URLEncoder.encode(grantId, StandardCharsets.UTF_8.name())
                val responseBody = executeHttpRequest(
                    method = "POST",
                    path = "/api/shares/$encodedId/code",
                    accessToken = accessToken,
                    operationName = "obtener código compartido"
                )
                val obj = JSONObject(responseBody)
                val periodSeconds = obj.optInt("periodSeconds", SecurityConfig.DEFAULT_TOTP_PERIOD_SECONDS).coerceAtLeast(1)
                val remainingSeconds = obj.optInt("remainingSeconds", periodSeconds)
                val codesObj = obj.optJSONObject("codesByStep")
                val codesMap = HashMap<Long, String>()
                if (codesObj != null) {
                    val keys = codesObj.keys()
                    while (keys.hasNext()) {
                        val stepKey = keys.next()
                        val stepLong = stepKey.toLongOrNull() ?: continue
                        val stepCode = codesObj.optString(stepKey, "")
                        if (stepCode.isNotBlank()) {
                            codesMap[stepLong] = stepCode
                        }
                    }
                }
                val expiresAtMillis = if (obj.has("expiresAtMillis")) {
                    obj.optLong("expiresAtMillis", 0L)
                } else if (codesMap.isNotEmpty()) {
                    (codesMap.keys.max() + 1L) * periodSeconds * 1000L
                } else {
                    System.currentTimeMillis() + (remainingSeconds * 1000L)
                }
                LiveSharedCodeResult(
                    code = obj.getString("code"),
                    remainingSeconds = remainingSeconds,
                    periodSeconds = periodSeconds,
                    expiresAtMillis = expiresAtMillis,
                    codesByStep = codesMap,
                    singleUseConsumed = obj.optBoolean("singleUseConsumed", false)
                )
            }
        }

    /**
     * Descarga un lote precalculado de códigos futuros cuando el propietario autorizó [ShareAccessMode.OFFLINE_BATCH].
     */
    suspend fun downloadPrecomputedBatch(accessToken: String, grantId: String): Result<SharedOtpBatch> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedId = URLEncoder.encode(grantId, StandardCharsets.UTF_8.name())
                val responseBody = executeHttpRequest(
                    method = "POST",
                    path = "/api/shares/$encodedId/batch",
                    accessToken = accessToken,
                    operationName = "descargar lote sin conexión"
                )
                SharedBatchKeyStore.deserializeBatch(responseBody)
            }
        }

    private fun executeHttpRequest(
        method: String,
        path: String,
        accessToken: String,
        operationName: String,
        requestBody: String? = null
    ): String {
        return try {
            executeSingleHttpRequest(method, path, accessToken, operationName, requestBody)
        } catch (e: UnauthorizedException) {
            GoogleDriveManager.currentAccessToken = null
            val refreshedToken = runCatching {
                val app = AuthenticatorApp.instance
                val authClient = GoogleDriveManager.getAuthorizationClient(app)
                val authResult = Tasks.await(authClient.authorize(GoogleDriveManager.getAuthorizationRequest()))
                if (!authResult.hasResolution()) authResult.accessToken else null
            }.getOrNull()

            if (!refreshedToken.isNullOrBlank() && refreshedToken != accessToken) {
                GoogleDriveManager.currentAccessToken = refreshedToken
                executeSingleHttpRequest(method, path, refreshedToken, operationName, requestBody)
            } else {
                throw e
            }
        }
    }

    private fun executeSingleHttpRequest(
        method: String,
        path: String,
        accessToken: String,
        operationName: String,
        requestBody: String? = null
    ): String {
        val url = URL("${SecurityConfig.SHARED_SERVICES_API_BASE_URL}$path")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = SecurityConfig.SHARED_SERVICES_HTTP_TIMEOUT_MILLIS
            readTimeout = SecurityConfig.SHARED_SERVICES_HTTP_TIMEOUT_MILLIS
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (requestBody != null) {
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                doOutput = true
            }
        }

        try {
            if (requestBody != null) {
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(requestBody)
                    writer.flush()
                }
            }
            GoogleDriveManager.validateResponseCode(connection.responseCode, operationName)
            return BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun reconcileCallerEmailIfPresent(root: JSONObject) {
        val callerEmail = root.optString("callerEmail", "").trim()
        if (callerEmail.isNotEmpty() && callerEmail.contains("@")) {
            runCatching {
                val prefs = AuthenticatorApp.instance.preferencesManager
                if (prefs.getConnectedDriveEmail().isNullOrBlank()) {
                    prefs.setConnectedDriveEmail(callerEmail)
                    prefs.setLastKnownDriveEmail(callerEmail)
                }
            }
        }
    }

    /**
     * Parsea una respuesta JSON de permisos salientes hacia una lista inmutable de [OutgoingShareGrant].
     */
    fun parseOutgoingGrantsJson(jsonString: String): List<OutgoingShareGrant> {
        val root = JSONObject(jsonString)
        reconcileCallerEmailIfPresent(root)
        val array = root.optJSONArray("grants") ?: return emptyList()
        val result = ArrayList<OutgoingShareGrant>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(SharedBatchKeyStore.parseOutgoingGrantJsonObject(item))
        }
        return result
    }

    /**
     * Parsea una respuesta JSON de servicios recibidos hacia una lista inmutable de [IncomingSharedService].
     */
    fun parseIncomingServicesJson(jsonString: String): List<IncomingSharedService> {
        val root = JSONObject(jsonString)
        reconcileCallerEmailIfPresent(root)
        val array = root.optJSONArray("services") ?: return emptyList()
        val result = ArrayList<IncomingSharedService>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(SharedBatchKeyStore.parseIncomingServiceJsonObject(item))
        }
        return result
    }
}
