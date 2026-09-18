package com.example.appopt

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

/**
 * Actividad dedicada exclusivamente a la ejecución de pruebas instrumentadas de Compose en androidTest.
 *
 * Configura banderas de desbloqueo de pantalla y visibilidad sobre el bloqueo del sistema
 * para permitir la ejecución desatendida en dispositivos físicos y emuladores.
 */
class TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }
}
