package com.example.appopt.domain.model

/**
 * Lote temporal de códigos OTP precalculados para uso sin conexión de un servicio compartido (Directivas 30 y 31).
 *
 * Garantía Zero-Secret:
 * - Almacena únicamente pares `(timeStep -> código de 6/8 dígitos)` dentro de la ventana temporal autorizada.
 * - Es matemáticamente imposible reconstruir la semilla Base32 a partir de los códigos truncados.
 * - Caduca de forma determinística e irreversible al alcanzarse [expiresAtMillis].
 *
 * @property grantId Identificador del permiso compartido al que pertenece el lote.
 * @property periodSeconds Período de rotación en segundos (por defecto 30 s).
 * @property expiresAtMillis Marca de tiempo de expiración absoluta del lote en milisegundos.
 * @property codesByStep Mapa de pasos de tiempo (`epochSeconds / periodSeconds`) hacia su código OTP correspondiente.
 * @property isSingleUseWindow Indica si corresponde a una brecha de un solo uso que debe eliminarse al concluir el último código.
 */
data class SharedOtpBatch(
    val grantId: String,
    val periodSeconds: Int,
    val expiresAtMillis: Long,
    val codesByStep: Map<Long, String>,
    val isSingleUseWindow: Boolean = false
) {
    /**
     * Determina si el lote precalculado ha caducado respecto al instante indicado.
     *
     * @param nowMillis Tiempo actual en milisegundos.
     * @return `true` si el lote ya expiró o no contiene códigos vigentes.
     */
    fun isExpired(nowMillis: Long): Boolean {
        return nowMillis >= expiresAtMillis || codesByStep.isEmpty()
    }

    /**
     * Obtiene el código OTP precalculado correspondiente al instante indicado si el lote sigue vigente.
     *
     * @param nowMillis Tiempo actual en milisegundos.
     * @return Código OTP de 6/8 dígitos, o `null` si el lote expiró o no cubre dicho instante.
     */
    fun getCodeAt(nowMillis: Long): String? {
        if (isExpired(nowMillis)) return null
        val safePeriod = periodSeconds.coerceAtLeast(1)
        val currentStep = (nowMillis / 1000L) / safePeriod
        return codesByStep[currentStep]
    }

    /**
     * Si el lote corresponde a una brecha corta en línea (p. ej. 3 códigos consecutivos),
     * devuelve el par `(pasoActualBase1, totalPasos)` vigente en `nowMillis`.
     *
     * @param nowMillis Tiempo actual en milisegundos.
     * @param maxLiveWindowSteps Límite máximo de pasos para considerar el lote como una brecha corta en línea.
     * @return Par `(pasoActual, totalPasos)` o `null` si es un lote largo sin conexión o expiró.
     */
    fun getWindowStepProgress(nowMillis: Long, maxLiveWindowSteps: Int = 3): Pair<Int, Int>? {
        if (isExpired(nowMillis) || codesByStep.size > maxLiveWindowSteps) return null
        val safePeriod = periodSeconds.coerceAtLeast(1)
        val currentStep = (nowMillis / 1000L) / safePeriod
        val sortedSteps = codesByStep.keys.sorted()
        val idx = sortedSteps.indexOf(currentStep)
        return if (idx >= 0) (idx + 1) to sortedSteps.size else null
    }

    /**
     * Calcula los segundos restantes de validez del código actual dentro de su ventana de rotación.
     *
     * @param nowMillis Tiempo actual en milisegundos.
     * @return Segundos restantes en el rango `1..periodSeconds`.
     */
    fun getRemainingSecondsAt(nowMillis: Long): Int {
        val safePeriod = periodSeconds.coerceAtLeast(1)
        val nowSeconds = nowMillis / 1000L
        return (safePeriod - (nowSeconds % safePeriod)).toInt()
    }
}
