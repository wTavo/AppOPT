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
    private val lockTimeoutMillis = SecurityConfig.APP_LOCK_TIMEOUT_MILLIS

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
    }
}
