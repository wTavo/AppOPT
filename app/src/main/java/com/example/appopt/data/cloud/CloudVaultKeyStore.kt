package com.example.appopt.data.cloud

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.example.appopt.AuthenticatorApp
import com.example.appopt.security.BackupCrypto
import com.example.appopt.security.CryptoManager
import com.example.appopt.security.EncryptedPayload

/**
 * Gestor de persistencia segura para la clave de bóveda en la nube ([vaultKey]) y ranuras asociadas.
 *
 * Protege la [vaultKey] utilizando el hardware seguro del dispositivo (Android Keystore / TEE)
 * mediante [CryptoManager], permitiendo que [AutoSyncWorker] ejecute sincronizaciones automáticas
 * en segundo plano cifrando los datos con la misma [vaultKey] del sobre v2 sin requerir
 * la contraseña del usuario en cada ejecución.
 */
object CloudVaultKeyStore {

    private const val PREFS_NAME = "appopt_cloud_vault_keystore"
    private const val KEY_ENCRYPTED_VAULT_KEY = "encrypted_vault_key"
    private const val KEY_VAULT_KEY_IV = "vault_key_iv"
    private const val KEY_MAIN_SLOT_SALT = "main_slot_salt"
    private const val KEY_MAIN_SLOT_IV = "main_slot_iv"
    private const val KEY_MAIN_SLOT_WRAPPED_KEY = "main_slot_wrapped_key"
    private const val KEY_EMERGENCY_SLOT_SALT = "emergency_slot_salt"
    private const val KEY_EMERGENCY_SLOT_IV = "emergency_slot_iv"
    private const val KEY_EMERGENCY_SLOT_WRAPPED_KEY = "emergency_slot_wrapped_key"

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Guarda la clave de la bóveda (cifrada con Android Keystore) y los metadatos de las ranuras.
     *
     * @param context Contexto de la aplicación.
     * @param vaultKey Clave simétrica de 256 bits a custodiar.
     * @param mainSlot Ranura principal envuelta con la contraseña maestra.
     * @param emergencySlot Ranura de emergencia opcional envuelta con mnemónico BIP-39.
     */
    fun saveVaultKeyAndSlots(
        context: Context,
        vaultKey: ByteArray,
        mainSlot: BackupCrypto.WrappedSlot,
        emergencySlot: BackupCrypto.WrappedSlot?
    ) {
        val cryptoManager = AuthenticatorApp.instance.cryptoManager
        val encrypted = cryptoManager.encrypt(vaultKey)

        val prefs = getPrefs(context)
        prefs.edit {
            putString(KEY_ENCRYPTED_VAULT_KEY, Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP))
            putString(KEY_VAULT_KEY_IV, Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
            putString(KEY_MAIN_SLOT_SALT, mainSlot.saltB64)
            putString(KEY_MAIN_SLOT_IV, mainSlot.ivB64)
            putString(KEY_MAIN_SLOT_WRAPPED_KEY, mainSlot.wrappedKeyB64)
            if (emergencySlot != null) {
                putString(KEY_EMERGENCY_SLOT_SALT, emergencySlot.saltB64)
                putString(KEY_EMERGENCY_SLOT_IV, emergencySlot.ivB64)
                putString(KEY_EMERGENCY_SLOT_WRAPPED_KEY, emergencySlot.wrappedKeyB64)
            } else {
                remove(KEY_EMERGENCY_SLOT_SALT)
                remove(KEY_EMERGENCY_SLOT_IV)
                remove(KEY_EMERGENCY_SLOT_WRAPPED_KEY)
            }
        }
    }

    /**
     * Guarda la sesión completa de clave de bóveda y ranuras.
     *
     * @param context Contexto de la aplicación.
     * @param session Sesión activa con [VaultKeySession].
     */
    fun saveVaultKeyAndSlots(
        context: Context,
        session: VaultKeySession
    ) {
        saveVaultKeyAndSlots(
            context = context,
            vaultKey = session.vaultKey,
            mainSlot = session.mainSlot,
            emergencySlot = session.emergencySlot
        )
    }

