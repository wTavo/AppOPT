package com.example.appopt

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.appopt.ui.navigation.AppNavigation
import com.example.appopt.ui.theme.AppTheme

import com.example.appopt.performance.PerformanceMonitor

/**
 * Actividad principal y único punto de entrada visual de la aplicación.
 *
 * Medidas de seguridad y monitoreo implementadas:
 * - Extiende de [FragmentActivity] para la integración nativa con AndroidX BiometricPrompt.
 * - Habilita [WindowManager.LayoutParams.FLAG_SECURE] en la ventana para evitar capturas de pantalla,
 *   grabaciones y previsualizaciones no deseadas en el menú de aplicaciones recientes.
 * - Inicia el [PerformanceMonitor] para el registro de FPS y latencias de renderizado en tiempo real.
 */
class MainActivity : FragmentActivity() {

    /**
     * Inicializa la configuración de seguridad de la ventana y el árbol de vistas en Jetpack Compose.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Seguridad: Bloqueo activo de capturas de pantalla y previsualización en apps recientes
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val prefs = AuthenticatorApp.instance.preferencesManager
        if (prefs.isFpsOverlayEnabled()) {
            PerformanceMonitor.start()
        }
    }

    override fun onStop() {
        super.onStop()
        PerformanceMonitor.stop()
        AuthenticatorApp.instance.accountRepository.clearMemoryCache()
    }
}