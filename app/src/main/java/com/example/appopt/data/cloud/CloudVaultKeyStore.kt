package com.example.appopt.data.cloud

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.example.appopt.AuthenticatorApp
import com.example.appopt.security.BackupCrypto
import com.example.appopt.data.local.PreferencesManager

/**
 * Gestor de persistencia segura para la clave de bóveda en la nube ([vaultKey]) y ranuras asociadas.
 *
 * Protege la [vaultKey] utilizando el hardware seguro del dispositivo (Android Keystore / TEE)
 * mediante [CryptoManager], permitiendo que [AutoSyncWorker] ejecute sincronizaciones automáticas
 * en segundo plano cifrando los datos con la misma [vaultKey] del sobre v2 sin requerir
 * la contraseña del usuario en cada ejecución.
 */
object CloudVaultKeyStore {

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PreferencesManager.PREFS_CLOUD_VAULT_KEYSTORE, Context.MODE_PRIVATE)

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
            putString(PreferencesManager.KEY_ENCRYPTED_VAULT_KEY, Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP))
            putString(PreferencesManager.KEY_VAULT_KEY_IV, Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
            putString(PreferencesManager.KEY_MAIN_SLOT_SALT, mainSlot.saltB64)
            putString(PreferencesManager.KEY_MAIN_SLOT_IV, mainSlot.ivB64)
            putString(PreferencesManager.KEY_MAIN_SLOT_WRAPPED_KEY, mainSlot.wrappedKeyB64)
            if (emergencySlot != null) {
                putString(PreferencesManager.KEY_EMERGENCY_SLOT_SALT, emergencySlot.saltB64)
                putString(PreferencesManager.KEY_EMERGENCY_SLOT_IV, emergencySlot.ivB64)
                putString(PreferencesManager.KEY_EMERGENCY_SLOT_WRAPPED_KEY, emergencySlot.wrappedKeyB64)
            } else {
                remove(PreferencesManager.KEY_EMERGENCY_SLOT_SALT)
                remove(PreferencesManager.KEY_EMERGENCY_SLOT_IV)
                remove(PreferencesManager.KEY_EMERGENCY_SLOT_WRAPPED_KEY)
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
        return prefs.contains(PreferencesManager.KEY_ENCRYPTED_VAULT_KEY) && prefs.contains(PreferencesManager.KEY_MAIN_SLOT_WRAPPED_KEY)
    }

    /**
     * Elimina la clave de bóveda y ranuras custodiadas cuando el usuario cambia a una cuenta de Google diferente.
     *
     * @param context Contexto de la aplicación.
     */
    fun clearVaultKey(context: Context) {
        getPrefs(context).edit { clear() }
    }

    /**
     * Recupera la clave de bóveda descifrada mediante Android Keystore y las ranuras para la copia de seguridad.
     *
     * @param context Contexto de la aplicación.
     * @return [VaultKeySession] con la clave de 256 bits y ranuras, o `null` si no está disponible o falla el Keystore.
     */
    fun getVaultKeyAndSlots(context: Context): VaultKeySession? {
        val prefs = getPrefs(context)
        val cipherB64 = prefs.getString(PreferencesManager.KEY_ENCRYPTED_VAULT_KEY, null) ?: return null
        val ivB64 = prefs.getString(PreferencesManager.KEY_VAULT_KEY_IV, null) ?: return null
        val mainSalt = prefs.getString(PreferencesManager.KEY_MAIN_SLOT_SALT, null) ?: return null
        val mainIv = prefs.getString(PreferencesManager.KEY_MAIN_SLOT_IV, null) ?: return null
        val mainKey = prefs.getString(PreferencesManager.KEY_MAIN_SLOT_WRAPPED_KEY, null) ?: return null

        val cryptoManager = AuthenticatorApp.instance.cryptoManager
        val cipherBytes = Base64.decode(cipherB64, Base64.NO_WRAP)
        val ivBytes = Base64.decode(ivB64, Base64.NO_WRAP)
        val vaultKey = try {
            cryptoManager.decrypt(ciphertext = cipherBytes, iv = ivBytes)
        } catch (_: Exception) {
            return null
        }

        val mainSlot = BackupCrypto.WrappedSlot(mainSalt, mainIv, mainKey)
        val emergencySlot = if (prefs.contains(PreferencesManager.KEY_EMERGENCY_SLOT_WRAPPED_KEY)) {
            val emSalt = prefs.getString(PreferencesManager.KEY_EMERGENCY_SLOT_SALT, null)
            val emIv = prefs.getString(PreferencesManager.KEY_EMERGENCY_SLOT_IV, null)
            val emKey = prefs.getString(PreferencesManager.KEY_EMERGENCY_SLOT_WRAPPED_KEY, null)
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
            result = 31 * result + emergencySlot.hashCode()
            return result
        }
    }
}
