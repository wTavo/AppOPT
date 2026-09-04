package com.example.appopt.ui.screens.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appopt.AuthenticatorApp
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
 * @property errorMessage Mensaje de error descriptivo si la validación falla.
 * @property isSavedSuccessfully Bandera para indicar navegación tras guardar con éxito.
 */
data class AddAccountUiState(
    val issuer: String = "",
    val accountName: String = "",
    val secret: String = "",
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
    val type: OtpType = OtpType.TOTP,
    val isSecretValid: Boolean = false,
    val errorMessage: String? = null,
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
        _uiState.value = _uiState.value.copy(issuer = value, errorMessage = null)
    }

    /**
     * Actualiza el nombre de la cuenta o usuario (campo opcional).
     */
    fun onAccountNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(accountName = value, errorMessage = null)
    }

    /**
     * Actualiza y sanea la clave secreta Base32 verificando su validez de formato.
     */
    fun onSecretChanged(value: String) {
        val sanitized = Base32.sanitize(value)
        val isValid = Base32.isValid(sanitized)

        _uiState.value = _uiState.value.copy(
            secret = value,
            isSecretValid = isValid,
            errorMessage = null
        )
    }

    /**
     * Cambia el algoritmo HMAC configurado.
     */
    fun onAlgorithmChanged(algo: OtpAlgorithm) {
        _uiState.value = _uiState.value.copy(algorithm = algo)
    }

    /**
     * Cambia la cantidad de dígitos configurada.
     */
    fun onDigitsChanged(digits: Int) {
        _uiState.value = _uiState.value.copy(digits = digits)
    }

    /**
     * Cambia el periodo en segundos.
     */
    fun onPeriodChanged(period: Int) {
        _uiState.value = _uiState.value.copy(period = period)
    }

    /**
     * Valida la presencia de emisor y clave Base32 válida, cifra el secreto y guarda la cuenta en Room.
     */
    fun saveAccount() {
        val state = _uiState.value
        if (state.issuer.isBlank()) {
            _uiState.value = state.copy(errorMessage = "El nombre del servicio es obligatorio")
            return
        }
        if (!state.isSecretValid) {
            _uiState.value = state.copy(errorMessage = "La clave secreta Base32 no es válida")
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
                _uiState.value = _uiState.value.copy(errorMessage = "Error al cifrar y guardar: ${e.localizedMessage}")
            }
        }
    }
}
