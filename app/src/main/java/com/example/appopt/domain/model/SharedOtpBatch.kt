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
 */
data class SharedOtpBatch(
    val grantId: String,
    val periodSeconds: Int,
    val expiresAtMillis: Long,
    val codesByStep: Map<Long, String>
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
