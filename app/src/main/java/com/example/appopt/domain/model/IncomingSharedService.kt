package com.example.appopt.domain.model

/**
 * Representa un servicio compartido por otro usuario hacia el dispositivo actual (Directiva 30 y 31).
 *
 * Garantía de seguridad Zero-Secret:
 * - Este modelo contiene únicamente metadatos descriptivos y parámetros de visualización.
 * - Jamás contiene ni transporta la clave secreta Base32.
 *
 * @property grantId Identificador único del permiso de compartición.
 * @property ownerEmail Correo electrónico del propietario que compartió el servicio.
 * @property issuer Nombre del servicio o proveedor.
 * @property accountName Nombre de usuario o cuenta asociada.
 * @property digits Cantidad de dígitos del código OTP (6 u 8).
 * @property period Período de rotación en segundos (estándar 30 s).
 * @property accessMode Modo de acceso configurado por el propietario ([ShareAccessMode]).
 * @property batchHours Horas de validez del lote sin conexión (si [accessMode] es [ShareAccessMode.OFFLINE_BATCH]).
 * @property status Estado actual de la invitación ([ShareGrantStatus.PENDING] o [ShareGrantStatus.ACCEPTED]).
 */
data class IncomingSharedService(
    val grantId: String,
    val ownerEmail: String,
    val issuer: String,
    val accountName: String,
    val digits: Int,
    val period: Int,
    val accessMode: ShareAccessMode,
    val batchHours: Int,
    val status: ShareGrantStatus = ShareGrantStatus.ACCEPTED
)
