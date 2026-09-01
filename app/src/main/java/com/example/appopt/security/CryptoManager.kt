package com.example.appopt.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Contenedor del resultado de una operación de cifrado autenticado.
 *
 * @property ciphertext Datos cifrados protegidos con etiqueta de autenticación GCM.
 * @property iv Vector de inicialización único utilizado en la operación.
 */
data class EncryptedPayload(
    val ciphertext: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedPayload

        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!iv.contentEquals(other.iv)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}

/**
 * Gestor criptográfico responsable de la seguridad del almacenamiento local.
 *
 * Garantías de seguridad:
 * - Utiliza el hardware seguro del dispositivo (**Android Keystore / TEE**) para custodiar la clave maestra AES-256.
 * - Utiliza cifrado simétrico autenticado **AES/GCM/NoPadding** que previene ataques de modificación de datos (*tampering*).
 * - Proporciona utilidades para sobreescribir memoria con ceros (*zeroing*).
 */
class CryptoManager(
    private val keyAlias: String = "AppOPT_Vault_Master_Key"
) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private val secureRandom = SecureRandom()

    /**
     * Obtiene la clave maestra existente en el hardware seguro o genera una nueva de 256 bits si no existe.
     *
     * @return [SecretKey] gestionada por el AndroidKeyStore.
     */
    private fun getOrCreateSecretKey(): SecretKey {
        if (keyStore.containsAlias(keyAlias)) {
            val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Cifra un arreglo de bytes en texto plano utilizando AES-256-GCM.
     *
     * @param plainBytes Material sensible a proteger.
     * @return [EncryptedPayload] con el texto cifrado y el vector de inicialización (IV).
     */
    fun encrypt(plainBytes: ByteArray): EncryptedPayload {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainBytes)
        return EncryptedPayload(ciphertext = ciphertext, iv = iv)
    }

    /**
     * Descifra un texto cifrado verificando su integridad y autenticidad mediante la etiqueta GCM.
     *
     * @param ciphertext Texto cifrado.
     * @param iv Vector de inicialización con el que fue cifrado.
     * @return Arreglo de bytes en texto plano.
     * @throws javax.crypto.AEADBadTagException si los datos han sido manipulados o la clave es incorrecta.
     */
    fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Genera una clave de recuperación (Recovery Key) independiente con 256 bits de entropía CSPRNG.
     *
     * @return Cadena formateada en grupos de 4 caracteres (ej. `XXXX-XXXX-XXXX-...`).
     */
    fun generateRecoveryKey(): String {
        val randomBytes = ByteArray(32)
        secureRandom.nextBytes(randomBytes)
        val base32 = com.example.appopt.domain.totp.Base32.encode(randomBytes)
        return base32.chunked(4).joinToString("-")
    }

    companion object {
        /**
         * Sobreescribe un arreglo de bytes con ceros en memoria RAM para evitar que persista en volcados.
         *
         * @param bytes Arreglo de bytes a limpiar.
         */
        fun zeroize(bytes: ByteArray) {
            bytes.fill(0)
        }
    }
}
