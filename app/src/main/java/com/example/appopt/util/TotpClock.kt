package com.example.appopt.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reloj centralizado de pulsos de segundo para sincronizar todos los componentes visuales TOTP.
 *
 * Ventajas de rendimiento:
 * - Emisión reactiva gobernada por [SharingStarted.WhileSubscribed]: suspende y detiene la corrutina en segundo plano cuando no hay observadores activos (0% uso de CPU y 0 consumo de batería al salir de la app).
 * - Elimina la creación y destrucción de [androidx.compose.runtime.LaunchedEffect] independientes por cada tarjeta visible.
 * - Sincroniza todas las tarjetas exactamente al mismo fotograma de refresco.
 */
object TotpClock {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Emite el timestamp Unix actual en segundos (segundo exacto).
     */
    val currentSecondEpoch: StateFlow<Long> = flow {
        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            emit(now / 1000L)
            val msUntilNext = 1000L - (now % 1000L)
            delay(msUntilNext.coerceAtLeast(50L).milliseconds)
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1000L),
        initialValue = System.currentTimeMillis() / 1000L
    )
}
