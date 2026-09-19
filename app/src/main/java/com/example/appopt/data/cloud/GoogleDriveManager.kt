package com.example.appopt.data.cloud

import android.content.Context
import com.example.appopt.security.BackupCrypto
import com.example.appopt.util.DateTimeFormatter
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Gestor seguro y moderno para la sincronización con Google Drive API.
 *
 * Utiliza Google Identity Services (GIS) mediante [AuthorizationClient], la API estándar oficial
 * que reemplaza las interfaces obsoletas de GoogleSignIn.
 *
 * Características de seguridad:
 * - Ámbito estricto y seguro: `https://www.googleapis.com/auth/drive.appdata`.
 * - Almacenamiento aislado en la carpeta `appDataFolder` de Drive, invisible e inaccesible para terceros.
 * - Operaciones atómicas HTTP REST bajo TLS 1.3 con tokens OAuth2 efímeros.
 */
object GoogleDriveManager {

    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    const val MAX_BACKUP_VERSIONS = 3
    private const val BACKUP_FILE_PREFIX = "appopt_vault_backup_"
    private const val LEGACY_BACKUP_FILE_NAME = "appopt_vault_backup.json"

    /**
     * Excepción lanzada cuando se supera la cuota o tasa de peticiones a Google Drive (HTTP 429).
     */
    class RateLimitExceededException(message: String = "Demasiadas solicitudes a Google Drive (HTTP 429)") : Exception(message)

    /**
     * Excepción lanzada cuando la sesión de Google Drive expira o no cuenta con permisos (HTTP 401 / 403).
     */
    class UnauthorizedException(message: String = "Sesión de Google Drive expirada o no autorizada") : Exception(message)

    /**
     * Excepción lanzada cuando los servidores de Google Drive están temporalmente fuera de servicio (HTTP 503).
     */
    class ServiceUnavailableException(message: String = "El servicio de Google Drive no está disponible temporalmente") : Exception(message)

    /**
     * Valida de manera centralizada el código de respuesta HTTP de Google Drive.
     *
     * @param responseCode Código de estado HTTP devuelto por la API.
     * @param operation Nombre de la operación para el mensaje de diagnóstico.
     * @throws RateLimitExceededException Si el código es 429.
     * @throws UnauthorizedException Si el código es 401 o 403.
     * @throws ServiceUnavailableException Si el código es 503.
     * @throws IllegalStateException Si el código no está en el rango 200..299.
     */
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

    /**
     * Estructura de caché en memoria para archivos cifrados descargados desde Google Drive.
     */
    private data class CachedEncryptedFile(
        val fileId: String,
        val encryptedContent: String,
        val fetchedAtMillis: Long
    )

    /** Caché en memoria para evitar descargas redundantes de red ante reintentos de restauración. */
    private val downloadedFilesCache = java.util.concurrent.ConcurrentHashMap<String, CachedEncryptedFile>()

    /**
     * Purga y limpia la memoria caché de archivos cifrados descargados.
     */
    fun clearDownloadCache() {
        downloadedFilesCache.clear()
    }

    /**
     * Purga y limpia el token de sesión y la caché de Google Drive.
     */
    fun clearSession() {
        currentAccessToken = null
        clearDownloadCache()
    }

    /**
     * Cierra la sesión en Google Identity Services e invalida la credencial en caché del sistema operativo,
     * garantizando que la próxima conexión solicite explícitamente el selector de cuentas (Account Chooser).
     *
     * @param context Contexto de la aplicación.
     */
    @Suppress("DEPRECATION")
    fun signOut(context: Context) {
        clearSession()
        try {
            Identity.getSignInClient(context).signOut()
        } catch (_: Exception) {
            // Manejo silencioso defensivo si el cliente de Google no está disponible
        }
    }

