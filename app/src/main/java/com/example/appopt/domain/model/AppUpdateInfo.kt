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

    /**
     * Procesa y limpia las notas de la versión para su visualización estética en la interfaz de usuario.
     *
     * Filtra los bloques técnicos de verificación criptográfica (como hashes SHA-256), títulos redundantes
     * con el nombre de la app y limpia las marcas Markdown (`##`, `###`, `**`) transformando las listas
     * en viñetas legibles (`• `).
     *
     * @return Lista de líneas limpias listas para renderizar.
     */
    fun getFormattedReleaseNotes(): List<String> {
        val lines = releaseNotes.lines()
        val result = mutableListOf<String>()
        var skipTechnicalSection = false

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            // 1. Filtrar bloques técnicos de verificación criptográfica (SHA-256)
            if (line.contains("Verificación de Integridad", ignoreCase = true) ||
                line.contains("SHA-256", ignoreCase = true) ||
                line.matches(Regex("""`?[a-f0-9]{64}`?""", RegexOption.IGNORE_CASE))
            ) {
                skipTechnicalSection = true
                continue
            }

            if (skipTechnicalSection) {
                if (line.startsWith("#")) {
                    skipTechnicalSection = false
                } else {
                    continue
                }
            }

            // 2. Filtrar título redundante con nombre de versión (ej. "## AppOPT Authenticator v1.0.1")
            if (line.startsWith("#") && (line.contains("AppOPT", ignoreCase = true) || line.contains(versionName))) {
                continue
            }

            // 3. Limpiar subtítulos y viñetas
            val cleaned = when {
                line.startsWith("###") || line.startsWith("##") -> {
                    line.replace(Regex("""^#+\s*"""), "").replace("**", "").trim().let {
                        if (!it.endsWith(":")) "$it:" else it
                    }
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    "• " + line.substring(2).replace("**", "").replace("`", "").trim()
                }
                else -> {
                    line.replace("**", "").replace("`", "").trim()
                }
            }

            if (cleaned.isNotBlank()) {
                result.add(cleaned)
            }
        }
        return result
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
