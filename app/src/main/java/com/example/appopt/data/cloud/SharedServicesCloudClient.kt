package com.example.appopt.data.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.appopt.AuthenticatorApp
import com.example.appopt.data.local.AppDatabase
import com.example.appopt.domain.model.IncomingSharedService
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
 * Resultado de una solicitud de brecha de códigos OTP efímeros en línea desde el servidor calculador (Directiva 31).
 *
 * Entrega una ventana corta de [SecurityConfig.SHARED_LIVE_CODE_WINDOW_STEPS] códigos consecutivos (por defecto 3)
 * para que el invitado visualice hasta 3 rotaciones seguidas con un solo toque y, al concluir el tercer código,
 * se oculte automáticamente volviendo al estado inicial de solicitud manual.
 *
 * @property code Código OTP de 6 u 8 dígitos para el paso inicial.
 * @property remainingSeconds Segundos restantes de vigencia del código inicial.
 * @property periodSeconds Período total de rotación en segundos.
 * @property expiresAtMillis Marca de tiempo en milisegundos en la que caduca el último código de la brecha.
 * @property codesByStep Mapa de pasos de tiempo (`epochSeconds / periodSeconds`) hacia su código OTP dentro de la brecha.
 */
data class LiveSharedCodeResult(
    val code: String,
    val remainingSeconds: Int,
    val periodSeconds: Int,
    val expiresAtMillis: Long = 0L,
    val codesByStep: Map<Long, String> = emptyMap()
) {
    /**
     * Obtiene el código OTP correspondiente al instante indicado dentro de la brecha de códigos,
     * o `null` si ya finalizaron los códigos de la ventana.
     *
     * @param nowMillis Marca de tiempo actual en milisegundos.
     * @return Código OTP vigente de 6 u 8 dígitos, o `null` si la brecha expiró.
     */
    fun getCodeAt(nowMillis: Long): String? {
        if (expiresAtMillis > 0L && nowMillis >= expiresAtMillis) return null
        if (codesByStep.isNotEmpty()) {
            val safePeriod = periodSeconds.coerceAtLeast(1)
            val currentStep = (nowMillis / 1000L) / safePeriod
            return codesByStep[currentStep]
        }
        return code
    }

    /**
     * Calcula el progreso actual dentro de la brecha de códigos (ej. `1 to 3`, `2 to 3`, `3 to 3`),
     * o `null` si la brecha ya concluyó.
     *
     * @param nowMillis Marca de tiempo actual en milisegundos.
     * @return Par `(pasoActualBase1, totalPasos)` o `null`.
     */
    fun getWindowStepProgress(nowMillis: Long): Pair<Int, Int>? {
        if (codesByStep.isEmpty()) return null
        if (expiresAtMillis > 0L && nowMillis >= expiresAtMillis) return null
        val safePeriod = periodSeconds.coerceAtLeast(1)
        val currentStep = (nowMillis / 1000L) / safePeriod
        val sortedSteps = codesByStep.keys.sorted()
        val idx = sortedSteps.indexOf(currentStep)
        return if (idx >= 0) (idx + 1) to sortedSteps.size else null
    }
}

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
     *
     * @param context Contexto de la aplicación.
     * @return `true` si existe red con acceso a Internet validado, `false` en caso contrario.
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
     *
     * @param context Contexto de la aplicación.
     * @param accountIds Conjunto de identificadores de cuentas locales eliminadas.
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
     *
     * Descifra el secreto de hardware únicamente en el instante de la petición y limpia el arreglo de bytes en `finally` (Directiva 9).
     *
     * @param accessToken Token OAuth2 activo de Google Identity Services.
     * @param account Cuenta TOTP propietaria a compartir.
     * @param guestEmail Correo de Google del usuario invitado.
     * @param accessMode Modo de acceso ([ShareAccessMode.ONLINE_ONLY] o [ShareAccessMode.OFFLINE_BATCH]).
     * @param batchDuration Duración del lote temporal cuando [accessMode] es [ShareAccessMode.OFFLINE_BATCH].
     * @return [Result] con el [OutgoingShareGrant] registrado en el servidor.
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

            parseSingleOutgoingGrantJson(JSONObject(responseBody))
        }
    }

    /**
     * Consulta la lista de accesos compartidos que el usuario propietario ha otorgado a terceros.
     *
     * @param accessToken Token OAuth2 activo.
     * @return [Result] con la lista de [OutgoingShareGrant].
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
     *
     * @param accessToken Token OAuth2 activo.
     * @return [Result] con la lista de [IncomingSharedService].
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
     *
     * @param accessToken Token OAuth2 activo del invitado.
     * @param grantId Identificador del permiso a aceptar.
     * @return [Result] indicando el éxito de la aceptación.
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
     *
     * @param accessToken Token OAuth2 activo.
     * @param grantId Identificador del permiso a revocar o abandonar.
     * @return [Result] indicando el éxito de la operación.
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
     * Solicita bajo demanda la brecha de [SecurityConfig.SHARED_LIVE_CODE_WINDOW_STEPS] códigos OTP consecutivos (por defecto 3)
     * para un servicio compartido conmigo.
     *
     * @param accessToken Token OAuth2 activo.
     * @param grantId Identificador del permiso compartido.
     * @return [Result] con [LiveSharedCodeResult] conteniendo la ventana de 3 códigos temporales.
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
                    codesByStep = codesMap
                )
            }
        }

    /**
     * Descarga un lote precalculado de códigos futuros cuando el propietario autorizó [ShareAccessMode.OFFLINE_BATCH].
     *
     * @param accessToken Token OAuth2 activo.
     * @param grantId Identificador del permiso compartido.
     * @return [Result] con el [SharedOtpBatch] temporal (sin semilla Base32).
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

    /**
     * Ejecuta una petición HTTP REST con reintento automático de renovación de token OAuth2 ante expiración (HTTP 401/403)
     * y liberación determinística de flujos y conexión (Directiva 9).
     */
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
     *
     * @param jsonString Cadena JSON devuelta por el endpoint `/api/shares/outgoing`.
     * @return Lista de [OutgoingShareGrant].
     */
    fun parseOutgoingGrantsJson(jsonString: String): List<OutgoingShareGrant> {
        val root = JSONObject(jsonString)
        reconcileCallerEmailIfPresent(root)
        val array = root.optJSONArray("grants") ?: return emptyList()
        val result = ArrayList<OutgoingShareGrant>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(parseSingleOutgoingGrantJson(item))
        }
        return result
    }

    private fun parseSingleOutgoingGrantJson(item: JSONObject): OutgoingShareGrant {
        return OutgoingShareGrant(
            grantId = item.getString("grantId"),
            accountId = item.optString("accountId", item.optString("serviceId", "")),
            issuer = item.optString("issuer", ""),
            accountName = item.optString("accountName", ""),
            guestEmail = item.getString("guestEmail"),
            accessMode = ShareAccessMode.fromWireValue(item.optString("accessMode")),
            batchHours = item.optInt("batchHours", ShareBatchDuration.TWENTY_FOUR_HOURS.hours),
            createdAtMillis = item.optLong("createdAtMillis", 0L),
            status = com.example.appopt.domain.model.ShareGrantStatus.fromWireValue(item.optString("status"))
        )
    }

    /**
     * Parsea una respuesta JSON de servicios recibidos hacia una lista inmutable de [IncomingSharedService],
     * garantizando que jamás se lea ni almacene una clave secreta.
     *
     * @param jsonString Cadena JSON devuelta por el endpoint `/api/shares/incoming`.
     * @return Lista de [IncomingSharedService].
     */
    fun parseIncomingServicesJson(jsonString: String): List<IncomingSharedService> {
        val root = JSONObject(jsonString)
        reconcileCallerEmailIfPresent(root)
        val array = root.optJSONArray("services") ?: return emptyList()
        val result = ArrayList<IncomingSharedService>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                IncomingSharedService(
                    grantId = item.getString("grantId"),
                    ownerEmail = item.getString("ownerEmail"),
                    issuer = item.optString("issuer", ""),
                    accountName = item.optString("accountName", ""),
                    digits = item.optInt("digits", SecurityConfig.DEFAULT_OTP_DIGITS),
                    period = item.optInt("period", SecurityConfig.DEFAULT_TOTP_PERIOD_SECONDS),
                    accessMode = ShareAccessMode.fromWireValue(item.optString("accessMode")),
                    batchHours = item.optInt("batchHours", ShareBatchDuration.TWENTY_FOUR_HOURS.hours),
                    status = com.example.appopt.domain.model.ShareGrantStatus.fromWireValue(item.optString("status"))
                )
            )
        }
        return result
    }
}
