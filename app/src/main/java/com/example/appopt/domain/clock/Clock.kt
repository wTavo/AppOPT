package com.example.appopt.domain.clock

/**
 * Abstracción de reloj para desacoplar el tiempo del sistema y permitir pruebas unitarias deterministas.
 */
interface Clock {
    /**
     * Retorna el tiempo actual en milisegundos desde la época UNIX (1 de enero de 1970 UTC).
     */
    fun currentTimeMillis(): Long
}

/**
 * Implementación de producción basada en el reloj del sistema operativo.
 */
object SystemClock : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
