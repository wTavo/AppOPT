package com.example.appopt.security

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Gestor de autenticación biométrica y credenciales del dispositivo mediante AndroidX Biometric.
 *
 * Características:
 * - Soporta sensores de huella digital y reconocimiento facial seguros.
 * - Permite fallback a PIN, patrón o contraseña del dispositivo si la biometría no está disponible.
 */
class BiometricAuthManager {

    /**
     * Muestra el diálogo del sistema de autenticación biométrica o PIN.
     *
     * @param activity Contexto de FragmentActivity requerido por AndroidX BiometricPrompt.
     * @param title Título a mostrar en el modal del sistema.
     * @param subtitle Subtítulo descriptivo.
     * @param onSuccess Callback invocado tras la autenticación exitosa.
     * @param onError Callback invocado si ocurre un error irrecuperable o el usuario cancela.
     * @param onFailed Callback invocado en intentos fallidos individuales (ej. huella no reconocida).
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: String) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }
}
