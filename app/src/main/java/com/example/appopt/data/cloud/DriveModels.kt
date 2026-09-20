package com.example.appopt.data.cloud

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
 * Representa una versión individual de copia de seguridad almacenada en Google Drive (Point-in-Time Recovery).
 *
 * @property fileId Identificador único del archivo en Google Drive API.
 * @property fileName Nombre del archivo físico en la nube.
 * @property modifiedTimeMillis Marca de tiempo UNIX de la creación o última modificación.
 * @property sizeBytes Tamaño en bytes del archivo cifrado en la nube.
 * @property deviceName Nombre o modelo del dispositivo que generó la copia.
 * @property deviceId Identificador único persistente del dispositivo emisor para discriminación de versiones locales.
 * @property isMostRecent Indica si corresponde a la versión más actual del historial.
 * @property isEncrypted Indica si la copia cuenta con cifrado de extremo a extremo (E2EE) o si es una copia estándar.
 */
data class DriveBackupItem(
    val fileId: String,
    val fileName: String,
    val modifiedTimeMillis: Long,
    val sizeBytes: Long,
    val deviceName: String,
    val isMostRecent: Boolean = false,
    val deviceId: String = "",
    val isEncrypted: Boolean = true
)

/**
 * Resultado detallado de descarga que incluye el JSON descifrado y la sesión de clave simétrica con ranuras si es cifrado.
 *
 * @property plainJson Contenido descifrado en formato JSON.
 * @property session Sesión de clave para su custodia en Android Keystore (nula en respaldos estándar no cifrados).
 */
data class DownloadedBackupResult(
    val plainJson: String,
    val session: CloudVaultKeyStore.VaultKeySession? = null
)

/**
 * Estructura de caché en memoria para archivos cifrados descargados desde Google Drive.
 *
 * @property fileId Identificador del archivo en Google Drive.
 * @property encryptedContent Contenido encriptado en memoria.
 * @property fetchedAtMillis Marca de tiempo UNIX de descarga.
 */
data class CachedEncryptedFile(
    val fileId: String,
    val encryptedContent: String,
    val fetchedAtMillis: Long
)
