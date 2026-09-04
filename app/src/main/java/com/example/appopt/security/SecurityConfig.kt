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
    const val PBKDF2_BACKUP_ITERATIONS = 120_000

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
}