    /**
     * Construye la solicitud moderna de autorización con alcance exclusivo a `appDataFolder`.
     */
    fun getAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_SCOPE)))
            .build()
    }

    /**
     * Obtiene el cliente moderno de autorización de Google Identity Services.
     *
     * @param context Contexto de la aplicación.
     * @return Instancia de [AuthorizationClient].
     */
    fun getAuthorizationClient(context: Context): AuthorizationClient {
        return Identity.getAuthorizationClient(context)
    }

    /**
     * Genera una clave criptográfica de 64 dígitos hexadecimales (256 bits de entropía) mediante [java.security.SecureRandom].
     */
    fun generate64DigitKey(): String {
        val randomBytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(randomBytes)
        return randomBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Sube una nueva versión de copia de seguridad sellada a Google Drive en `appDataFolder`.
     * Tras la subida exitosa, ejecuta la poda automática conservando únicamente las [MAX_BACKUP_VERSIONS] versiones más recientes.
     *
     * @param accessToken Token OAuth2 activo.
     * @param rawBackupJson Estructura JSON de cuentas a cifrar.
     * @param secretKeyPass Contraseña o clave de 64 dígitos en [CharArray].
     * @param emergencyMnemonic Frase mnemónica de 12 palabras opcional en [CharArray].
     * @param deviceId Identificador único persistente del dispositivo emisor para control de versiones.
     * @return [Result] exitoso si el archivo fue creado y podado correctamente.
     */
    /**
     * Sube una nueva versión de copia de seguridad sellada a Google Drive en `appDataFolder`.
     * Tras la subida exitosa, ejecuta la poda automática conservando únicamente las [MAX_BACKUP_VERSIONS] versiones más recientes.
     *
     * @param accessToken Token OAuth2 activo.
     * @param rawBackupJson Estructura JSON de cuentas a cifrar.
     * @param secretKeyPass Contraseña o clave de 64 dígitos en [CharArray] si se crea un nuevo respaldo.
     * @param emergencyMnemonic Frase mnemónica de 12 palabras opcional en [CharArray].
     * @param existingSession Sesión existente de clave simétrica y ranuras para sincronización en segundo plano.
     * @param deviceId Identificador único persistente del dispositivo emisor para control de versiones.
     * @return [Result] con [CloudVaultKeyStore.VaultKeySession] resultante para su custodia persistente.
     */
    suspend fun uploadBackup(
        accessToken: String,
        rawBackupJson: String,
        secretKeyPass: CharArray? = null,
        emergencyMnemonic: CharArray? = null,
        existingSession: CloudVaultKeyStore.VaultKeySession? = null,
        deviceId: String = ""
    ): Result<CloudVaultKeyStore.VaultKeySession> = withContext(Dispatchers.IO) {
        runCatching {
            val (encryptedBytes, session) = if (existingSession != null) {
                val bytes = BackupCrypto.encryptWithExistingKey(
                    plainJson = rawBackupJson,
                    vaultKey = existingSession.vaultKey,
                    mainSlot = existingSession.mainSlot,
                    emergencySlot = existingSession.emergencySlot
                )
                bytes to existingSession
            } else if (secretKeyPass != null) {
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
            } else {
                throw IllegalArgumentException("Se requiere una contraseña o una sesión activa de clave para cifrar el respaldo")
            }

            val encryptedEnvelopeString = String(encryptedBytes, StandardCharsets.UTF_8)
            val deviceName = "${android.os.Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} ${android.os.Build.MODEL}".trim()

            val fileName = "${BACKUP_FILE_PREFIX}${System.currentTimeMillis()}.json"
            val boundary = "=====AppOPTBoundary${System.currentTimeMillis()}====="
            val createUrl = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            val connection = (createUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                doOutput = true
            }

            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("description", deviceName)
                put("appProperties", JSONObject().apply {
                    put("deviceName", deviceName)
                    if (deviceId.isNotBlank()) {
                        put("deviceId", deviceId)
                    }
                })
                put("parents", org.json.JSONArray().apply { put("appDataFolder") })
            }.toString()

            try {
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write("--$boundary\r\n")
                    writer.write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                    writer.write(metadataJson)
                    writer.write("\r\n--$boundary\r\n")
                    writer.write("Content-Type: application/json\r\n\r\n")
                    writer.write(encryptedEnvelopeString)
                    writer.write("\r\n--$boundary--\r\n")
                    writer.flush()
                }

                val responseCode = connection.responseCode
                validateResponseCode(responseCode, "crear respaldo en Drive")
            } finally {
                connection.disconnect()
            }

            // Poda automática: eliminar copias que excedan el límite de retención
            pruneOldBackups(accessToken, MAX_BACKUP_VERSIONS)
            clearDownloadCache()

            session
        }
    }

    /**
     * Consulta y lista todas las versiones de copia de seguridad disponibles en Google Drive ordenadas de más reciente a más antigua.
     *
     * @param accessToken Token OAuth2 activo.
     * @return [Result] con la lista de [DriveBackupItem] disponibles.
     */
    suspend fun fetchAllBackups(accessToken: String): Result<List<DriveBackupItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedQuery = java.net.URLEncoder.encode("trashed = false and (name = '$LEGACY_BACKUP_FILE_NAME' or name contains '$BACKUP_FILE_PREFIX')", "UTF-8")
            val queryUrl = URL("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$encodedQuery&fields=files(id,name,modifiedTime,size,description,appProperties,trashed)&orderBy=modifiedTime+desc")
            val connection = (queryUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
            }

            val responseText = try {
                val responseCode = connection.responseCode
                validateResponseCode(responseCode, "listar copias de seguridad de Drive")

                BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use {
                    it.readText()
                }
            } finally {
                connection.disconnect()
            }

            val json = JSONObject(responseText)
            val filesArray = json.optJSONArray("files") ?: return@runCatching emptyList()

            val items = mutableListOf<DriveBackupItem>()
            for (i in 0 until filesArray.length()) {
                val fileObj = filesArray.getJSONObject(i)
                val id = fileObj.optString("id")
                if (id.isNullOrEmpty()) continue

                val name = fileObj.optString("name", "Copia de seguridad")
                val modifiedTimeStr = fileObj.optString("modifiedTime")
                val modifiedTimeMillis = DateTimeFormatter.parseIso8601ToMillis(modifiedTimeStr)
                val sizeBytes = fileObj.optLong("size", 0L)
                val appProps = fileObj.optJSONObject("appProperties")
                val deviceName = appProps?.optString("deviceName")?.ifEmpty { null }
                    ?: fileObj.optString("description").ifEmpty { "Dispositivo Android" }
                val deviceId = appProps?.optString("deviceId", "") ?: ""

                items.add(
                    DriveBackupItem(
                        fileId = id,
                        fileName = name,
                        modifiedTimeMillis = modifiedTimeMillis,
                        sizeBytes = sizeBytes,
                        deviceName = deviceName,
                        deviceId = deviceId,
                        isMostRecent = false
                    )
                )
            }

            // Ordenar de más reciente a más antigua
            val sorted = items.sortedByDescending { it.modifiedTimeMillis }
            if (sorted.isNotEmpty()) {
                sorted.mapIndexed { index, item ->
                    if (index == 0) item.copy(isMostRecent = true) else item
                }
            } else {
                emptyList()
            }
        }
    }

    /**
     * Elimina las copias de seguridad antiguas que excedan el límite de retención configurado.
     *
     * @param accessToken Token OAuth2 activo.
     * @param maxKeep Número máximo de versiones a conservar (por defecto [MAX_BACKUP_VERSIONS]).
     */
    suspend fun pruneOldBackups(accessToken: String, maxKeep: Int = MAX_BACKUP_VERSIONS): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val allBackups = fetchAllBackups(accessToken).getOrNull() ?: return@runCatching
            if (allBackups.size > maxKeep) {
                val toDelete = allBackups.drop(maxKeep)
                toDelete.forEach { backup ->
                    deleteBackupById(accessToken, backup.fileId)
                }
            }
        }
    }

    /**
     * Resultado detallado de descarga que incluye el JSON descifrado y la sesión de clave simétrica con ranuras.
     *
     * @property plainJson Contenido descifrado en formato JSON.
     * @property session Sesión de clave para su custodia en Android Keystore ([CloudVaultKeyStore]).
     */
    data class DownloadedBackupResult(
        val plainJson: String,
        val session: CloudVaultKeyStore.VaultKeySession
    )

    private fun fetchRawEncryptedContent(accessToken: String, fileId: String): String {
        val now = System.currentTimeMillis()
        val cached = downloadedFilesCache[fileId]
        if (cached != null && (now - cached.fetchedAtMillis < com.example.appopt.security.SecurityConfig.BACKUP_DOWNLOAD_CACHE_TTL_MILLIS)) {
            return cached.encryptedContent
        }
        val downloadUrl = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
        val connection = (downloadUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        val content = try {
            val responseCode = connection.responseCode
            validateResponseCode(responseCode, "descargar respaldo de Drive")

            BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.readText()
            }
        } finally {
            connection.disconnect()
        }
        downloadedFilesCache[fileId] = CachedEncryptedFile(fileId, content, now)
        return content
    }

    /**
     * Descarga y descifra una versión específica de copia de seguridad recuperando la sesión de clave y ranuras.
     *
     * @param accessToken Token OAuth2 activo.
     * @param fileId Identificador del archivo en Google Drive.
     * @param secretKeyPass Contraseña o PIN de descifrado en [CharArray].
     * @return [Result] con [DownloadedBackupResult].
     */
    suspend fun downloadBackupDetailedById(
        accessToken: String,
        fileId: String,
        secretKeyPass: CharArray
    ): Result<DownloadedBackupResult> = withContext(Dispatchers.IO) {
        runCatching {
            val content = fetchRawEncryptedContent(accessToken, fileId)
            val decryptedResult = BackupCrypto.decryptBackupDetailed(content.toByteArray(StandardCharsets.UTF_8), secretKeyPass)
            val payload = decryptedResult.getOrThrow()
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

    /**
     * Descarga y descifra una versión específica de copia de seguridad por su identificador único de archivo.
     *
     * @param accessToken Token OAuth2 activo.
     * @param fileId Identificador del archivo en Google Drive.
     * @param secretKeyPass Contraseña o PIN de descifrado en [CharArray].
     * @return [Result] con el contenido del respaldo en formato JSON descifrado.
     */
    suspend fun downloadBackupById(
        accessToken: String,
        fileId: String,
        secretKeyPass: CharArray
    ): Result<String> = withContext(Dispatchers.IO) {
        downloadBackupDetailedById(accessToken, fileId, secretKeyPass).map { it.plainJson }
    }

    /**
     * Descarga la copia de seguridad más reciente de Google Drive recuperando la sesión de clave y ranuras.
     *
     * @param accessToken Token OAuth2 activo.
     * @param secretKeyPass Contraseña o clave de descifrado en [CharArray].
     * @return [Result] con [DownloadedBackupResult].
     */
    suspend fun downloadBackupDetailed(
        accessToken: String,
        secretKeyPass: CharArray
    ): Result<DownloadedBackupResult> = withContext(Dispatchers.IO) {
        val backupsResult = fetchAllBackups(accessToken)
        val mostRecent = backupsResult.getOrNull()?.firstOrNull()
            ?: return@withContext Result.failure(NoSuchElementException("No se encontró ninguna copia de seguridad en tu Google Drive"))

        downloadBackupDetailedById(accessToken, mostRecent.fileId, secretKeyPass)
    }

    /**
     * Descarga la copia de seguridad más reciente de Google Drive.
     *
     * @param accessToken Token OAuth2 activo.
     * @param secretKeyPass Contraseña o clave de descifrado en [CharArray].
     * @return [Result] con el contenido JSON descifrado.
     */
    suspend fun downloadBackup(
        accessToken: String,
        secretKeyPass: CharArray
    ): Result<String> = withContext(Dispatchers.IO) {
        downloadBackupDetailed(accessToken, secretKeyPass).map { it.plainJson }
    }

    /**
     * Elimina permanentemente una versión específica de copia de seguridad en Google Drive.
     *
     * @param accessToken Token OAuth2 activo.
     * @param fileId Identificador del archivo a eliminar.
     * @return [Result] con éxito o fallo de la eliminación.
     */
    suspend fun deleteBackupById(accessToken: String, fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val deleteUrl = URL("https://www.googleapis.com/drive/v3/files/$fileId")
            val connection = (deleteUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $accessToken")
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode != 404) {
                    validateResponseCode(responseCode, "eliminar la copia en Google Drive")
                }
            } finally {
                connection.disconnect()
            }
            downloadedFilesCache.remove(fileId)
            Unit
        }
    }
}

/**
 * Metadatos descriptivos del archivo de respaldo en Google Drive.
 *
 * @property fileId Identificador único en Google Drive.
 * @property modifiedTimeMillis Marca de tiempo UNIX de modificación.
 * @property deviceName Modelo del dispositivo que originó la copia.
 */
data class DriveBackupInfo(
    val fileId: String,
    val modifiedTimeMillis: Long,
    val deviceName: String
)
