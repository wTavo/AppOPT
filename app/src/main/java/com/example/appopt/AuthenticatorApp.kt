package com.example.appopt

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.example.appopt.data.local.AppDatabase
import com.example.appopt.data.local.PreferencesManager
import com.example.appopt.data.repository.AccountRepositoryImpl
import com.example.appopt.domain.repository.AccountRepository
import com.example.appopt.security.AppLockManager
import com.example.appopt.security.BiometricAuthManager
import com.example.appopt.security.CryptoManager
import com.example.appopt.security.SecureClipboardManager

/**
 * Clase principal de la Aplicación que inicializa el contenedor de dependencias
 * y los gestores de seguridad globales de la bóveda.
 */
class AuthenticatorApp : Application() {

    /**
     * Gestor criptográfico para operaciones AES-256-GCM y Android Keystore.
     */
    lateinit var cryptoManager: CryptoManager
        private set

    /**
     * Repositorio para la gestión segura de cuentas 2FA.
     */
    lateinit var accountRepository: AccountRepository
        private set

    /**
     * Gestor de autenticación biométrica y credenciales del dispositivo.
     */
    lateinit var biometricAuthManager: BiometricAuthManager
        private set

    /**
     * Gestor del estado de bloqueo global de la aplicación.
     */
    lateinit var appLockManager: AppLockManager
        private set

    /**
     * Gestor del portapapeles con limpieza automática programada.
     */
    lateinit var secureClipboardManager: SecureClipboardManager
        private set

    /**
     * Gestor de preferencias persistentes del usuario.
     */
    lateinit var preferencesManager: PreferencesManager
        private set

    /**
     * Alcance global de corrutinas ligado al ciclo de vida de la aplicación.
     * Utilizado para tareas críticas en segundo plano que deben sobrevivir a la destrucción de pantallas.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Inicializa las instancias de base de datos, seguridad y repositorios al iniciar la aplicación.
     */
    override fun onCreate() {
        super.onCreate()
        instance = this

        cryptoManager = CryptoManager()
        val database = AppDatabase.getInstance(this)
        accountRepository = AccountRepositoryImpl(database.accountDao(), cryptoManager)
        biometricAuthManager = BiometricAuthManager()
        appLockManager = AppLockManager()
        secureClipboardManager = SecureClipboardManager(this)
        preferencesManager = PreferencesManager(this)

        // Precalienta la caché de drawables vectoriales en segundo plano
        com.example.appopt.ui.brand.ServiceBrandProvider.preloadBrandIcons(this)
    }

    /**
     * Purga y sobreescribe inmediatamente cachés volátiles de descifrado ante eventos de advertencia de memoria.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::accountRepository.isInitialized) {
            accountRepository.clearMemoryCache()
        }
    }

    companion object {
        /**
         * Instancia singleton accesible de la aplicación.
         */
        lateinit var instance: AuthenticatorApp
            private set
    }
}
