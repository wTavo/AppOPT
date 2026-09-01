package com.example.appopt.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de persistencia en base de datos SQLite / Room para una cuenta OTP.
 *
 * Principio de diseño de seguridad:
 * - Los secretos nunca se almacenan en texto plano en la base de datos.
 * - [encryptedSecret] almacena el ciphertext cifrado con AES-256-GCM.
 * - [iv] almacena el vector de inicialización único de 12 bytes requerido para descifrar y verificar autenticidad.
 * - Metadatos no sensibles ([issuer], [accountName]) se conservan en texto plano para búsquedas y listados eficientes.
 */
@Entity(tableName = "totp_accounts")
data class AccountEntity(
    @PrimaryKey
    val id: String,
    val issuer: String,
    val accountName: String,
    val encryptedSecret: ByteArray,
    val iv: ByteArray,
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30,
    val type: String = "TOTP",
    val counter: Long = 0L,
    val isFavorite: Boolean = false,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccountEntity

        if (id != other.id) return false
        if (issuer != other.issuer) return false
        if (accountName != other.accountName) return false
        if (!encryptedSecret.contentEquals(other.encryptedSecret)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (algorithm != other.algorithm) return false
        if (digits != other.digits) return false
        if (period != other.period) return false
        if (type != other.type) return false
        if (counter != other.counter) return false
        if (isFavorite != other.isFavorite) return false
        if (orderIndex != other.orderIndex) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + issuer.hashCode()
        result = 31 * result + accountName.hashCode()
        result = 31 * result + encryptedSecret.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + digits
        result = 31 * result + period
        result = 31 * result + type.hashCode()
        result = 31 * result + counter.hashCode()
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + orderIndex
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
