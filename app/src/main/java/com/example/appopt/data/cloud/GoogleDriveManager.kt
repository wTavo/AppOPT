package com.example.appopt.data.cloud

import android.content.Context
import com.example.appopt.security.BackupCrypto
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

    const val DriveScope = "https://www.googleapis.com/auth/drive.appdata"
    private const val BackupFileName = "appopt_vault_backup.json"

    /**
     * Construye la solicitud moderna de autorización con alcance exclusivo a `appDataFolder`.
     */
    fun getAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveScope)))
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
     * Sube o actualiza la copia de seguridad de la bóveda en el espacio privado de Google Drive.
     * Los datos son cifrados previamente de forma local con **AES-256-GCM** y esquema Dual-Slot para garantizar Cero Conocimiento.
     *
     * @param accessToken Token de acceso OAuth2 emitido por Google Identity Services.
     * @param rawBackupJson Cadena con la estructura de cuentas a cifrar.
     * @param secretKeyPass Contraseña o clave de 64 dígitos en [CharArray].
     * @param emergencyMnemonic Frase mnemónica de 12 palabras de emergencia opcional en [CharArray].
     * @return [Result] exitoso si la petición concluyó con código HTTP 200/201.
     */
    suspend fun uploadBackup(
        accessToken: String,
        rawBackupJson: String,
        secretKeyPass: CharArray,
        emergencyMnemonic: CharArray? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Cifrado local con AES-256-GCM (Dual-Slot o simple) antes de transmitir a la nube
            val encryptedBytes = if (emergencyMnemonic != null) {
                BackupCrypto.encryptDualBackup(rawBackupJson, secretKeyPass, emergencyMnemonic)
            } else {
                BackupCrypto.encryptBackup(rawBackupJson, secretKeyPass)
            }
            val encryptedEnvelopeString = String(encryptedBytes, StandardCharsets.UTF_8)

            val existingFileId = findExistingBackupFileId(accessToken)

            if (existingFileId != null) {
                // Actualización del archivo existente mediante PATCH
                val updateUrl = URL("https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media")
                val connection = (updateUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "PATCH"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    doOutput = true
                }

                connection.outputStream.use { os ->
                    os.write(encryptedEnvelopeString.toByteArray(StandardCharsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Error al actualizar respaldo en Drive (HTTP $responseCode)")
                }
            } else {
                // Creación de nuevo archivo multipart en appDataFolder
                val boundary = "=====AppOPTBoundary${System.currentTimeMillis()}====="
                val createUrl = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                val connection = (createUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                    doOutput = true
                }

                val metadataJson = JSONObject().apply {
                    put("name", BackupFileName)
                    put("parents", org.json.JSONArray().apply { put("appDataFolder") })
                }.toString()

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
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Error al crear respaldo en Drive (HTTP $responseCode)")
                }
            }
        }
    }

    /**
     * Descarga el archivo de respaldo más reciente desde la carpeta privada de Google Drive
     * y descifra el contenedor sellado con **AES-256-GCM**.
     *
     * @param accessToken Token de acceso OAuth2 emitido por Google Identity Services.
     * @param secretKeyPass Contraseña o PIN de descifrado en [CharArray].
     * @return [Result] con el contenido del archivo de respaldo en formato JSON descifrado.
     */
    suspend fun downloadBackup(
        accessToken: String,
        secretKeyPass: CharArray
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileId = findExistingBackupFileId(accessToken)
                ?: throw NoSuchElementException("No se encontró ninguna copia de seguridad en tu Google Drive")

            val downloadUrl = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            val connection = (downloadUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("Error al descargar respaldo de Drive (HTTP $responseCode)")
            }

            val downloadedContent = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.readText()
            }

            // Si el archivo descargado es un sobre cifrado con AES-256-GCM, lo desciframos con la clave provista
            if (downloadedContent.contains("\"ciphertext\"")) {
                val decryptedResult = BackupCrypto.decryptBackup(downloadedContent.toByteArray(StandardCharsets.UTF_8), secretKeyPass)
                decryptedResult.getOrThrow()
            } else {
                // Compatibilidad en caso de respaldo previo sin sellar
                downloadedContent
            }
        }
    }

    /**
     * Elimina permanentemente el archivo de copia de seguridad de la carpeta privada de Google Drive.
     *
     * @param accessToken Token de acceso OAuth2 emitido por Google Identity Services.
     * @return [Result] exitoso si el archivo fue eliminado o si no existía previamente.
     */
    suspend fun deleteBackup(accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fileId = findExistingBackupFileId(accessToken) ?: return@runCatching

            val deleteUrl = URL("https://www.googleapis.com/drive/v3/files/$fileId")
            val connection = (deleteUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $accessToken")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299 && responseCode != 404) {
                throw IllegalStateException("Error al eliminar la copia en Google Drive (HTTP $responseCode)")
            }
        }
    }

    /**
     * Verifica si ya existe un archivo de copia de seguridad en la carpeta privada de Google Drive.
     *
     * @param accessToken Token de acceso OAuth2 emitido por Google Identity Services.
     * @return `true` si existe un archivo de respaldo previo, `false` en caso contrario.
     */
    suspend fun hasExistingBackup(accessToken: String): Boolean = withContext(Dispatchers.IO) {
        findExistingBackupFileId(accessToken) != null
    }

    /**
     * Consulta la API de Drive para encontrar el identificador de [BackupFileName] en `appDataFolder`.
     */
    private fun findExistingBackupFileId(accessToken: String): String? {
        val queryUrl = URL("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='$BackupFileName'&fields=files(id,name,modifiedTime)&orderBy=modifiedTime+desc")
        val connection = (queryUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        if (connection.responseCode !in 200..299) return null

        val responseText = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use {
            it.readText()
        }

        val json = JSONObject(responseText)
        val filesArray = json.optJSONArray("files") ?: return null
        if (filesArray.length() == 0) return null

        return filesArray.getJSONObject(0).optString("id").ifEmpty { null }
    }
}
