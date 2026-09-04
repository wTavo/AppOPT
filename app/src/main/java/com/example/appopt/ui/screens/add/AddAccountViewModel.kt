package com.example.appopt.ui.screens.add

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appopt.AuthenticatorApp
import com.example.appopt.R
import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.totp.Base32
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado UI del formulario de alta manual de cuenta.
 *
 * @property issuer Nombre del servicio o emisor (obligatorio, ej. "Google", "GitHub").
 * @property accountName Nombre de usuario o cuenta (opcional, ej. "correo@ejemplo.com").
 * @property secret Clave secreta Base32 ingresada por el usuario (obligatoria).
 * @property algorithm Algoritmo HMAC configurado (por defecto SHA1).
 * @property digits Cantidad de dígitos requerida (por defecto 6).
 * @property period Periodo de rotación en segundos (por defecto 30).
 * @property type Tipo de OTP (TOTP por defecto).
 * @property isSecretValid Indica si la clave Base32 tiene formato canónico válido.
 * @property errorMessageResId Identificador de recurso del mensaje de error descriptivo si la validación falla.
 * @property isSavedSuccessfully Bandera para indicar navegación tras guardar con éxito.
 */
@Immutable
data class AddAccountUiState(
    val issuer: String = "",
    val accountName: String = "",
    val secret: String = "",
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
    val type: OtpType = OtpType.TOTP,
    val isSecretValid: Boolean = false,
    val errorMessageResId: Int? = null,
    val isSavedSuccessfully: Boolean = false
)

/**
 * ViewModel responsable del formulario de adición manual de cuentas.
 *
 * Principio de diseño y seguridad:
 * - Requiere obligatoriamente el nombre del servicio y una clave Base32 válida.
 * - Trata el campo de correo/cuenta como opcional para agilizar el alta de cuentas.
 * - Establece valores predeterminados seguros y universales (SHA1, 6 dígitos, 30s).
 */
class AddAccountViewModel : ViewModel() {

    private val repository = AuthenticatorApp.instance.accountRepository

    private val _uiState = MutableStateFlow(AddAccountUiState())

    /** Flujo inmutable del estado del formulario. */
    val uiState = _uiState.asStateFlow()

    /**
     * Actualiza el nombre del servicio o emisor.
     */
    fun onIssuerChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            issuer = value,
            errorMessageResId = null
        )
    }

    /**
     * Actualiza el identificador de la cuenta o usuario.
     */
    fun onAccountNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            accountName = value,
            errorMessageResId = null
        )
    }

    /**
     * Actualiza la clave secreta y valida su formato Base32 canónico.
     */
    fun onSecretChanged(value: String) {
        val sanitized = Base32.sanitize(value)
        val isValid = Base32.isValid(sanitized)
        _uiState.value = _uiState.value.copy(
            secret = value,
            isSecretValid = isValid,
            errorMessageResId = null
        )
    }

    /**
     * Actualiza el algoritmo criptográfico HMAC.
     */
    fun onAlgorithmChanged(value: OtpAlgorithm) {
        _uiState.value = _uiState.value.copy(algorithm = value)
    }

    /**
     * Actualiza el número de dígitos generados.
     */
    fun onDigitsChanged(value: Int) {
        _uiState.value = _uiState.value.copy(digits = value)
    }

    /**
     * Valida, cifra y guarda la nueva cuenta en la base de datos segura.
     */
    fun saveAccount() {
        val state = _uiState.value
        if (state.issuer.isBlank()) {
            _uiState.value = state.copy(errorMessageResId = R.string.add_account_error_issuer_required)
            return
        }
        if (!state.isSecretValid) {
            _uiState.value = state.copy(errorMessageResId = R.string.add_account_error_secret_invalid)
            return
        }

        viewModelScope.launch {
            try {
                val secretBytes = Base32.decode(Base32.sanitize(state.secret))
                repository.saveAccount(
                    issuer = state.issuer.trim(),
                    accountName = state.accountName.trim(),
                    secretBytes = secretBytes,
                    algorithm = state.algorithm,
                    digits = state.digits,
                    period = state.period,
                    type = state.type
                )
                _uiState.value = _uiState.value.copy(isSavedSuccessfully = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessageResId = R.string.add_account_error_save_failed)
            }
        }
    }
}
