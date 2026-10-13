package com.example.appopt.domain.model

/**
 * Estado del ciclo de vida de un permiso o invitación de servicio compartido (Directivas 30 y 31).
 *
 * - [PENDING]: Invitación enviada por el propietario (A), pendiente de que el invitado (B) la acepte.
 *   Mientras esté en este estado, el servidor bloquea cualquier generación o descarga de códigos TOTP.
 * - [ACCEPTED]: Invitación aceptada explícitamente por el invitado (B). Permite consultar códigos en línea
 *   o descargar lotes precalculados según el modo autorizado.
 */
enum class ShareGrantStatus {
    PENDING,
    ACCEPTED;

    companion object {
        /**
         * Convierte el valor en texto devuelto por la API REST hacia [ShareGrantStatus].
         *
         * @param raw Cadena recibida desde JSON (`"PENDING"` o `"ACCEPTED"`).
         * @return Estado tipado correspondiente (por defecto [ACCEPTED] para compatibilidad retroactiva).
         */
        fun fromWireValue(raw: String?): ShareGrantStatus {
            return if (raw.equals("PENDING", ignoreCase = true)) PENDING else ACCEPTED
        }
    }
}
