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
 * Resultado detallado de descarga que incluye el JSON descifrado y la sesión de clave simétrica con ranuras.
 *
 * @property plainJson Contenido descifrado en formato JSON.
 * @property session Sesión de clave para su custodia en Android Keystore.
 */
data class DownloadedBackupResult(
    val plainJson: String,
    val session: CloudVaultKeyStore.VaultKeySession
)

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
