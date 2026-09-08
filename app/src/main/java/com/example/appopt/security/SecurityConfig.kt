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
     * Longitud en dígitos para el PIN de transferencia de servicios por código QR.
     */
    const val TRANSFER_QR_PIN_LENGTH = 6

    /**
     * Tiempo de expiración en segundos para la validez del código QR de transferencia cifrado (90 segundos).
     */
    const val TRANSFER_QR_EXPIRATION_SECONDS = 90

    /**
     * Número de iteraciones PBKDF2 para la derivación de clave a partir del PIN de transferencia QR.
     * Optimizado para derivación instantánea en dispositivos móviles sin bloquear el renderizado de cámara.
     */
    const val TRANSFER_QR_PBKDF2_ITERATIONS = 10_000

    /**
     * Versión del esquema del sobre criptográfico de transferencia por código QR.
     */
    const val TRANSFER_QR_VERSION = 1
}
