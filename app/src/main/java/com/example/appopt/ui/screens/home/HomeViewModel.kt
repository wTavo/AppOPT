package com.example.appopt.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appopt.AuthenticatorApp
import com.example.appopt.domain.repository.AccountWithCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla principal que sincroniza en tiempo real las cuentas registradas con el reloj del sistema.
 *
 * Principio de diseño:
 * - El cálculo del código y del progreso temporal se actualiza automáticamente mediante [tickerFlow] cada 500ms.
 * - Filtra las cuentas en memoria según la consulta del buscador sin bloquear el hilo principal.
 * - Persiste y sincroniza el estado de privacidad para ocultar/mostrar códigos.
 * - Persiste de forma atómica el ordenamiento personalizado tras finalizar el arrastre.
 */
class HomeViewModel : ViewModel() {

    private val repository = AuthenticatorApp.instance.accountRepository
    private val clipboardManager = AuthenticatorApp.instance.secureClipboardManager
    private val appLockManager = AuthenticatorApp.instance.appLockManager
    private val preferencesManager = AuthenticatorApp.instance.preferencesManager

    /** Consulta de búsqueda actual para filtrar cuentas por emisor o nombre de usuario. */
    val searchQuery = MutableStateFlow("")

    /** Estado persistente del modo de privacidad para ocultar códigos. */
    private val _isHideCodesEnabled = MutableStateFlow(preferencesManager.isHideCodesEnabled())
    val isHideCodesEnabled: StateFlow<Boolean> = _isHideCodesEnabled.asStateFlow()

    /** Flujo de pulsos de reloj (500ms) para animaciones suaves del temporizador. */
    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(500)
        }
    }

    /**
     * Lista reactiva de cuentas acompañadas de sus códigos OTP actualizados y filtrados.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val accounts: StateFlow<List<AccountWithCode>> = tickerFlow
        .flatMapLatest { time ->
            repository.getAccountsWithCodes(time)
        }
        .combine(searchQuery) { list, query ->
            if (query.isBlank()) {
                list
            } else {
                list.filter {
                    it.account.issuer.contains(query, ignoreCase = true) ||
                            it.account.accountName.contains(query, ignoreCase = true)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Alterna y persiste el modo de privacidad para ocultar los códigos numéricos.
     */
    fun toggleHideCodes() {
        val newState = !_isHideCodesEnabled.value
        _isHideCodesEnabled.value = newState
        preferencesManager.setHideCodesEnabled(newState)
    }

    /**
     * Actualiza el término de búsqueda.
     */
    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    /**
     * Copia un código OTP de forma segura con programación de borrado automático según [com.example.appopt.security.SecurityConfig.CLIPBOARD_OTP_AUTO_CLEAR_SECONDS].
     */
    fun copyCode(code: String, label: String = "OTP") {
        clipboardManager.copyToClipboard(
            label = label,
            text = code,
            autoClearSeconds = com.example.appopt.security.SecurityConfig.CLIPBOARD_OTP_AUTO_CLEAR_SECONDS
        )
    }

    /**
     * Actualiza el nombre del servicio (emisor) y el nombre de cuenta/usuario.
     */
    fun updateAccount(id: String, issuer: String, accountName: String) {
        viewModelScope.launch {
            repository.updateAccount(id, issuer, accountName)
        }
    }

    /**
     * Alterna la marca de favorito de una cuenta.
     */
    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            repository.toggleFavorite(id)
        }
    }

    /**
     * Guarda de forma atómica el orden final de la lista de cuentas en la base de datos Room.
     *
     * @param orderedIds Lista de IDs de cuentas en su orden definitivo tras finalizar el arrastre.
     */
    fun commitReorder(orderedIds: List<String>) {
        viewModelScope.launch {
            repository.reorderAccounts(orderedIds)
        }
    }

    /**
     * Elimina permanentemente una cuenta de la bóveda.
     */
    fun deleteAccount(id: String) {
        viewModelScope.launch {
            repository.deleteAccount(id)
        }
    }

    /**
     * Avanza el contador de un token HOTP y actualiza el código numérico.
     */
    fun nextHotpCode(id: String) {
        viewModelScope.launch {
            repository.incrementHotpCounter(id)
        }
    }

    /**
     * Bloquea manualmente la bóveda criptográfica.
     */
    fun lockVault() {
        appLockManager.lock()
    }
}
