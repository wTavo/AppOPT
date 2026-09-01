package com.example.appopt.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestor de preferencias de usuario persistentes (modo de privacidad, etc.).
 *
 * @param context Contexto de la aplicación.
 */
class PreferencesManager(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Retorna si el modo de ocultar códigos está habilitado de forma persistente.
     */
    fun isHideCodesEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_HIDE_CODES, false)
    }

    /**
     * Guarda el estado de ocultar códigos en disco.
     */
    fun setHideCodesEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_HIDE_CODES, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "authenticator_user_preferences"
        private const val KEY_HIDE_CODES = "key_hide_codes"
    }
}
