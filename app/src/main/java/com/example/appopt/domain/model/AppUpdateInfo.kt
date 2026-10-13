package com.example.appopt.domain.model

import java.util.Locale

/**
 * Modelo de dominio inmutable que representa la información de una versión publicada en GitHub Releases (Directivas 7 y 8).
 *
 * @property tagName Etiqueta original de la versión en GitHub (ej. `"v1.1.0"`).
 * @property versionName Versión semántica limpia (ej. `"1.1.0"`).
 * @property releaseNotes Notas de cambios descriptivas publicadas junto al lanzamiento.
 * @property apkDownloadUrl URL directa de descarga del activo `.apk` oficial.
 * @property apkSizeBytes Tamaño en bytes del archivo `.apk`.
 * @property expectedSha256 Huella criptográfica SHA-256 oficial en hexadecimal (64 caracteres) si está disponible en el lanzamiento.
 */
data class AppUpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val expectedSha256: String?
) {
    /**
     * Formatea el tamaño del APK en megabytes legibles para el usuario (ej. `"6.4 MB"`).
     *
     * @return Cadena formateada con el peso del paquete.
     */
    fun formattedSizeMb(): String {
        val mb = apkSizeBytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb.coerceAtLeast(0.1))
    }

    companion object {
        /**
         * Normaliza una cadena de versión eliminando prefijos como `v` y sufijos como `-debug`.
         *
         * @param raw Cadena de versión original.
         * @return Cadena compuesta por segmentos numéricos separados por punto.
         */
        fun normalizeVersionString(raw: String): String {
            return raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore("-")
                .trim()
        }

        /**
         * Compara de forma determinística si una versión remota (`remoteTag`) es estrictamente superior
         * a la versión actualmente instalada (`installedVersionName`).
         *
         * Soporta esquemas semánticos (`MAJOR.MINOR.PATCH`) de longitud variable (ej. `"1.0"` vs `"1.0.1"` o `"1.1.0"`).
         *
         * @param installedVersionName Nombre de versión instalado en el dispositivo.
         * @param remoteTag Etiqueta o nombre de versión publicado en GitHub Releases.
         * @return `true` únicamente si la versión remota es mayor que la instalada.
         */
        fun isRemoteVersionNewer(installedVersionName: String, remoteTag: String): Boolean {
            val installedParts = normalizeVersionString(installedVersionName)
                .split(".")
                .map { it.toIntOrNull() ?: 0 }
            val remoteParts = normalizeVersionString(remoteTag)
                .split(".")
                .map { it.toIntOrNull() ?: 0 }

            val maxLen = maxOf(installedParts.size, remoteParts.size)
            for (i in 0 until maxLen) {
                val installedVal = installedParts.getOrElse(i) { 0 }
                val remoteVal = remoteParts.getOrElse(i) { 0 }
                if (remoteVal > installedVal) return true
                if (remoteVal < installedVal) return false
            }
            return false
        }
    }
}
