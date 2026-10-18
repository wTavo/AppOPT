package com.example.appopt.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.appopt.domain.model.AppUpdateInfo
import com.example.appopt.security.SecurityConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import androidx.core.net.toUri

/**
 * Excepción de seguridad Zero-Trust cuando el archivo APK descargado no supera la verificación de integridad SHA-256
 * o el nombre de paquete no coincide con la aplicación.
 */
class ApkIntegrityVerificationException : SecurityException("Fallo en la verificación de integridad SHA-256 del paquete APK")

/**
 * Gestor de actualizaciones integradas (In-App Updater) vía GitHub Releases con verificación criptográfica SHA-256 (Directivas 6, 7, 8, 9 y 15).
 *
 * Garantías de seguridad y ciclo de vida:
 * - Cero fugas de recursos: todos los flujos de red y archivo se envuelven en bloques `.use { ... }` y las conexiones se cierran en `finally` (Directiva 9).
 * - Verificación de integridad SHA-256 en vuelo antes de invocar al instalador del sistema.
 * - Validación de identidad de paquete (`packageName`) mediante `PackageManager.getPackageArchiveInfo`.
 * - Limpieza determinística de paquetes `.apk` residuales en caché (`cleanupDownloadedApks`).
 */
object AppUpdateManager {

