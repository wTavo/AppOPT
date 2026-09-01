package com.example.appopt.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor del estado de bloqueo global de la aplicación.
 *
 * Principio de seguridad:
 * - Observa el ciclo de vida del proceso general de la app ([ProcessLifecycleOwner]).
 * - Cuando la app pasa a segundo plano, calcula el tiempo transcurrido y bloquea la bóveda
 *   para exigir re-autenticación biométrica o por PIN.
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
     * Bloquea inmediatamente la bóveda y limpia el estado de acceso.
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
    }
}
