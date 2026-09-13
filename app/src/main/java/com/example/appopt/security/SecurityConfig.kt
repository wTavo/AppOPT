package com.example.appopt.security

/**
 * Parámetros y constantes criptográficas y de seguridad centralizadas para toda la aplicación.
 *
 * Principio de diseño:
 * - Define los tiempos de expiración para limpieza segura de memoria y portapapeles.
 * - Centraliza las especificaciones de derivación de claves PBKDF2 y tamaños de bloques AES-GCM.
 */
object SecurityConfig {

    /**
     * Tiempo en segundos tras el cual el portapapeles sobreescribe y elimina automáticamente un código OTP copiado.
     */
    const val CLIPBOARD_OTP_AUTO_CLEAR_SECONDS = 30

    /**
     * Tiempo en segundos tras el cual el portapapeles elimina una clave de recuperación criptográfica copiada.
     */
    const val CLIPBOARD_RECOVERY_KEY_AUTO_CLEAR_SECONDS = 60

    /**
     * Etiqueta para la copia de la clave de 64 dígitos en el portapapeles seguro.
     */
    const val CLIPBOARD_LABEL_RECOVERY_64KEY = "AppOPT_Recovery_64Key"

    /**
     * Etiqueta para la copia de la frase de emergencia (12 palabras) en el portapapeles seguro.
     */
    const val CLIPBOARD_LABEL_RECOVERY_MNEMONIC = "AppOPT_Recovery_Mnemonic"

    /**
     * Período de rotación por defecto en segundos para tokens TOTP (RFC 6238).
     */
    const val DEFAULT_TOTP_PERIOD_SECONDS = 30

    /**
     * Número de dígitos por defecto para tokens OTP (RFC 6238 / RFC 4226).
     */
    const val DEFAULT_OTP_DIGITS = 6

    /**
     * Número de iteraciones para la función de derivación de claves PBKDF2-HMAC-SHA256 en respaldos cifrados.
     */
    const val PBKDF2_BACKUP_ITERATIONS = 100_000

    /**
     * Longitud en bits de la clave de respaldo derivada con PBKDF2 (AES-256).
     */
    const val BACKUP_KEY_SIZE_BITS = 256

    /**
     * Longitud en bytes del vector de inicialización (IV) para AES-GCM.
     */
    const val AES_GCM_IV_SIZE_BYTES = 12

    /**
     * Longitud en bits del tag de autenticación para AES-GCM.
     */
    const val AES_GCM_TAG_LENGTH_BITS = 128

    /**
     * Alias de la clave maestra custodial en el hardware seguro Android Keystore.
     */
    const val KEYSTORE_MASTER_KEY_ALIAS = "AppOPT_Vault_Master_Key"

    /**
     * Proveedor del almacén seguro de claves en el sistema Android.
     */
    const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"

    /**
     * Tiempo en milisegundos de inactividad en segundo plano antes de bloquear automáticamente la bóveda.
     */
    const val APP_LOCK_TIMEOUT_MILLIS = 5_000L

    /**
     * Retardo de consolidación (*Debounce*) en segundos antes de disparar la sincronización reactiva automática en la nube.
     */
    const val REACTIVE_SYNC_DEBOUNCE_SECONDS = 20L

    /**
     * Semilla de derivación interna para la clave simétrica de sincronización automática en la nube.
     */
    const val AUTO_SYNC_VAULT_KEY = "AppOPT_AutoSync_Vault_E2EE_v1"

    /**
     * Nombre del archivo SQLite de la base de datos local Room.
     */
    const val ROOM_DATABASE_NAME = "authenticator_vault.db"

    /**
     * Nombre canónico de la tabla de cuentas OTP en SQLite / Room.
     */
    const val TABLE_ACCOUNTS_NAME = "totp_accounts"

    /**
     * Días de retención en la papelera de reciclaje antes de la eliminación definitiva.
     */
    const val TRASH_RETENTION_DAYS = 30L

    /**
     * Tiempo en milisegundos de retención en la papelera de reciclaje (30 días).
     */
    const val TRASH_RETENTION_MILLIS = TRASH_RETENTION_DAYS * 24L * 60L * 60L * 1000L

    /**
     * Tiempo de expiración de caché en milisegundos (TTL) para el historial de copias de seguridad de Google Drive (20 segundos).
     * Evita sobrecargar la API remota con peticiones redundantes ante aperturas consecutivas del diálogo de historial.
     */
    const val BACKUP_HISTORY_CACHE_TTL_MILLIS = 20_000L

    /**
     * Tiempo de expiración de caché en milisegundos (TTL) para el archivo de respaldo cifrado descargado (2 minutos / 120 segundos).
     * Permite reintentos de descifrado y operaciones continuas con 0 peticiones de red adicionales a Google Drive.
     */
    const val BACKUP_DOWNLOAD_CACHE_TTL_MILLIS = 120_000L

    /**
     * Longitud en dígitos para el PIN de transferencia de servicios por código QR.
     */
    const val TRANSFER_QR_PIN_LENGTH = 6

    /**
     * Cantidad máxima de intentos fallidos permitidos para ingresar el PIN de transferencia QR antes del bloqueo de sesión.
     */
    const val TRANSFER_QR_MAX_PIN_ATTEMPTS = 5

    /**
     * Tiempo de expiración en segundos para la validez del código QR de transferencia cifrado (90 segundos base).
     */
    const val TRANSFER_QR_EXPIRATION_SECONDS = 90

    /**
     * Tiempo adicional en segundos asignado por cada lote o código QR complementario en transferencias multi-QR.
     */
    const val TRANSFER_QR_EXTRA_SECONDS_PER_BATCH = 30

    /**
     * Calcula el tiempo total de expiración en segundos proporcional a la cantidad de fragmentos QR.
     * Base: 90 segundos para 1 código + 30 segundos por cada lote adicional.
     *
     * @param batchCount Cantidad total de fragmentos o códigos QR generados.
     * @return Tiempo de validez en segundos.
     */
    fun calculateTransferExpirationSeconds(batchCount: Int): Int {
        val safeCount = maxOf(1, batchCount)
        return TRANSFER_QR_EXPIRATION_SECONDS + (safeCount - 1) * TRANSFER_QR_EXTRA_SECONDS_PER_BATCH
    }

    /**
     * Número de iteraciones PBKDF2 para la derivación de clave a partir del PIN de transferencia QR.
     * Optimizado para derivación instantánea en dispositivos móviles sin bloquear el renderizado de cámara.
     */
    const val TRANSFER_QR_PBKDF2_ITERATIONS = 10_000

    /**
     * Versión actual del esquema de datos para respaldos en la nube y transferencias JSON.
     */
    const val CURRENT_BACKUP_VERSION = 1

    /**
     * Versión 1 (legada / mono-código) del esquema del sobre criptográfico de transferencia por código QR.
     */
    const val TRANSFER_QR_VERSION = 1

    /**
     * Versión 2 (Todo o Nada / Multi-lote ensamblado) del sobre criptográfico de transferencia.
     */
    const val TRANSFER_QR_VERSION_V2 = 2

    /**
     * Cantidad máxima de cuentas por código QR en transferencias por lotes (Multi-QR).
     * Garantiza una densidad visual óptima y lectura instantánea sin importar el tamaño total de la bóveda.
     */
    const val TRANSFER_QR_BATCH_SIZE = 10

    /**
     * Tamaño máximo en bytes de carga útil de texto cifrado por fragmento QR (Todo o Nada).
     * Mantiene los códigos QR con baja densidad de puntos para escaneo instantáneo.
     */
    const val TRANSFER_QR_CHUNK_MAX_BYTES = 220
}
