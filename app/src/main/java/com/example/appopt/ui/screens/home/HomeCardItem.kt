package com.example.appopt.ui.screens.home

import com.example.appopt.domain.model.AccountWithCode
import com.example.appopt.domain.model.IncomingSharedService

/**
 * Modela un elemento visual de tarjeta OTP para la pantalla principal unificando cuentas propias y compartidas (Directivas 8, 30 y DRY).
 */
sealed interface HomeCardItem {
    val key: String
    val isFavorite: Boolean
    val issuer: String

    /**
     * Tarjeta correspondiente a una cuenta propia persistida en la base de datos local.
     */
    data class Own(val item: AccountWithCode) : HomeCardItem {
        override val key: String get() = item.account.id
        override val isFavorite: Boolean get() = item.account.isFavorite
        override val issuer: String get() = item.account.issuer
    }

    /**
     * Tarjeta correspondiente a un servicio compartido recibido de un tercero (Zero-Secret).
     */
    data class Shared(val service: IncomingSharedService) : HomeCardItem {
        override val key: String get() = "shared_${service.grantId}"
        override val isFavorite: Boolean get() = service.isFavorite
        override val issuer: String get() = service.issuer
    }

    /**
     * Determina si el elemento coincide con la consulta de búsqueda activa.
     */
    fun matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        return when (this) {
            is Own -> {
                item.account.issuer.contains(query, ignoreCase = true) ||
                    item.account.accountName.contains(query, ignoreCase = true)
            }
            is Shared -> {
                service.issuer.contains(query, ignoreCase = true) ||
                    service.accountName.contains(query, ignoreCase = true) ||
                    service.ownerEmail.contains(query, ignoreCase = true)
            }
        }
    }
}