    /**
     * Retorna si existe una clave de bóveda custodiada y lista para sincronización automática.
     *
     * @param context Contexto de la aplicación.
     * @return `true` si la clave existe y está disponible, `false` en caso contrario.
     */
    fun hasVaultKey(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.contains(KEY_ENCRYPTED_VAULT_KEY) && prefs.contains(KEY_MAIN_SLOT_WRAPPED_KEY)
    }

    /**
     * Recupera la clave de bóveda descifrada mediante Android Keystore y las ranuras para la copia de seguridad.
     *
     * @param context Contexto de la aplicación.
     * @return [VaultKeySession] con la clave de 256 bits y ranuras, o `null` si no está disponible o falla el Keystore.
     */
    fun getVaultKeyAndSlots(context: Context): VaultKeySession? {
        val prefs = getPrefs(context)
        val cipherB64 = prefs.getString(KEY_ENCRYPTED_VAULT_KEY, null) ?: return null
        val ivB64 = prefs.getString(KEY_VAULT_KEY_IV, null) ?: return null
        val mainSalt = prefs.getString(KEY_MAIN_SLOT_SALT, null) ?: return null
        val mainIv = prefs.getString(KEY_MAIN_SLOT_IV, null) ?: return null
        val mainKey = prefs.getString(KEY_MAIN_SLOT_WRAPPED_KEY, null) ?: return null

        val cryptoManager = AuthenticatorApp.instance.cryptoManager
        val cipherBytes = Base64.decode(cipherB64, Base64.NO_WRAP)
        val ivBytes = Base64.decode(ivB64, Base64.NO_WRAP)
        val vaultKey = try {
            cryptoManager.decrypt(ciphertext = cipherBytes, iv = ivBytes)
        } catch (_: Exception) {
            return null
        }

        val mainSlot = BackupCrypto.WrappedSlot(mainSalt, mainIv, mainKey)
        val emergencySlot = if (prefs.contains(KEY_EMERGENCY_SLOT_WRAPPED_KEY)) {
            val emSalt = prefs.getString(KEY_EMERGENCY_SLOT_SALT, null)
            val emIv = prefs.getString(KEY_EMERGENCY_SLOT_IV, null)
            val emKey = prefs.getString(KEY_EMERGENCY_SLOT_WRAPPED_KEY, null)
            if (emSalt != null && emIv != null && emKey != null) {
                BackupCrypto.WrappedSlot(emSalt, emIv, emKey)
            } else {
                null
            }
        } else {
            null
        }

        return VaultKeySession(vaultKey, mainSlot, emergencySlot)
    }

    /**
     * Elimina de forma definitiva la clave protegida y ranuras custodiadas en el dispositivo.
     *
     * @param context Contexto de la aplicación.
     */
    fun clear(context: Context) {
        getPrefs(context).edit { clear() }
    }

    /**
     * Sesión de clave de bóveda y ranuras asociadas para operaciones de sincronización en la nube.
     *
     * @property vaultKey Clave simétrica de 256 bits recuperada de hardware seguro.
     * @property mainSlot Ranura principal para la contraseña maestra.
     * @property emergencySlot Ranura de emergencia opcional para la frase mnemónica.
     */
    data class VaultKeySession(
        val vaultKey: ByteArray,
        val mainSlot: BackupCrypto.WrappedSlot,
        val emergencySlot: BackupCrypto.WrappedSlot?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as VaultKeySession
            if (!vaultKey.contentEquals(other.vaultKey)) return false
            if (mainSlot != other.mainSlot) return false
            if (emergencySlot != other.emergencySlot) return false
            return true
        }

        override fun hashCode(): Int {
            var result = vaultKey.contentHashCode()
            result = 31 * result + mainSlot.hashCode()
            result = 31 * result + (emergencySlot?.hashCode() ?: 0)
            return result
        }
    }
}
