package com.example.appopt.security

import android.os.SystemClock
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
 * - Utiliza un reloj monotónico inmutable ([SystemClock.elapsedRealtime]) inmune a alteraciones manuales o NTP del reloj del sistema.
 * - Cuando la app pasa a segundo plano por más de 5 segundos, bloquea la interfaz para exigir biometría.
 * - Acota las suspensiones temporales para actividades externas del sistema con un TTL máximo estricto ([SecurityConfig.EXTERNAL_ACTIVITY_SUSPEND_TTL_MILLIS]).
 */
class AppLockManager : DefaultLifecycleObserver {

    private val _isUnlocked = MutableStateFlow(false)

    /**
     * Flujo reactivo que indica si la bóveda está actualmente desbloqueada.
     */
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var backgroundTimestamp = 0L
    private var suspendedAtTimestamp = 0L
    private var isTemporarilySuspended = false
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

    /**
     * Suspende temporalmente el bloqueo de la bóveda para flujos del sistema legítimos
     * (como la vista previa de impresión PDF o selectores de archivos del sistema operativo),
     * acotado por [SecurityConfig.EXTERNAL_ACTIVITY_SUSPEND_TTL_MILLIS].
     */
    fun suspendLockTemporarily() {
        isTemporarilySuspended = true
        suspendedAtTimestamp = elapsedMonotonicMillis()
        backgroundTimestamp = 0L
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        backgroundTimestamp = elapsedMonotonicMillis()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        val now = elapsedMonotonicMillis()
        if (isTemporarilySuspended) {
            val suspendedElapsed = now - suspendedAtTimestamp
            isTemporarilySuspended = false
            suspendedAtTimestamp = 0L
            backgroundTimestamp = 0L
            if (suspendedElapsed > SecurityConfig.EXTERNAL_ACTIVITY_SUSPEND_TTL_MILLIS) {
                lock()
            }
            return
        }
        if (backgroundTimestamp > 0L && (now - backgroundTimestamp) >= lockTimeoutMillis) {
            lock()
        }
    }

    private fun elapsedMonotonicMillis(): Long {
        return try {
            val rt = SystemClock.elapsedRealtime()
            if (rt > 0L) rt else System.nanoTime() / 1_000_000L
        } catch (_: Throwable) {
            System.nanoTime() / 1_000_000L
        }
    }
}