    private val SHA256_REGEX = Regex("""(?i)(?:sha256[:\s]+)?([a-f0-9]{64})""")

    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)

    /**
     * Flujo reactivo con los metadatos de la actualización disponible en GitHub Releases,
     * o `null` cuando la aplicación ya se encuentra en su versión más reciente.
     */
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate.asStateFlow()

    @Volatile
    private var lastCheckTimestampMillis: Long = 0L

    @Volatile
    private var lastCheckedResult: AppUpdateInfo? = null

    @Volatile
    private var hasValidCachedCheck: Boolean = false

    @Volatile
    private var cachedETag: String? = null

    @Volatile
    private var cachedReleaseJson: String? = null

    /**
     * Obtiene el par `(versionName, versionCode)` actualmente instalado en el dispositivo.
     *
     * @param context Contexto de la aplicación.
     * @return Par con el nombre de versión limpio (sin sufijos `-debug`) y el código numérico de compilación.
     */
    fun getInstalledVersion(context: Context): Pair<String, Long> {
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val cleanVersionName = AppUpdateInfo.normalizeVersionString(pkgInfo.versionName ?: "1.0.0")
        return cleanVersionName to pkgInfo.longVersionCode
    }

    /**
     * Elimina cualquier archivo `.apk` temporal descargado previamente en la carpeta privada de actualizaciones.
     *
     * @param context Contexto de la aplicación.
     */
    fun cleanupDownloadedApks(context: Context) {
        runCatching {
            val dir = File(context.cacheDir, SecurityConfig.APP_UPDATE_CACHE_DIR_NAME)
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        }
    }

    /**
     * Consulta el último lanzamiento publicado en GitHub Releases con protección de enfriamiento (*Cooldown*)
     * y validación condicional por cabecera `ETag` (`304 Not Modified`) para no consumir cuota de API en pulsaciones repetidas.
     *
     * Si el repositorio aún no tiene ningún lanzamiento publicado (HTTP 404) o si la versión publicada no supera
     * a la instalada, retorna `Result.success(null)` indicando que la aplicación está al día.
     *
     * @param context Contexto de la aplicación.
     * @param force Si es `true`, ignora la ventana de enfriamiento y fuerza una consulta fresca a GitHub.
     * @return [Result] con el [AppUpdateInfo] si hay una versión más reciente, o `null` si ya está actualizada.
     */
    suspend fun checkForUpdate(context: Context, force: Boolean = false): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            if (!force && hasValidCachedCheck && (now - lastCheckTimestampMillis) < SecurityConfig.APP_UPDATE_CHECK_COOLDOWN_MILLIS) {
                return@runCatching lastCheckedResult
            }

            if (force) {
                cachedETag = null
                hasValidCachedCheck = false
            }

            val (installedVersionName, _) = getInstalledVersion(context)
            val responseJson = fetchLatestReleaseJsonWithETag()
            if (responseJson == null) {
                cleanupDownloadedApks(context)
                lastCheckedResult = null
                _availableUpdate.value = null
                hasValidCachedCheck = true
                lastCheckTimestampMillis = now
                return@runCatching null
            }

            val releaseInfo = parseLatestReleaseJson(responseJson)
            val updateAvailable = if (releaseInfo != null &&
                AppUpdateInfo.isRemoteVersionNewer(installedVersionName, releaseInfo.versionName)
            ) {
                releaseInfo
            } else {
                cleanupDownloadedApks(context)
                null
            }

            lastCheckedResult = updateAvailable
            _availableUpdate.value = updateAvailable
            hasValidCachedCheck = true
            lastCheckTimestampMillis = now
            updateAvailable
        }
    }

    /**
     * Descarga el archivo `.apk` de la versión indicada a la caché privada del dispositivo,
     * calculando y verificando su hash SHA-256 en tiempo real antes de autorizar su uso.
     *
     * @param context Contexto de la aplicación.
     * @param updateInfo Metadatos de la actualización a descargar.
     * @param onProgress Callback de progreso invocado con valores de `0` a `100`.
     * @return [Result] con el archivo [File] verificado y listo para instalar.
     */
    suspend fun downloadAndVerifyApk(
        context: Context,
        updateInfo: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, SecurityConfig.APP_UPDATE_CACHE_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(updatesDir, SecurityConfig.APP_UPDATE_APK_FILE_NAME)
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val connection = (URL(updateInfo.apkDownloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = SecurityConfig.APP_UPDATE_HTTP_TIMEOUT_MILLIS
                readTimeout = SecurityConfig.APP_UPDATE_HTTP_TIMEOUT_MILLIS
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "AppOPT-Authenticator-Updater")
            }

            val computedSha256Hex: String
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code al descargar APK")
                }

                val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: updateInfo.apkSizeBytes
                val digest = MessageDigest.getInstance("SHA-256")

                connection.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloadedBytes = 0L
                        var lastReportedPercent = -1
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            digest.update(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            if (totalBytes > 0L) {
                                val percent = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                        output.flush()
                    }
                }

                computedSha256Hex = digest.digest().joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                targetFile.delete()
                throw e
            } finally {
                connection.disconnect()
            }

            // 1. Verificación criptográfica SHA-256 si el lanzamiento incluye digest oficial
            val expectedHash = updateInfo.expectedSha256?.trim()?.lowercase()
            if (!expectedHash.isNullOrBlank() && expectedHash.length == 64) {
                if (!computedSha256Hex.equals(expectedHash, ignoreCase = true)) {
                    targetFile.delete()
                    throw ApkIntegrityVerificationException()
                }
            }

            // 2. Verificación de estructura APK válida mediante PackageManager de Android
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    targetFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(targetFile.absolutePath, 0)
            }

            val expectedBasePkg = context.packageName.removeSuffix(".debug")
            val archiveBasePkg = archiveInfo?.packageName?.removeSuffix(".debug")
            if (archiveInfo == null || archiveBasePkg != expectedBasePkg) {
                targetFile.delete()
                throw ApkIntegrityVerificationException()
            }

            onProgress(100)
            targetFile
        }
    }

    /**
     * Verifica si la aplicación cuenta con el permiso del sistema para solicitar la instalación de paquetes APK.
     *
     * @param context Contexto de la aplicación.
     * @return `true` si puede iniciar el instalador directamente, `false` si requiere autorización del usuario.
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    /**
     * Abre la pantalla del sistema operativo para autorizar la instalación de paquetes desde esta aplicación.
     *
     * @param context Contexto de la aplicación.
     */
    fun openManageUnknownAppSourcesSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri()
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Lanza el instalador nativo de paquetes de Android utilizando [FileProvider] sobre el APK ya verificado.
     *
     * @param context Contexto de la aplicación.
     * @param apkFile Archivo APK verificado dentro de `cacheDir/apk_updates/`.
     */
    fun launchApkInstaller(context: Context, apkFile: File) {
        val authority = "${context.packageName}${SecurityConfig.APP_UPDATE_FILE_PROVIDER_SUFFIX}"
        val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    private fun fetchLatestReleaseJsonWithETag(): String? {
        val connection = (URL(SecurityConfig.GITHUB_LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = SecurityConfig.APP_UPDATE_HTTP_TIMEOUT_MILLIS
            readTimeout = SecurityConfig.APP_UPDATE_HTTP_TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "AppOPT-Authenticator-Updater")
            val previousETag = cachedETag
            if (!previousETag.isNullOrBlank()) {
                setRequestProperty("If-None-Match", previousETag)
            }
        }

        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED && cachedReleaseJson != null) {
                return cachedReleaseJson
            }
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                cachedReleaseJson = null
                return null
            }
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code al consultar GitHub Releases")
            }

            val newETag = connection.getHeaderField("ETag")
            if (!newETag.isNullOrBlank()) {
                cachedETag = newETag
            }

            val body = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.readText()
            }
            cachedReleaseJson = body
            return body
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parsea la respuesta JSON de GitHub Releases extrayendo el primer activo `.apk`, su tamaño y su hash SHA-256.
     *
     * @param jsonString Respuesta JSON de `/releases/latest`.
     * @return Instancia de [AppUpdateInfo] o `null` si el lanzamiento no contiene ningún archivo `.apk`.
     */
    fun parseLatestReleaseJson(jsonString: String): AppUpdateInfo? {
        val root = JSONObject(jsonString)
        if (root.optBoolean("draft", false)) return null

        val tagName = root.optString("tag_name", "").trim()
        if (tagName.isEmpty()) return null

        val versionName = AppUpdateInfo.normalizeVersionString(tagName)
        val body = root.optString("body", "").trim()
        val assets = root.optJSONArray("assets") ?: return null

        var apkUrl: String? = null
        var apkSize = 0L
        var assetDigestSha256: String? = null

        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            val downloadUrl = asset.optString("browser_download_url", "")
            if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                apkUrl = downloadUrl
                apkSize = asset.optLong("size", 0L)
                val rawDigest = asset.optString("digest", "")
                if (rawDigest.startsWith("sha256:", ignoreCase = true)) {
                    assetDigestSha256 = rawDigest.substringAfter(":").trim().lowercase()
                }
                break
            }
        }

        val finalApkUrl = apkUrl ?: return null
        val fallbackBodySha256 = SHA256_REGEX.find(body)?.groupValues?.getOrNull(1)?.lowercase()
        val expectedSha256 = (assetDigestSha256 ?: fallbackBodySha256)?.takeIf { it.length == 64 }

        return AppUpdateInfo(
            tagName = tagName,
            versionName = versionName,
            releaseNotes = body,
            apkDownloadUrl = finalApkUrl,
            apkSizeBytes = apkSize,
            expectedSha256 = expectedSha256
        )
    }
}
