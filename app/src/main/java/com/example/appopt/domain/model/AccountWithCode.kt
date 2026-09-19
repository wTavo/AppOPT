package com.example.appopt.domain.model

/**
 * Contenedor de presentación que asocia una cuenta [TotpAccount] con su código OTP activo.
 *
 * Nota de rendimiento: Los campos de tiempo (segundos restantes, progreso) se eliminaron
 * intencionalmente de este modelo para evitar que el StateFlow emita nuevos valores cada segundo,
 * lo que causaba la recomposición de todas las tarjetas visibles durante el scroll.
 * [CircularTimeProgress] calcula el tiempo de forma autónoma internamente.
 *
 * @property account Datos descriptivos de la cuenta.
 * @property code Código numérico OTP activo (o valor de espera).
 */
data class AccountWithCode(
    val account: TotpAccount,
    val code: String
)
