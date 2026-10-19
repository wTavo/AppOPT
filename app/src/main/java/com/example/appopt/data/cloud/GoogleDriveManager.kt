package com.example.appopt.data.cloud

import android.content.Context
import com.example.appopt.AuthenticatorApp
import com.example.appopt.security.BackupCrypto
import com.example.appopt.security.SecurityConfig
import com.example.appopt.util.DateTimeFormatter
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestor seguro y moderno para la sincronización con Google Drive API.
 *
 * Características de seguridad:
 * - Ámbito estricto y seguro: `https://www.googleapis.com/auth/drive.appdata`.
 * - Almacenamiento aislado en la carpeta `appDataFolder` de Drive, invisible e inaccesible para terceros.
 * - Operaciones atómicas HTTP REST bajo TLS con timeouts estrictos de conexión y lectura.
 */
object GoogleDriveManager {

    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    const val EMAIL_SCOPE = "email"
    const val MAX_BACKUP_VERSIONS = 3
    private const val BACKUP_FILE_PREFIX = "appopt_vault_backup_"
    private const val LEGACY_BACKUP_FILE_NAME = "appopt_vault_backup.json"

    /** Valida de manera centralizada el código de respuesta HTTP de Google Drive y servicios en la nube. */
    fun validateResponseCode(responseCode: Int, operation: String) {
        when (responseCode) {
            in 200..299 -> return
            429 -> throw RateLimitExceededException()
            401, 403 -> throw UnauthorizedException("HTTP $responseCode")
            503 -> throw ServiceUnavailableException()
            else -> throw IllegalStateException("Error al $operation (HTTP $responseCode)")
        }
    }

    /** Token de acceso en memoria activo emitido por Google Identity Services. */
    @Volatile
    var currentAccessToken: String? = null

    @Volatile
    private var lastNetworkFetchMillis = 0L

    @Volatile
    private var cachedBackupItems: List<DriveBackupItem> = emptyList()

    /** Caché en memoria para evitar descargas redundantes de red ante reintentos de restauración. */
    private val downloadedFilesCache = ConcurrentHashMap<String, CachedEncryptedFile>()

    /** Purga y limpia la memoria caché tanto de la lista de versiones como de los archivos cifrados descargados. */
    fun clearDownloadCache() {
        downloadedFilesCache.clear()
        cachedBackupItems = emptyList()
        lastNetworkFetchMillis = 0L
    }

    /** Purga y limpia el token de sesión y la caché de Google Drive. */
    fun clearSession() {
        currentAccessToken = null
        clearDownloadCache()
    }

    /** Cierra la sesión activa limpiando el token OAuth2 en memoria y la caché de sincronización. */
    fun signOut(context: Context) {
        clearSession()
        SharedBatchKeyStore.clearAllBatches(context)
    }

