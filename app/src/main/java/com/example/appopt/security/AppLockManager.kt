package com.example.appopt.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.appopt.AuthenticatorApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Gestor del estado de bloqueo global de la aplicación.
 *
 * Principio de seguridad y rendimiento:
 * - Observa el ciclo de vida del proceso general de la app ([ProcessLifecycleOwner]).
 * - Cuando la app pasa a segundo plano por más de 5 segundos, bloquea la interfaz para exigir biometría.
 * - Los secretos crudos en RAM ya fueron sobreescritos con ceros tras su uso ([com.example.appopt.security.CryptoManager.zeroize]).
 * - Al volver al primer plano ([onStart]), precalienta en segundo plano los códigos en RAM para que el scroll sea instantáneo a 120 FPS al desbloquear.
 */
class AppLockManager : DefaultLifecycleObserver {

    private val _isUnlocked = MutableStateFlow(false)

    /**
     * Flujo reactivo que indica si la bóveda está actualmente desbloqueada.
     */
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var backgroundTimestamp = 0L
    private val lockTimeoutMillis = 5_000L // Bloqueo tras 5 segundos en background

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * Marca la bóveda como desbloqueada tras una autenticación exitosa.
     */
    fun unlock() {
        _isUnlocked.value = true
    }

    /**
     * Bloquea inmediatamente la interfaz de la bóveda.
     */
    fun lock() {
        _isUnlocked.value = false
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        backgroundTimestamp = System.currentTimeMillis()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        if (backgroundTimestamp > 0L && (System.currentTimeMillis() - backgroundTimestamp) >= lockTimeoutMillis) {
            lock()
        }

        // Precalentamiento de códigos en segundo plano mientras el usuario se autentica
        try {
            val repo = AuthenticatorApp.instance.accountRepository
            CoroutineScope(Dispatchers.IO).launch {
                repo.getAccounts().firstOrNull()?.let { accounts ->
                    if (accounts.isNotEmpty()) {
                        repo.computeAccountsWithCodes(accounts, System.currentTimeMillis())
                    }
                }
            }
        } catch (_: Exception) {
            // Repositorio aún no inicializado
        }
    }
}
