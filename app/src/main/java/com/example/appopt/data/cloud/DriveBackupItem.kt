package com.example.appopt.data.cloud

import androidx.compose.runtime.Immutable

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
 */
@Immutable
data class DriveBackupItem(
    val fileId: String,
    val fileName: String,
    val modifiedTimeMillis: Long,
    val sizeBytes: Long,
    val deviceName: String,
    val isMostRecent: Boolean = false,
    val deviceId: String = ""
)