    /** Construye la solicitud moderna de autorización con alcance a `appDataFolder` e identidad de correo. */
    fun getAuthorizationRequest(accountEmail: String? = null): AuthorizationRequest {
        val resolvedEmail = accountEmail ?: AuthenticatorApp.instance.preferencesManager.getConnectedDriveEmail()
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_SCOPE), Scope(EMAIL_SCOPE)))
        if (!resolvedEmail.isNullOrBlank()) {
            builder.setAccount(android.accounts.Account(resolvedEmail, "com.google"))
        }
        return builder.build()
    }

    /** Obtiene el cliente moderno de autorización de Google Identity Services. */
    fun getAuthorizationClient(context: Context): AuthorizationClient = Identity.getAuthorizationClient(context)

    /** Genera una clave criptográfica de 64 dígitos hexadecimales (256 bits de entropía) mediante [SecureRandom]. */
    fun generate64DigitKey(): String {
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        return randomBytes.joinToString("") { "%02x".format(it) }
    }

    private fun openAuthenticatedConnection(urlStr: String, method: String, accessToken: String): HttpURLConnection {
        return (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = SecurityConfig.GOOGLE_DRIVE_HTTP_CONNECT_TIMEOUT_MILLIS
            readTimeout = SecurityConfig.GOOGLE_DRIVE_HTTP_READ_TIMEOUT_MILLIS
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
    }

    /** Sube una nueva versión de copia de seguridad a Google Drive (Point-in-Time Recovery v2). */
    suspend fun uploadBackup(
        accessToken: String,
        rawBackupJson: String,
        secretKeyPass: CharArray? = null,
        emergencyMnemonic: CharArray? = null,
        existingSession: CloudVaultKeyStore.VaultKeySession? = null,
        deviceId: String = "",
        isEncrypted: Boolean = true,
        vaultHash: String = ""
    ): Result<CloudVaultKeyStore.VaultKeySession?> = withContext(Dispatchers.IO) {
        runCatching {
            val (encryptedBytes, session) = when {
                !isEncrypted -> BackupCrypto.createUnencryptedBackup(rawBackupJson) to null
                existingSession != null -> {
                    val bytes = BackupCrypto.encryptWithExistingKey(
                        plainJson = rawBackupJson,
                        vaultKey = existingSession.vaultKey,
                        mainSlot = existingSession.mainSlot,
                        emergencySlot = existingSession.emergencySlot
                    )
                    bytes to existingSession
                }
                secretKeyPass != null -> {
                    val encResult = BackupCrypto.encryptBackupResult(
                        plainJson = rawBackupJson,
                        primaryPassword = secretKeyPass,
                        emergencyMnemonic = emergencyMnemonic
                    )
                    val newSession = CloudVaultKeyStore.VaultKeySession(
                        vaultKey = encResult.vaultKey,
                        mainSlot = encResult.mainSlot,
                        emergencySlot = encResult.emergencySlot
                    )
                    encResult.envelopeBytes to newSession
                }
                else -> throw IllegalArgumentException("Se requiere una contraseña o una sesión activa de clave para cifrar el respaldo")
            }

            val encryptedEnvelopeString = String(encryptedBytes, StandardCharsets.UTF_8)
            val deviceName = "${android.os.Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} ${android.os.Build.MODEL}".trim()
            val fileName = "${BACKUP_FILE_PREFIX}${System.currentTimeMillis()}.json"
            val boundary = "=====AppOPTBoundary${System.currentTimeMillis()}====="

            val connection = openAuthenticatedConnection(
                urlStr = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart",
                method = "POST",
                accessToken = accessToken
            ).apply {
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                doOutput = true
            }

            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("description", deviceName)
                put("appProperties", JSONObject().apply {
                    put("deviceName", deviceName)
                    put("isEncrypted", isEncrypted.toString())
                    if (deviceId.isNotBlank()) put("deviceId", deviceId)
                    if (vaultHash.isNotBlank()) put("vaultHash", vaultHash)
                })
                put("parents", JSONArray().apply { put("appDataFolder") })
            }.toString()

            try {
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
                    writer.write(metadataJson)
                    writer.write("\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n")
                    writer.write(encryptedEnvelopeString)
                    writer.write("\r\n--$boundary--\r\n")
                    writer.flush()
                }
                validateResponseCode(connection.responseCode, "crear respaldo en Drive")
            } finally {
                connection.disconnect()
            }

            pruneOldBackups(accessToken, MAX_BACKUP_VERSIONS)
            clearDownloadCache()
            session
        }
    }

    /** Consulta y lista todas las versiones de copia de seguridad disponibles en Google Drive ordenadas de más reciente a más antigua. */
    suspend fun fetchAllBackups(
        accessToken: String,
        forceRefresh: Boolean = false
    ): Result<List<DriveBackupItem>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && (now - lastNetworkFetchMillis < SecurityConfig.BACKUP_CACHE_TTL_MILLIS) && cachedBackupItems.isNotEmpty()) {
            return@withContext Result.success(cachedBackupItems)
        }

        runCatching {
            val encodedQuery = URLEncoder.encode(
                "trashed = false and (name = '$LEGACY_BACKUP_FILE_NAME' or name contains '$BACKUP_FILE_PREFIX')",
                "UTF-8"
            )
            val connection = openAuthenticatedConnection(
                urlStr = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$encodedQuery&fields=files(id,name,modifiedTime,size,description,appProperties,trashed)&orderBy=modifiedTime+desc",
                method = "GET",
                accessToken = accessToken
            )

            val responseText = try {
                validateResponseCode(connection.responseCode, "listar copias de seguridad de Drive")
                BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
            } finally {
                connection.disconnect()
            }

            val filesArray = JSONObject(responseText).optJSONArray("files") ?: return@runCatching emptyList()
            val items = mutableListOf<DriveBackupItem>()
            for (i in 0 until filesArray.length()) {
                val fileObj = filesArray.getJSONObject(i)
                val id = fileObj.optString("id")
                if (id.isNullOrEmpty()) continue

                val appProps = fileObj.optJSONObject("appProperties")
                val deviceName = appProps?.optString("deviceName")?.ifEmpty { null }
                    ?: fileObj.optString("description").ifEmpty { "Dispositivo Android" }

                items.add(
                    DriveBackupItem(
                        fileId = id,
                        fileName = fileObj.optString("name", "Copia de seguridad"),
                        modifiedTimeMillis = DateTimeFormatter.parseIso8601ToMillis(fileObj.optString("modifiedTime")),
                        sizeBytes = fileObj.optLong("size", 0L),
                        deviceName = deviceName,
                        deviceId = appProps?.optString("deviceId", "").orEmpty(),
                        isMostRecent = false,
                        isEncrypted = appProps?.optString("isEncrypted")?.toBooleanStrictOrNull() ?: true,
                        vaultHash = appProps?.optString("vaultHash", "").orEmpty()
                    )
                )
            }

            val resultList = items.sortedByDescending { it.modifiedTimeMillis }.mapIndexed { index, item ->
                if (index == 0) item.copy(isMostRecent = true) else item
            }

            lastNetworkFetchMillis = System.currentTimeMillis()
            cachedBackupItems = resultList
            resultList
        }
    }

    /** Elimina las copias de seguridad antiguas que excedan el límite de retención configurado. */
    suspend fun pruneOldBackups(accessToken: String, maxKeep: Int = MAX_BACKUP_VERSIONS): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val allBackups = fetchAllBackups(accessToken).getOrNull() ?: return@runCatching
            if (allBackups.size > maxKeep) {
                allBackups.drop(maxKeep).forEach { backup -> deleteBackupById(accessToken, backup.fileId) }
            }
        }
    }

    private fun fetchRawEncryptedContent(accessToken: String, fileId: String): String {
        val now = System.currentTimeMillis()
        val cached = downloadedFilesCache[fileId]
        if (cached != null && (now - cached.fetchedAtMillis < SecurityConfig.BACKUP_CACHE_TTL_MILLIS)) {
            return cached.encryptedContent
        }
        val encodedFileId = URLEncoder.encode(fileId, "UTF-8")
        val connection = openAuthenticatedConnection(
            urlStr = "https://www.googleapis.com/drive/v3/files/$encodedFileId?alt=media",
            method = "GET",
            accessToken = accessToken
        )

        val content = try {
            validateResponseCode(connection.responseCode, "descargar respaldo de Drive")
            BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
        } finally {
            connection.disconnect()
        }
        downloadedFilesCache[fileId] = CachedEncryptedFile(fileId, content, now)
        return content
    }

    /** Descarga y descifra una versión específica de copia de seguridad recuperando la sesión de clave y ranuras si está cifrada. */
    suspend fun downloadBackupDetailedById(
        accessToken: String,
        fileId: String,
        secretKeyPass: CharArray? = null
    ): Result<DownloadedBackupResult> = withContext(Dispatchers.IO) {
        runCatching {
            val contentBytes = fetchRawEncryptedContent(accessToken, fileId).toByteArray(StandardCharsets.UTF_8)
            if (!BackupCrypto.isBackupEncrypted(contentBytes)) {
                val plainJson = BackupCrypto.readUnencryptedBackup(contentBytes).getOrThrow()
                DownloadedBackupResult(plainJson = plainJson, session = null)
            } else {
                requireNotNull(secretKeyPass) { "Se requiere una contraseña para descifrar la copia de seguridad" }
                val payload = BackupCrypto.decryptBackupDetailed(contentBytes, secretKeyPass).getOrThrow()
                DownloadedBackupResult(
                    plainJson = payload.plainJson,
                    session = CloudVaultKeyStore.VaultKeySession(
                        vaultKey = payload.vaultKey,
                        mainSlot = payload.mainSlot,
                        emergencySlot = payload.emergencySlot
                    )
                )
            }
        }
    }

    /** Descarga y descifra una versión específica de copia de seguridad por su identificador único de archivo. */
    suspend fun downloadBackupById(
        accessToken: String,
        fileId: String,
        secretKeyPass: CharArray? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        downloadBackupDetailedById(accessToken, fileId, secretKeyPass).map { it.plainJson }
    }

    /** Descarga la copia de seguridad más reciente de Google Drive recuperando la sesión de clave y ranuras. */
    suspend fun downloadBackupDetailed(
        accessToken: String,
        secretKeyPass: CharArray? = null
    ): Result<DownloadedBackupResult> = withContext(Dispatchers.IO) {
        val mostRecent = fetchAllBackups(accessToken).getOrNull()?.firstOrNull()
            ?: return@withContext Result.failure(NoSuchElementException("No se encontró ninguna copia de seguridad en tu Google Drive"))
        downloadBackupDetailedById(accessToken, mostRecent.fileId, secretKeyPass)
    }

    /** Elimina permanentemente una versión específica de copia de seguridad en Google Drive. */
    suspend fun deleteBackupById(accessToken: String, fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedFileId = URLEncoder.encode(fileId, "UTF-8")
            val connection = openAuthenticatedConnection(
                urlStr = "https://www.googleapis.com/drive/v3/files/$encodedFileId",
                method = "DELETE",
                accessToken = accessToken
            )
            try {
                val responseCode = connection.responseCode
                if (responseCode != 404) validateResponseCode(responseCode, "eliminar la copia en Google Drive")
            } finally {
                connection.disconnect()
            }
            downloadedFilesCache.remove(fileId)
            cachedBackupItems = emptyList()
            lastNetworkFetchMillis = 0L
        }
    }
}
