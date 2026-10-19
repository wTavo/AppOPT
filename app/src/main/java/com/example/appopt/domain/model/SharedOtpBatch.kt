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
     */
    fun isExpired(nowMillis: Long): Boolean {
        return nowMillis >= expiresAtMillis || codesByStep.isEmpty()
    }

    /**
     * Obtiene el código OTP precalculado correspondiente al instante indicado si el lote sigue vigente.
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
     */
    fun getRemainingSecondsAt(nowMillis: Long): Int {
        val safePeriod = periodSeconds.coerceAtLeast(1)
        val nowSeconds = nowMillis / 1000L
        return (safePeriod - (nowSeconds % safePeriod)).toInt()
    }
}

/**
 * Resultado de una solicitud de brecha de códigos OTP efímeros en línea desde el servidor calculador (Directiva 31).
 *
 * @property code Código OTP de 6 u 8 dígitos para el paso inicial.
 * @property remainingSeconds Segundos restantes de vigencia del código inicial.
 * @property periodSeconds Período total de rotación en segundos.
 * @property expiresAtMillis Marca de tiempo en milisegundos en la que caduca el último código de la brecha.
 * @property codesByStep Mapa de pasos de tiempo (`epochSeconds / periodSeconds`) hacia su código OTP dentro de la brecha.
 * @property singleUseConsumed Indica si el permiso era de un solo uso y fue consumido al entregar esta brecha.
 */
data class LiveSharedCodeResult(
    val code: String,
    val remainingSeconds: Int,
    val periodSeconds: Int,
    val expiresAtMillis: Long = 0L,
    val codesByStep: Map<Long, String> = emptyMap(),
    val singleUseConsumed: Boolean = false
) {
    /**
     * Convierte esta brecha temporal de códigos en un [SharedOtpBatch] para resguardarla cifrada durante su vigencia.
     */
    fun toSharedOtpBatch(grantId: String): SharedOtpBatch {
        return SharedOtpBatch(
            grantId = grantId,
            periodSeconds = periodSeconds,
            expiresAtMillis = expiresAtMillis,
            codesByStep = codesByStep,
            isSingleUseWindow = singleUseConsumed
        )
    }

    /**
     * Obtiene el código OTP correspondiente al instante indicado dentro de la brecha de códigos.
     */
    fun getCodeAt(nowMillis: Long): String? = toSharedOtpBatch("").getCodeAt(nowMillis)

    /**
     * Calcula el progreso actual dentro de la brecha de códigos (ej. `1 to 3`, `2 to 3`, `3 to 3`).
     */
    fun getWindowStepProgress(nowMillis: Long): Pair<Int, Int>? =
        toSharedOtpBatch("").getWindowStepProgress(nowMillis, maxLiveWindowSteps = 3)
}
