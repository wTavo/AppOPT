package com.example.appopt.domain.model

/**
 * Modos de acceso disponibles al compartir un servicio TOTP con otro usuario (Directiva 31).
 */
enum class ShareAccessMode {
    /**
     * Modo estricto en línea: el dispositivo invitado solicita únicamente el código de 30 segundos actual
     * bajo demanda al servidor, garantizando revocación instantánea en 0 segundos.
     */
    ONLINE_ONLY,

    /**
     * Modo de lote temporal sin conexión: el servidor entrega una lista precalculada de códigos futuros
     * para la ventana de horas autorizada sin exponer jamás la semilla Base32.
     */
    OFFLINE_BATCH;

    companion object {
        /**
         * Resuelve de forma segura el modo de acceso a partir de su representación textual.
         *
         * @param value Cadena del modo de acceso.
         * @return [ShareAccessMode] correspondiente (por defecto [ONLINE_ONLY]).
         */
        fun fromWireValue(value: String?): ShareAccessMode {
            return if (value?.uppercase() == OFFLINE_BATCH.name) OFFLINE_BATCH else ONLINE_ONLY
        }
    }
}

/**
 * Duraciones predefinidas de vigencia para lotes de códigos temporales sin conexión (Directiva 31).
 *
 * @property hours Cantidad de horas de validez del lote precalculado.
 */
enum class ShareBatchDuration(val hours: Int) {
    /** Lote corto de alta seguridad válido por 1 hora (120 códigos de 30 s). */
    ONE_HOUR(1),

    /** Lote intermedio válido por 6 horas (720 códigos de 30 s). */
    SIX_HOURS(6),

    /** Lote estándar válido por 24 horas (2,880 códigos de 30 s). */
    TWENTY_FOUR_HOURS(24);

    companion object {
        /**
         * Obtiene la duración estandarizada a partir del número de horas.
         *
         * @param hours Horas solicitadas.
         * @return [ShareBatchDuration] correspondiente.
         */
        fun fromHours(hours: Int): ShareBatchDuration {
            return entries.firstOrNull { it.hours == hours } ?: TWENTY_FOUR_HOURS
        }
    }
}
