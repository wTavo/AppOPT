package com.example.appopt.ui.screens.settings.util

import android.content.Context
import com.example.appopt.R
import com.example.appopt.data.cloud.RateLimitExceededException
import com.example.appopt.data.cloud.ServiceUnavailableException
import java.security.GeneralSecurityException

/**
 * Utilidad para resolver de forma segura y amigable las excepciones técnicas de Google Drive
 * hacia mensajes descriptivos y localizados en la interfaz de usuario (Directiva 15).
 */
object DriveErrorMessageResolver {

    /**
     * Mapea un [Throwable] a su respectiva cadena amigable en [strings.xml].
     *
     * @param context Contexto de la aplicación para resolver recursos.
     * @param error Excepción capturada en la operación de red o descifrado.
     * @return Cadena localizada explicativa y amigable para el usuario.
     */
    fun resolve(context: Context, error: Throwable): String {
        val isRateLimited = error is RateLimitExceededException ||
                error.message?.contains("429", ignoreCase = true) == true ||
                error.message?.contains("rate", ignoreCase = true) == true ||
                error.cause?.message?.contains("429", ignoreCase = true) == true
        if (isRateLimited) {
            return context.getString(R.string.settings_drive_error_rate_limited)
        }

        val isServiceUnavailable = error is ServiceUnavailableException ||
                error.message?.contains("503", ignoreCase = true) == true
        if (isServiceUnavailable) {
            return context.getString(R.string.settings_drive_error_service_unavailable)
        }

        val isDecryptFailure = error is GeneralSecurityException ||
                error is IllegalArgumentException ||
                error.cause is GeneralSecurityException ||
                error.cause is IllegalArgumentException ||
                error.message?.contains("clave", ignoreCase = true) == true ||
                error.message?.contains("ranura", ignoreCase = true) == true ||
                error.message?.contains("descifrar", ignoreCase = true) == true ||
                error.message?.contains("tag", ignoreCase = true) == true ||
                error.message?.contains("credenciales", ignoreCase = true) == true ||
                error.message?.contains("padding", ignoreCase = true) == true
        if (isDecryptFailure) {
            return context.getString(R.string.settings_drive_decrypt_error)
        }

        return context.getString(R.string.settings_drive_error)
    }
}
