package com.example.appopt.data.cloud

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
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
 * Gestor seguro para la sincronización de la bóveda con Google Drive API.
 *
 * Características de seguridad y privacidad:
 * - Emplea exclusivamente el ámbito `https://www.googleapis.com/auth/drive.appdata` (App Data Folder).
 * - La carpeta `appDataFolder` es invisible para el usuario y para otras aplicaciones instaladas.
 * - Toda la comunicación se realiza mediante HTTPS autenticado con tokens OAuth2 efímeros.
 */
object GoogleDriveManager {

    private const val DriveScope = "https://www.googleapis.com/auth/drive.appdata"
    private const val OauthScope = "oauth2:$DriveScope"
    private const val BackupFileName = "appopt_vault_backup.json"

    /**
     * Construye el cliente oficial de inicio de sesión de Google con alcance restringido a App Data Folder.
     *
     * @param context Contexto de la aplicación.
     * @return Instancia configurada de [GoogleSignInClient].
     */
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScope))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Obtiene la cuenta de Google actualmente autenticada en el dispositivo, si existe.
     */
    fun getLastSignedInAccount(context: Context): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScope))) {
            account
        } else {
            null
        }
    }

    /**
     * Sube o actualiza la copia de seguridad en el espacio privado [BackupFileName] de Google Drive.
     *
     * @param context Contexto de la aplicación.
     * @param account Cuenta de Google autenticada con permisos de Drive.
     * @param backupJson Cadena JSON que contiene los secretos cifrados de la bóveda.
     * @return [Result] exitoso si la sincronización concluyó con código HTTP 200/201.
     */
    suspend fun uploadBackup(
        context: Context,
        account: GoogleSignInAccount,
        backupJson: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val androidAccount = account.account ?: throw IllegalStateException("Cuenta de Google no válida")
            val token = GoogleAuthUtil.getToken(context, androidAccount, OauthScope)

            // 1. Buscar si ya existe un archivo de respaldo previo en appDataFolder
            val existingFileId = findExistingBackupFileId(token)

            if (existingFileId != null) {
                // 2. Actualizar el archivo existente mediante PATCH media
                val updateUrl = URL("https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media")
                val connection = (updateUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "PATCH"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    doOutput = true
                }

                connection.outputStream.use { os ->
                    os.write(backupJson.toByteArray(StandardCharsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Error al actualizar respaldo en Drive (HTTP $responseCode)")
                }
            } else {
                // 3. Crear nuevo archivo multipart en appDataFolder
                val boundary = "=====AppOPTBoundary${System.currentTimeMillis()}====="
                val createUrl = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                val connection = (createUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $token")
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
                    writer.write(backupJson)
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
     * Descarga el archivo de respaldo más reciente desde la carpeta privada de Google Drive.
     *
     * @param context Contexto de la aplicación.
     * @param account Cuenta de Google autenticada.
     * @return [Result] con el contenido JSON del respaldo.
     */
    suspend fun downloadBackup(
        context: Context,
        account: GoogleSignInAccount
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val androidAccount = account.account ?: throw IllegalStateException("Cuenta de Google no válida")
            val token = GoogleAuthUtil.getToken(context, androidAccount, OauthScope)

            val fileId = findExistingBackupFileId(token)
                ?: throw NoSuchElementException("No se encontró ninguna copia de seguridad en tu Google Drive")

            val downloadUrl = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            val connection = (downloadUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("Error al descargar respaldo de Drive (HTTP $responseCode)")
            }

            BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.readText()
            }
        }
    }

    /**
     * Consulta la API de Drive para encontrar el identificador de [BackupFileName] en `appDataFolder`.
     */
    private fun findExistingBackupFileId(token: String): String? {
        val queryUrl = URL("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='$BackupFileName'&fields=files(id,name,modifiedTime)&orderBy=modifiedTime+desc")
        val connection = (queryUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
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
