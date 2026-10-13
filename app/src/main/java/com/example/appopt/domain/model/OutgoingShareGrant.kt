package com.example.appopt.domain.model

/**
 * Representa un permiso de acceso compartido otorgado por el propietario hacia un usuario invitado (Directiva 30 y 31).
 *
 * @property grantId Identificador único del permiso en el servidor.
 * @property accountId Identificador de la cuenta local asociada en la bóveda del propietario.
 * @property issuer Nombre del emisor o servicio compartido.
 * @property accountName Nombre de usuario o cuenta asociada.
 * @property guestEmail Correo electrónico verificado del usuario invitado.
 * @property accessMode Modo de acceso autorizado ([ShareAccessMode.ONLINE_ONLY] o [ShareAccessMode.OFFLINE_BATCH]).
 * @property batchHours Duración en horas del lote sin conexión (si aplica).
 * @property createdAtMillis Marca de tiempo de creación del permiso en milisegundos.
 * @property status Estado actual de la invitación ([ShareGrantStatus.PENDING] o [ShareGrantStatus.ACCEPTED]).
 */
data class OutgoingShareGrant(
    val grantId: String,
    val accountId: String,
    val issuer: String,
    val accountName: String,
    val guestEmail: String,
    val accessMode: ShareAccessMode,
    val batchHours: Int,
    val createdAtMillis: Long,
    val status: ShareGrantStatus = ShareGrantStatus.ACCEPTED
)
