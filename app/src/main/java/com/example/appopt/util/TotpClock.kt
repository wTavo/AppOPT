package com.example.appopt.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Reloj centralizado de pulsos de segundo para sincronizar todos los componentes visuales TOTP.
 *
 * Ventajas de rendimiento:
 * - Mantiene una única corrutina en segundo plano ([Dispatchers.Default]) en toda la app.
 * - Elimina la creación y destrucción de [androidx.compose.runtime.LaunchedEffect] independientes por cada tarjeta visible.
 * - Sincroniza todas las tarjetas exactamente al mismo fotograma de refresco.
 */
object TotpClock {

    private val _currentSecondEpoch = MutableStateFlow(System.currentTimeMillis() / 1000L)

    /**
     * Emite el timestamp Unix actual en segundos (segundo exacto).
     */
    val currentSecondEpoch: StateFlow<Long> = _currentSecondEpoch.asStateFlow()

    init {
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            while (true) {
                val now = System.currentTimeMillis()
                _currentSecondEpoch.value = now / 1000L
                val msUntilNext = 1000L - (now % 1000L)
                delay(msUntilNext.coerceAtLeast(50L))
            }
        }
    }
}
