package com.example.appopt.ui.navigation

/**
 * Rutas de navegación de primer nivel en Jetpack Compose.
 */
sealed class Screen(val route: String) {
    /** Pantalla principal con la lista de cuentas y códigos en vivo. */
    data object Home : Screen("home")

    /** Pantalla de escaneo de códigos QR mediante CameraX y ML Kit. */
    data object ScanQr : Screen("scan_qr")

    /** Pantalla de registro manual de cuentas con clave Base32. */
    data object AddManual : Screen("add_manual")

    /** Pantalla de diagnóstico de seguridad y generación de Recovery Key. */
    data object Settings : Screen("settings")
}
